#!/usr/bin/env python3
"""
Generate a directory tree text file for any given root directory.
Usage:
    python dir_structure.py [root] [-o output_file] [--exclude dir1 dir2 ...]
"""

import argparse
from pathlib import Path

# Default output file name
DEFAULT_OUTPUT = "directory_structure.txt"

# Directories to exclude by default (optional)
DEFAULT_EXCLUDES = {'.git', '__pycache__', 'node_modules', '.idea', '.vscode'}


def generate_tree(
    root_dir: Path,
    prefix: str = "",
    exclude_dirs: set = None,
    include_files: bool = True,
    max_depth: int = None,
    current_depth: int = 0
) -> str:
    """
    Recursively build a string representation of the directory tree.
    """
    if max_depth is not None and current_depth > max_depth:
        return ""

    if exclude_dirs is None:
        exclude_dirs = set()

    # Get sorted list of items in the directory
    items = sorted(root_dir.iterdir(), key=lambda p: (not p.is_dir(), p.name))

    lines = []
    for i, item in enumerate(items):
        # Skip excluded directories (only if it's a directory)
        if item.is_dir() and item.name in exclude_dirs:
            continue

        # Determine if this is the last item in the current directory
        is_last = (i == len(items) - 1)

        # Choose the appropriate branch symbols
        connector = "└── " if is_last else "├── "
        line = prefix + connector + item.name
        if item.is_dir():
            line += "/"   # mark directories with a slash
        lines.append(line)

        # If it's a directory, recurse into it
        if item.is_dir():
            # Extend prefix: if last, add 4 spaces, else add "│   "
            extension = "    " if is_last else "│   "
            sub_tree = generate_tree(
                item,
                prefix + extension,
                exclude_dirs,
                include_files,
                max_depth,
                current_depth + 1
            )
            if sub_tree:
                lines.append(sub_tree)

    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(
        description="Write directory structure to a text file."
    )
    parser.add_argument(
        "root",
        nargs="?",
        default=".",
        help="Root directory to scan (default: current directory)"
    )
    parser.add_argument(
        "-o", "--output",
        default=DEFAULT_OUTPUT,
        help=f"Output text file (default: {DEFAULT_OUTPUT})"
    )
    parser.add_argument(
        "--exclude",
        nargs="*",
        default=[],
        help="Additional directory names to exclude (space-separated)"
    )
    parser.add_argument(
        "--no-files",
        action="store_true",
        help="Include only directories (skip files)"
    )
    parser.add_argument(
        "--max-depth",
        type=int,
        default=None,
        help="Maximum depth to traverse (default: unlimited)"
    )

    args = parser.parse_args()

    root = Path(args.root).resolve()
    if not root.is_dir():
        print(f"Error: '{root}' is not a valid directory.")
        return

    # Combine default and user-provided excludes
    exclude_set = DEFAULT_EXCLUDES.union(set(args.exclude))

    # Generate the tree
    tree_str = generate_tree(
        root,
        exclude_dirs=exclude_set,
        include_files=not args.no_files,
        max_depth=args.max_depth
    )

    # Prepend the root directory name as the top line
    header = f"{root.name}/"
    if args.max_depth is not None:
        header += f"  (max depth: {args.max_depth})"
    full_output = header + "\n" + tree_str

    # Write to the output file
    output_path = Path(args.output)
    output_path.write_text(full_output, encoding="utf-8")

    print(f"Directory structure written to: {output_path.resolve()}")


if __name__ == "__main__":
    main()