#!/usr/bin/env python3
"""
Mock benchmark application that generates fake benchmark results.
Reads workload configuration and generates aligned results.
Phase completion is determined by workload config (duration or requests).
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


def load_workload_config(filepath: Path) -> dict:
    with open(filepath) as f:
        return json.load(f)


def get_phase_config(workload_config: dict, phase_id: str) -> dict:
    for phase in workload_config.get("phases", []):
        if phase["id"] == phase_id:
            return phase
    return {}


def get_phase_duration(phase_config: dict, default_duration: int = 60) -> int:
    """Get phase duration from completion config"""
    completion = phase_config.get("completion", {})
    if completion.get("type") == "duration":
        return completion.get("seconds", default_duration)
    else:
        # For request-based completion, simulate with a duration
        total_requests = completion.get("requests", 1000000)
        rps_limit = phase_config.get("rps_limit", -1)
        if rps_limit > 0:
            return max(1, total_requests // rps_limit)
        return default_duration


def get_phase_requests(phase_config: dict) -> int:
    """Get total requests for a phase"""
    completion = phase_config.get("completion", {})
    if completion.get("type") == "requests":
        return completion.get("requests", 1000000)
    else:
        # For duration-based, estimate based on RPS limit
        duration = completion.get("seconds", 60)
        rps_limit = phase_config.get("rps_limit", -1)
        if rps_limit > 0:
            return duration * rps_limit
        else:
            # Unlimited RPS, use keyspace size as estimate
            return phase_config.get("keyspace", {}).get("keys_count", 100000)


def generate_phase_results(phase_config: dict, total_requests: int) -> list:
    commands = phase_config.get("commands", [])
    results = []

    latency_profiles = {
        "SET": [
            (100, 0.02), (200, 0.35), (300, 0.40), (500, 0.15),
            (1000, 0.05), (2000, 0.02), (5000, 0.01)
        ],
        "GET": [
            (100, 0.03), (200, 0.45), (300, 0.35), (500, 0.10),
            (1000, 0.04), (2000, 0.02), (5000, 0.01)
        ]
    }

    total_weight = sum(cmd.get("weight", 1.0) for cmd in commands)

    for cmd in commands:
        command = cmd.get("command", "").upper()
        weight = cmd.get("weight", 1.0)
        cmd_requests = int(total_requests * (weight / total_weight))

        failed = max(0, int(cmd_requests * 0.00001))
        successful = cmd_requests - failed

        profile = latency_profiles.get(command, latency_profiles["GET"])

        results.append({
            "name": command,
            "num_requests": cmd_requests,
            "successful_requests": successful,
            "failed_requests": failed,
            "latency_min_us": 85 if command == "SET" else 92,
            "latency_max_us": 4400 if command == "SET" else 5100,
            "histogram": generate_histogram(profile, cmd_requests)
        })

    return results


def main():
    parser = argparse.ArgumentParser(description="Mock benchmark application")
    parser.add_argument("--workload-config", type=str, required=True, help="Path to workload JSON config")
    parser.add_argument("--output", type=str, required=True, help="Output CSV file path")
    args = parser.parse_args()

    workload_config = load_workload_config(Path(args.workload_config))
    profile_name = workload_config.get("benchmark_profile", {}).get("name", "Unknown")

    warmup_config = get_phase_config(workload_config, "WARMUP")
    steady_config = get_phase_config(workload_config, "STEADY")

    warmup_duration = get_phase_duration(warmup_config, default_duration=10)
    steady_duration = get_phase_duration(steady_config, default_duration=60)

    print(f"Mock benchmark starting", file=sys.stderr)
    print(f"  Workload: {profile_name}", file=sys.stderr)
    print(f"  WARMUP: {warmup_config.get('completion', {})}", file=sys.stderr)
    print(f"  STEADY: {steady_config.get('completion', {})}", file=sys.stderr)
    print(f"  Output: {args.output}", file=sys.stderr)

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
        print(f"Starting WARMUP phase ({warmup_duration}s)...", file=sys.stderr)

        warmup_cmds = warmup_config.get("commands", [{"command": "set", "weight": 1.0}])
        warmup_running = [
            {
                "name": cmd.get("command", "").upper(),
                "num_requests": 0,
                "successful_requests": 0,
                "failed_requests": 0,
                "latency_min_us": 0,
                "latency_max_us": 0,
                "histogram": []
            }
            for cmd in warmup_cmds
        ]
        write_row(writer, f, "WARMUP", "running", start_time, warmup_running)

        warmup_end = time.time() + warmup_duration
        warmup_iterations = 0
        while time.time() < warmup_end:
            cpu_work(500)
            warmup_iterations += 1

        warmup_total_requests = get_phase_requests(warmup_config)
        warmup_results = generate_phase_results(warmup_config, warmup_total_requests)

        write_row(writer, f, "WARMUP", "done", start_time, warmup_results)
        print(f"WARMUP phase complete ({warmup_iterations} iterations)", file=sys.stderr)

        time.sleep(0.5)

        # ========== STEADY STATE PHASE ==========
        print(f"Starting STEADY phase ({steady_duration}s)...", file=sys.stderr)

        steady_cmds = steady_config.get("commands", [])
        steady_running = [
            {
                "name": cmd.get("command", "").upper(),
                "num_requests": 0,
                "successful_requests": 0,
                "failed_requests": 0,
                "latency_min_us": 0,
                "latency_max_us": 0,
                "histogram": []
            }
            for cmd in steady_cmds
        ]
        write_row(writer, f, "STEADY", "running", start_time, steady_running)

        steady_end = time.time() + steady_duration
        steady_iterations = 0
        while time.time() < steady_end:
            cpu_work(1000)
            steady_iterations += 1

        steady_total_requests = get_phase_requests(steady_config)
        steady_results = generate_phase_results(steady_config, steady_total_requests)

        write_row(writer, f, "STEADY", "done", start_time, steady_results)
        print(f"STEADY phase complete ({steady_iterations} iterations)", file=sys.stderr)

    total_time = round(time.time() - start_time, 2)
    print(f"Mock benchmark complete. Total time: {total_time}s", file=sys.stderr)


if __name__ == "__main__":
    main()