#!/usr/bin/env python3
"""
Combine all .kt and .xml files from a Kotlin project (excluding drawable folders)
into one .txt file, with directory structure and file markers for AI consumption
and later extraction.
"""

import os
import sys
import argparse
from pathlib import Path

# Extensions to process
INCLUDE_EXTS = {'.kt', '.xml'}


def should_skip(path):
    """
    Return True if the path contains a segment named 'drawable' (case‑insensitive).
    This skips the entire drawable folder hierarchy.
    """
    return any(part.lower() == 'drawable' for part in path.parts)


def collect_files(root_dir):
    """
    Walk through root_dir and yield (rel_path, file_path) for each .kt/.xml file
    that is not inside a drawable folder.
    """
    root_path = Path(root_dir).resolve()
    for dirpath, dirnames, filenames in os.walk(root_path):
        dirpath = Path(dirpath)
        # Skip this directory if it is a drawable folder itself or inside one
        if should_skip(dirpath.relative_to(root_path)):
            continue
        # Filter files with desired extensions
        for f in filenames:
            file_path = dirpath / f
            if file_path.suffix in INCLUDE_EXTS:
                rel_path = file_path.relative_to(root_path)
                yield rel_path, file_path


def write_combined_file(output_file, root_dir, files):
    """
    Write all collected files into a single output file.
    Each file is preceded by a marker line with its relative path,
    and followed by an end marker.
    """
    with open(output_file, 'w', encoding='utf-8') as out_f:
        # Optional header
        out_f.write(f"# Combined Kotlin/XML sources from: {root_dir}\n")
        out_f.write(f"# Total files: {len(files)}\n\n")

        # Process files in deterministic order (alphabetical by path)
        for rel_path, file_path in sorted(files, key=lambda x: str(x[0])):
            # Write start marker
            out_f.write(f"===== File: {rel_path} =====\n")
            try:
                with open(file_path, 'r', encoding='utf-8') as in_f:
                    out_f.write(in_f.read())
            except UnicodeDecodeError:
                out_f.write(f"[WARNING: Could not read {rel_path} as text]\n")
            # End marker with extra newline
            out_f.write(f"\n===== End of {rel_path} =====\n\n")


def main():
    parser = argparse.ArgumentParser(
        description="Combine all .kt and .xml files (except drawable folders) "
                    "into a single .txt file with clear file markers."
    )
    parser.add_argument(
        'root_dir',
        nargs='?',
        default='.',
        help="Root directory of the Kotlin project (default: current directory)"
    )
    parser.add_argument(
        '-o', '--output',
        default='combined_code.txt',
        help="Output file path (default: combined_code.txt)"
    )
    args = parser.parse_args()

    root = Path(args.root_dir).resolve()
    if not root.is_dir():
        print(f"Error: '{root}' is not a valid directory.", file=sys.stderr)
        sys.exit(1)

    output_path = Path(args.output).resolve()

    print(f"Scanning {root} (excluding drawable folders)…")
    files = list(collect_files(root))
    if not files:
        print("No .kt or .xml files found outside drawable folders.")
        sys.exit(0)

    write_combined_file(output_path, root, files)
    print(f"Done. Combined {len(files)} files into: {output_path}")


if __name__ == "__main__":
    main()