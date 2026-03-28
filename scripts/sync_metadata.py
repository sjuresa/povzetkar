import re
import json
import os
from typing import List, Dict, Any

# Simple translation map for metadata terms
TRANSLATIONS = {
    "Update": "Posodobitev",
    "In Progress": "V teku",
    "Vision Path": "Pot vizije",
    "Strategic Updates": "Strateške posodobitve",
    "System Discovery": "Sistemsko raziskovanje",
}

def translate_to_sl(text: str) -> str:
    """
    Translates basic metadata terms to Slovenian.
    For complex summaries, it currently returns the original but identifies them for manual review.
    """
    translated = text
    for en, sl in TRANSLATIONS.items():
        translated = translated.replace(en, sl)
    
    # Custom logic for "Update vX.Y.Z"
    if text.startswith("Update v"):
        translated = text.replace("Update", "Posodobitev")
        
    return translated

def parse_changelog(path: str) -> List[Dict[str, str]]:
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    version_blocks = re.split(r'##\s+\[', content)[1:]
    
    entries = []
    for block in version_blocks[:4]:
        lines = block.strip().split('\n')
        header = lines[0]
        
        version_match = re.search(r'([^\]]+)\]', header)
        date_match = re.search(r'(\d{4}-\d{2}-\d{2})', header)
        
        version = version_match.group(1) if version_match else "Unknown"
        date_str = date_match.group(1) if date_match else "In Progress"
        
        if "Unreleased" in version:
             ver_in_header = re.search(r'\((v[\d\.]+)', header)
             if ver_in_header:
                 version = ver_in_header.group(1)
             else:
                 raise ValueError(f"Could not determine version from 'Unreleased' header: {header}")

        summary = ""
        summary_match = re.search(r'### Summary\n(.*?)(?=\n###|\n##|$)', block, re.DOTALL)
        if summary_match:
            summary = summary_match.group(1).strip().replace('\n', ' ')
        else:
            bullets = re.findall(r'-\s+\*\*([^*]+)\*\*:\s*([^\n]+)', block)
            if bullets:
                summary = " ".join([f"{b[0]}: {b[1]}" for b in bullets[:2]])
        
        if len(summary) > 150:
            summary = summary[:147] + "..."
            
        entries.append({
            "version": version,
            "date": date_str,
            "title": f"Update {version}",
            "desc": summary
        })
    
    return entries

def parse_roadmap(path: str) -> List[Dict[str, str]]:
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    phase_blocks = re.split(r'##\s+(v\d+\.\d+):', content)[1:]
    phases = []
    
    for block in phase_blocks:
        lines = block.strip().split('\n')
        title = lines[0].strip()
        
        status_match = re.search(r'\*\*Status:\*\*\s*(.*?)\n', block)
        status = status_match.group(1) if status_match else ""
        
        if "Completed" in status or "✅" in status:
            continue
            
        # Extract version from the split delimiter if we wanted it, 
        # but here we just need the title and goal
        goal_match = re.search(r'\*\*Goal:\*\*\s*(.*?)(?=\n\*\*Status|\n###|$)', block, re.DOTALL)
        goal = goal_match.group(1).strip().replace('\n', ' ') if goal_match else ""
        
        phases.append({
            "title": title,
            "desc": goal
        })

    return phases[:5]

def update_i18n(path: str, changelog_entries: List[Dict[str, str]], roadmap_phases: List[Dict[str, str]], lang: str):
    with open(path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    is_sl = (lang == 'sl')
    
    # Update Changelog
    for i, entry in enumerate(changelog_entries):
        idx = i + 1
        title = entry['title']
        desc = entry['desc']
        date_str = entry['date']
        
        if is_sl:
            title = translate_to_sl(title)
            date_str = translate_to_sl(date_str)
            # For complex descriptions, we keep original if no translation logic exists
            # In a production app, we'd trigger a translation service here.
    
        data['system']['changelog'][f'entry{idx}_title'] = title
        data['system']['changelog'][f'entry{idx}_desc'] = desc
        data['system']['changelog'][f'entry{idx}_date'] = date_str
    
    # Update Roadmap
    # NOTE: Roadmap titles are highly specific and harder to auto-translate without AI.
    # For now, we update them but keep the structure.
    for i, phase in enumerate(roadmap_phases):
        idx = i + 1
        title = phase['title']
        desc = phase['desc']
        
        if is_sl:
            # We don't overwrite Roadmap titles in SL if they are already significantly different
            # (i.e., already translated).
            existing_title = data['system']['roadmap'].get(f'q{idx}_title', "")
            if existing_title and existing_title != title:
                 # It's likely already translated, so we don't overwrite the title with EN
                 continue
                 
        data['system']['roadmap'][f'q{idx}_title'] = title
        data['system']['roadmap'][f'q{idx}_desc_full'] = desc

    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    
    print(f"Successfully updated {path}")

def sync_metadata():
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    changelog_path = os.path.join(root_dir, 'CHANGELOG.md')
    roadmap_path = os.path.join(root_dir, 'docs/overview/roadmap.md')
    en_i18n_path = os.path.join(root_dir, 'frontend/public/locales/en.json')
    sl_i18n_path = os.path.join(root_dir, 'frontend/public/locales/sl.json')

    if not os.path.exists(changelog_path) or not os.path.exists(roadmap_path):
        print("Error: Required documentation files not found.")
        return

    print("Parsing documentation...")
    try:
        changelog_entries = parse_changelog(changelog_path)
    except ValueError as e:
        print(f"Error parsing changelog: {e}")
        return
        
    roadmap_phases = parse_roadmap(roadmap_path)

    # Update English
    update_i18n(en_i18n_path, changelog_entries, roadmap_phases, 'en')
    
    # Update Slovenian 
    update_i18n(sl_i18n_path, changelog_entries, roadmap_phases, 'sl')

if __name__ == "__main__":
    sync_metadata()
