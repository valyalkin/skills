#!/usr/bin/env python3
"""Structural validation for this plugin marketplace.

Checks the marketplace manifest, every plugin manifest, and every SKILL.md against the
conventions Claude Code relies on for discovery. Stdlib only, so CI needs nothing installed.

Usage: python3 scripts/validate.py [repo_root]
"""

import json
import sys
from pathlib import Path

REQUIRED_MARKETPLACE_KEYS = ("name", "owner", "plugins")
REQUIRED_PLUGIN_ENTRY_KEYS = ("name", "description", "source")
REQUIRED_PLUGIN_MANIFEST_KEYS = ("name", "description")
REQUIRED_SKILL_KEYS = ("name", "description")

errors: list[str] = []
notes: list[str] = []


def fail(msg: str) -> None:
    errors.append(msg)


def load_json(path: Path):
    try:
        return json.loads(path.read_text())
    except FileNotFoundError:
        fail(f"{path}: missing")
    except json.JSONDecodeError as exc:
        fail(f"{path}: invalid JSON ({exc})")
    return None


def parse_frontmatter(path: Path) -> dict[str, str] | None:
    """Minimal YAML frontmatter reader: top-level `key: value` pairs only."""
    text = path.read_text()
    if not text.startswith("---\n"):
        fail(f"{path}: must start with a '---' YAML frontmatter block")
        return None
    end = text.find("\n---", 3)
    if end == -1:
        fail(f"{path}: frontmatter block is not closed with '---'")
        return None
    fields: dict[str, str] = {}
    for line in text[4:end].splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if line[0].isspace() or ":" not in line:
            continue  # nested or continuation line; not needed for these checks
        key, _, value = line.partition(":")
        fields[key.strip()] = value.strip().strip("\"'")
    return fields


def check_skills(plugin_dir: Path) -> list[str]:
    skills_dir = plugin_dir / "skills"
    if not skills_dir.is_dir():
        return []
    found = []
    for skill_dir in sorted(p for p in skills_dir.iterdir() if p.is_dir()):
        skill_md = skill_dir / "SKILL.md"
        if not skill_md.is_file():
            fail(f"{skill_dir}: skill directory has no SKILL.md")
            continue
        fields = parse_frontmatter(skill_md)
        if fields is None:
            continue
        for key in REQUIRED_SKILL_KEYS:
            if not fields.get(key):
                fail(f"{skill_md}: frontmatter is missing '{key}'")
        name = fields.get("name")
        if name and name != skill_dir.name:
            fail(f"{skill_md}: frontmatter name '{name}' != directory '{skill_dir.name}'")
        if len(fields.get("description", "")) < 40:
            notes.append(f"{skill_md}: description is short; it is the only trigger signal")
        found.append(skill_dir.name)
    return found


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    manifest_path = root / ".claude-plugin" / "marketplace.json"
    manifest = load_json(manifest_path)
    if manifest is None:
        print_report()
        return 1

    for key in REQUIRED_MARKETPLACE_KEYS:
        if key not in manifest:
            fail(f"{manifest_path}: missing '{key}'")

    listed: dict[str, Path] = {}
    for entry in manifest.get("plugins", []):
        for key in REQUIRED_PLUGIN_ENTRY_KEYS:
            if not entry.get(key):
                fail(f"{manifest_path}: plugin entry {entry.get('name', '?')} missing '{key}'")
        source = entry.get("source")
        if not isinstance(source, str):
            continue  # externally hosted (git-subdir object form); nothing local to check
        plugin_dir = (root / source).resolve()
        if not plugin_dir.is_dir():
            fail(f"{manifest_path}: plugin '{entry['name']}' source '{source}' does not exist")
            continue
        listed[entry["name"]] = plugin_dir

        plugin_manifest_path = plugin_dir / ".claude-plugin" / "plugin.json"
        plugin_manifest = load_json(plugin_manifest_path)
        if plugin_manifest is not None:
            for key in REQUIRED_PLUGIN_MANIFEST_KEYS:
                if not plugin_manifest.get(key):
                    fail(f"{plugin_manifest_path}: missing '{key}'")
            if plugin_manifest.get("name") not in (None, entry["name"]):
                fail(
                    f"{plugin_manifest_path}: name '{plugin_manifest['name']}' does not match "
                    f"marketplace entry '{entry['name']}'"
                )
            if plugin_manifest.get("name") not in (None, plugin_dir.name):
                fail(
                    f"{plugin_manifest_path}: name '{plugin_manifest['name']}' does not match "
                    f"directory '{plugin_dir.name}'"
                )

        skills = check_skills(plugin_dir)
        has_component = skills or any(
            (plugin_dir / d).is_dir() for d in ("commands", "agents", "hooks")
        )
        if not has_component:
            fail(f"{plugin_dir}: plugin ships no skills, commands, agents or hooks")
        print(f"  {entry['name']}: {', '.join(skills) if skills else 'no skills'}")

    plugins_dir = root / "plugins"
    if plugins_dir.is_dir():
        for candidate in sorted(p for p in plugins_dir.iterdir() if p.is_dir()):
            if candidate.resolve() not in listed.values():
                fail(f"{candidate}: not listed in {manifest_path.relative_to(root)}")

    print_report()
    return 1 if errors else 0


def print_report() -> None:
    for note in notes:
        print(f"note: {note}")
    for err in errors:
        print(f"error: {err}")
    print(f"\n{len(errors)} error(s), {len(notes)} note(s)")


if __name__ == "__main__":
    sys.exit(main())
