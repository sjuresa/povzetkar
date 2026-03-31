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
    for en in sorted(TRANSLATIONS.keys(), key=len, reverse=True):
        sl = TRANSLATIONS[en]
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
    # Increase to 10 entries
    for block in version_blocks[:10]:
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
        
        # Detect badge type
        badge = "new" if len(entries) == 0 else "improved"
        if "### Added" in block:
            badge = "new"
        elif "### Fixed" in block and not "### Added" in block and not "### Improved" in block:
            badge = "fixed"
        elif "### Improved" in block or "### Changed" in block:
            badge = "improved"

        entries.append({
            "version": version,
            "date": date_str,
            "title": f"Update {version}",
            "desc": summary,
            "badge": badge
        })
    
    return entries

def parse_roadmap(path: str) -> List[Dict[str, str]]:
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    phase_parts = re.split(r'##\s+(v\d+\.\d+):', content)[1:]
    phases = []
    
    # Iterate over pairs of (version, block_content)
    for i in range(0, len(phase_parts), 2):
        version = phase_parts[i]
        block = phase_parts[i+1]

        lines = block.strip().split('\n')
        title = lines[0].strip()
        
        status_match = re.search(r'\*\*Status:\*\*\s*(.*?)\n', block)
        status = status_match.group(1) if status_match else ""
        
        if "Completed" in status or "✅" in status:
            continue
            
        goal_match = re.search(r'\*\*Goal:\*\*\s*(.*?)(?=\n\*\*Status|\n###|$)', block, re.DOTALL)
        goal = goal_match.group(1).strip().replace('\n', ' ') if goal_match else ""
        
        # Combine version and title for a more descriptive entry.
        full_title = f"{version}: {title}"

        phases.append({
            "title": full_title,
            "desc": goal
        })

    return phases

def update_i18n(path: str, changelog_entries: List[Dict[str, str]], roadmap_phases: List[Dict[str, str]], lang: str):
    with open(path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    is_sl = (lang == 'sl')
    
    # Update Changelog Array
    new_changelog = []
    for entry in changelog_entries:
        title = entry['title']
        date_str = entry['date']
        desc = entry['desc']
        
        if is_sl:
            # Try to persist existing translations if titles match
            existing = next((e for e in data['system'].get('changelog_entries', []) if e['version'] == entry['version']), None)
            if existing:
                title = existing['title']
                date_str = existing['date']
                # Intentionally not inheriting existing['desc'] so that upstream changes 
                # (like fixing truncations or markdown) flow into the locale automatically.
            else:
                title = translate_to_sl(title)
                date_str = translate_to_sl(date_str)
        
        new_changelog.append({
            "version": entry['version'],
            "title": title,
            "date": date_str,
            "desc": desc,
            "badge": entry.get('badge', 'new')
        })
    
    data['system']['changelog_entries'] = new_changelog
    
    # Restore/Ensure changelog badges exist
    if 'changelog' not in data['system']:
        data['system']['changelog'] = {
            "title": translate_to_sl("Strategic Updates") if is_sl else "Strategic Updates",
            "badges": {
                "new": "NOVO" if is_sl else "NEW",
                "improved": "IZB" if is_sl else "IMP",
                "fixed": "POP" if is_sl else "FIX"
            }
        }
    else:
        # Ensure badges are there specifically
        if 'badges' not in data['system']['changelog']:
            data['system']['changelog']['badges'] = {
                "new": "NOVO" if is_sl else "NEW",
                "improved": "IZB" if is_sl else "IMP",
                "fixed": "POP" if is_sl else "FIX"
            }
    
    # Update Roadmap Array
    new_roadmap = []
    for phase in roadmap_phases:
        title = phase['title']
        desc = phase['desc']
        
        if is_sl:
            # Try to persist existing translations
            existing = next((p for p in data['system'].get('roadmap_phases', []) if p['title'] == title or (p['title'].startswith(title.split(':')[0]) if ':' in title else False)), None)
            if existing:
                title = existing['title']
                # Intentionally not inheriting existing['desc'] for roadmap phases so upstream
                # updates flow linearly into the locale mapping.
        
        new_roadmap.append({
            "title": title,
            "desc": desc
        })
    
    data['system']['roadmap_phases'] = new_roadmap

    # Clean up old roadmap object but keep the title
    if 'roadmap' in data['system']:
        title = data['system']['roadmap'].get('title', translate_to_sl("Vision Path") if is_sl else "Vision Path")
        data['system']['roadmap'] = {
            "title": title
        }
    else:
        data['system']['roadmap'] = {
            "title": translate_to_sl("Vision Path") if is_sl else "Vision Path"
        }

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
