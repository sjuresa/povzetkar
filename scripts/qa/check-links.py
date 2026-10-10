#!/usr/bin/env python3
"""Relative-link checker for the tracked Markdown files (POV-223).

Checks every relative Markdown link and image and every HTML ``src``/``srcset`` in every tracked ``*.md``: the
target file must exist. Anchors (``#...``, GitHub slug rules) are checked only in non-historical files; broken
anchors in historical records (docs/versions, docs/retrospectives, docs/adr, docs/process/archive) are reported as
information, because those records are not edited after the fact. External URLs (``http(s):``, ``mailto:``) are
not checked.

Usage (repo root): python -I scripts/qa/check-links.py   — exits 1 on a missing file (in any file) or a missing
anchor in a non-historical file.
"""
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote

HISTORICAL = ("docs/versions/", "docs/retrospectives/", "docs/adr/", "docs/process/archive/")
LINK = re.compile(r"!?\[(?:[^\[\]]|\[[^\]]*\])*\]\(\s*<?([^)\s>]+)>?(?:\s+\"[^\"]*\")?\s*\)")
REF_DEF = re.compile(r"^\s{0,3}\[[^\]]+\]:\s*<?(\S+?)>?(?:\s+.*)?$")
FENCE = re.compile(r"^\s{0,3}(`{3,}|~{3,})(.*)$")
HEADING = re.compile(r"^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$")
HTML_ANCHOR = re.compile(r"<a\s+(?:name|id)=\"([^\"]+)\"", re.I)
HTML_TAG = re.compile(r"<(?:img|source)\b[^>]*>", re.I)
HTML_SRC = re.compile(r"\b(src|srcset)=\"([^\"]+)\"", re.I)


def slug(text):
    """GitHub heading slug: lower-case, drop punctuation except '-' and '_', spaces to '-'."""
    text = re.sub(r"`([^`]*)`", r"\1", text)
    text = re.sub(r"!?\[([^\]]*)\]\([^)]*\)", r"\1", text)
    text = re.sub(r"<[^>]+>", "", text).strip().lower()
    text = re.sub(r"[^\w\- ]", "", text)
    return text.replace(" ", "-")


def strip_code(lines, keep_inline=False):
    """Yield (line number, line) outside fenced code blocks, with inline code removed unless keep_inline.

    CommonMark: a fence closes only with the same character, at least as long, and no info string."""
    fence = None
    for no, line in enumerate(lines, 1):
        m = FENCE.match(line)
        if fence is None and m:
            fence = m.group(1)
            continue
        if fence is not None:
            if m and m.group(1)[0] == fence[0] and len(m.group(1)) >= len(fence) and not m.group(2).strip():
                fence = None
            continue
        yield no, line if keep_inline else re.sub(r"`[^`]*`", "", line)


_anchor_cache = {}


def anchors(path):
    if path not in _anchor_cache:
        found, counts = set(), {}
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        for _, line in strip_code(lines, keep_inline=True):  # inline code is part of a heading's slug
            m = HEADING.match(line)
            if m:
                base = slug(m.group(2))
                n = counts.get(base, 0)
                counts[base] = n + 1
                found.add(base if n == 0 else f"{base}-{n}")
            found.update(a.lower() for a in HTML_ANCHOR.findall(line))
        _anchor_cache[path] = found
    return _anchor_cache[path]


def link_targets(line):
    """Markdown links and images, HTML src/srcset and reference definitions on one line."""
    targets = [m.group(1) for m in LINK.finditer(line)]
    attrs = [a for tag in HTML_TAG.findall(line) for a in HTML_SRC.findall(tag)]  # every src/srcset of a tag
    for attr, value in attrs:
        if attr.lower() == "srcset":  # comma-separated "url descriptor" candidates
            targets += [c.split()[0] for c in value.split(",") if c.strip()]
        else:
            targets.append(value)
    ref = REF_DEF.match(line)
    if ref:
        targets.append(ref.group(1))
    return targets


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # cp1252 consoles (POV-203 lesson)
    root = Path(subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True,
                               check=True).stdout.strip())
    files = subprocess.run(["git", "-C", str(root), "ls-files", "*.md"], capture_output=True, text=True,
                           check=True).stdout.splitlines()
    errors, infos = [], []
    for rel in files:
        src = root / rel
        if not src.exists():
            continue
        historical = rel.startswith(HISTORICAL)
        lines = src.read_text(encoding="utf-8", errors="replace").splitlines()
        for no, line in strip_code(lines):
            for target in link_targets(line):
                if re.match(r"^[a-z][a-z0-9+.-]*:", target, re.I):
                    continue  # external URL, mailto:, etc.
                path_part, _, anchor = target.partition("#")
                dest = (src.parent / unquote(path_part)).resolve() if path_part else src
                where = f"{rel}:{no}: {target}"
                if path_part and not dest.exists():
                    errors.append(f"{where} — missing file")
                    continue
                if anchor and dest.suffix == ".md" and dest.is_file():
                    if anchor.lower() not in anchors(dest):
                        (infos if historical else errors).append(f"{where} — missing anchor")
    for e in errors:
        print(f"BROKEN  {e}")
    for i in infos:
        print(f"INFO    {i} (historical, not fixed)")
    print(f"{len(files)} files, {len(errors)} broken, {len(infos)} historical anchor notes")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
