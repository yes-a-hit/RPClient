"""Rebuild res/values/strings.xml from the Simplified Chinese translation.

The app defaults to English because values/ holds English text. Swapping the
default resource set to Chinese makes Chinese the fallback for every locale,
while the other translated folders keep working. Brand entries that are marked
translatable="false" and therefore absent from the Chinese file are preserved
and renamed to the custom build name.
"""
import re
import sys
from pathlib import Path

# 相对脚本位置推导仓库根，避免把本机绝对路径写进版本库
REPO_ROOT = Path(__file__).resolve().parent.parent
RES = REPO_ROOT / "app" / "src" / "main" / "res"
EN = RES / "values" / "strings.xml"
ZH = RES / "values-b+zh+Hans" / "strings.xml"
CUSTOM_NAME = "RPClient UV"

STRING_RE = re.compile(r'^(\s*)<string name="([^"]+)"([^>]*)>(.*)</string>\s*$', re.S)
PLURALS_OPEN_RE = re.compile(r'^(\s*)<plurals name="([^"]+)"')
BRAND_KEYS = {"app_name", "about_brand_name"}


def read_text(p: Path) -> str:
    return p.read_text(encoding="utf-8")


def parse_strings(text: str) -> dict:
    out = {}
    for line in text.splitlines():
        m = STRING_RE.match(line)
        if m:
            out[m.group(2)] = m.group(4)
    return out


def parse_plurals(text: str) -> dict:
    out = {}
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        m = PLURALS_OPEN_RE.match(lines[i])
        if not m:
            i += 1
            continue
        name = m.group(2)
        block = [lines[i]]
        i += 1
        while i < len(lines) and "</plurals>" not in lines[i]:
            block.append(lines[i])
            i += 1
        if i < len(lines):
            block.append(lines[i])
        out[name] = "\n".join(block)
        i += 1
    return out


def main() -> int:
    en_text = read_text(EN)
    zh_text = read_text(ZH)
    zh_strings = parse_strings(zh_text)
    zh_plurals = parse_plurals(zh_text)

    lines = en_text.splitlines()
    result = []
    replaced = 0
    i = 0
    while i < len(lines):
        line = lines[i]
        m = PLURALS_OPEN_RE.match(line)
        if m and m.group(2) in zh_plurals:
            result.append(zh_plurals[m.group(2)])
            replaced += 1
            while i < len(lines) and "</plurals>" not in lines[i]:
                i += 1
            i += 1
            continue

        s = STRING_RE.match(line)
        if s:
            indent, name, attrs, _ = s.groups()
            if name in BRAND_KEYS:
                result.append(f'{indent}<string name="{name}"{attrs}>{CUSTOM_NAME}</string>')
            elif name in zh_strings:
                result.append(f'{indent}<string name="{name}"{attrs}>{zh_strings[name]}</string>')
                replaced += 1
            else:
                result.append(line)
            i += 1
            continue

        result.append(line)
        i += 1

    EN.write_text("\n".join(result) + "\n", encoding="utf-8", newline="\n")
    print(f"replaced entries: {replaced}, total lines: {len(result)}")
    missing = [k for k in zh_strings if not STRING_RE.match(next((l for l in result if f'name="{k}"' in l), ""))]
    if missing:
        print("missing after rewrite:", missing[:10])
    return 0


if __name__ == "__main__":
    sys.exit(main())
