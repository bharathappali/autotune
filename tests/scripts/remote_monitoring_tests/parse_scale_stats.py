#!/usr/bin/env python3
"""
Parse existing rosSimulationScalabilityTest log timings into kruize_stats.json.

Does NOT recompute or alter scale client behavior — only structures the same
log lines historically grepped by run_bulk_scalability_test.sh.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any, Dict, List, Optional


TIME_ELAPSED_RE = re.compile(
    r"Time elapsed:\s*(\d{2,}):(\d{2}):(\d{2}(?:\.\d+)?)"
)
CREATE_EXP_RE = re.compile(
    r"createExp elapsed time:\s*(\d{2,}):(\d{2}):(\d{2}(?:\.\d+)?)"
)
BULK_RE = re.compile(
    r"bulkDataPost elapsed time:\s*(\d{2,}):(\d{2}):(\d{2}(?:\.\d+)?)"
)
UPDATE_REC_RE = re.compile(
    r"updateRec elapsed time:\s*(\d{2,}):(\d{2}):(\d{2}(?:\.\d+)?)"
)


def hms_to_seconds(h: str, m: str, s: str) -> float:
    return int(h) * 3600 + int(m) * 60 + float(s)


def sum_matches(pattern: re.Pattern, text: str) -> float:
    total = 0.0
    for match in pattern.finditer(text):
        total += hms_to_seconds(*match.groups())
    return total


def parse_log_file(path: Path) -> Dict[str, Any]:
    text = path.read_text(errors="replace")
    return {
        "log_file": str(path),
        "time_elapsed_seconds": sum_matches(TIME_ELAPSED_RE, text),
        "createExp_elapsed_seconds": sum_matches(CREATE_EXP_RE, text),
        "bulkDataPost_elapsed_seconds": sum_matches(BULK_RE, text),
        "updateRec_elapsed_seconds": sum_matches(UPDATE_REC_RE, text),
        "time_elapsed_line_count": len(TIME_ELAPSED_RE.findall(text)),
    }


def parse_scale_dir(
    log_dir: Path,
    instance_id: str,
    namespace: str,
    image: str,
    infra_setup_seconds: Optional[float] = None,
) -> Dict[str, Any]:
    logs = sorted(log_dir.glob("*.log"))
    per_log: List[Dict[str, Any]] = []
    totals = {
        "time_elapsed_seconds": 0.0,
        "createExp_elapsed_seconds": 0.0,
        "bulkDataPost_elapsed_seconds": 0.0,
        "updateRec_elapsed_seconds": 0.0,
    }
    for log in logs:
        # Prefer client scale logs; still parse all *.log that contain markers
        parsed = parse_log_file(log)
        if parsed["time_elapsed_line_count"] == 0 and parsed["createExp_elapsed_seconds"] == 0:
            continue
        per_log.append(parsed)
        for key in totals:
            totals[key] += parsed[key]

    stats: Dict[str, Any] = {
        "id": instance_id,
        "namespace": namespace,
        "image": image,
        "source": "rosSimulationScalabilityTest_logs",
        "load_model": "closed_loop",
        "metric_kind": "traditional_scale_client_timings",
        "note": (
            "Timings are parsed from unchanged rosSimulationScalabilityTest.py "
            "log lines (closed-loop). Not open-loop / CO-corrected latency."
        ),
        "scale_client": totals,
        "per_log": per_log,
    }
    if infra_setup_seconds is not None:
        stats["infra_setup_seconds"] = infra_setup_seconds
        stats["infra_metric_kind"] = "fixed_work_wall_clock"
    return stats


def write_comparison(stats_list: List[Dict[str, Any]], out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    csv_path = out_dir / "comparison.csv"
    md_path = out_dir / "comparison.md"

    headers = [
        "id",
        "namespace",
        "image",
        "infra_setup_seconds",
        "time_elapsed_seconds",
        "createExp_elapsed_seconds",
        "bulkDataPost_elapsed_seconds",
        "updateRec_elapsed_seconds",
        "load_model",
        "source",
    ]

    def row_values(s: Dict[str, Any]) -> List[str]:
        sc = s.get("scale_client") or {}
        return [
            str(s.get("id", "")),
            str(s.get("namespace", "")),
            str(s.get("image", "")),
            str(s.get("infra_setup_seconds", "")),
            str(sc.get("time_elapsed_seconds", s.get("suite_total_seconds", ""))),
            str(sc.get("createExp_elapsed_seconds", "")),
            str(sc.get("bulkDataPost_elapsed_seconds", "")),
            str(sc.get("updateRec_elapsed_seconds", "")),
            str(s.get("load_model", "")),
            str(s.get("source", "")),
        ]

    with csv_path.open("w") as f:
        f.write(",".join(headers) + "\n")
        for s in stats_list:
            f.write(",".join(row_values(s)) + "\n")

    lines = [
        "# Multi-instance comparison",
        "",
        "Scale fields are parsed from existing `rosSimulationScalabilityTest` logs "
        "(same methodology as standalone scale_test). `load_model: closed_loop` — "
        "not open-loop latency percentiles.",
        "",
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join(["---"] * len(headers)) + " |",
    ]
    for s in stats_list:
        lines.append("| " + " | ".join(row_values(s)) + " |")
    md_path.write_text("\n".join(lines) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser(description="Parse scale logs into kruize_stats.json")
    parser.add_argument("--log-dir", required=True, help="Directory containing scale *.log files")
    parser.add_argument("--id", required=True, help="Instance id")
    parser.add_argument("--namespace", required=True, help="Kubernetes namespace")
    parser.add_argument("--image", required=True, help="Resolved image ref")
    parser.add_argument("--out", required=True, help="Output kruize_stats.json path")
    parser.add_argument("--infra-setup-seconds", type=float, default=None)
    args = parser.parse_args()

    stats = parse_scale_dir(
        Path(args.log_dir),
        args.id,
        args.namespace,
        args.image,
        args.infra_setup_seconds,
    )
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(stats, indent=2) + "\n")
    print(f"Wrote {out}")


if __name__ == "__main__":
    main()
