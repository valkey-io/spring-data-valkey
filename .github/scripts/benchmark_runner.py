#!/usr/bin/env python3
"""
Benchmark runner that orchestrates:
1. Starting Valkey infrastructure
2. Running benchmark application
3. Collecting system metrics (perf, CPU, disk I/O, network)
4. Outputting results to JSON and PostgreSQL

The benchmark app writes NDJSON metrics (one JSON object per line).
Each line contains phase info, totals, and per-command metrics with HDR latency histograms.
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
from typing import Optional

from hdrh.histogram import HdrHistogram


def generate_job_id(prefix: str = ""):
    now = datetime.now(timezone.utc)
    random_suffix = "".join(random.choices(string.ascii_lowercase + string.digits, k=6))
    base = f"bench-{now.strftime('%Y%m%d-%H%M%S')}-{random_suffix}"
    if prefix:
        return f"{prefix}-{base}"
    return base


def get_timestamp():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def load_json_config(filepath: Path) -> dict:
    with open(filepath) as f:
        return json.load(f)


def extract_buckets_from_hdr(payload_b64: str) -> list:
    """
    Decode HDR histogram payload and extract buckets.
    Returns list of [upper_bound_us, count] pairs.
    """
    if not payload_b64:
        return []

    try:
        # Pass base64 string directly - hdrh handles decoding internally
        histogram = HdrHistogram.decode(payload_b64)
        buckets = []
        for item in histogram.get_recorded_iterator():
            buckets.append([
                item.value_iterated_to,
                item.count_at_value_iterated_to
            ])
        return buckets
    except Exception as e:
        print(f"Warning: Failed to decode HDR histogram: {e}")
        return []


def add_buckets_to_phase_records(phase_records: dict) -> dict:
    """
    Process phase records and add explicit buckets to each command's latency data.
    Modifies the records in place and returns them.
    """
    for phase_id, record in phase_records.items():
        metrics = record.get("metrics", {})
        for cmd_name, cmd_data in metrics.items():
            latency = cmd_data.get("latency", {})
            hdr = latency.get("hdr", {})
            payload = hdr.get("payload_b64", "")
            if payload and "buckets" not in hdr:
                hdr["buckets"] = extract_buckets_from_hdr(payload)

    return phase_records


class PostgreSQLPublisher:
    """Publishes benchmark results to Aurora PostgreSQL"""

    TABLE_NAME = "benchmark_results"

    def __init__(self, host: str, port: int = 5432, database: str = "postgres",
                 secret_name: str = None, region: str = "us-east-1"):
        self.host = host
        self.port = port
        self.database = database
        self.secret_name = secret_name
        self.region = region
        self.connection = None

    def _get_credentials(self) -> dict:
        import boto3
        client = boto3.client('secretsmanager', region_name=self.region)
        response = client.get_secret_value(SecretId=self.secret_name)
        secret = json.loads(response['SecretString'])
        return {'username': secret.get('username'), 'password': secret.get('password')}

    def connect(self):
        import psycopg2
        creds = self._get_credentials()
        self.connection = psycopg2.connect(
            host=self.host, port=self.port, database=self.database,
            user=creds['username'], password=creds['password'],
            sslmode='require', connect_timeout=10
        )
        print(f"✓ Connected to PostgreSQL at {self.host}:{self.port}/{self.database}")

    def _migrate_add_versions_column(self):
        """Add versions column to existing tables that lack it."""
        check_sql = f"""
        SELECT column_name FROM information_schema.columns
        WHERE table_name = '{self.TABLE_NAME}' AND column_name = 'versions';
        """
        with self.connection.cursor() as cursor:
            cursor.execute(check_sql)
            if cursor.fetchone() is None:
                print(f"Migrating: adding 'versions' column to {self.TABLE_NAME}...")
                cursor.execute(f"""
                    ALTER TABLE {self.TABLE_NAME}
                    ADD COLUMN versions JSONB NOT NULL DEFAULT '{{}}'::jsonb;
                """)
                self.connection.commit()
                print(f"✓ Added 'versions' column")

    def _ensure_table_exists(self):
        create_table_sql = f"""
        CREATE TABLE IF NOT EXISTS {self.TABLE_NAME} (
            id SERIAL PRIMARY KEY,
            job_id VARCHAR(100) UNIQUE NOT NULL,
            timestamp TIMESTAMPTZ NOT NULL,
            versions JSONB NOT NULL DEFAULT '{{}}'::jsonb,
            config JSONB NOT NULL,
            results JSONB NOT NULL,
            created_at TIMESTAMPTZ DEFAULT NOW()
        );
        CREATE INDEX IF NOT EXISTS idx_{self.TABLE_NAME}_job_id
            ON {self.TABLE_NAME} (job_id);
        CREATE INDEX IF NOT EXISTS idx_{self.TABLE_NAME}_timestamp
            ON {self.TABLE_NAME} (timestamp);
        CREATE INDEX IF NOT EXISTS idx_{self.TABLE_NAME}_config_driver
            ON {self.TABLE_NAME} ((config->'driver'->>'driver_id'));
        CREATE INDEX IF NOT EXISTS idx_{self.TABLE_NAME}_config_workload
            ON {self.TABLE_NAME} ((config->'workload'->'benchmark_profile'->>'name'));
        CREATE INDEX IF NOT EXISTS idx_{self.TABLE_NAME}_primary_driver
            ON {self.TABLE_NAME} ((versions->>'primary_driver_id'));
        CREATE INDEX IF NOT EXISTS idx_{self.TABLE_NAME}_primary_version
            ON {self.TABLE_NAME} ((versions->>'primary_driver_version'));
        CREATE INDEX IF NOT EXISTS idx_{self.TABLE_NAME}_secondary_driver
            ON {self.TABLE_NAME} ((versions->>'secondary_driver_id'));
        CREATE INDEX IF NOT EXISTS idx_{self.TABLE_NAME}_secondary_version
            ON {self.TABLE_NAME} ((versions->>'secondary_driver_version'));
        """
        with self.connection.cursor() as cursor:
            cursor.execute(create_table_sql)
        self.connection.commit()
        print(f"✓ Ensured table '{self.TABLE_NAME}' exists")

    def publish(self, job_id: str, timestamp: str, versions: dict,
                config: dict, results: dict):
        from psycopg2.extras import Json
        if not self.connection:
            self.connect()
        self._ensure_table_exists()
        self._migrate_add_versions_column()

        insert_sql = f"""
        INSERT INTO {self.TABLE_NAME} (job_id, timestamp, versions, config, results)
        VALUES (%s, %s, %s, %s, %s)
        ON CONFLICT (job_id) DO UPDATE SET
            timestamp = EXCLUDED.timestamp,
            versions = EXCLUDED.versions,
            config = EXCLUDED.config,
            results = EXCLUDED.results,
            created_at = NOW()
        RETURNING id
        """
        with self.connection.cursor() as cursor:
            cursor.execute(insert_sql, (
                job_id, timestamp, Json(versions), Json(config), Json(results)
            ))
            row_id = cursor.fetchone()[0]
        self.connection.commit()
        print(f"✓ Published results for job '{job_id}' (row id: {row_id})")

    def close(self):
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
        self.smt_original = None

    def setup(self, network_delay_ms: int = 1):
        print("Setting up variance control...")
        self._disable_smt()
        self._disable_turbo_boost()
        self._setup_network_delay(network_delay_ms)
        self._disable_nmi_watchdog()
        self._set_perf_permissions()
        print("Variance control setup complete")

    def teardown(self):
        print("Restoring system settings...")
        self._restore_smt()
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
                subprocess.run(["sudo", "tee", str(intel_path)], input=b"1",
                               capture_output=True)
                print("  ✓ Intel Turbo Boost disabled")
            elif amd_path.exists():
                self.turbo_boost_path = amd_path
                self.original_turbo_state = amd_path.read_text().strip()
                subprocess.run(["sudo", "tee", str(amd_path)], input=b"0",
                               capture_output=True)
                print("  ✓ AMD Boost disabled")
            else:
                print("  ⚠ No turbo boost control found")
        except Exception as e:
            print(f"  ⚠ Could not disable turbo boost: {e}")

    def _restore_turbo_boost(self):
        if self.turbo_boost_path and self.original_turbo_state:
            try:
                subprocess.run(["sudo", "tee", str(self.turbo_boost_path)],
                               input=self.original_turbo_state.encode(),
                               capture_output=True)
                print("  ✓ Turbo boost restored")
            except Exception as e:
                print(f"  ⚠ Could not restore turbo boost: {e}")

    def _disable_smt(self):
        """Disable SMT (hyperthreading) for consistent benchmark results."""
        smt_control = Path("/sys/devices/system/cpu/smt/control")
        try:
            if smt_control.exists():
                self.smt_original = smt_control.read_text().strip()
                if self.smt_original != "off":
                    subprocess.run(["sudo", "tee", str(smt_control)],
                                   input=b"off", capture_output=True)
                    print("  ✓ SMT (hyperthreading) disabled")
                else:
                    print("  ✓ SMT already disabled")
            else:
                print("  ⚠ SMT control not available")
        except Exception as e:
            print(f"  ⚠ Could not disable SMT: {e}")

    def _restore_smt(self):
        """Restore SMT to original state."""
        if self.smt_original and self.smt_original != "off":
            smt_control = Path("/sys/devices/system/cpu/smt/control")
            try:
                subprocess.run(["sudo", "tee", str(smt_control)],
                               input=self.smt_original.encode(),
                               capture_output=True)
                print("  ✓ SMT restored")
            except Exception as e:
                print(f"  ⚠ Could not restore SMT: {e}")

    def _setup_network_delay(self, delay_ms: int):
        try:
            subprocess.run(["sudo", "tc", "qdisc", "del", "dev", "lo", "root"],
                           capture_output=True)
            result = subprocess.run(
                ["sudo", "tc", "qdisc", "add", "dev", "lo", "root", "netem",
                 "delay", f"{delay_ms}ms"],
                capture_output=True, text=True)
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
                subprocess.run(["sudo", "tc", "qdisc", "del", "dev", "lo", "root"],
                               capture_output=True)
                print("  ✓ Network delay removed")
            except Exception as e:
                print(f"  ⚠ Could not remove network delay: {e}")

    def _disable_nmi_watchdog(self):
        try:
            nmi_path = Path("/proc/sys/kernel/nmi_watchdog")
            if nmi_path.exists():
                self.nmi_watchdog_original = nmi_path.read_text().strip()
                subprocess.run(["sudo", "tee", str(nmi_path)], input=b"0",
                               capture_output=True)
                print("  ✓ NMI watchdog disabled")
        except Exception as e:
            print(f"  ⚠ Could not disable NMI watchdog: {e}")

    def _restore_nmi_watchdog(self):
        if self.nmi_watchdog_original:
            try:
                subprocess.run(["sudo", "tee", "/proc/sys/kernel/nmi_watchdog"],
                               input=self.nmi_watchdog_original.encode(),
                               capture_output=True)
                print("  ✓ NMI watchdog restored")
            except Exception as e:
                print(f"  ⚠ Could not restore NMI watchdog: {e}")

    def _set_perf_permissions(self):
        try:
            subprocess.run(["sudo", "sysctl", "-w", "kernel.perf_event_paranoid=-1"],
                           capture_output=True)
            subprocess.run(["sudo", "sysctl", "-w", "kernel.kptr_restrict=0"],
                           capture_output=True)
            print("  ✓ Perf permissions configured")
        except Exception as e:
            print(f"  ⚠ Could not set perf permissions: {e}")


class MetricsWatcher:
    """Watches a JSONL metrics file for phase transitions using tail -f."""

    def __init__(self, metrics_path: Path):
        self.metrics_path = metrics_path
        self.warmup_done_event = threading.Event()
        self.steady_done_event = threading.Event()
        self.error_event = threading.Event()
        self.error_message: Optional[str] = None
        self.tail_process: Optional[subprocess.Popen] = None
        self.watcher_thread: Optional[threading.Thread] = None
        self.phase_records: dict = {}

    def start(self):
        print(f"Waiting for metrics file: {self.metrics_path}")
        while not self.metrics_path.exists():
            time.sleep(0.1)

        self.tail_process = subprocess.Popen(
            ["tail", "-f", "-n", "+1", str(self.metrics_path)],
            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True
        )

        self.watcher_thread = threading.Thread(target=self._read_loop, daemon=True)
        self.watcher_thread.start()
        print(f"Started watching metrics (tail -f): {self.metrics_path}")

    def stop(self):
        if self.tail_process:
            self.tail_process.terminate()
            try:
                self.tail_process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                self.tail_process.kill()
            self.tail_process = None

        if self.watcher_thread:
            self.watcher_thread.join(timeout=2)
        print("Metrics watcher stopped")

    def wait_for_warmup_done(self) -> None:
        self.warmup_done_event.wait()

    def wait_for_steady_done(self) -> None:
        self.steady_done_event.wait()

    def has_error(self) -> bool:
        return self.error_event.is_set()

    def get_error(self) -> Optional[str]:
        return self.error_message

    def get_phase_record(self, phase_id: str) -> Optional[dict]:
        return self.phase_records.get(phase_id)

    def get_all_phase_records(self) -> dict:
        return dict(self.phase_records)

    def _read_loop(self):
        if not self.tail_process:
            return

        for line in self.tail_process.stdout:
            line = line.strip()
            if not line:
                continue
            self._process_line(line)

            if self.warmup_done_event.is_set() and self.steady_done_event.is_set():
                break

    def _process_line(self, line: str):
        try:
            record = json.loads(line)
        except json.JSONDecodeError as e:
            print(f"  [Metrics] JSON parse error: {e}")
            return

        phase_info = record.get("phase", {})
        phase_id = phase_info.get("id", "UNKNOWN")
        status = phase_info.get("status", "UNKNOWN")

        print(f"  [Metrics] Phase: {phase_id}, Status: {status}")

        if status == "ERROR":
            error_msg = record.get("error", "Unknown error")
            self.error_message = f"Phase {phase_id} failed: {error_msg}"
            print(f"  ✗ {self.error_message}")
            self.phase_records[phase_id] = record
            self.error_event.set()
            # Unblock waiters so they can check for error
            self.warmup_done_event.set()
            self.steady_done_event.set()
        elif status == "COMPLETED":
            self.phase_records[phase_id] = record

            if phase_id == "WARMUP" and not self.warmup_done_event.is_set():
                totals = record.get("totals", {})
                print(f"  → WARMUP complete: {totals.get('requests', 0):,} requests, "
                      f"{totals.get('errors', 0)} errors")
                self.warmup_done_event.set()
            elif phase_id == "STEADY" and not self.steady_done_event.is_set():
                totals = record.get("totals", {})
                print(f"  → STEADY complete: {totals.get('requests', 0):,} requests, "
                      f"{totals.get('errors', 0)} errors")
                self.steady_done_event.set()


class MonitoringManager:
    """Manages background monitoring processes"""
    ASYNC_PROFILER_PATH = "/opt/async-profiler/bin"

    def __init__(self, work_dir: Path):
        self.work_dir = work_dir
        self.processes = {}
        self.output_files = {}
        self._file_handles = {}
        self.hardware_perf_available = False
        self.async_profiler_process = None
        self.collapsed_stacks_file = None

    def _check_hardware_perf_events(self) -> bool:
        try:
            result = subprocess.run(
                ["perf", "stat", "-e", "cycles", "--", "sleep", "0.1"],
                capture_output=True, text=True, timeout=5)
            available = ("<not supported>" not in result.stderr
                         and "<not counted>" not in result.stderr)
            print(f"Hardware perf events: "
                  f"{'AVAILABLE' if available else 'NOT AVAILABLE'}")
            return available
        except Exception as e:
            print(f"Hardware perf check failed: {e}")
            return False

    def _ensure_async_profiler(self) -> bool:
        asprof = Path(self.ASYNC_PROFILER_PATH) / "asprof"
        if asprof.exists():
            return True
        print(f"⚠ async-profiler not found at {self.ASYNC_PROFILER_PATH}")
        return False

    def start_mpstat(self):
        output_file = self.work_dir / "mpstat.log"
        self.output_files["mpstat"] = output_file
        fh = open(output_file, "w")
        self._file_handles["mpstat"] = fh
        proc = subprocess.Popen(["mpstat", "1"], stdout=fh,
                                stderr=subprocess.DEVNULL, preexec_fn=os.setsid)
        self.processes["mpstat"] = proc

    def start_iostat(self):
        output_file = self.work_dir / "iostat.log"
        self.output_files["iostat"] = output_file
        fh = open(output_file, "w")
        self._file_handles["iostat"] = fh
        proc = subprocess.Popen(["iostat", "-x", "1"], stdout=fh,
                                stderr=subprocess.DEVNULL, preexec_fn=os.setsid)
        self.processes["iostat"] = proc

    def start_sar_network(self):
        output_file = self.work_dir / "sar_network.log"
        self.output_files["sar_network"] = output_file
        fh = open(output_file, "w")
        self._file_handles["sar_network"] = fh
        proc = subprocess.Popen(["sar", "-n", "DEV", "1"], stdout=fh,
                                stderr=subprocess.DEVNULL, preexec_fn=os.setsid)
        self.processes["sar_network"] = proc

    def start_perf_stat(self, pid: int):
        output_file = self.work_dir / "perf_stat.log"
        self.output_files["perf_stat"] = output_file
        fh = open(output_file, "w")
        self._file_handles["perf_stat"] = fh
        self.hardware_perf_available = self._check_hardware_perf_events()
        if self.hardware_perf_available:
            events = ("cycles,instructions,cache-references,cache-misses,"
                      "branch-instructions,branch-misses,context-switches,"
                      "cpu-migrations,page-faults")
        else:
            events = "context-switches,cpu-migrations,page-faults"
        proc = subprocess.Popen(["perf", "stat", "-e", events, "-p", str(pid)],
                                stdout=subprocess.DEVNULL, stderr=fh)
        self.processes["perf_stat"] = proc
        print(f"Started perf stat on PID {pid}")

    def start_async_profiler(self, pid: int):
        """Start async-profiler to collect Java flame graph data."""
        if not self._ensure_async_profiler():
            print("⚠ Skipping async-profiler (not available)")
            return

        self.collapsed_stacks_file = self.work_dir / "collapsed.txt"
        asprof = Path(self.ASYNC_PROFILER_PATH) / "asprof"

        # Start async-profiler in the background
        # -e cpu: profile CPU usage
        # -i 1ms: sampling interval
        # -o collapsed: output in collapsed stack format for flame graphs
        # -f: output file
        cmd = [
            str(asprof),
            "-e", "cpu",
            "-i", "1ms",
            "-o", "collapsed",
            "-f", str(self.collapsed_stacks_file),
            "start",
            str(pid)
        ]

        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
            if result.returncode == 0:
                print(f"✓ Started async-profiler on PID {pid}")
            else:
                print(f"⚠ Failed to start async-profiler: {result.stderr}")
                self.collapsed_stacks_file = None
        except Exception as e:
            print(f"⚠ Error starting async-profiler: {e}")
            self.collapsed_stacks_file = None

    def stop_async_profiler(self, pid: int):
        """Stop async-profiler and collect the flame graph data."""
        if not self.collapsed_stacks_file:
            return

        asprof = Path(self.ASYNC_PROFILER_PATH) / "asprof"
        if not asprof.exists():
            return

        # Must include output options in stop command to dump the data
        cmd = [
            str(asprof),
            "stop",
            "-o", "collapsed",
            "-f", str(self.collapsed_stacks_file),
            str(pid)
        ]

        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
            if result.returncode == 0:
                print("✓ async-profiler stopped")
            else:
                print(f"⚠ Error stopping async-profiler: {result.stderr}")
        except subprocess.TimeoutExpired:
            print("⚠ Timeout stopping async-profiler")
        except Exception as e:
            print(f"⚠ Error stopping async-profiler: {e}")

    def get_collapsed_stacks(self) -> Optional[Path]:
        """Get the collapsed stacks file generated by async-profiler."""
        if not self.collapsed_stacks_file:
            print("⚠ No collapsed stacks file (async-profiler not started)")
            return None

        if not self.collapsed_stacks_file.exists():
            print("⚠ Collapsed stacks file not found")
            return None

        if self.collapsed_stacks_file.stat().st_size == 0:
            print("⚠ Collapsed stacks file is empty")
            return None

        line_count = sum(1 for _ in open(self.collapsed_stacks_file))
        print(f"✓ Collapsed stacks: {line_count} lines, "
              f"{self.collapsed_stacks_file.stat().st_size} bytes")
        self.output_files["collapsed_stacks"] = self.collapsed_stacks_file
        return self.collapsed_stacks_file

    def start_all(self, benchmark_pid: int):
        self.benchmark_pid = benchmark_pid  # Store for stop_all
        self.start_mpstat()
        self.start_iostat()
        self.start_sar_network()
        self.start_perf_stat(benchmark_pid)
        self.start_async_profiler(benchmark_pid)
        print("All monitoring processes started")

    def stop_all(self):
        # Stop async-profiler first to collect flame graph data
        if hasattr(self, 'benchmark_pid'):
            self.stop_async_profiler(self.benchmark_pid)

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

        for fh in self._file_handles.values():
            try:
                fh.close()
            except Exception:
                pass
        print("All monitoring processes stopped")


def parse_mpstat(filepath: Path) -> dict:
    result = {
        "user_percent_avg": 0.0, "user_percent_max": 0.0,
        "system_percent_avg": 0.0, "system_percent_max": 0.0,
        "idle_percent_avg": 0.0, "idle_percent_min": 100.0,
        "iowait_percent_avg": 0.0, "steal_percent_avg": 0.0
    }
    if not filepath.exists():
        return result

    user_values, system_values, idle_values = [], [], []
    iowait_values, steal_values = [], []
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
        result["user_percent_avg"] = round(
            sum(user_values) / len(user_values), 1)
        result["user_percent_max"] = round(max(user_values), 1)
    if system_values:
        result["system_percent_avg"] = round(
            sum(system_values) / len(system_values), 1)
        result["system_percent_max"] = round(max(system_values), 1)
    if idle_values:
        result["idle_percent_avg"] = round(
            sum(idle_values) / len(idle_values), 1)
        result["idle_percent_min"] = round(min(idle_values), 1)
    if iowait_values:
        result["iowait_percent_avg"] = round(
            sum(iowait_values) / len(iowait_values), 1)
    if steal_values:
        result["steal_percent_avg"] = round(
            sum(steal_values) / len(steal_values), 1)
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
    result = {"bytes_sent": 0, "bytes_recv": 0,
              "packets_sent": 0, "packets_recv": 0}
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


def convert_collapsed_to_nested_set(collapsed_file: Path,
                                     output_csv: Path) -> bool:
    try:
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
                            "name": frame, "children": {}, "self_value": 0}
                    node = node["children"][frame]
                node["self_value"] += count

        def calc_total(node):
            total = node["self_value"]
            for child in node["children"].values():
                total += calc_total(child)
            node["total_value"] = total
            return total

        calc_total(root)
        rows = []

        def dfs(node, level):
            rows.append({"level": level, "value": node["total_value"],
                         "self": node["self_value"], "label": node["name"]})
            for child in sorted(node["children"].values(),
                                key=lambda n: n["total_value"], reverse=True):
                dfs(child, level + 1)

        dfs(root, 0)
        with open(output_csv, "w", newline="") as f:
            writer = csv.DictWriter(
                f, fieldnames=["level", "value", "self", "label"])
            writer.writeheader()
            writer.writerows(rows)
        print(f"✓ Generated nested set flame graph CSV: {len(rows)} rows")
        return True
    except Exception as e:
        print(f"⚠ Failed to convert to nested set model: {e}")
        return False


def parse_perf_stat(filepath: Path, hardware_available: bool) -> dict:
    result = {
        "cpu_cycles": None, "instructions": None, "ipc": None,
        "cache_references": None, "cache_misses": None,
        "cache_miss_rate": None,
        "branch_instructions": None, "branch_misses": None,
        "branch_miss_rate": None,
        "context_switches": 0, "cpu_migrations": 0, "page_faults": 0
    }
    if not filepath.exists():
        return result

    content = filepath.read_text()
    for key, pattern in [
        ("context_switches", r"([\d,]+)\s+context-switches"),
        ("cpu_migrations", r"([\d,]+)\s+cpu-migrations"),
        ("page_faults", r"([\d,]+)\s+page-faults")
    ]:
        match = re.search(pattern, content)
        if match:
            result[key] = int(match.group(1).replace(",", ""))

    if hardware_available:
        for key, pattern in [
            ("cpu_cycles", r"([\d,]+)\s+cycles"),
            ("instructions", r"([\d,]+)\s+instructions"),
            ("cache_references", r"([\d,]+)\s+cache-references"),
            ("cache_misses", r"([\d,]+)\s+cache-misses"),
            ("branch_instructions",
             r"([\d,]+)\s+branch(?:es|-instructions)"),
            ("branch_misses", r"([\d,]+)\s+branch-misses"),
        ]:
            match = re.search(pattern, content)
            if match:
                result[key] = int(match.group(1).replace(",", ""))

        if result["cpu_cycles"] and result["instructions"]:
            result["ipc"] = round(
                result["instructions"] / result["cpu_cycles"], 2)
        if result["cache_references"] and result["cache_references"] > 0:
            result["cache_miss_rate"] = round(
                100.0 * (result["cache_misses"] or 0)
                / result["cache_references"], 2)
        if (result["branch_instructions"]
                and result["branch_instructions"] > 0):
            result["branch_miss_rate"] = round(
                100.0 * (result["branch_misses"] or 0)
                / result["branch_instructions"], 2)
    return result


class BenchmarkRunner:
    """Main benchmark orchestration class"""

    INFRA_CORES = "0-3"
    S3_BUCKET = "java-benchmarks-artifacts"
    S3_REGION = "us-east-1"

    DEFAULT_PG_HOST = \
        "database-1.cluster-ceoc7e0aaxvr.us-east-1.rds.amazonaws.com"
    DEFAULT_PG_PORT = 5432
    DEFAULT_PG_DATABASE = "postgres"
    DEFAULT_PG_SECRET_NAME = \
        "rds!cluster-7be93190-f497-428f-85e5-c33b16781f63"
    DEFAULT_PG_REGION = "us-east-1"

    def __init__(self, resp_bench_dir: Path, output_file: Path,
                 workload_config_path: Path, driver_config_path: Path,
                 versions: dict,
                 job_id_prefix: str = "",
                 skip_infra: bool = False, network_delay_ms: int = 1,
                 publish_to_db: bool = True, pg_host: str = None,
                 pg_port: int = None, pg_database: str = None,
                 pg_secret_name: str = None, pg_region: str = None):
        self.resp_bench_dir = resp_bench_dir
        self.output_file = output_file
        self.workload_config_path = workload_config_path
        self.driver_config_path = driver_config_path
        self.workload_config = load_json_config(workload_config_path)
        self.driver_config = load_json_config(driver_config_path)
        self.versions = versions
        self.skip_infra = skip_infra
        self.network_delay_ms = network_delay_ms
        self.publish_to_db = publish_to_db
        self.job_id = generate_job_id(prefix=job_id_prefix)
        self.timestamp = get_timestamp()
        self.variance_control = VarianceControl()

        self.pg_host = pg_host or self.DEFAULT_PG_HOST
        self.pg_port = pg_port or self.DEFAULT_PG_PORT
        self.pg_database = pg_database or self.DEFAULT_PG_DATABASE
        self.pg_secret_name = pg_secret_name or self.DEFAULT_PG_SECRET_NAME
        self.pg_region = pg_region or self.DEFAULT_PG_REGION

        # Java JAR path - find the shaded JAR dynamically
        self.java_jar = self._find_java_jar()

        cpu_count = os.cpu_count() or 8
        if cpu_count > 4:
            self.benchmark_cores = f"4-{cpu_count - 1}"
        else:
            self.benchmark_cores = "0-3"
            print(f"WARNING: Only {cpu_count} cores, benchmark shares "
                  f"cores with server")
        print(f"Core allocation: Server={self.INFRA_CORES}, "
              f"Benchmark={self.benchmark_cores}")

    def _find_java_jar(self) -> Path:
        """Find the benchmark JAR file dynamically."""
        target_dir = self.resp_bench_dir / "java/target"
        # Look for the shaded JAR (excludes -sources, -javadoc, original-)
        jars = list(target_dir.glob("resp-bench-java-*.jar"))
        jars = [j for j in jars if not any(x in j.name for x in
                ["-sources", "-javadoc", "original-"])]
        if not jars:
            raise RuntimeError(f"No benchmark JAR found in {target_dir}")
        if len(jars) > 1:
            print(f"Warning: Multiple JARs found, using: {jars[0]}")
        return jars[0]

    def _extract_versions_from_metadata(self, all_phases: dict) -> dict:
        """Extract version info from resp-bench phase metadata."""
        # Prefer STEADY phase, fall back to any available phase
        metadata = None
        for phase_id in ["STEADY", "WARMUP"]:
            if phase_id in all_phases:
                metadata = all_phases[phase_id].get("metadata", {})
                if metadata:
                    break

        if not metadata:
            # Fall back to first available phase
            for phase_record in all_phases.values():
                metadata = phase_record.get("metadata", {})
                if metadata:
                    break

        if not metadata:
            print("Warning: No metadata found in phase records, using empty versions")
            return {}

        return {
            "primary_driver_id": metadata.get("driver_id"),
            "primary_driver_version": metadata.get("primary_driver_version"),
            "secondary_driver_id": metadata.get("secondary_driver_id"),
            "secondary_driver_version": metadata.get("secondary_driver_version"),
            "commit_id": metadata.get("commit_id")
        }

    def _is_cluster_mode(self) -> bool:
        """Check if the driver config specifies cluster mode."""
        return self.driver_config.get("mode", "standalone") == "cluster"

    def _get_server_port(self) -> int:
        """Get the primary server port based on mode."""
        return 7379 if self._is_cluster_mode() else 6379

    def _pin_server_processes(self):
        """Pin all running valkey-server processes to designated cores."""
        work_dir = self.resp_bench_dir / "work"
        pinned = 0
        for pid_file in work_dir.glob("*.pid"):
            try:
                pid = int(pid_file.read_text().strip())
                result = subprocess.run(
                    ["taskset", "-cp", self.INFRA_CORES, str(pid)],
                    capture_output=True, text=True)
                if result.returncode == 0:
                    pinned += 1
                else:
                    print(f"  Warning: Failed to pin PID {pid}: {result.stderr}")
            except (ValueError, FileNotFoundError) as e:
                print(f"  Warning: Could not read PID from {pid_file}: {e}")
        print(f"Pinned {pinned} server processes to cores {self.INFRA_CORES}")

    def start_infrastructure(self):
        if self.skip_infra:
            print("Skipping infrastructure setup")
            return

        is_cluster = self._is_cluster_mode()
        mode_name = "cluster" if is_cluster else "standalone"
        make_target = "server-cluster-init" if is_cluster else "server-standalone-start"
        stop_target = "server-cluster-stop" if is_cluster else "server-standalone-stop"
        port = self._get_server_port()

        print(f"Starting Valkey {mode_name} infrastructure on cores {self.INFRA_CORES}...")
        subprocess.run(["make", stop_target], cwd=self.resp_bench_dir,
                        capture_output=True)
        subprocess.run(["pkill", "-f", "valkey-server"], capture_output=True)
        time.sleep(1)

        work_dir = self.resp_bench_dir / "work"
        if work_dir.exists():
            shutil.rmtree(work_dir)

        result = subprocess.run(
            ["taskset", "-c", self.INFRA_CORES, "make", make_target],
            cwd=self.resp_bench_dir, timeout=600)
        if result.returncode != 0:
            raise RuntimeError(
                f"Failed to start {mode_name} infrastructure "
                f"(exit code {result.returncode})")
        time.sleep(2)

        valkey_cli = self.resp_bench_dir / "work/valkey/bin/valkey-cli"
        result = subprocess.run([str(valkey_cli), "-p", str(port), "ping"],
                                capture_output=True, text=True)
        if "PONG" not in result.stdout:
            raise RuntimeError(f"Valkey verification failed on port {port}")
        print(f"Valkey {mode_name} infrastructure started and verified on port {port}")

        # Pin server processes to designated cores
        self._pin_server_processes()

    def stop_infrastructure(self):
        if self.skip_infra:
            return
        is_cluster = self._is_cluster_mode()
        mode_name = "cluster" if is_cluster else "standalone"
        stop_target = "server-cluster-stop" if is_cluster else "server-standalone-stop"

        print(f"Stopping Valkey {mode_name} infrastructure...")
        subprocess.run(["make", stop_target], cwd=self.resp_bench_dir,
                        capture_output=True)
        subprocess.run(["make", "clean"], cwd=self.resp_bench_dir,
                        capture_output=True)
        print("Valkey infrastructure stopped")

    def run_benchmark(self, output_metrics: Path) -> subprocess.Popen:
        port = self._get_server_port()
        server = f"localhost:{port}"

        cmd = [
            "taskset", "-c", self.benchmark_cores,
            "java", "-jar", str(self.java_jar),
            "--server", server,
            "--driver", str(self.driver_config_path),
            "--workload", str(self.workload_config_path),
            "--metrics", str(output_metrics)
        ]

        # Add commit ID if available
        commit_id = self.versions.get("primary_driver_version")
        if commit_id:
            cmd.extend(["--commit-id", commit_id])

        print(f"Starting Java benchmark on cores {self.benchmark_cores}")
        print(f"  Server: {server}")
        print(f"  Driver: {self.driver_config_path}")
        print(f"  Workload: {self.workload_config_path}")
        print(f"  Command: {' '.join(cmd)}")

        return subprocess.Popen(cmd, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE)

    def _publish_to_postgresql(self, versions: dict, config: dict,
                               results: dict):
        if not self.publish_to_db:
            print("Skipping PostgreSQL publication (disabled)")
            return

        print("\nPublishing results to PostgreSQL...")
        try:
            publisher = PostgreSQLPublisher(
                host=self.pg_host, port=self.pg_port,
                database=self.pg_database,
                secret_name=self.pg_secret_name, region=self.pg_region)
            with publisher:
                publisher.publish(
                    job_id=self.job_id, timestamp=self.timestamp,
                    versions=versions, config=config, results=results)
        except Exception as e:
            print(f"⚠ Failed to publish to PostgreSQL: {e}")
            import traceback
            traceback.print_exc()

    def _upload_to_s3(self, local_path: Path,
                      s3_key: str) -> Optional[str]:
        try:
            import boto3
            s3_client = boto3.client('s3', region_name=self.S3_REGION)
            s3_client.upload_file(str(local_path), self.S3_BUCKET, s3_key)
            s3_url = f"s3://{self.S3_BUCKET}/{s3_key}"
            print(f"✓ Uploaded to {s3_url}")
            return s3_url
        except Exception as e:
            print(f"⚠ Failed to upload to S3: {e}")
            import traceback
            traceback.print_exc()
            return None

    def run(self):
        """Execute the full benchmark workflow"""
        self.variance_control.setup(network_delay_ms=self.network_delay_ms)

        with tempfile.TemporaryDirectory() as tmpdir:
            work_dir = Path(tmpdir)
            benchmark_metrics = work_dir / "benchmark_metrics.ndjson"

            try:
                self.start_infrastructure()

                print("Starting benchmark...")
                benchmark_proc = self.run_benchmark(benchmark_metrics)

                metrics_watcher = MetricsWatcher(benchmark_metrics)
                metrics_watcher.start()

                print("Waiting for WARMUP phase to complete...")
                metrics_watcher.wait_for_warmup_done()
                if metrics_watcher.has_error():
                    raise RuntimeError(metrics_watcher.get_error())

                print("Starting monitoring for STEADY phase...")
                monitor = MonitoringManager(work_dir)
                monitor.start_all(benchmark_proc.pid)

                print("Waiting for STEADY phase to complete...")
                metrics_watcher.wait_for_steady_done()
                if metrics_watcher.has_error():
                    raise RuntimeError(metrics_watcher.get_error())

                monitor.stop_all()
                metrics_watcher.stop()

                _, stderr = benchmark_proc.communicate(timeout=10)
                print(f"Benchmark stderr:\n{stderr.decode()}")

                # Get collapsed stacks from async-profiler and upload to S3
                collapsed_stacks_url = None
                nested_set_flamegraph_url = None
                print("\nCollecting flame graph data from async-profiler...")
                collapsed_file = monitor.get_collapsed_stacks()
                if collapsed_file:
                    s3_key = f"{self.job_id}/collapsed.txt"
                    collapsed_stacks_url = self._upload_to_s3(
                        collapsed_file, s3_key)

                    nested_set_csv = work_dir / "flamegraph_grafana.csv"
                    if convert_collapsed_to_nested_set(
                            collapsed_file, nested_set_csv):
                        s3_key = f"{self.job_id}/flamegraph_grafana.csv"
                        nested_set_flamegraph_url = self._upload_to_s3(
                            nested_set_csv, s3_key)

                # Collect all phase records from the benchmark
                all_phases = metrics_watcher.get_all_phase_records()

                # Add explicit buckets to HDR histograms
                add_buckets_to_phase_records(all_phases)

                steady_record = metrics_watcher.get_phase_record("STEADY")

                # Extract versions from resp-bench metadata (prefer STEADY, fall back to any phase)
                versions = self._extract_versions_from_metadata(all_phases)

                # Parse system-level metrics
                perf_counters = parse_perf_stat(
                    monitor.output_files.get("perf_stat", Path()),
                    monitor.hardware_perf_available)
                cpu_stats = parse_mpstat(
                    monitor.output_files.get("mpstat", Path()))
                disk_stats = parse_iostat(
                    monitor.output_files.get("iostat", Path()))
                network_stats = parse_sar_network(
                    monitor.output_files.get("sar_network", Path()))

                # Copy NDJSON metrics to output directory
                output_metrics_path = self.output_file.with_suffix('.ndjson')
                if benchmark_metrics.exists():
                    shutil.copy(benchmark_metrics, output_metrics_path)
                    print(f"Benchmark metrics saved to {output_metrics_path}")

                # Copy collapsed stacks to output directory for artifact upload
                if collapsed_file and collapsed_file.exists():
                    output_collapsed_path = self.output_file.with_name(
                        self.output_file.stem + '_collapsed.txt')
                    shutil.copy(collapsed_file, output_collapsed_path)
                    print(f"Collapsed stacks saved to {output_collapsed_path}")

                # Build result structure
                config = {
                    "workload": self.workload_config,
                    "driver": self.driver_config
                }

                # Extract elapsed from STEADY phase
                elapsed_ms = 0
                if steady_record:
                    elapsed_ms = steady_record.get(
                        "phase", {}).get("duration_ms", 0)

                results = {
                    "elapsed_ms": elapsed_ms,
                    "phases": all_phases,
                    "perf": {
                        "counters": perf_counters,
                        "collapsed_stacks_url": collapsed_stacks_url,
                        "nested_set_flamegraph_url":
                            nested_set_flamegraph_url
                    },
                    "cpu": cpu_stats,
                    "io": {
                        "disk": disk_stats,
                        "network": network_stats
                    }
                }

                output = {
                    "job_id": self.job_id,
                    "timestamp": self.timestamp,
                    "versions": versions,
                    "config": config,
                    "results": results
                }

                with open(self.output_file, "w") as f:
                    json.dump(output, f, indent=2)

                print(f"\nResults written to {self.output_file}")

                # Publish to PostgreSQL
                self._publish_to_postgresql(versions, config, results)

            finally:
                self.stop_infrastructure()
                self.variance_control.teardown()


def main():
    parser = argparse.ArgumentParser(description="Benchmark runner")
    parser.add_argument("--output", type=str,
                        default="benchmark_results.json")
    parser.add_argument("--workload-config", type=str, required=True)
    parser.add_argument("--driver-config", type=str, required=True)
    parser.add_argument("--resp-bench-dir", type=str, required=True,
                        help="Path to the cloned resp-bench repository")
    parser.add_argument("--skip-infra", action="store_true")
    parser.add_argument("--network-delay", type=int, default=1)

    parser.add_argument("--versions-json", type=str, default="{}",
                        help="JSON string with version info (optional, versions extracted from resp-bench output)")

    parser.add_argument("--job-id-prefix", type=str, default="",
                        help="Optional prefix for the job ID "
                             "(e.g., 'regression', 'nightly', 'pr-123')")

    parser.add_argument("--no-publish", action="store_true",
                        help="Skip publishing to PostgreSQL")
    parser.add_argument("--pg-host", type=str, default=None)
    parser.add_argument("--pg-port", type=int, default=None)
    parser.add_argument("--pg-database", type=str, default=None)
    parser.add_argument("--pg-secret-name", type=str, default=None)
    parser.add_argument("--pg-region", type=str, default=None)

    args = parser.parse_args()

    resp_bench_dir = Path(args.resp_bench_dir)
    output_file = Path(args.output)
    workload_config_path = Path(args.workload_config)
    driver_config_path = Path(args.driver_config)

    # Parse versions JSON from the workflow
    try:
        versions = json.loads(args.versions_json)
    except json.JSONDecodeError as e:
        print(f"ERROR: Failed to parse --versions-json: {e}")
        print(f"  Raw value: {args.versions_json}")
        raise SystemExit(1)

    runner = BenchmarkRunner(
        resp_bench_dir=resp_bench_dir,
        output_file=output_file,
        workload_config_path=workload_config_path,
        driver_config_path=driver_config_path,
        versions=versions,
        job_id_prefix=args.job_id_prefix,
        skip_infra=args.skip_infra,
        network_delay_ms=args.network_delay,
        publish_to_db=not args.no_publish,
        pg_host=args.pg_host,
        pg_port=args.pg_port,
        pg_database=args.pg_database,
        pg_secret_name=args.pg_secret_name,
        pg_region=args.pg_region
    )

    print(f"Loaded workload config: "
          f"{runner.workload_config['benchmark_profile']['name']}")
    print(f"Loaded driver config: {runner.driver_config['driver_id']}")

    runner.run()


if __name__ == "__main__":
    main()