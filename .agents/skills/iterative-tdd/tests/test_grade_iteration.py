#!/usr/bin/env python3
"""Behavioral tests for the iterative TDD evidence grader."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SKILL_ROOT = Path(__file__).resolve().parents[1]
GRADER = SKILL_ROOT / "scripts" / "grade_iteration.py"


def valid_evidence(root: Path) -> dict:
    baseline = root / "baseline"
    baseline.mkdir()
    visual_diff = root / "visual-diff.html"
    visual_diff.write_text("<html>diff</html>", encoding="utf-8")
    return {
        "schema_version": 1,
        "iteration_id": "iteration-1",
        "iteration_number": 1,
        "approved_plan_item": "Implement observable behavior",
        "events": [
            {"sequence": 1, "type": "plan_approved", "evidence": "Human said proceed"},
            {"sequence": 2, "type": "baseline_captured", "evidence": str(baseline)},
            {"sequence": 3, "type": "red_test_failed", "evidence": "test command exited 1"},
            {"sequence": 4, "type": "implementation_started", "evidence": "production edit"},
            {"sequence": 5, "type": "green_test_passed", "evidence": "test command exited 0"},
            {"sequence": 6, "type": "validation_completed", "evidence": "checks recorded"},
            {"sequence": 7, "type": "visual_diff_created", "evidence": str(visual_diff)},
            {"sequence": 8, "type": "grade_requested", "evidence": "evidence bundle complete"},
        ],
        "acceptance_criteria": [
            {
                "id": "AC-1",
                "description": "Required behavior is observable",
                "verdict": "PASS",
                "evidence": ["Focused test test_required_behavior passed"],
            }
        ],
        "checks": [
            {"command": "focused test", "required": True, "status": "PASS", "exit_code": 0},
            {"command": "optional lint", "required": False, "status": "SKIPPED", "reason": "Unavailable"},
        ],
        "scope_review": {"verdict": "PASS", "evidence": "Only approved files changed"},
        "claims_review": {"verdict": "PASS", "evidence": "Handoff matches command results"},
        "artifacts": {
            "baseline_path": str(baseline),
            "baseline_digest": "abc123",
            "visual_diff_path": str(visual_diff),
            "visual_diff_baseline_digest": "abc123",
        },
        "git": {"commit_authorized": False, "commit_created": False},
        "checklist_item_completed": True,
    }


class GradeIterationTest(unittest.TestCase):
    def run_grader(self, evidence: dict) -> tuple[subprocess.CompletedProcess[str], dict]:
        with tempfile.TemporaryDirectory() as directory:
            evidence_path = Path(directory) / "evidence.json"
            evidence_path.write_text(json.dumps(evidence), encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(GRADER), str(evidence_path)],
                capture_output=True,
                check=False,
                text=True,
            )
            return result, json.loads(result.stdout)

    def test_valid_evidence_passes_both_dimensions(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            result, report = self.run_grader(valid_evidence(Path(directory)))
        self.assertEqual(0, result.returncode)
        self.assertEqual("PASS", report["overall_verdict"])
        self.assertEqual("PASS", report["output_correctness"]["verdict"])
        self.assertEqual("PASS", report["process_compliance"]["verdict"])

    def test_implementation_before_red_fails_process_compliance(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            evidence = valid_evidence(Path(directory))
            evidence["events"][2]["sequence"] = 4
            evidence["events"][3]["sequence"] = 3
            result, report = self.run_grader(evidence)
        self.assertEqual(1, result.returncode)
        self.assertEqual("FAIL", report["process_compliance"]["verdict"])
        self.assertIn("red_test_failed must occur before implementation_started", report["process_compliance"]["findings"])

    def test_failed_acceptance_criterion_fails_output_correctness(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            evidence = valid_evidence(Path(directory))
            evidence["acceptance_criteria"][0]["verdict"] = "FAIL"
            result, report = self.run_grader(evidence)
        self.assertEqual(1, result.returncode)
        self.assertEqual("FAIL", report["output_correctness"]["verdict"])

    def test_unauthorized_commit_fails_process_compliance(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            evidence = valid_evidence(Path(directory))
            evidence["git"]["commit_created"] = True
            result, report = self.run_grader(evidence)
        self.assertEqual(1, result.returncode)
        self.assertIn("commit was created without recorded authorization", report["process_compliance"]["findings"])

    def test_missing_required_evidence_is_unverifiable(self) -> None:
        result, report = self.run_grader({"schema_version": 1})
        self.assertEqual(1, result.returncode)
        self.assertEqual("UNVERIFIABLE", report["overall_verdict"])
        self.assertTrue(report["missing_evidence"])


if __name__ == "__main__":
    unittest.main()
