#!/usr/bin/env python3
"""
Mock benchmark application that generates fake benchmark results.
Reads benchmark workload config and outputs results aligned with it.
"""

import argparse
import csv
import json
import math
import sys
import time
from datetime import datetime, timezone
from pathlib import Path


def get_timestamp():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def cpu_work(iterations: int = 1000):
    result = 0.0
    for i in range(iterations):
        result += math.sin(i) * math.cos(i)
    return result


def generate_histogram(base_latencies: list, count: int) -> list:
    buckets = []
    remaining = count
    for i, (upper_bound, ratio) in enumerate(base_latencies):
        if i == len(base_latencies) - 1:
            bucket_count = remaining
        else:
            bucket_count = int(count * ratio)
            remaining -= bucket_count
        buckets.append({
            "upper_bound_us": upper_bound,
            "count": max(0, bucket_count)
        })
    return buckets


def write_row(writer, file_handle, phase: str, status: str, start_time: float, commands: list):
    timestamp = get_timestamp()
    time_elapsed = round(time.time() - start_time, 3)

    for cmd in commands:
        row = {
            "phase": phase,
            "status": status,
            "timestamp": timestamp,
            "time_elapsed": time_elapsed,
            "command_name": cmd["name"],
            "num_requests": cmd["num_requests"],
            "successful_requests": cmd["successful_requests"],
            "failed_requests": cmd["failed_requests"],
            "latency_min_us": cmd["latency_min_us"],
            "latency_max_us": cmd["latency_max_us"],
            "histogram_json": json.dumps(cmd["histogram"])
        }
        writer.writerow(row)

    file_handle.flush()


def get_phase_config(workload_config: dict, phase_id: str) -> dict:
    """Get phase configuration by ID"""
    for phase in workload_config.get("phases", []):
        if phase.get("id") == phase_id:
            return phase
    return {}


def calculate_request_counts(phase_config: dict, duration_seconds: int) -> dict:
    """Calculate request counts based on phase config"""
    completion = phase_config.get("completion", {})
    operations = phase_config.get("operations", [])
    
    # Determine total requests
    if completion.get("type") == "requests":
        total_requests = completion.get("request_limit", 1000000)
    else:
        # Duration-based: estimate based on target_rps
        target_rps = phase_config.get("target_rps", 10000)
        if target_rps == -1:
            target_rps = 10000  # Default for warmup
        total_requests = target_rps * duration_seconds

    # Calculate per-operation counts based on weights
    counts = {}
    total_weight = sum(op.get("weight", 1) for op in operations)
    
    for op in operations:
        cmd_name = op.get("command", "UNKNOWN").upper()
        weight = op.get("weight", 1)
        counts[cmd_name] = int(total_requests * (weight / total_weight))
    
    return counts


# Latency profiles for different commands
LATENCY_PROFILES = {
    "SET": [
        (100, 0.02),
        (200, 0.35),
        (300, 0.40),
        (500, 0.15),
        (1000, 0.05),
        (2000, 0.02),
        (5000, 0.01)
    ],
    "GET": [
        (100, 0.03),
        (200, 0.45),
        (300, 0.35),
        (500, 0.10),
        (1000, 0.04),
        (2000, 0.02),
        (5000, 0.01)
    ],
    "DEFAULT": [
        (100, 0.05),
        (200, 0.40),
        (300, 0.35),
        (500, 0.12),
        (1000, 0.05),
        (2000, 0.02),
        (5000, 0.01)
    ]
}


def main():
    parser = argparse.ArgumentParser(description="Mock benchmark application")
    parser.add_argument("--duration", type=int, default=60, help="Steady state duration in seconds")
    parser.add_argument("--warmup", type=int, default=10, help="Warmup duration in seconds")
    parser.add_argument("--output", type=str, required=True, help="Output CSV file path")
    parser.add_argument("--workload-config", type=str, required=True, help="Benchmark workload config JSON file")
    args = parser.parse_args()

    # Load workload config
    workload_config_path = Path(args.workload_config)
    if not workload_config_path.exists():
        print(f"ERROR: Workload config not found: {workload_config_path}", file=sys.stderr)
        sys.exit(1)

    with open(workload_config_path) as f:
        workload_config = json.load(f)

    print(f"Mock benchmark starting", file=sys.stderr)
    print(f"  Workload config: {args.workload_config}", file=sys.stderr)
    print(f"  Warmup duration: {args.warmup}s", file=sys.stderr)
    print(f"  Steady state duration: {args.duration}s", file=sys.stderr)
    print(f"  Output: {args.output}", file=sys.stderr)

    # CSV columns
    fieldnames = [
        "phase", "status", "timestamp", "time_elapsed",
        "command_name", "num_requests", "successful_requests", "failed_requests",
        "latency_min_us", "latency_max_us", "histogram_json"
    ]

    start_time = time.time()

    with open(args.output, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        f.flush()

        # ========== WARMUP PHASE ==========
        warmup_config = get_phase_config(workload_config, "WARMUP")
        warmup_operations = warmup_config.get("operations", [])
        warmup_request_counts = calculate_request_counts(warmup_config, args.warmup)

        print(f"Starting WARMUP phase...", file=sys.stderr)
        print(f"  Operations: {list(warmup_request_counts.keys())}", file=sys.stderr)

        # Write warmup running status
        warmup_commands = []
        for op in warmup_operations:
            cmd_name = op.get("command", "UNKNOWN").upper()
            warmup_commands.append({
                "name": cmd_name,
                "num_requests": 0,
                "successful_requests": 0,
                "failed_requests": 0,
                "latency_min_us": 0,
                "latency_max_us": 0,
                "histogram": []
            })
        write_row(writer, f, "WARMUP", "running", start_time, warmup_commands)

        # Do warmup work
        warmup_end = time.time() + args.warmup
        warmup_iterations = 0
        while time.time() < warmup_end:
            cpu_work(500)
            warmup_iterations += 1

        # Write warmup done status with results
        warmup_commands = []
        for op in warmup_operations:
            cmd_name = op.get("command", "UNKNOWN").upper()
            num_requests = warmup_request_counts.get(cmd_name, 10000)
            latency_profile = LATENCY_PROFILES.get(cmd_name, LATENCY_PROFILES["DEFAULT"])
            warmup_commands.append({
                "name": cmd_name,
                "num_requests": num_requests,
                "successful_requests": num_requests,
                "failed_requests": 0,
                "latency_min_us": 95,
                "latency_max_us": 4200,
                "histogram": generate_histogram(latency_profile, num_requests)
            })
        write_row(writer, f, "WARMUP", "done", start_time, warmup_commands)
        print(f"WARMUP phase complete ({warmup_iterations} iterations)", file=sys.stderr)

        time.sleep(0.5)

        # ========== STEADY STATE PHASE ==========
        steady_config = get_phase_config(workload_config, "STEADY")
        steady_operations = steady_config.get("operations", [])
        steady_request_counts = calculate_request_counts(steady_config, args.duration)

        print(f"Starting STEADY phase...", file=sys.stderr)
        print(f"  Operations: {list(steady_request_counts.keys())}", file=sys.stderr)
        print(f"  Request counts: {steady_request_counts}", file=sys.stderr)

        # Write steady running status
        steady_commands = []
        for op in steady_operations:
            cmd_name = op.get("command", "UNKNOWN").upper()
            steady_commands.append({
                "name": cmd_name,
                "num_requests": 0,
                "successful_requests": 0,
                "failed_requests": 0,
                "latency_min_us": 0,
                "latency_max_us": 0,
                "histogram": []
            })
        write_row(writer, f, "STEADY", "running", start_time, steady_commands)

        # Do steady state work
        steady_end = time.time() + args.duration
        steady_iterations = 0
        while time.time() < steady_end:
            cpu_work(1000)
            steady_iterations += 1

        # Write steady done status with results
        steady_commands = []
        for op in steady_operations:
            cmd_name = op.get("command", "UNKNOWN").upper()
            num_requests = steady_request_counts.get(cmd_name, 100000)
            latency_profile = LATENCY_PROFILES.get(cmd_name, LATENCY_PROFILES["DEFAULT"])
            # Simulate small number of failures
            failed = max(1, num_requests // 100000)
            steady_commands.append({
                "name": cmd_name,
                "num_requests": num_requests,
                "successful_requests": num_requests - failed,
                "failed_requests": failed,
                "latency_min_us": 85,
                "latency_max_us": 5100,
                "histogram": generate_histogram(latency_profile, num_requests)
            })
        write_row(writer, f, "STEADY", "done", start_time, steady_commands)
        print(f"STEADY phase complete ({steady_iterations} iterations)", file=sys.stderr)

    total_time = round(time.time() - start_time, 2)
    print(f"Mock benchmark complete. Total time: {total_time}s", file=sys.stderr)


if __name__ == "__main__":
    main()