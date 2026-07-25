#!/usr/bin/env python3
"""
Aggregate all .kt and .xml files from each directory of a Kotlin project
into a single .txt file per directory, with start/end markers for each file.
"""

import os
import sys
import argparse
from pathlib import Path

# Extensions to process
INCLUDE_EXTS = {'.kt', '.xml'}


def collect_files(root_dir):
    """
    Walk through root_dir and yield (rel_path, list_of_file_paths)
    for each directory that contains at least one .kt or .xml file.
    """
    root_path = Path(root_dir).resolve()
    for dirpath, _, filenames in os.walk(root_path):
        dirpath = Path(dirpath)
        # Filter files with desired extensions
        files = [dirpath / f for f in filenames if Path(f).suffix in INCLUDE_EXTS]
        if files:
            # Relative path from root
            rel_path = dirpath.relative_to(root_path)
            yield rel_path, files


def write_aggregated_file(output_dir, rel_path, files):
    """
    Write all contents of 'files' into a single .txt file placed in output_dir/rel_path.
    The output file name is the last component of rel_path (the directory name) with .txt.
    """
    # Create output subdirectory
    out_subdir = Path(output_dir) / rel_path
    out_subdir.mkdir(parents=True, exist_ok=True)

    # Output filename: directory name + .txt
    out_filename = out_subdir / (rel_path.name + ".txt")

    with open(out_filename, 'w', encoding='utf-8') as out_f:
        for file_path in sorted(files):  # deterministic order
            file_name = file_path.name
            # Write start tag
            out_f.write(f"//File {file_name} START\n")
            try:
                with open(file_path, 'r', encoding='utf-8') as in_f:
                    out_f.write(in_f.read())
            except UnicodeDecodeError:
                # If a file is binary (shouldn't happen for .kt/.xml, but just in case)
                out_f.write(f"[WARNING: Could not read {file_name} as text]\n")
            # Write end tag with extra newline for separation
            out_f.write(f"\n//File {file_name} END\n\n")


def main():
    parser = argparse.ArgumentParser(
        description="Aggregate .kt and .xml files per directory into one .txt file per directory."
    )
    parser.add_argument(
        'root_dir',
        nargs='?',
        default='.',
        help="Root directory of the Kotlin project (default: current directory)"
    )
    parser.add_argument(
        '-o', '--output',
        default='./combined',
        help="Output directory where the aggregated .txt files will be stored (default: ./combined)"
    )
    args = parser.parse_args()

    root = Path(args.root_dir).resolve()
    if not root.is_dir():
        print(f"Error: '{root}' is not a valid directory.", file=sys.stderr)
        sys.exit(1)

    output_root = Path(args.output).resolve()

    print(f"Scanning {root} …")
    count_dirs = 0
    for rel_path, files in collect_files(root):
        count_dirs += 1
        write_aggregated_file(output_root, rel_path, files)
        print(f"Created: {output_root / rel_path / (rel_path.name + '.txt')} "
              f"({len(files)} files)")

    print(f"\nDone. Processed {count_dirs} directories. Output in: {output_root}")


if __name__ == "__main__":
    main()