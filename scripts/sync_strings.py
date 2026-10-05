#!/usr/bin/env python3
"""Adds every missing string to each translation file.

Run after adding a string to ``app/src/main/res/values/strings.xml`` so translators see the new
keys in the right place. Elements are written out as text rather than through ElementTree, which
would otherwise reformat every file it touches.

    python3 scripts/sync_strings.py
"""

import re
import sys
from pathlib import Path

RES_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"
DEFAULT_STRINGS = RES_DIR / "values" / "strings.xml"

# A locale folder is a language on its own ("values-de") or with a region ("values-zh-rCN").
LOCALE_FOLDER = re.compile(r"^values-([a-z]{2,3})(-r[A-Z]{2})?$")
ELEMENT = re.compile(r"[ \t]*<(string|plurals|string-array)\s[^>]*>.*?</\1>[ \t]*\n?", re.S)
UNTRANSLATABLE = re.compile(r'translatable="false"')
PROLOGUE = re.compile(r"\A(?:<\?xml[^>]*\?>\s*)?(?:<!--.*?-->\s*)*")
EPILOGUE = re.compile(r"(?:\s*</resources>)?[ \t]*\Z")


def split_document(text):
    """The text before the first element, the elements, and the text after the last one.

    The prologue holds the XML declaration and the ``<resources>`` tag; the epilogue holds the
    closing tag. Both are put back verbatim so a sync never reformats the file.
    """
    first = ELEMENT.search(text)
    last = None
    for match in ELEMENT.finditer(text):
        last = match
    if first is None:
        return text, "", ""
    prologue = text[:first.start()]
    # Elements are appended with their own trailing newline, so the closing tag needs one too.
    epilogue = text[last.end():]
    if not epilogue.startswith("\n") and epilogue.strip():
        epilogue = "\n" + epilogue
    return prologue, text[first.start():last.end()], epilogue


def parse_elements(body):
    """The element blocks in ``body``, keyed by their ``name`` attribute, plus their order."""
    elements = {}
    order = []
    for match in ELEMENT.finditer(body):
        block = match.group(0)
        name = re.search(r'name="([^"]+)"', block)
        if name:
            elements[name.group(1)] = block
            order.append(name.group(1))
    return elements, order


def is_translatable(block):
    return UNTRANSLATABLE.search(block) is None


def sync():
    if not DEFAULT_STRINGS.exists():
        sys.exit(f"Missing {DEFAULT_STRINGS}")

    default_text = DEFAULT_STRINGS.read_text(encoding="utf-8")
    default_prologue, default_body, _ = split_document(default_text)
    default_elements, _ = parse_elements(default_body)
    translatable = [name for name, block in default_elements.items() if is_translatable(block)]

    for lang_dir in sorted(RES_DIR.glob("values-*")):
        if not LOCALE_FOLDER.match(lang_dir.name):
            continue
        strings_file = lang_dir / "strings.xml"
        if not strings_file.exists():
            strings_file.write_text(default_text, encoding="utf-8")
            print(f"Created {strings_file.relative_to(RES_DIR.parent.parent.parent)}")
            continue

        lang_text = strings_file.read_text(encoding="utf-8")
        lang_prologue, lang_body, lang_epilogue = split_document(lang_text)
        lang_elements, lang_order = parse_elements(lang_body)
        missing = [name for name in translatable if name not in lang_elements]
        if not missing:
            print(f"Up to date: {lang_dir.name}")
            continue

        default_order = {name: i for i, name in enumerate(default_elements)}
        rebuilt = []
        for name in lang_order:
            rebuilt.append(lang_elements[name])
        # Insert each missing string directly before the element that follows it in the default
        # file, so the translation files keep the same order translators expect.
        for name in missing:
            block = default_elements[name]
            anchor = next(
                (other for other in lang_order
                 if default_order.get(other, 1 << 30) > default_order[name]),
                None,
            )
            if anchor is None:
                rebuilt.append(block)
            else:
                rebuilt.insert(lang_order.index(anchor), block)

        strings_file.write_text(lang_prologue + "".join(rebuilt) + lang_epilogue,
                                encoding="utf-8")
        print(f"Added {len(missing)} string(s) to {lang_dir.name}: {', '.join(missing)}")


if __name__ == "__main__":
    sync()