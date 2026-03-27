import re
import json
import os

def sync_metadata():
    # Paths
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    changelog_path = os.path.join(root_dir, 'CHANGELOG.md')
    roadmap_path = os.path.join(root_dir, 'docs/overview/roadmap.md')
    en_i18n_path = os.path.join(root_dir, 'frontend/public/locales/en.json')
    sl_i18n_path = os.path.join(root_dir, 'frontend/public/locales/sl.json')

    print(f"Syncing metadata from {changelog_path} and {roadmap_path}...")

    # 1. Parse Version from CHANGELOG (Top entry)
    with open(changelog_path, 'r', encoding='utf-8') as f:
        content = f.read()
        version_match = re.search(r'## \[([\d\.]+(?:-SNAPSHOT)?)\] - (\d{4}-\d{2}-\d{2})', content)
        if version_match:
            version = version_match.group(1)
            release_date = version_match.group(2)
            print(f"Found latest version: {version} ({release_date})")

    # 2. Extract Top 4 entries (Simplified logic)
    # This is where 'marketing-frizz' would happen, for now we just find the titles.
    entries = []
    # (Simplified extraction for this demo script)
    
    # 3. Update JSON files
    with open(en_i18n_path, 'r', encoding='utf-8') as f:
        en_data = json.load(f)

    # Optional: Update version in i18n too if desired, although API handles it now.
    # en_data['landing']['footer']['version'] = f"v{version}"
    
    print("Metadata synchronization logic ready. (This script can be extended for full automated marketing-frizz).")

if __name__ == "__main__":
    sync_metadata()
