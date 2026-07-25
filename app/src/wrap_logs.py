#!/usr/bin/env python3
"""
wrap_logs.py — Wraps all Android Log.* calls in Kotlin files with
               if (BuildConfig.DEBUG) to strip them from release builds.

Usage:
    python3 wrap_logs.py <src_directory> [--dry-run]

Examples:
    python3 wrap_logs.py app/src/main/java          # process in-place
    python3 wrap_logs.py app/src/main/java --dry-run  # preview only

Behaviour:
  - Finds all *.kt files recursively under <src_directory>
  - Wraps Log.d / Log.i / Log.w / Log.e / Log.v / Log.wtf calls
  - Single-line calls:  if (BuildConfig.DEBUG) Log.d(...)
  - Multi-line calls:   if (BuildConfig.DEBUG) {
                            Log.d(...)
                        }
  - Already-wrapped calls are skipped (idempotent — safe to re-run)
  - Log calls inside line comments (//) are skipped
  - Creates a .bak backup of every file it modifies
  - Prints a summary of changes made
"""

import os
import re
import sys
import shutil

# Matches the start of any Log call that begins a statement
# (i.e. Log.d, Log.i, Log.w, Log.e, Log.v, Log.wtf)
LOG_START = re.compile(r'^(\s*)(Log\.(d|i|w|e|v|wtf)\s*\()')

INDENT_STEP = "    "   # 4 spaces — matches standard Android/Kotlin style


def is_in_line_comment(line: str, log_pos: int) -> bool:
    """Return True if the Log call at log_pos is preceded by // on the same line."""
    before = line[:log_pos]
    # Find // that is not inside a string — simple heuristic: count quotes
    in_string = False
    i = 0
    while i < len(before) - 1:
        ch = before[i]
        if ch == '"' and (i == 0 or before[i - 1] != '\\'):
            in_string = not in_string
        if not in_string and before[i] == '/' and before[i + 1] == '/':
            return True
        i += 1
    return False


def already_wrapped(lines: list, index: int) -> bool:
    """
    Return True if the Log call at lines[index] is already guarded by
    BuildConfig.DEBUG — either on the same line or on the previous non-blank line.
    Also returns True if the Log call is indented inside an existing
    if (BuildConfig.DEBUG) { block (detected by scanning backwards for the opening).
    """
    line = lines[index]
    # Same line (compact form): if (BuildConfig.DEBUG) Log.d(...)
    if "BuildConfig.DEBUG" in line:
        return True

    # Scan backwards through non-blank lines
    j = index - 1
    while j >= 0:
        prev = lines[j].strip()
        if not prev:
            j -= 1
            continue
        # Line immediately above is the if guard
        if "BuildConfig.DEBUG" in prev:
            return True
        # Line above is an opening brace alone — check one more level up
        if prev == "{":
            j -= 1
            while j >= 0 and not lines[j].strip():
                j -= 1
            if j >= 0 and "BuildConfig.DEBUG" in lines[j]:
                return True
        break

    return False


def count_parens(text: str) -> int:
    """Count net open parentheses in text (open minus close), ignoring strings."""
    depth = 0
    in_string = False
    in_template = 0   # nesting depth of ${ } template expressions
    i = 0
    while i < len(text):
        ch = text[i]
        if ch == '"' and not in_string and (i == 0 or text[i - 1] != '\\'):
            in_string = True
        elif ch == '"' and in_string and (i == 0 or text[i - 1] != '\\') and in_template == 0:
            in_string = False
        elif in_string and ch == '$' and i + 1 < len(text) and text[i + 1] == '{':
            in_template += 1
            i += 1
        elif in_template > 0 and ch == '{':
            in_template += 1
        elif in_template > 0 and ch == '}':
            in_template -= 1
        elif not in_string:
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
        i += 1
    return depth


def collect_log_statement(lines: list, start: int) -> list:
    """
    Starting at lines[start] (which begins a Log call), collect all lines
    that are part of the same statement by counting parentheses.
    Returns the list of lines belonging to the statement.
    """
    collected = [lines[start]]
    depth = count_parens(lines[start])
    i = start + 1
    while depth > 0 and i < len(lines):
        collected.append(lines[i])
        depth += count_parens(lines[i])
        i += 1
    return collected


def wrap_statement(log_lines: list, base_indent: str) -> list:
    """
    Wrap a Log statement (one or more lines) with if (BuildConfig.DEBUG).
    Single-line → compact form (no braces).
    Multi-line  → brace form with extra indentation.
    """
    if len(log_lines) == 1:
        # Compact: if (BuildConfig.DEBUG) Log.d(...)
        stripped = log_lines[0].rstrip()
        original_indent = len(stripped) - len(stripped.lstrip())
        indent = stripped[:original_indent]
        return [f"{indent}if (BuildConfig.DEBUG) {stripped.lstrip()}\n"]
    else:
        # Brace form
        result = [f"{base_indent}if (BuildConfig.DEBUG) {{\n"]
        for log_line in log_lines:
            if log_line.strip():
                result.append(INDENT_STEP + log_line.rstrip("\n") + "\n")
            else:
                result.append("\n")
        result.append(f"{base_indent}}}\n")
        return result


def process_file(path: str, dry_run: bool) -> int:
    """
    Process one Kotlin file.  Returns the number of Log calls wrapped.
    """
    with open(path, "r", encoding="utf-8") as f:
        original_lines = f.readlines()

    out_lines = []
    changes = 0
    i = 0

    while i < len(original_lines):
        line = original_lines[i]

        match = LOG_START.match(line)
        if match:
            base_indent = match.group(1)
            log_pos = len(base_indent)

            # Skip if inside a line comment
            if is_in_line_comment(line, log_pos):
                out_lines.append(line)
                i += 1
                continue

            # Skip if already wrapped
            if already_wrapped(original_lines, i):
                out_lines.append(line)
                i += 1
                continue

            # Collect the full multi-line statement
            statement_lines = collect_log_statement(original_lines, i)

            # Build the wrapped replacement
            wrapped = wrap_statement(statement_lines, base_indent)
            out_lines.extend(wrapped)
            changes += 1
            i += len(statement_lines)
        else:
            out_lines.append(line)
            i += 1

    if changes > 0 and not dry_run:
        shutil.copy2(path, path + ".bak")
        with open(path, "w", encoding="utf-8") as f:
            f.writelines(out_lines)

    return changes


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 wrap_logs.py <src_directory> [--dry-run]")
        sys.exit(1)

    src_dir = sys.argv[1]
    dry_run = "--dry-run" in sys.argv

    if not os.path.isdir(src_dir):
        print(f"ERROR: '{src_dir}' is not a directory")
        sys.exit(1)

    if dry_run:
        print("DRY RUN — no files will be modified\n")

    total_files = 0
    total_changes = 0
    changed_files = []

    for root, _, files in os.walk(src_dir):
        for filename in sorted(files):
            if not filename.endswith(".kt"):
                continue
            filepath = os.path.join(root, filename)
            changes = process_file(filepath, dry_run)
            total_files += 1
            if changes > 0:
                total_changes += changes
                changed_files.append((filepath, changes))
                action = "would wrap" if dry_run else "wrapped"
                print(f"  {action} {changes:>3} call(s)  {filepath}")

    print(f"\n{'─' * 60}")
    print(f"Scanned : {total_files} Kotlin file(s)")
    print(f"Modified: {len(changed_files)} file(s)")
    print(f"Wrapped : {total_changes} Log call(s)")
    if not dry_run and total_changes > 0:
        print(f"\nBackup  : original files saved as <filename>.kt.bak")
        print(f"Undo    : rename .kt.bak → .kt to restore originals")


if __name__ == "__main__":
    main()
