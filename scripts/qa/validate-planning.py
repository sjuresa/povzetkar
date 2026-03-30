import re
import json
import argparse
import sys
from pathlib import Path
from typing import List, Dict, Set, Optional

# --- Constants & Defaults ---
DEFAULT_CONFIG = "scripts/qa/planning-validator-config.json"

class Colors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKCYAN = '\033[96m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'

class PlanningValidator:
    def __init__(self, config_path: str, current_version: str):
        self.root = Path.cwd()
        self.config = self._load_config(config_path)
        self.current_version = current_version
        self.violations = []
        
        # Doc filenames from config for dynamic comparison
        self.use_cases_fn = Path(self.config['docs']['use_cases']).name
        self.test_spec_fn = Path(self.config['docs']['test_spec']).name
        
        # State
        self.use_cases = {} # ID -> Title
        self.test_cases = {} # ID -> {uc, version, priority, automation, name, raw}
        self.task_links = [] # {file, id, target_file}

    def _load_config(self, path: str) -> dict:
        p = self.root / path
        if not p.exists():
            print(f"{Colors.FAIL}Error: Config not found at {path}{Colors.ENDC}")
            sys.exit(1)
        with open(p, 'r', encoding='utf-8') as f:
            return json.load(f)

    def log_violation(self, level: str, file: str, reference: str, issue: str, recommendation: str):
        self.violations.append({
            "level": level,
            "file": file,
            "reference": reference,
            "issue": issue,
            "recommendation": recommendation
        })

    def parse_use_cases(self):
        path = self.root / self.config['docs']['use_cases']
        if not path.exists():
            self.log_violation("CRITICAL", str(path), "N/A", "Use cases file missing", f"Ensure {self.config['docs']['use_cases']} exists.")
            return

        content = path.read_text(encoding='utf-8')
        # Look for UC-X globally in the file (robust against table/header shifts)
        pattern = re.compile(r'\b(UC-\d+)\b', re.MULTILINE)
        for match in pattern.finditer(content):
            uc_id = match.group(1)
            if uc_id not in self.use_cases:
                # We extract the ID even if we can't find a title nearby
                self.use_cases[uc_id] = ""

    def parse_test_spec(self):
        path = self.root / self.config['docs']['test_spec']
        if not path.exists():
            self.log_violation("CRITICAL", str(path), "N/A", "Test spec file missing", f"Ensure {self.config['docs']['test_spec']} exists.")
            return

        content = path.read_text(encoding='utf-8')
        # Parsing Markdown tables
        # Format: | ID | Priority | Related UC | Version | Name | Automation | Expected Result |
        lines = content.splitlines()
        for line in lines:
            match = re.match(r'^\s*\|\s*\*\*?(TC-[A-Z]+-\d+)\*\*?\s*\|', line)
            if match:
                tc_id = match.group(1)
                parts = [p.strip() for p in line.split('|')]
                if len(parts) >= 7:
                    priority = parts[2].replace('**', '')
                    related_uc = parts[3]
                    version = parts[4].strip('[]')
                    name = parts[5]
                    automation = parts[6]
                    
                    self.test_cases[tc_id] = {
                        "raw": line,
                        "priority": priority,
                        "related_uc": related_uc,
                        "version": version,
                        "name": name,
                        "automation": automation
                    }

    def parse_tasks(self):
        glob_pattern = self.config['docs']['tasks_glob']
        task_files = list(self.root.glob(glob_pattern))
        
        for task_file in task_files:
            content = task_file.read_text(encoding='utf-8')
            # Look for IDs, optionally preceded by a markdown link
            # We use \b boundary to capture pure IDs like (UC-1) or UC-1 (without parens)
            pattern = re.compile(r'(?:\[[^\]]*\]\(([^)]+)\)\s*)?\b(UC-\d+|TC-[A-Z]+-\d+)\b', re.MULTILINE)
            for match in pattern.finditer(content):
                link_path = match.group(1)
                ref_id = match.group(2)
                
                if link_path:
                    target_filename = Path(link_path.split('#')[0]).name
                else:
                    # Infer target from ID prefix if bare
                    if ref_id.startswith('UC-'):
                        target_filename = self.use_cases_fn
                    elif ref_id.startswith('TC-'):
                        target_filename = self.test_spec_fn
                    else:
                        target_filename = "unknown"

                self.task_links.append({
                    "src_file": task_file.name,
                    "target_file": target_filename,
                    "id": ref_id
                })

    def validate_links(self):
        for link in self.task_links:
            target = link['target_file']
            ref_id = link['id']
            
            if target == self.use_cases_fn:
                if ref_id not in self.use_cases:
                    self.log_violation("CRITICAL", link['src_file'], ref_id, 
                                     f"Broken reference to Use Case: {ref_id}", 
                                     f"Ensure {ref_id} exists in {self.use_cases_fn}")
            elif target == self.test_spec_fn:
                if ref_id not in self.test_cases:
                    self.log_violation("CRITICAL", link['src_file'], ref_id, 
                                     f"Broken reference to Test Case: {ref_id}", 
                                     f"Ensure {ref_id} exists in {self.test_spec_fn}")

    def validate_traceability(self):
        # Every UC must have at least one TC
        mapped_ucs = set()
        for tc in self.test_cases.values():
            ucs = [u.strip() for u in tc['related_uc'].split(',')]
            for u in ucs:
                if u != '-':
                    mapped_ucs.add(u)
        
        for uc_id in self.use_cases:
            if uc_id not in mapped_ucs:
                self.log_violation("WARNING", self.use_cases_fn, uc_id, 
                                 f"Use Case {uc_id} has no mapped Test Case", 
                                 f"Add a corresponding TC in {self.test_spec_fn}")

    def validate_automation_guardrail(self):
        p0_label = self.config['priority']['p0_label'].replace('**', '')
        safe_keywords = self.config['automation']['safe_keywords']
        
        def is_automated(status: str) -> bool:
            return any(k in status for k in safe_keywords)

        def get_version_val(v_str: str):
            # Extract all digit groups (e.g. 'v0.15.0' -> [0, 15, 0], '[v0.13]' -> [0, 13])
            nums = re.findall(r'\d+', v_str)
            return [int(n) for n in nums]

        def compare_versions(v1, v2):
            """Returns -1 if v1 < v2, 0 if v1 == v2, 1 if v1 > v2"""
            for i in range(max(len(v1), len(v2))):
                n1 = v1[i] if i < len(v1) else 0
                n2 = v2[i] if i < len(v2) else 0
                if n1 < n2: return -1
                if n1 > n2: return 1
            return 0

        curr_v_val = get_version_val(self.current_version)

        for tc_id, data in self.test_cases.items():
            if data['priority'] == p0_label:
                # Skip if row contains override comment
                raw_content = data.get('raw', '')
                if "validate:skip" in raw_content:
                    skip_match = re.search(r'validate:skip until (v\d+\.\d+(?:\.\d+)?)', raw_content)
                    if skip_match:
                        until_v = skip_match.group(1)
                        until_v_val = get_version_val(until_v)
                        # If until_v > current_v, we skip the audit (it hasn't expired)
                        if compare_versions(until_v_val, curr_v_val) > 0:
                            continue
                        # If until_v <= current_v, the skip has EXPIRED and we fall through to validate
                    else:
                        # Flag indefinite skips as violations to enforce time-bound debt
                        self.log_violation("WARNING", self.test_spec_fn, tc_id, 
                                         f"P0 Test Case {tc_id} has an indefinite skip", 
                                         "Replace with 'validate:skip until vX.Y.Z' to set a mandatory deadline.")
                        continue

                tc_v_val = get_version_val(data['version'])
                
                # Check version delta (e.g. grace period for automation)
                threshold = self.config['thresholds']['p0_automation_max_version_delta']
                
                # Simple version distance: using minor version if major is equal, otherwise consider it exceeded
                def get_distance(v_old, v_curr):
                    if not v_old or not v_curr: return 99
                    # [major, minor, patch] indices
                    if v_old[0] < v_curr[0]: return 99 # Major version jump
                    if len(v_old) < 2 or len(v_curr) < 2: return 0
                    return v_curr[1] - v_old[1]

                v_dist = get_distance(tc_v_val, curr_v_val)
                
                # If distance > threshold, and it's not automated -> Violation
                if v_dist > threshold:
                    if not is_automated(data['automation']):
                        self.log_violation("CRITICAL", self.test_spec_fn, tc_id, 
                                         f"P0 Test Case {tc_id} (from {data['version']}) exceeds automation threshold ({threshold}) in {self.current_version}", 
                                         "Implement Playwright or Integration test immediately.")

    def run_all(self):
        self.parse_use_cases()
        self.parse_test_spec()
        self.parse_tasks()
        
        self.validate_links()
        self.validate_traceability()
        self.validate_automation_guardrail()

    def print_report(self) -> int:
        if not self.violations:
            print(f"\n{Colors.OKGREEN}✅ Planning Validation Passed! No violations found.{Colors.ENDC}\n")
            return 0
        
        print(f"\n{Colors.BOLD}Planning Validation Report{Colors.ENDC}")
        print("-" * 100)
        print(f"{'Level':<10} | {'File':<25} | {'Ref':<15} | {'Issue':<40}")
        print("-" * 100)
        
        critical_count = 0
        for v in self.violations:
            color = Colors.FAIL if v['level'] == "CRITICAL" else Colors.WARNING
            if v['level'] == "CRITICAL":
                critical_count += 1
            
            print(f"{color}{v['level']:<10}{Colors.ENDC} | {v['file']:<25} | {v['reference']:<15} | {v['issue']:<40}")
            print(f"{' ':<10} | {'Rec: ' + v['recommendation']}")
            print("-" * 100)
        
        print(f"\nSummary: Found {len(self.violations)} violations ({critical_count} critical).\n")
        return 1 if critical_count > 0 else 0

def main():
    parser = argparse.ArgumentParser(description="Povzetkar Planning Validator")
    parser.add_argument("--config", default=DEFAULT_CONFIG, help="Path to config file")
    parser.add_argument("--current-version", required=True, help="Current version string (e.g., v0.15.0)")
    parser.add_argument("--fail-on", default="critical", choices=["critical", "all", "none"], 
                        help="Violation level that triggers non-zero exit code")
    
    args = parser.parse_args()
    
    validator = PlanningValidator(args.config, args.current_version)
    validator.run_all()
    
    exit_code = validator.print_report()
    
    if args.fail_on == "none":
        sys.exit(0)
    elif args.fail_on == "all" and validator.violations:
        sys.exit(1)
    else:
        sys.exit(exit_code)

if __name__ == "__main__":
    main()
