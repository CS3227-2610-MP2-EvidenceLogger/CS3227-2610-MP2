#!/usr/bin/env python3
"""Validate iterative-TDD evidence and emit a two-dimension grade."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


REQUIRED_FIELDS = (
    "iteration_id",
    "iteration_number",
    "approved_plan_item",
    "events",
    "acceptance_criteria",
    "checks",
    "scope_review",
    "claims_review",
    "artifacts",
    "git",
    "checklist_item_completed",
)
EVENT_ORDER = (
    "plan_approved",
    "baseline_captured",
    "red_test_failed",
    "implementation_started",
    "green_test_passed",
    "validation_completed",
    "visual_diff_created",
    "grade_requested",
)
VALID_VERDICTS = {"PASS", "FAIL", "UNVERIFIABLE"}


def result(verdict: str, findings: list[str]) -> dict[str, Any]:
    return {"verdict": verdict, "findings": findings}


def evidence_path(manifest_path: Path, value: Any) -> Path | None:
    if not isinstance(value, str) or not value.strip():
        return None
    path = Path(value)
    return path if path.is_absolute() else manifest_path.parent / path


def grade(manifest: dict[str, Any], manifest_path: Path) -> dict[str, Any]:
    missing = [field for field in REQUIRED_FIELDS if field not in manifest]
    if manifest.get("schema_version") != 1:
        missing.append("schema_version=1")
    if missing:
        finding = ["essential grading evidence is missing"]
        return {
            "overall_verdict": "UNVERIFIABLE",
            "output_correctness": result("UNVERIFIABLE", finding.copy()),
            "process_compliance": result("UNVERIFIABLE", finding.copy()),
            "missing_evidence": sorted(set(missing)),
            "required_remediation": ["Provide the missing raw evidence and grade again."],
        }

    correctness_findings: list[str] = []
    process_findings: list[str] = []
    unverifiable_output: list[str] = []
    unverifiable_process: list[str] = []

    criteria = manifest["acceptance_criteria"]
    if not isinstance(criteria, list) or not criteria:
        unverifiable_output.append("acceptance_criteria must contain at least one criterion")
    else:
        for index, criterion in enumerate(criteria, start=1):
            if not isinstance(criterion, dict):
                unverifiable_output.append(f"acceptance criterion {index} is not an object")
                continue
            verdict = criterion.get("verdict")
            evidence = criterion.get("evidence")
            criterion_id = criterion.get("id", str(index))
            if verdict not in VALID_VERDICTS:
                unverifiable_output.append(f"acceptance criterion {criterion_id} lacks a valid verdict")
            elif verdict == "FAIL":
                correctness_findings.append(f"acceptance criterion {criterion_id} failed")
            elif verdict == "UNVERIFIABLE":
                unverifiable_output.append(f"acceptance criterion {criterion_id} is unverifiable")
            if not isinstance(evidence, list) or not any(isinstance(item, str) and item.strip() for item in evidence):
                unverifiable_output.append(f"acceptance criterion {criterion_id} lacks evidence")

    checks = manifest["checks"]
    if not isinstance(checks, list) or not checks:
        unverifiable_output.append("checks must contain executed or explicitly skipped validation")
    else:
        for index, check in enumerate(checks, start=1):
            if not isinstance(check, dict):
                unverifiable_output.append(f"check {index} is not an object")
                continue
            label = check.get("command", f"check {index}")
            status = check.get("status")
            if check.get("required") is True and (status != "PASS" or check.get("exit_code") != 0):
                correctness_findings.append(f"required check did not pass: {label}")
            elif status == "FAIL":
                correctness_findings.append(f"check failed: {label}")
            elif status == "SKIPPED" and (check.get("required") is True or not check.get("reason")):
                correctness_findings.append(f"check was skipped without an acceptable reason: {label}")
            elif status not in {"PASS", "FAIL", "SKIPPED"}:
                unverifiable_output.append(f"check has invalid status: {label}")

    for field in ("scope_review", "claims_review"):
        review = manifest[field]
        if not isinstance(review, dict) or review.get("verdict") not in VALID_VERDICTS or not review.get("evidence"):
            unverifiable_output.append(f"{field} lacks a valid verdict or evidence")
        elif review["verdict"] == "FAIL":
            correctness_findings.append(f"{field} failed")
        elif review["verdict"] == "UNVERIFIABLE":
            unverifiable_output.append(f"{field} is unverifiable")

    events = manifest["events"]
    event_sequences: dict[str, int] = {}
    if not isinstance(events, list):
        unverifiable_process.append("events must be a list")
    else:
        for event in events:
            if not isinstance(event, dict):
                unverifiable_process.append("an event is not an object")
                continue
            event_type = event.get("type")
            sequence = event.get("sequence")
            if not isinstance(event_type, str) or not isinstance(sequence, int) or not event.get("evidence"):
                unverifiable_process.append("every event requires type, integer sequence, and evidence")
                continue
            if event_type in event_sequences:
                process_findings.append(f"duplicate event: {event_type}")
            event_sequences[event_type] = sequence

    for event_type in EVENT_ORDER:
        if event_type not in event_sequences:
            unverifiable_process.append(f"missing event: {event_type}")
    if all(event_type in event_sequences for event_type in EVENT_ORDER):
        for earlier, later in zip(EVENT_ORDER, EVENT_ORDER[1:]):
            if event_sequences[earlier] >= event_sequences[later]:
                process_findings.append(f"{earlier} must occur before {later}")

    iteration_number = manifest["iteration_number"]
    if not isinstance(iteration_number, int) or iteration_number < 1:
        unverifiable_process.append("iteration_number must be a positive integer")
    elif iteration_number > 1:
        continuation = event_sequences.get("continuation_approved")
        baseline = event_sequences.get("baseline_captured")
        if continuation is None:
            unverifiable_process.append("later iterations require continuation_approved evidence")
        elif baseline is not None and continuation >= baseline:
            process_findings.append("continuation_approved must occur before baseline_captured")

    artifacts = manifest["artifacts"]
    if not isinstance(artifacts, dict):
        unverifiable_process.append("artifacts must be an object")
    else:
        baseline_path = evidence_path(manifest_path, artifacts.get("baseline_path"))
        diff_path = evidence_path(manifest_path, artifacts.get("visual_diff_path"))
        if baseline_path is None or not baseline_path.exists():
            unverifiable_process.append("baseline artifact does not exist")
        if diff_path is None or not diff_path.is_file():
            unverifiable_process.append("visual diff artifact does not exist")
        baseline_digest = artifacts.get("baseline_digest")
        if not baseline_digest or baseline_digest != artifacts.get("visual_diff_baseline_digest"):
            process_findings.append("visual diff does not identify the captured iteration baseline")

    git = manifest["git"]
    if not isinstance(git, dict) or not isinstance(git.get("commit_authorized"), bool) or not isinstance(git.get("commit_created"), bool):
        unverifiable_process.append("git evidence requires boolean authorization and creation fields")
    elif git["commit_created"] and not git["commit_authorized"]:
        process_findings.append("commit was created without recorded authorization")

    output_verdict = "FAIL" if correctness_findings else "UNVERIFIABLE" if unverifiable_output else "PASS"
    process_verdict = "FAIL" if process_findings else "UNVERIFIABLE" if unverifiable_process else "PASS"
    if manifest["checklist_item_completed"] is True and (output_verdict != "PASS" or process_verdict != "PASS"):
        process_findings.append("checklist item was completed before both grading dimensions passed")
        process_verdict = "FAIL"
    elif not isinstance(manifest["checklist_item_completed"], bool):
        unverifiable_process.append("checklist_item_completed must be boolean")
        if process_verdict == "PASS":
            process_verdict = "UNVERIFIABLE"

    if "FAIL" in {output_verdict, process_verdict}:
        overall = "FAIL"
    elif "UNVERIFIABLE" in {output_verdict, process_verdict}:
        overall = "UNVERIFIABLE"
    else:
        overall = "PASS"

    missing.extend(unverifiable_output)
    missing.extend(unverifiable_process)
    remediation = correctness_findings + process_findings + missing
    return {
        "overall_verdict": overall,
        "output_correctness": result(output_verdict, correctness_findings + unverifiable_output),
        "process_compliance": result(process_verdict, process_findings + unverifiable_process),
        "missing_evidence": missing,
        "required_remediation": remediation,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("evidence", type=Path, help="Path to an iteration evidence JSON file")
    arguments = parser.parse_args()
    try:
        manifest = json.loads(arguments.evidence.read_text(encoding="utf-8"))
        if not isinstance(manifest, dict):
            raise ValueError("top-level JSON value must be an object")
        report = grade(manifest, arguments.evidence.resolve())
    except (OSError, json.JSONDecodeError, ValueError) as error:
        report = {
            "overall_verdict": "UNVERIFIABLE",
            "output_correctness": result("UNVERIFIABLE", [str(error)]),
            "process_compliance": result("UNVERIFIABLE", [str(error)]),
            "missing_evidence": ["readable evidence JSON"],
            "required_remediation": ["Provide a readable evidence JSON object and grade again."],
        }
    json.dump(report, sys.stdout, indent=2, sort_keys=True)
    sys.stdout.write("\n")
    return 0 if report["overall_verdict"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
