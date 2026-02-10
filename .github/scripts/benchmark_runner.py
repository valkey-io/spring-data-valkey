#!/usr/bin/env python3
"""
Benchmark runner that orchestrates:
1. Starting Valkey infrastructure
2. Running benchmark application
3. Collecting system metrics (perf, CPU, disk I/O, network)
4. Outputting results to JSON and PostgreSQL

Reads configuration from external JSON files.
Phase completion is determined by workload config (duration or requests).
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
from typing import Optional, Tuple


def generate_job_id():
    now = datetime.now(timezone.utc)
    random_suffix = "".join(random.choices(string.ascii_lowercase + string.digits, k=6))
    return f"bench-{now.strftime('%Y%m%d-%H%M%S')}-{random_suffix}"


def get_timestamp():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def load_json_config(filepath: Path) -> dict:
    """Load JSON configuration file"""
    with open(filepath) as f:
        return json.load(f)


class PostgreSQLPublisher:
    """Publishes benchmark results to Aurora PostgreSQL"""

    TABLE_NAME = "benchmark_results"

    def __init__(
        self,
        host: str,
        port: int = 5432,
        database: str = "postgres",
        secret_name: str = None,
        region: str = "us-east-1"
    ):
        self.host = host
        self.port = port
        self.database = database
        self.secret_name = secret_name
        self.region = region
        self.connection = None

    def _get_credentials(self) -> dict:
        """Retrieve database credentials from AWS Secrets Manager"""
        import boto3
        client = boto3.client('secretsmanager', region_name=self.region)
        response = client.get_secret_value(SecretId=self.secret_name)
        secret = json.loads(response['SecretString'])
        return {
            'username': secret.get('username'),
            'password': secret.get('password')
        }

    def connect(self):
        """Establish connection to PostgreSQL"""
        import psycopg2
        
        creds = self._get_credentials()
        self.connection = psycopg2.connect(
            host=self.host,
            port=self.port,
            database=self.database,
            user=creds['username'],
            password=creds['password'],
            sslmode='require',
            connect_timeout=10
        )
        print(f"✓ Connected to PostgreSQL at {self.host}:{self.port}/{self.database}")

    def _ensure_table_exists(self):
        """Create the benchmark_results table if it doesn't exist"""
        create_table_sql = f"""
        CREATE TABLE IF NOT EXISTS {self.TABLE_NAME} (
            id SERIAL PRIMARY KEY,
            job_id VARCHAR(100) UNIQUE NOT NULL,
            timestamp TIMESTAMPTZ NOT NULL,
            config JSONB NOT NULL,
            results JSONB NOT NULL,
            created_at TIMESTAMPTZ DEFAULT NOW()
        );
        
        CREATE INDEX IF NOT EXISTS idx_{self.TABLE_NAME}_job_id 
            ON {self.TABLE_NAME} (job_id);
        CREATE INDEX IF NOT EXISTS idx_{self.TABLE_NAME}_timestamp 
            ON {self.TABLE_NAME} (timestamp);
        CREATE INDEX IF NOT EXISTS idx_{self.TABLE_NAME}_config_client 
            ON {self.TABLE_NAME} ((config->'client'->>'client_name'));
        CREATE INDEX IF NOT EXISTS idx_{self.TABLE_NAME}_config_workload 
            ON {self.TABLE_NAME} ((config->'workload'->'benchmark-profile'->>'name'));
        """

        with self.connection.cursor() as cursor:
            cursor.execute(create_table_sql)
        self.connection.commit()
        print(f"✓ Ensured table '{self.TABLE_NAME}' exists")

    def publish(self, job_id: str, timestamp: str, config: dict, results: dict):
        """Publish benchmark results to PostgreSQL"""
        from psycopg2.extras import Json

        if not self.connection:
            self.connect()

        self._ensure_table_exists()

        insert_sql = f"""
        INSERT INTO {self.TABLE_NAME} (job_id, timestamp, config, results)
        VALUES (%s, %s, %s, %s)
        ON CONFLICT (job_id) DO UPDATE SET
            timestamp = EXCLUDED.timestamp,
            config = EXCLUDED.config,
            results = EXCLUDED.results,
            created_at = NOW()
        RETURNING id
        """

        with self.connection.cursor() as cursor:
            cursor.execute(insert_sql, (job_id, timestamp, Json(config), Json(results)))
            row_id = cursor.fetchone()[0]
        self.connection.commit()
        print(f"✓ Published results for job '{job_id}' (row id: {row_id})")

    def close(self):
        """Close the database connection"""
        if self.connection:
            self.connection.close()
            self.connection = None
            print("✓ PostgreSQL connection closed")

    def __enter__(self):
        self.connect()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()
        return False


class VarianceControl:
    """Manages system settings for benchmark stability"""

    def __init__(self):
        self.turbo_boost_path = None
        self.original_turbo_state = None
        self.tc_configured = False
        self.nmi_watchdog_original = None

    def setup(self, network_delay_ms: int = 1):
        print("Setting up variance control...")
        self._disable_turbo_boost()
        self._setup_network_delay(network_delay_ms)
        self._disable_nmi_watchdog()
        self._set_perf_permissions()
        print("Variance control setup complete")

    def teardown(self):
        print("Restoring system settings...")
        self._restore_turbo_boost()
        self._remove_network_delay()
        self._restore_nmi_watchdog()
        print("System settings restored")

    def _disable_turbo_boost(self):
        intel_path = Path("/sys/devices/system/cpu/intel_pstate/no_turbo")
        amd_path = Path("/sys/devices/system/cpu/cpufreq/boost")

        try:
            if intel_path.exists():
                self.turbo_boost_path = intel_path
                self.original_turbo_state = intel_path.read_text().strip()
                subprocess.run(["sudo", "tee", str(intel_path)], input=b"1", capture_output=True)
                print("  ✓ Intel Turbo Boost disabled")
            elif amd_path.exists():
                self.turbo_boost_path = amd_path
                self.original_turbo_state = amd_path.read_text().strip()
                subprocess.run(["sudo", "tee", str(amd_path)], input=b"0", capture_output=True)
                print("  ✓ AMD Boost disabled")
            else:
                print("  ⚠ No turbo boost control found")
        except Exception as e:
            print(f"  ⚠ Could not disable turbo boost: {e}")

    def _restore_turbo_boost(self):
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
        try:
            subprocess.run(["sudo", "tc", "qdisc", "del", "dev", "lo", "root"], capture_output=True)
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
        if self.tc_configured:
            try:
                subprocess.run(["sudo", "tc", "qdisc", "del", "dev", "lo", "root"], capture_output=True)
                print("  ✓ Network delay removed")
            except Exception as e:
                print(f"  ⚠ Could not remove network delay: {e}")

    def _disable_nmi_watchdog(self):
        try:
            nmi_path = Path("/proc/sys/kernel/nmi_watchdog")
            if nmi_path.exists():
                self.nmi_watchdog_original = nmi_path.read_text().strip()
                subprocess.run(["sudo", "tee", str(nmi_path)], input=b"0", capture_output=True)
                print("  ✓ NMI watchdog disabled")
        except Exception as e:
            print(f"  ⚠ Could not disable NMI watchdog: {e}")

    def _restore_nmi_watchdog(self):
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
        try:
            subprocess.run(["sudo", "sysctl", "-w", "kernel.perf_event_paranoid=-1"], capture_output=True)
            subprocess.run(["sudo", "sysctl", "-w", "kernel.kptr_restrict=0"], capture_output=True)
            print("  ✓ Perf permissions configured")
        except Exception as e:
            print(f"  ⚠ Could not set perf permissions: {e}")


class CSVWatcher:
    """Watches a CSV file for phase transitions using tail -f"""

    def __init__(self, csv_path: Path):
        self.csv_path = csv_path
        self.warmup_done_event = threading.Event()
        self.steady_done_event = threading.Event()
        self.tail_process: Optional[subprocess.Popen] = None
        self.watcher_thread: Optional[threading.Thread] = None

    def start(self):
        """Start watching the CSV file using tail -f"""
        print(f"Waiting for CSV file: {self.csv_path}")
        while not self.csv_path.exists():
            time.sleep(0.1)

        self.tail_process = subprocess.Popen(
            ["tail", "-f", "-n", "+1", str(self.csv_path)],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True
        )

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

    def wait_for_warmup_done(self) -> None:
        """Block until warmup phase is done (no timeout)"""
        self.warmup_done_event.wait()

    def wait_for_steady_done(self) -> None:
        """Block until steady phase is done (no timeout)"""
        self.steady_done_event.wait()

    def _read_loop(self):
        """Read lines from tail -f stdout"""
        if not self.tail_process:
            return

        for line in self.tail_process.stdout:
            line = line.strip()
            if not line or line.startswith("phase,"):
                continue

            self._process_line(line)

            if self.warmup_done_event.is_set() and self.steady_done_event.is_set():
                break

    def _process_line(self, line: str):
        """Process a single CSV line"""
        try:
            parts = line.split(",", 4)
            if len(parts) >= 2:
                phase = parts[0]
                status = parts[1]

                print(f"  [CSV] Phase: {phase}, Status: {status}")

                if phase == "WARMUP" and status == "done":
                    if not self.warmup_done_event.is_set():
                        print("  → WARMUP phase complete, starting monitoring")
                        self.warmup_done_event.set()
                elif phase == "STEADY" and status == "done":
                    if not self.steady_done_event.is_set():
                        print("  → STEADY phase complete, stopping monitoring")
                        self.steady_done_event.set()
        except Exception as e:
            print(f"  [CSV] Parse error: {e}")


class MonitoringManager:
    """Manages background monitoring processes"""
    FLAMEGRAPH_PATH = "/opt/FlameGraph"

    def __init__(self, work_dir: Path):
        self.work_dir = work_dir
        self.processes = {}
        self.output_files = {}
        self._file_handles = {}
        self.hardware_perf_available = False
        self.perf_record_process = None
        self.perf_data_file = None

    def _check_hardware_perf_events(self) -> bool:
        try:
            result = subprocess.run(
                ["perf", "stat", "-e", "cycles", "--", "sleep", "0.1"],
                capture_output=True,
                text=True,
                timeout=5
            )
            available = "<not supported>" not in result.stderr and "<not counted>" not in result.stderr
            print(f"Hardware perf events: {'AVAILABLE' if available else 'NOT AVAILABLE'}")
            return available
        except Exception as e:
            print(f"Hardware perf check failed: {e}")
            return False

    def _ensure_flamegraph_tools(self) -> bool:
        """Check if FlameGraph tools are available"""
        stackcollapse = Path(self.FLAMEGRAPH_PATH) / "stackcollapse-perf.pl"
        
        if stackcollapse.exists():
            return True
        else:
            print(f"⚠ FlameGraph tools not found at {self.FLAMEGRAPH_PATH}")
            print("  Install with: git clone https://github.com/brendangregg/FlameGraph.git /opt/FlameGraph")
            return False

    def start_mpstat(self):
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
        output_file = self.work_dir / "perf_stat.log"
        self.output_files["perf_stat"] = output_file
        fh = open(output_file, "w")
        self._file_handles["perf_stat"] = fh

        self.hardware_perf_available = self._check_hardware_perf_events()

        if self.hardware_perf_available:
            events = "cycles,instructions,cache-references,cache-misses,branch-instructions,branch-misses,context-switches,cpu-migrations,page-faults"
        else:
            events = "context-switches,cpu-migrations,page-faults"

        proc = subprocess.Popen(
            ["perf", "stat", "-e", events, "-p", str(pid)],
            stdout=subprocess.DEVNULL,
            stderr=fh
        )
        self.processes["perf_stat"] = proc
        print(f"Started perf stat on PID {pid}")

    def start_perf_record(self, pid: int):
        """Start perf record for flame graph generation"""
        self.perf_data_file = self.work_dir / "perf_record.data"
        
        cmd = [
            "perf", "record",
            "-F", "99",
            "-p", str(pid),
            "-g",
            "-o", str(self.perf_data_file)
        ]
        
        self.perf_record_process = subprocess.Popen(
            cmd,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )
        print(f"Started perf record on PID {pid}")

    def stop_perf_record(self):
        """Stop perf record"""
        if self.perf_record_process:
            try:
                if self.perf_record_process.poll() is None:
                    self.perf_record_process.send_signal(signal.SIGINT)
                    self.perf_record_process.wait(timeout=10)
                print("✓ Perf record stopped")
            except subprocess.TimeoutExpired:
                self.perf_record_process.kill()
                self.perf_record_process.wait()
                print("⚠ Perf record killed (timeout)")
            except Exception as e:
                print(f"⚠ Error stopping perf record: {e}")
            finally:
                self.perf_record_process = None

    def generate_collapsed_stacks(self) -> Optional[Path]:
        """Generate collapsed stacks from perf data for flame graphs"""
        if not self.perf_data_file or not self.perf_data_file.exists():
            print("⚠ No perf data file found")
            return None

        if self.perf_data_file.stat().st_size == 0:
            print("⚠ Perf data file is empty")
            return None

        if not self._ensure_flamegraph_tools():
            return None

        collapsed_file = self.work_dir / "collapsed.txt"
        stackcollapse = Path(self.FLAMEGRAPH_PATH) / "stackcollapse-perf.pl"

        try:
            print("Generating collapsed stacks...")
            
            # Run: perf script -i perf.data | stackcollapse-perf.pl > collapsed.txt
            perf_script = subprocess.Popen(
                ["perf", "script", "-i", str(self.perf_data_file)],
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL
            )
            
            with open(collapsed_file, "w") as f:
                stackcollapse_proc = subprocess.Popen(
                    ["perl", str(stackcollapse)],
                    stdin=perf_script.stdout,
                    stdout=f,
                    stderr=subprocess.DEVNULL
                )
                perf_script.stdout.close()
                stackcollapse_proc.wait(timeout=60)
            
            perf_script.wait(timeout=10)

            if collapsed_file.exists() and collapsed_file.stat().st_size > 0:
                line_count = sum(1 for _ in open(collapsed_file))
                file_size = collapsed_file.stat().st_size
                print(f"✓ Generated collapsed stacks: {line_count} lines, {file_size} bytes")
                self.output_files["collapsed_stacks"] = collapsed_file
                return collapsed_file
            else:
                print("⚠ Collapsed stacks file is empty")
                return None

        except subprocess.TimeoutExpired:
            print("⚠ Timeout generating collapsed stacks")
            return None
        except Exception as e:
            print(f"⚠ Failed to generate collapsed stacks: {e}")
            import traceback
            traceback.print_exc()
            return None

    def start_all(self, benchmark_pid: int):
        """Start all monitoring processes"""
        self.start_mpstat()
        self.start_iostat()
        self.start_sar_network()
        self.start_perf_stat(benchmark_pid)
        self.start_perf_record(benchmark_pid)
        print("All monitoring processes started")

    def stop_all(self):
        """Stop all monitoring processes"""
        # Stop perf record first (needs SIGINT)
        self.stop_perf_record()

        # Stop perf stat (needs SIGINT)
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

        # Stop other processes (use SIGTERM to process group)
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

        # Close file handles
        for fh in self._file_handles.values():
            try:
                fh.close()
            except Exception:
                pass

        print("All monitoring processes stopped")


def parse_mpstat(filepath: Path) -> dict:
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

    user_values, system_values, idle_values, iowait_values, steal_values = [], [], [], [], []

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
    result = {"read_bytes": 0, "write_bytes": 0, "read_iops": 0, "write_iops": 0}

    if not filepath.exists():
        return result

    read_kb, write_kb, read_iops, write_iops = [], [], [], []

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
                        read_iops.append(float(parts[1]))
                        write_iops.append(float(parts[2]))
                        read_kb.append(float(parts[3]))
                        write_kb.append(float(parts[4]))
                    except (ValueError, IndexError):
                        continue
            elif in_device_section and not line.strip():
                in_device_section = False

    if read_kb:
        result["read_bytes"] = int(sum(read_kb) * 1024)
        result["read_iops"] = int(sum(read_iops) / len(read_iops))
    if write_kb:
        result["write_bytes"] = int(sum(write_kb) * 1024)
        result["write_iops"] = int(sum(write_iops) / len(write_iops))

    return result


def parse_sar_network(filepath: Path) -> dict:
    result = {"bytes_sent": 0, "bytes_recv": 0, "packets_sent": 0, "packets_recv": 0}

    if not filepath.exists():
        return result

    rx_bytes, tx_bytes, rx_packets, tx_packets = [], [], [], []

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

def convert_collapsed_to_nested_set(collapsed_file: Path, output_csv: Path) -> bool:
    """Convert collapsed stacks to nested set model CSV (which grafana can use)"""
    try:
        # Build tree from collapsed stacks
        root = {"name": "total", "children": {}, "self_value": 0}

        with open(collapsed_file) as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                parts = line.rsplit(" ", 1)
                if len(parts) != 2:
                    continue
                stack = parts[0]
                try:
                    count = int(parts[1])
                except ValueError:
                    continue

                frames = stack.split(";")
                node = root
                for frame in frames:
                    if frame not in node["children"]:
                        node["children"][frame] = {
                            "name": frame,
                            "children": {},
                            "self_value": 0
                        }
                    node = node["children"][frame]
                node["self_value"] += count

        # Calculate cumulative values (bottom-up)
        def calc_total(node):
            total = node["self_value"]
            for child in node["children"].values():
                total += calc_total(child)
            node["total_value"] = total
            return total

        calc_total(root)

        # Depth-first traversal to create nested set model
        rows = []

        def dfs(node, level):
            rows.append({
                "level": level,
                "value": node["total_value"],
                "self": node["self_value"],
                "label": node["name"]
            })
            # Sort children by total_value descending for consistent output
            sorted_children = sorted(
                node["children"].values(),
                key=lambda n: n["total_value"],
                reverse=True
            )
            for child in sorted_children:
                dfs(child, level + 1)

        dfs(root, 0)

        # Write CSV
        with open(output_csv, "w", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=["level", "value", "self", "label"])
            writer.writeheader()
            writer.writerows(rows)

        print(f"✓ Generated nestes set flame graph CSV: {len(rows)} rows")
        return True

    except Exception as e:
        print(f"⚠ Failed to convert to nested set model: {e}")
        return False


def parse_perf_stat(filepath: Path, hardware_available: bool) -> dict:
    """Parse perf stat output"""
    result = {
        "cpu_cycles": None,
        "instructions": None,
        "ipc": None,
        "cache_references": None,
        "cache_misses": None,
        "cache_miss_rate": None,
        "branch_instructions": None,
        "branch_misses": None,
        "branch_miss_rate": None,
        "context_switches": 0,
        "cpu_migrations": 0,
        "page_faults": 0
    }

    if not filepath.exists():
        return result

    content = filepath.read_text()

    # Software events (always available)
    for key, pattern in [
        ("context_switches", r"([\d,]+)\s+context-switches"),
        ("cpu_migrations", r"([\d,]+)\s+cpu-migrations"),
        ("page_faults", r"([\d,]+)\s+page-faults")
    ]:
        match = re.search(pattern, content)
        if match:
            result[key] = int(match.group(1).replace(",", ""))

    # Hardware events (only on bare metal)
    if hardware_available:
        for key, pattern in [
            ("cpu_cycles", r"([\d,]+)\s+cycles"),
            ("instructions", r"([\d,]+)\s+instructions"),
            ("cache_references", r"([\d,]+)\s+cache-references"),
            ("cache_misses", r"([\d,]+)\s+cache-misses"),
            ("branch_instructions", r"([\d,]+)\s+branch(?:es|-instructions)"),
            ("branch_misses", r"([\d,]+)\s+branch-misses"),
        ]:
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
        if result["branch_instructions"] and result["branch_instructions"] > 0:
            result["branch_miss_rate"] = round(
                100.0 * (result["branch_misses"] or 0) / result["branch_instructions"], 2
            )

    return result


def parse_benchmark_csv(filepath: Path) -> Tuple[dict, float]:
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
                elapsed_seconds = float(row["time_elapsed"])

    return operations, elapsed_seconds


class BenchmarkRunner:
    """Main benchmark orchestration class"""

    INFRA_CORES = "0-3"
    
    # S3 configuration
    S3_BUCKET = "java-benchmarks-artifacts"
    S3_REGION = "us-east-1"

    # Default PostgreSQL configuration
    DEFAULT_PG_HOST = "database-1.cluster-ceoc7e0aaxvr.us-east-1.rds.amazonaws.com"
    DEFAULT_PG_PORT = 5432
    DEFAULT_PG_DATABASE = "postgres"
    DEFAULT_PG_SECRET_NAME = "rds!cluster-7be93190-f497-428f-85e5-c33b16781f63"
    DEFAULT_PG_REGION = "us-east-1"

    def __init__(
        self,
        project_dir: Path,
        output_file: Path,
        workload_config: dict,
        client_config: dict,
        skip_infra: bool = False,
        network_delay_ms: int = 1,
        publish_to_db: bool = True,
        pg_host: str = None,
        pg_port: int = None,
        pg_database: str = None,
        pg_secret_name: str = None,
        pg_region: str = None
    ):
        self.project_dir = project_dir
        self.output_file = output_file
        self.workload_config = workload_config
        self.client_config = client_config
        self.skip_infra = skip_infra
        self.network_delay_ms = network_delay_ms
        self.publish_to_db = publish_to_db
        self.job_id = generate_job_id()
        self.timestamp = get_timestamp()
        self.variance_control = VarianceControl()

        # PostgreSQL configuration
        self.pg_host = pg_host or self.DEFAULT_PG_HOST
        self.pg_port = pg_port or self.DEFAULT_PG_PORT
        self.pg_database = pg_database or self.DEFAULT_PG_DATABASE
        self.pg_secret_name = pg_secret_name or self.DEFAULT_PG_SECRET_NAME
        self.pg_region = pg_region or self.DEFAULT_PG_REGION

        cpu_count = os.cpu_count() or 8
        if cpu_count > 4:
            self.benchmark_cores = f"4-{cpu_count - 1}"
        else:
            self.benchmark_cores = "0-3"
            print(f"WARNING: Only {cpu_count} cores, benchmark shares cores with server")

        print(f"Core allocation: Server={self.INFRA_CORES}, Benchmark={self.benchmark_cores}")

    def start_infrastructure(self):
        if self.skip_infra:
            print("Skipping infrastructure setup")
            return

        print(f"Starting Valkey infrastructure on cores {self.INFRA_CORES}...")

        subprocess.run(["make", "cluster-stop"], cwd=self.project_dir, capture_output=True)
        subprocess.run(["pkill", "-f", "valkey-server"], capture_output=True)
        time.sleep(1)

        work_dir = self.project_dir / "work"
        if work_dir.exists():
            shutil.rmtree(work_dir)

        result = subprocess.run(
            ["taskset", "-c", self.INFRA_CORES, "make", "cluster-init"],
            cwd=self.project_dir,
            timeout=600
        )

        if result.returncode != 0:
            raise RuntimeError(f"Failed to start infrastructure (exit code {result.returncode})")

        time.sleep(2)

        valkey_cli = self.project_dir / "work/valkey/bin/valkey-cli"
        result = subprocess.run([str(valkey_cli), "-p", "7379", "ping"], capture_output=True, text=True)
        if "PONG" not in result.stdout:
            raise RuntimeError("Valkey verification failed")

        print("Valkey infrastructure started and verified")

    def stop_infrastructure(self):
        if self.skip_infra:
            return
        print("Stopping Valkey infrastructure...")
        subprocess.run(["make", "cluster-stop"], cwd=self.project_dir, capture_output=True)
        subprocess.run(["make", "clean"], cwd=self.project_dir, capture_output=True)
        print("Valkey infrastructure stopped")

    def run_benchmark(self, output_csv: Path, workload_config_path: Path) -> subprocess.Popen:
        benchmark_script = self.project_dir / ".github/scripts/mock_benchmark.py"

        cmd = [
            "taskset", "-c", self.benchmark_cores,
            "python3", str(benchmark_script),
            "--workload-config", str(workload_config_path),
            "--output", str(output_csv)
        ]
        print(f"Starting benchmark on cores {self.benchmark_cores}")

        return subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    def _publish_to_postgresql(self, config: dict, results: dict):
        """Publish results to PostgreSQL database"""
        if not self.publish_to_db:
            print("Skipping PostgreSQL publication (disabled)")
            return

        print("\nPublishing results to PostgreSQL...")
        try:
            publisher = PostgreSQLPublisher(
                host=self.pg_host,
                port=self.pg_port,
                database=self.pg_database,
                secret_name=self.pg_secret_name,
                region=self.pg_region
            )
            with publisher:
                publisher.publish(
                    job_id=self.job_id,
                    timestamp=self.timestamp,
                    config=config,
                    results=results
                )
        except Exception as e:
            print(f"⚠ Failed to publish to PostgreSQL: {e}")
            import traceback
            traceback.print_exc()
            
    def run(self, workload_config_path: Path):
        """Execute the full benchmark workflow"""
        self.variance_control.setup(network_delay_ms=self.network_delay_ms)

        with tempfile.TemporaryDirectory() as tmpdir:
            work_dir = Path(tmpdir)
            benchmark_csv = work_dir / "benchmark_results.csv"

            try:
                self.start_infrastructure()

                print("Starting benchmark...")
                benchmark_proc = self.run_benchmark(benchmark_csv, workload_config_path)

                csv_watcher = CSVWatcher(benchmark_csv)
                csv_watcher.start()

                print("Waiting for WARMUP phase to complete...")
                csv_watcher.wait_for_warmup_done()

                print("Starting monitoring for STEADY phase...")
                monitor = MonitoringManager(work_dir)
                monitor.start_all(benchmark_proc.pid)

                print("Waiting for STEADY phase to complete...")
                csv_watcher.wait_for_steady_done()

                monitor.stop_all()
                csv_watcher.stop()

                stdout, stderr = benchmark_proc.communicate(timeout=10)
                print(f"Benchmark stderr:\n{stderr.decode()}")

                # Generate collapsed stacks and upload to S3
                collapsed_stacks_url = None
                nested_set_flamegraph_url = None
                print("\nGenerating flame graph data...")
                collapsed_file = monitor.generate_collapsed_stacks()
                if collapsed_file:
                    # Upload raw collapsed stacks
                    s3_key = f"{self.job_id}/collapsed.txt"
                    collapsed_stacks_url = self._upload_to_s3(collapsed_file, s3_key)

                    # Convert to nested set model and upload
                    nested_set_csv = work_dir / "flamegraph_grafana.csv"
                    if convert_collapsed_to_nested_set(collapsed_file, nested_set_csv):
                        s3_key = f"{self.job_id}/flamegraph_grafana.csv"
                        nested_set_flamegraph_url = self._upload_to_s3(nested_set_csv, s3_key)

                # Parse results
                operations, elapsed_seconds = parse_benchmark_csv(benchmark_csv)
                perf_counters = parse_perf_stat(
                    monitor.output_files.get("perf_stat", Path()),
                    monitor.hardware_perf_available
                )
                cpu_stats = parse_mpstat(monitor.output_files.get("mpstat", Path()))
                disk_stats = parse_iostat(monitor.output_files.get("iostat", Path()))
                network_stats = parse_sar_network(monitor.output_files.get("sar_network", Path()))

                # Copy CSV to output directory
                output_csv_path = self.output_file.with_suffix('.csv')
                if benchmark_csv.exists():
                    shutil.copy(benchmark_csv, output_csv_path)
                    print(f"Benchmark CSV saved to {output_csv_path}")

                # Build result structure
                config = {
                    "workload": self.workload_config,
                    "client": self.client_config
                }

                results = {
                    "elapsed_seconds": int(elapsed_seconds),
                    "operations": operations,
                    "perf": {
                        "counters": perf_counters,
                        "collapsed_stacks_url": collapsed_stacks_url,
                        "nested_set_flamegraph_url": nested_set_flamegraph_url
                    },
                    "cpu": cpu_stats,
                    "io": {
                        "disk": disk_stats,
                        "network": network_stats
                    }
                }

                # Final output structure
                output = {
                    "job_id": self.job_id,
                    "timestamp": self.timestamp,
                    "config": config,
                    "results": results
                }

                # Write JSON output
                with open(self.output_file, "w") as f:
                    json.dump(output, f, indent=2)

                print(f"\nResults written to {self.output_file}")

                # Publish to PostgreSQL
                self._publish_to_postgresql(config, results)

            finally:
                self.stop_infrastructure()
                self.variance_control.teardown()
                
    def _upload_to_s3(self, local_path: Path, s3_key: str) -> Optional[str]:
        """Upload file to S3 and return the S3 URL"""
        try:
            import boto3
            
            s3_client = boto3.client('s3', region_name=self.S3_REGION)
            s3_client.upload_file(
                str(local_path),
                self.S3_BUCKET,
                s3_key
            )
            
            s3_url = f"s3://{self.S3_BUCKET}/{s3_key}"
            print(f"✓ Uploaded to {s3_url}")
            return s3_url
            
        except Exception as e:
            print(f"⚠ Failed to upload to S3: {e}")
            import traceback
            traceback.print_exc()
            return None


def main():
    parser = argparse.ArgumentParser(description="Benchmark runner")
    parser.add_argument("--output", type=str, default="benchmark_results.json")
    parser.add_argument("--workload-config", type=str, required=True)
    parser.add_argument("--client-config", type=str, required=True)
    parser.add_argument("--project-dir", type=str, default=None)
    parser.add_argument("--skip-infra", action="store_true")
    parser.add_argument("--network-delay", type=int, default=1)
    
    # PostgreSQL options
    parser.add_argument("--no-publish", action="store_true", help="Skip publishing to PostgreSQL")
    parser.add_argument("--pg-host", type=str, default=None)
    parser.add_argument("--pg-port", type=int, default=None)
    parser.add_argument("--pg-database", type=str, default=None)
    parser.add_argument("--pg-secret-name", type=str, default=None)
    parser.add_argument("--pg-region", type=str, default=None)
    
    args = parser.parse_args()

    project_dir = Path(args.project_dir) if args.project_dir else Path.cwd()
    output_file = Path(args.output)
    workload_config_path = Path(args.workload_config)
    client_config_path = Path(args.client_config)

    workload_config = load_json_config(workload_config_path)
    client_config = load_json_config(client_config_path)

    print(f"Loaded workload config: {workload_config['benchmark-profile']['name']}")
    print(f"Loaded client config: {client_config['client_name']}")

    runner = BenchmarkRunner(
        project_dir=project_dir,
        output_file=output_file,
        workload_config=workload_config,
        client_config=client_config,
        skip_infra=args.skip_infra,
        network_delay_ms=args.network_delay,
        publish_to_db=not args.no_publish,
        pg_host=args.pg_host,
        pg_port=args.pg_port,
        pg_database=args.pg_database,
        pg_secret_name=args.pg_secret_name,
        pg_region=args.pg_region
    )
    runner.run(workload_config_path)


if __name__ == "__main__":
    main()