#!/usr/bin/env python3
"""
Scans Android res/drawable* folders for vector drawables, reports the
UTF-8 encoded byte size of each pathData attribute (this is what AAPT2
actually limits to 32767 bytes), and can optionally write size-reduced
copies by rounding decimal precision in the path data.

Usage:
    python3 vector_size_check.py /path/to/app/src/main/res
    python3 vector_size_check.py /path/to/app/src/main/res --optimize --precision 2
"""

import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET

AAPT2_LIMIT = 32767  # 0x7FFF, hard limit per string in compiled resources
NS = {"android": "http://schemas.android.com/apk/res/android"}

DECIMAL_RE = re.compile(r"-?\d+\.\d+")


def find_vector_files(res_dir):
    files = []
    for root, _dirs, names in os.walk(res_dir):
        base = os.path.basename(root)
        if not base.startswith("drawable"):
            continue
        for name in names:
            if name.endswith(".xml"):
                files.append(os.path.join(root, name))
    return files


def get_pathdata_attrs(xml_path):
    """Returns list of (element_tag, byte_length, raw_string) for each
    android:pathData found in the file (covers <path> and clip-path)."""
    try:
        tree = ET.parse(xml_path)
    except ET.ParseError as e:
        print(f"  ! could not parse {xml_path}: {e}", file=sys.stderr)
        return []

    root = tree.getroot()
    if not root.tag.endswith("vector"):
        return []  # not a vector drawable, skip (could be shape, layer-list etc.)

    results = []
    for el in root.iter():
        path_data = el.get(f"{{{NS['android']}}}pathData")
        if path_data is not None:
            byte_len = len(path_data.encode("utf-8"))
            results.append((el.tag, byte_len, path_data))
    return results


def round_pathdata(path_data, precision):
    def repl(m):
        val = float(m.group())
        rounded = round(val, precision)
        # avoid "-0.0" and trailing ".0" noise
        if rounded == int(rounded):
            return str(int(rounded))
        s = f"{rounded:.{precision}f}".rstrip("0").rstrip(".")
        return s
    return DECIMAL_RE.sub(repl, path_data)


def optimize_file(xml_path, precision, out_suffix="_opt"):
    with open(xml_path, "r", encoding="utf-8") as f:
        content = f.read()

    def attr_repl(m):
        prefix, value, suffix = m.group(1), m.group(2), m.group(3)
        new_value = round_pathdata(value, precision)
        return f"{prefix}{new_value}{suffix}"

    # Match android:pathData="....." preserving the surrounding quotes exactly
    pattern = re.compile(r'(android:pathData=")([^"]*)(")')
    new_content = pattern.sub(attr_repl, content)

    base, ext = os.path.splitext(xml_path)
    out_path = f"{base}{out_suffix}{ext}"
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(new_content)
    return out_path, len(content.encode("utf-8")), len(new_content.encode("utf-8"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("res_dir", help="Path to your res/ directory")
    parser.add_argument("--optimize", action="store_true",
                         help="Write rounded-precision copies of any oversized files")
    parser.add_argument("--precision", type=int, default=2,
                         help="Decimal places to keep when optimizing (default: 2)")
    parser.add_argument("--warn-threshold", type=int, default=20000,
                         help="Byte size above which to flag a file as 'getting close' "
                              "(default: 20000; hard limit is 32767)")
    args = parser.parse_args()

    if not os.path.isdir(args.res_dir):
        print(f"Not a directory: {args.res_dir}", file=sys.stderr)
        sys.exit(1)

    vector_files = find_vector_files(args.res_dir)
    if not vector_files:
        print("No vector drawables found under drawable*/ folders.")
        return

    findings = []  # (file, max_pathdata_bytes)
    for path in vector_files:
        attrs = get_pathdata_attrs(path)
        if not attrs:
            continue
        max_bytes = max(b for _tag, b, _v in attrs)
        findings.append((path, max_bytes, attrs))

    findings.sort(key=lambda x: x[1], reverse=True)

    print(f"Scanned {len(vector_files)} XML files under drawable*/, "
          f"{len(findings)} are vector drawables with pathData.\n")
    print(f"{'STATUS':10} {'BYTES':>8}   FILE")
    print("-" * 70)

    over_limit = []
    for path, max_bytes, attrs in findings:
        if max_bytes >= AAPT2_LIMIT:
            status = "OVER LIMIT"
            over_limit.append(path)
        elif max_bytes >= args.warn_threshold:
            status = "warn"
        else:
            continue  # don't clutter output with small/fine files
        rel = os.path.relpath(path, args.res_dir)
        print(f"{status:10} {max_bytes:>8}   {rel}")
        if len(attrs) > 1:
            print(f"           (file has {len(attrs)} separate pathData attributes; "
                  f"each is checked independently against the 32767 limit)")

    print(f"\n{len(over_limit)} file(s) over the AAPT2 hard limit of {AAPT2_LIMIT} bytes.")

    if args.optimize:
        if not over_limit:
            print("Nothing over the limit to optimize.")
            return
        print(f"\nOptimizing with precision={args.precision} decimal places...\n")
        for path in over_limit:
            out_path, before, after = optimize_file(path, args.precision)
            pct = 100 * (1 - after / before) if before else 0
            print(f"  {os.path.relpath(path, args.res_dir)}")
            print(f"    -> {os.path.basename(out_path)}: "
                  f"{before} -> {after} bytes ({pct:.0f}% smaller)")
            attrs_after = get_pathdata_attrs(out_path)
            still_over = any(b >= AAPT2_LIMIT for _t, b, _v in attrs_after)
            if still_over:
                print(f"    ! still over the limit at precision={args.precision}; "
                      f"try a lower --precision or simplify the path geometry")
        print("\nReview the *_opt.xml files (they're copies, originals untouched), "
              "compare visually, then rename to replace the originals if they look good.")


if __name__ == "__main__":
    main()
