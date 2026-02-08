#!/usr/bin/env python3
"""
Benchmark runner that orchestrates:
1. Starting Valkey infrastructure
2. Running benchmark application
3. Collecting system metrics (perf, CPU, disk I/O, network)
4. Outputting results to JSON

Includes variance control:
- Turbo boost disabled
- Network delay simulation (tc netem)
- CPU core pinning

Monitors benchmark CSV for phase transitions:
- Starts monitoring when WARMUP phase is done
- Stops monitoring when STEADY phase is done
"""

import argparse
import csv
import json
import os
import random
import re
import shutil
import signal
import string
import subprocess
import tempfile
import threading
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional, Callable


def generate_job_id():
    """Generate job ID in format: bench-YYYYMMDD-HHMMSS-random6"""
    now = datetime.now(timezone.utc)
    random_suffix = "".join(random.choices(string.ascii_lowercase + string.digits, k=6))
    return f"bench-{now.strftime('%Y%m%d-%H%M%S')}-{random_suffix}"


def get_timestamp():
    """Get current timestamp in ISO 8601 format"""
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


class VarianceControl:
    """Manages system settings for benchmark stability"""

    def __init__(self):
        self.turbo_boost_path = None
        self.original_turbo_state = None
        self.tc_configured = False
        self.nmi_watchdog_original = None

    def setup(self, network_delay_ms: int = 1):
        """Apply all variance control settings"""
        print("Setting up variance control...")
        self._disable_turbo_boost()
        self._setup_network_delay(network_delay_ms)
        self._disable_nmi_watchdog()
        self._set_perf_permissions()
        print("Variance control setup complete")

    def teardown(self):
        """Restore all original settings"""
        print("Restoring system settings...")
        self._restore_turbo_boost()
        self._remove_network_delay()
        self._restore_nmi_watchdog()
        print("System settings restored")

    def _disable_turbo_boost(self):
        """Disable CPU turbo boost for frequency stability"""
        intel_path = Path("/sys/devices/system/cpu/intel_pstate/no_turbo")
        amd_path = Path("/sys/devices/system/cpu/cpufreq/boost")

        try:
            if intel_path.exists():
                self.turbo_boost_path = intel_path
                self.original_turbo_state = intel_path.read_text().strip()
                subprocess.run(
                    ["sudo", "tee", str(intel_path)],
                    input=b"1",
                    capture_output=True
                )
                print("  ✓ Intel Turbo Boost disabled")
            elif amd_path.exists():
                self.turbo_boost_path = amd_path
                self.original_turbo_state = amd_path.read_text().strip()
                subprocess.run(
                    ["sudo", "tee", str(amd_path)],
                    input=b"0",
                    capture_output=True
                )
                print("  ✓ AMD Boost disabled")
            else:
                print("  ⚠ No turbo boost control found (may not be available)")
        except Exception as e:
            print(f"  ⚠ Could not disable turbo boost: {e}")

    def _restore_turbo_boost(self):
        """Restore original turbo boost setting"""
        if self.turbo_boost_path and self.original_turbo_state:
            try:
                subprocess.run(
                    ["sudo", "tee", str(self.turbo_boost_path)],
                    input=self.original_turbo_state.encode(),
                    capture_output=True
                )
                print("  ✓ Turbo boost restored")
            except Exception as e:
                print(f"  ⚠ Could not restore turbo boost: {e}")

    def _setup_network_delay(self, delay_ms: int):
        """Add artificial network delay on loopback for measurement stability"""
        try:
            subprocess.run(
                ["sudo", "tc", "qdisc", "del", "dev", "lo", "root"],
                capture_output=True
            )

            result = subprocess.run(
                ["sudo", "tc", "qdisc", "add", "dev", "lo", "root", "netem", "delay", f"{delay_ms}ms"],
                capture_output=True,
                text=True
            )

            if result.returncode == 0:
                self.tc_configured = True
                print(f"  ✓ Network delay configured: {delay_ms}ms on loopback")
            else:
                print(f"  ⚠ Could not configure network delay: {result.stderr}")
        except Exception as e:
            print(f"  ⚠ Network delay setup failed: {e}")

    def _remove_network_delay(self):
        """Remove artificial network delay"""
        if self.tc_configured:
            try:
                subprocess.run(
                    ["sudo", "tc", "qdisc", "del", "dev", "lo", "root"],
                    capture_output=True
                )
                print("  ✓ Network delay removed")
            except Exception as e:
                print(f"  ⚠ Could not remove network delay: {e}")

    def _disable_nmi_watchdog(self):
        """Disable NMI watchdog to free up PMU counter"""
        try:
            nmi_path = Path("/proc/sys/kernel/nmi_watchdog")
            if nmi_path.exists():
                self.nmi_watchdog_original = nmi_path.read_text().strip()
                subprocess.run(
                    ["sudo", "tee", str(nmi_path)],
                    input=b"0",
                    capture_output=True
                )
                print("  ✓ NMI watchdog disabled (frees PMU counter)")
        except Exception as e:
            print(f"  ⚠ Could not disable NMI watchdog: {e}")

    def _restore_nmi_watchdog(self):
        """Restore NMI watchdog"""
        if self.nmi_watchdog_original:
            try:
                subprocess.run(
                    ["sudo", "tee", "/proc/sys/kernel/nmi_watchdog"],
                    input=self.nmi_watchdog_original.encode(),
                    capture_output=True
                )
                print("  ✓ NMI watchdog restored")
            except Exception as e:
                print(f"  ⚠ Could not restore NMI watchdog: {e}")

    def _set_perf_permissions(self):
        """Set kernel parameters for perf access"""
        try:
            subprocess.run(
                ["sudo", "sysctl", "-w", "kernel.perf_event_paranoid=-1"],
                capture_output=True
            )
            subprocess.run(
                ["sudo", "sysctl", "-w", "kernel.kptr_restrict=0"],
                capture_output=True
            )
            print("  ✓ Perf permissions configured")
        except Exception as e:
            print(f"  ⚠ Could not set perf permissions: {e}")

    def get_status(self) -> dict:
        """Return current variance control status"""
        return {
            "turbo_boost_disabled": self.turbo_boost_path is not None,
            "network_delay_configured": self.tc_configured,
            "nmi_watchdog_disabled": self.nmi_watchdog_original is not None
        }


class CSVWatcher:
    """Watches a CSV file for phase transitions using tail -f"""

    def __init__(self, csv_path: Path):
        self.csv_path = csv_path
        self.warmup_done_event = threading.Event()
        self.steady_done_event = threading.Event()
        self.tail_process: Optional[subprocess.Popen] = None
        self.watcher_thread: Optional[threading.Thread] = None
        self.header_seen = False

    def start(self):
        """Start watching the CSV file using tail -f"""
        # Wait for file to exist first
        timeout = 30
        start = time.time()
        while not self.csv_path.exists():
            if time.time() - start > timeout:
                raise RuntimeError(f"Timeout waiting for CSV file: {self.csv_path}")
            time.sleep(0.1)

        # Start tail -f process
        self.tail_process = subprocess.Popen(
            ["tail", "-f", "-n", "+1", str(self.csv_path)],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True
        )

        # Start reader thread
        self.watcher_thread = threading.Thread(target=self._read_loop, daemon=True)
        self.watcher_thread.start()
        print(f"Started watching CSV (tail -f): {self.csv_path}")

    def stop(self):
        """Stop watching"""
        if self.tail_process:
            self.tail_process.terminate()
            try:
                self.tail_process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                self.tail_process.kill()
            self.tail_process = None

        if self.watcher_thread:
            self.watcher_thread.join(timeout=2)

        print("CSV watcher stopped")

    def wait_for_warmup_done(self, timeout: float = None) -> bool:
        """Block until warmup phase is done"""
        return self.warmup_done_event.wait(timeout=timeout)

    def wait_for_steady_done(self, timeout: float = None) -> bool:
        """Block until steady phase is done"""
        return self.steady_done_event.wait(timeout=timeout)

    def _read_loop(self):
        """Read lines from tail -f stdout"""
        if not self.tail_process:
            return

        for line in self.tail_process.stdout:
            line = line.strip()
            if not line:
                continue

            # Skip header
            if line.startswith("phase,"):
                self.header_seen = True
                continue

            self._process_line(line)

            # Exit if both phases done
            if self.warmup_done_event.is_set() and self.steady_done_event.is_set():
                break

    def _process_line(self, line: str):
        """Process a single CSV line"""
        try:
            parts = line.split(",", 4)  # Only need first few columns
            if len(parts) >= 2:
                phase = parts[0]
                status = parts[1]

                print(f"  [CSV] Phase: {phase}, Status: {status}")

                if phase == "WARMUP" and status == "done":
                    print("  → WARMUP phase complete, starting monitoring")
                    self.warmup_done_event.set()

                elif phase == "STEADY" and status == "done":
                    print("  → STEADY phase complete, stopping monitoring")
                    self.steady_done_event.set()

        except Exception as e:
            print(f"  [CSV] Parse error: {e}")


class MonitoringManager:
    """Manages background monitoring processes"""

    def __init__(self, work_dir: Path):
        self.work_dir = work_dir
        self.processes = {}
        self.output_files = {}
        self._file_handles = {}
        self.hardware_perf_available = False

    def _check_hardware_perf_events(self) -> bool:
        """Check if hardware perf events are available (requires bare metal)"""
        try:
            result = subprocess.run(
                ["perf", "stat", "-e", "cycles", "--", "sleep", "0.1"],
                capture_output=True,
                text=True,
                timeout=5
            )
            available = "<not supported>" not in result.stderr and "<not counted>" not in result.stderr
            if available:
                print("Hardware perf events: AVAILABLE (bare metal detected)")
            else:
                print("Hardware perf events: NOT AVAILABLE (VM/container detected)")
            return available
        except Exception as e:
            print(f"Hardware perf check failed: {e}")
            return False

    def start_mpstat(self):
        """Start CPU monitoring with mpstat"""
        output_file = self.work_dir / "mpstat.log"
        self.output_files["mpstat"] = output_file
        fh = open(output_file, "w")
        self._file_handles["mpstat"] = fh
        proc = subprocess.Popen(
            ["mpstat", "1"],
            stdout=fh,
            stderr=subprocess.DEVNULL,
            preexec_fn=os.setsid
        )
        self.processes["mpstat"] = proc

    def start_iostat(self):
        """Start disk I/O monitoring with iostat"""
        output_file = self.work_dir / "iostat.log"
        self.output_files["iostat"] = output_file
        fh = open(output_file, "w")
        self._file_handles["iostat"] = fh
        proc = subprocess.Popen(
            ["iostat", "-x", "1"],
            stdout=fh,
            stderr=subprocess.DEVNULL,
            preexec_fn=os.setsid
        )
        self.processes["iostat"] = proc

    def start_sar_network(self):
        """Start network monitoring with sar"""
        output_file = self.work_dir / "sar_network.log"
        self.output_files["sar_network"] = output_file
        fh = open(output_file, "w")
        self._file_handles["sar_network"] = fh
        proc = subprocess.Popen(
            ["sar", "-n", "DEV", "1"],
            stdout=fh,
            stderr=subprocess.DEVNULL,
            preexec_fn=os.setsid
        )
        self.processes["sar_network"] = proc

    def start_perf_stat(self, pid: int):
        """Start perf stat attached to benchmark process"""
        output_file = self.work_dir / "perf_stat.log"
        self.output_files["perf_stat"] = output_file
        fh = open(output_file, "w")
        self._file_handles["perf_stat"] = fh

        self.hardware_perf_available = self._check_hardware_perf_events()

        if self.hardware_perf_available:
            events = ",".join([
                "cycles",
                "instructions",
                "cache-references",
                "cache-misses",
                "branches",
                "branch-misses",
                "context-switches",
                "cpu-migrations",
                "page-faults"
            ])
        else:
            events = "task-clock,context-switches,cpu-migrations,page-faults"

        proc = subprocess.Popen(
            ["perf", "stat", "-e", events, "-p", str(pid)],
            stdout=subprocess.DEVNULL,
            stderr=fh
        )
        self.processes["perf_stat"] = proc
        print(f"Started perf stat on PID {pid}")

    def start_all(self, benchmark_pid: int):
        """Start all monitoring processes"""
        self.start_mpstat()
        self.start_iostat()
        self.start_sar_network()
        self.start_perf_stat(benchmark_pid)
        print("All monitoring processes started")

    def stop_all(self):
        """Stop all monitoring processes"""
        # Handle perf specially
        if "perf_stat" in self.processes:
            perf_proc = self.processes.pop("perf_stat")
            try:
                if perf_proc.poll() is None:
                    perf_proc.send_signal(signal.SIGINT)
                perf_proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                perf_proc.kill()
                perf_proc.wait(timeout=2)
            except Exception as e:
                print(f"Warning stopping perf: {e}")

        # Stop other processes
        for name, proc in self.processes.items():
            try:
                os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
                proc.wait(timeout=5)
            except (ProcessLookupError, subprocess.TimeoutExpired):
                try:
                    os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
                except ProcessLookupError:
                    pass
            except Exception as e:
                print(f"Warning stopping {name}: {e}")

        # Close all file handles
        for fh in self._file_handles.values():
            try:
                fh.close()
            except Exception:
                pass

        print("All monitoring processes stopped")


def parse_mpstat(filepath: Path) -> dict:
    """Parse mpstat output for CPU statistics"""
    result = {
        "user_percent_avg": 0.0,
        "user_percent_max": 0.0,
        "system_percent_avg": 0.0,
        "system_percent_max": 0.0,
        "idle_percent_avg": 0.0,
        "idle_percent_min": 100.0,
        "iowait_percent_avg": 0.0,
        "steal_percent_avg": 0.0
    }

    if not filepath.exists():
        return result

    user_values = []
    system_values = []
    idle_values = []
    iowait_values = []
    steal_values = []

    with open(filepath) as f:
        for line in f:
            if "all" in line and not line.startswith("Average"):
                parts = line.split()
                if len(parts) >= 12:
                    try:
                        user_values.append(float(parts[2]))
                        system_values.append(float(parts[4]))
                        iowait_values.append(float(parts[5]))
                        steal_values.append(float(parts[8]))
                        idle_values.append(float(parts[11]))
                    except (ValueError, IndexError):
                        continue

    if user_values:
        result["user_percent_avg"] = round(sum(user_values) / len(user_values), 1)
        result["user_percent_max"] = round(max(user_values), 1)
    if system_values:
        result["system_percent_avg"] = round(sum(system_values) / len(system_values), 1)
        result["system_percent_max"] = round(max(system_values), 1)
    if idle_values:
        result["idle_percent_avg"] = round(sum(idle_values) / len(idle_values), 1)
        result["idle_percent_min"] = round(min(idle_values), 1)
    if iowait_values:
        result["iowait_percent_avg"] = round(sum(iowait_values) / len(iowait_values), 1)
    if steal_values:
        result["steal_percent_avg"] = round(sum(steal_values) / len(steal_values), 1)

    return result


def parse_iostat(filepath: Path) -> dict:
    """Parse iostat output for disk I/O statistics"""
    result = {
        "read_bytes": 0,
        "write_bytes": 0,
        "read_iops": 0,
        "write_iops": 0
    }

    if not filepath.exists():
        return result

    read_kb_values = []
    write_kb_values = []
    read_iops_values = []
    write_iops_values = []

    with open(filepath) as f:
        in_device_section = False
        for line in f:
            if line.startswith("Device"):
                in_device_section = True
                continue
            if in_device_section and line.strip():
                parts = line.split()
                if len(parts) >= 6 and not parts[0].startswith("loop"):
                    try:
                        read_iops_values.append(float(parts[1]))
                        write_iops_values.append(float(parts[2]))
                        read_kb_values.append(float(parts[3]))
                        write_kb_values.append(float(parts[4]))
                    except (ValueError, IndexError):
                        continue
            elif in_device_section and not line.strip():
                in_device_section = False

    if read_kb_values:
        result["read_bytes"] = int(sum(read_kb_values) * 1024)
        result["read_iops"] = int(sum(read_iops_values) / len(read_iops_values))
    if write_kb_values:
        result["write_bytes"] = int(sum(write_kb_values) * 1024)
        result["write_iops"] = int(sum(write_iops_values) / len(write_iops_values))

    return result


def parse_sar_network(filepath: Path) -> dict:
    """Parse sar network output"""
    result = {
        "bytes_sent": 0,
        "bytes_recv": 0,
        "packets_sent": 0,
        "packets_recv": 0
    }

    if not filepath.exists():
        return result

    rx_bytes = []
    tx_bytes = []
    rx_packets = []
    tx_packets = []

    with open(filepath) as f:
        for line in f:
            if "lo" in line or "IFACE" in line or "Average" in line:
                continue
            parts = line.split()
            if len(parts) >= 9:
                try:
                    rx_packets.append(float(parts[2]))
                    tx_packets.append(float(parts[3]))
                    rx_bytes.append(float(parts[4]) * 1024)
                    tx_bytes.append(float(parts[5]) * 1024)
                except (ValueError, IndexError):
                    continue

    if rx_bytes:
        result["bytes_recv"] = int(sum(rx_bytes))
        result["bytes_sent"] = int(sum(tx_bytes))
        result["packets_recv"] = int(sum(rx_packets))
        result["packets_sent"] = int(sum(tx_packets))

    return result


def parse_perf_stat(filepath: Path, hardware_available: bool) -> dict:
    """Parse perf stat output"""
    result = {
        "cpu_cycles": None,
        "instructions": None,
        "ipc": None,
        "cache_references": None,
        "cache_misses": None,
        "cache_miss_rate": None,
        "branches": None,
        "branch_misses": None,
        "branch_miss_rate": None,
        "task_clock_ms": 0.0,
        "context_switches": 0,
        "cpu_migrations": 0,
        "page_faults": 0
    }

    if not filepath.exists():
        print(f"Warning: perf stat file not found: {filepath}")
        return result

    content = filepath.read_text()

    # Parse task-clock
    task_clock_match = re.search(r"([\d,.]+)\s+msec\s+task-clock", content)
    if task_clock_match:
        result["task_clock_ms"] = float(task_clock_match.group(1).replace(",", ""))

    # Software events
    software_patterns = {
        "context_switches": r"([\d,]+)\s+context-switches",
        "cpu_migrations": r"([\d,]+)\s+cpu-migrations",
        "page_faults": r"([\d,]+)\s+page-faults"
    }

    for key, pattern in software_patterns.items():
        match = re.search(pattern, content)
        if match:
            result[key] = int(match.group(1).replace(",", ""))

    # Hardware events
    if hardware_available:
        hardware_patterns = {
            "cpu_cycles": r"([\d,]+)\s+cycles",
            "instructions": r"([\d,]+)\s+instructions",
            "cache_references": r"([\d,]+)\s+cache-references",
            "cache_misses": r"([\d,]+)\s+cache-misses",
            "branches": r"([\d,]+)\s+branches",
            "branch_misses": r"([\d,]+)\s+branch-misses",
        }

        for key, pattern in hardware_patterns.items():
            match = re.search(pattern, content)
            if match:
                result[key] = int(match.group(1).replace(",", ""))

        # Calculate derived metrics
        if result["cpu_cycles"] and result["instructions"]:
            result["ipc"] = round(result["instructions"] / result["cpu_cycles"], 2)

        if result["cache_references"] and result["cache_references"] > 0:
            result["cache_miss_rate"] = round(
                100.0 * (result["cache_misses"] or 0) / result["cache_references"], 2
            )

        if result["branches"] and result["branches"] > 0:
            result["branch_miss_rate"] = round(
                100.0 * (result["branch_misses"] or 0) / result["branches"], 2
            )

    return result


def parse_benchmark_csv(filepath: Path) -> tuple[dict, float]:
    """Parse benchmark CSV - extract STEADY phase done results and elapsed time"""
    operations = {}
    elapsed_seconds = 0.0

    if not filepath.exists():
        return operations, elapsed_seconds

    with open(filepath) as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row["phase"] == "STEADY" and row["status"] == "done":
                cmd_name = row["command_name"]
                operations[cmd_name] = {
                    "total_requests": int(row["num_requests"]),
                    "successful_requests": int(row["successful_requests"]),
                    "failed_requests": int(row["failed_requests"]),
                    "latency_min_us": int(row["latency_min_us"]),
                    "latency_max_us": int(row["latency_max_us"]),
                    "histogram_buckets": json.loads(row["histogram_json"])
                }
                # Get elapsed time from the last STEADY done row
                elapsed_seconds = float(row["time_elapsed"])

    return operations, elapsed_seconds

class BenchmarkRunner:
    """Main benchmark orchestration class"""

    # Server cores are always fixed
    INFRA_CORES = "0-3"

    def __init__(
        self,
        project_dir: Path,
        output_file: Path,
        duration: int = 60,
        warmup: int = 10,
        skip_infra: bool = False,
        network_delay_ms: int = 1
    ):
        self.project_dir = project_dir
        self.output_file = output_file
        self.duration = duration
        self.warmup = warmup
        self.skip_infra = skip_infra
        self.network_delay_ms = network_delay_ms
        self.job_id = generate_job_id()
        self.timestamp = get_timestamp()
        self.variance_control = VarianceControl()

        # Calculate benchmark cores dynamically
        cpu_count = os.cpu_count() or 8
        if cpu_count > 4:
            self.benchmark_cores = f"4-{cpu_count - 1}"
        else:
            self.benchmark_cores = "0-3"
            print(f"WARNING: Only {cpu_count} cores available, benchmark will share cores with server")

        print(f"Core allocation: Server={self.INFRA_CORES}, Benchmark={self.benchmark_cores}")

    def start_infrastructure(self):
        """Start Valkey infrastructure on dedicated cores"""
        if self.skip_infra:
            print("Skipping infrastructure setup (--skip-infra)")
            return

        print(f"Starting Valkey infrastructure on cores {self.INFRA_CORES}...")

        # Stop any existing processes
        subprocess.run(["make", "cluster-stop"], cwd=self.project_dir, capture_output=True)
        subprocess.run(["pkill", "-f", "valkey-server"], capture_output=True)
        time.sleep(1)

        # Clean work directory
        work_dir = self.project_dir / "work"
        if work_dir.exists():
            shutil.rmtree(work_dir)

        # Start cluster with core pinning
        print(f"Running: taskset -c {self.INFRA_CORES} make cluster-init")
        result = subprocess.run(
            ["taskset", "-c", self.INFRA_CORES, "make", "cluster-init"],
            cwd=self.project_dir,
            timeout=600
        )

        if result.returncode != 0:
            raise RuntimeError(f"Failed to start infrastructure (exit code {result.returncode})")

        time.sleep(2)

        # Verify
        valkey_cli = self.project_dir / "work/valkey/bin/valkey-cli"
        result = subprocess.run(
            [str(valkey_cli), "-p", "7379", "ping"],
            capture_output=True,
            text=True
        )
        if "PONG" not in result.stdout:
            raise RuntimeError("Valkey infrastructure verification failed")

        print("Valkey infrastructure started and verified")

    def stop_infrastructure(self):
        """Stop Valkey infrastructure"""
        if self.skip_infra:
            return

        print("Stopping Valkey infrastructure...")
        subprocess.run(["make", "cluster-stop"], cwd=self.project_dir, capture_output=True)
        subprocess.run(["make", "clean"], cwd=self.project_dir, capture_output=True)
        print("Valkey infrastructure stopped")

    def run_benchmark(self, output_csv: Path) -> subprocess.Popen:
        """Start the benchmark process on dedicated cores"""
        benchmark_script = self.project_dir / ".github/scripts/mock_benchmark.py"

        cmd = [
            "taskset", "-c", self.benchmark_cores,
            "python3", str(benchmark_script),
            "--duration", str(self.duration),
            "--warmup", str(self.warmup),
            "--output", str(output_csv)
        ]
        print(f"Starting benchmark on cores {self.benchmark_cores}")

        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE
        )
        return proc

    def run(self):
        """Execute the full benchmark workflow"""
        # Setup variance control
        self.variance_control.setup(network_delay_ms=self.network_delay_ms)

        with tempfile.TemporaryDirectory() as tmpdir:
            work_dir = Path(tmpdir)
            benchmark_csv = work_dir / "benchmark_results.csv"

            try:
                self.start_infrastructure()

                print(f"Starting benchmark (warmup: {self.warmup}s, duration: {self.duration}s)...")
                benchmark_proc = self.run_benchmark(benchmark_csv)

                csv_watcher = CSVWatcher(benchmark_csv)
                csv_watcher.start()

                print("Waiting for WARMUP phase to complete...")
                if not csv_watcher.wait_for_warmup_done(timeout=self.warmup + 60):
                    raise RuntimeError("Timeout waiting for warmup")

                print("Starting monitoring for STEADY phase...")
                monitor = MonitoringManager(work_dir)
                monitor.start_all(benchmark_proc.pid)

                print("Waiting for STEADY phase to complete...")
                if not csv_watcher.wait_for_steady_done(timeout=self.duration + 60):
                    raise RuntimeError("Timeout waiting for steady phase")

                monitor.stop_all()
                csv_watcher.stop()

                stdout, stderr = benchmark_proc.communicate(timeout=10)
                print(f"Benchmark stderr:\n{stderr.decode()}")

                # Parse results - get elapsed from CSV
                operations, elapsed_seconds = parse_benchmark_csv(benchmark_csv)
                perf_counters = parse_perf_stat(
                    monitor.output_files.get("perf_stat", Path()),
                    monitor.hardware_perf_available
                )
                cpu_stats = parse_mpstat(monitor.output_files.get("mpstat", Path()))
                disk_stats = parse_iostat(monitor.output_files.get("iostat", Path()))
                network_stats = parse_sar_network(monitor.output_files.get("sar_network", Path()))

                output_csv_path = self.output_file.with_suffix('.csv')
                if benchmark_csv.exists():
                    shutil.copy(benchmark_csv, output_csv_path)
                    print(f"Benchmark CSV saved to {output_csv_path}")

                # Build result - original structure
                result = {
                    "job_id": self.job_id,
                    "timestamp": self.timestamp,
                    "environment": {
                        "hardware_perf_available": monitor.hardware_perf_available,
                        "cpu_count": os.cpu_count()
                    },
                    "benchmark_config": {
                        "warmup": {
                            "duration_seconds": self.warmup
                        },
                        "measurement": {
                            "duration_seconds": self.duration,
                            "target_rps": 10000
                        }
                    },
                    "client_config": {
                        "client_name": "valkey-glide",
                        "driver": "valkey-glide",
                        "version": "1.0.0",
                        "connection_pool_size": 24,
                        "read_from": "primary"
                    },
                    "elapsed_seconds": int(elapsed_seconds),
                    "operations": operations,
                    "perf": {
                        "counters": perf_counters,
                        "flame_graph_url": f"s3://benchmark-artifacts/{self.job_id}/cpu-flamegraph.svg"
                    },
                    "cpu": cpu_stats,
                    "io": {
                        "disk": disk_stats,
                        "network": network_stats
                    }
                }

                with open(self.output_file, "w") as f:
                    json.dump(result, f, indent=2)

                print(f"\nResults written to {self.output_file}")

            finally:
                self.stop_infrastructure()
                self.variance_control.teardown()


def main():
    parser = argparse.ArgumentParser(description="Benchmark runner with variance control")
    parser.add_argument(
        "--output",
        type=str,
        default="benchmark_results.json",
        help="Output JSON file path"
    )
    parser.add_argument(
        "--duration",
        type=int,
        default=60,
        help="Steady state duration in seconds"
    )
    parser.add_argument(
        "--warmup",
        type=int,
        default=10,
        help="Warmup duration in seconds"
    )
    parser.add_argument(
        "--project-dir",
        type=str,
        default=None,
        help="Project directory (defaults to current directory)"
    )
    parser.add_argument(
        "--skip-infra",
        action="store_true",
        help="Skip Valkey infrastructure setup (for testing)"
    )
    parser.add_argument(
        "--network-delay",
        type=int,
        default=1,
        help="Artificial network delay in ms (default: 1)"
    )
    args = parser.parse_args()

    project_dir = Path(args.project_dir) if args.project_dir else Path.cwd()
    output_file = Path(args.output)

    runner = BenchmarkRunner(
        project_dir,
        output_file,
        args.duration,
        args.warmup,
        args.skip_infra,
        args.network_delay
    )
    runner.run()


if __name__ == "__main__":
    main()