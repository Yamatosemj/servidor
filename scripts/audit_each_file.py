#!/usr/bin/env python3
"""Per-tracked-file reproducible STRUCTURAL audit of Auren + original CE.

Reads each tracked file's actual bytes (not only GitHub directory metadata).
Static checks do not prove game behavior or semantic correctness.
Run after git submodule update --init --recursive.
No file is changed or deleted. Output is a CSV/JSONL/Markdown report.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import csv
import gzip
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import sys
import tomllib
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
ORIGINAL = ROOT / "vendor" / "iceandfire-ce"
CODE_EXTENSIONS = {".java", ".py", ".gradle", ".properties", ".md", ".txt", ".mcfunction",
                   ".fsh", ".vsh", ".glsl", ".snbt", ".lang", ".cfg", ".toml", ".yml", ".yaml",
                   ".json", ".mcmeta", ".csv", ".gitignore", ".gitattributes", ".gitmodules"}
MAX_JSON_ZIP_FILE = 25 * 1024 * 1024
HIGH_RISK = [
    (re.compile(r"\bRuntime\.getRuntime\(\)\.exec\s*\("), "process execution"),
    (re.compile(r"\bnew\s+ProcessBuilder\s*\("), "process launch"),
    (re.compile(r"\bSystem\.exit\s*\("), "JVM termination"),
    (re.compile(r"\bsetAccessible\s*\(\s*true\s*\)"), "reflection bypass"),
    (re.compile(r"\bforceChunk\s*\("), "forced chunk loading"),
    (re.compile(r"\baddRegionTicket\s*\("), "chunk ticket lifecycle"),
]

def git_files(directory: Path):
    raw = subprocess.check_output(["git", "-C", str(directory), "ls-files", "--cached", "-z"])
    return [x.decode("utf-8", "surrogateescape") for x in raw.split(b"\0") if x]

def git_sha(directory: Path) -> str:
    return subprocess.check_output(["git", "-C", str(directory), "rev-parse", "HEAD"], text=True).strip()

def audit_png(data: bytes) -> tuple[str, str]:
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "ERROR", "PNG signature invalid"
    pos = 8
    dims = None
    chunks = 0
    end = False
    while pos + 12 <= len(data):
        count = struct.unpack_from(">I", data, pos)[0]
        tag = data[pos + 4: pos + 8]
        nxt = pos + 12 + count
        if nxt > len(data):
            return "ERROR", "PNG truncated chunk"
        if zlib.crc32(data[pos + 4: pos + 8 + count]) & 0xFFFFFFFF != struct.unpack_from(">I", data, pos + 8 + count)[0]:
            return "ERROR", "PNG chunk CRC mismatch"
        if chunks == 0:
            if tag != b"IHDR" or count != 13:
                return "ERROR", "PNG missing 13-byte IHDR"
            width, height = struct.unpack_from(">II", data, pos + 8)
            if width == 0 or height == 0 or width > 32768 or height > 32768:
                return "ERROR", "PNG dimensions invalid or extreme"
            dims = f"{width}x{height}"
        chunks += 1
        pos = nxt
        if tag == b"IEND":
            end = True
            break
        if chunks > 300000:
            return "ERROR", "PNG excessive chunk count"
    if not end or pos != len(data):
        return "ERROR", "PNG missing IEND or trailing bytes"
    return "PASS", f"PNG chunks CRC validated, {dims}, {chunks} chunks (pixels not decoded)"

def audit_tabula(data: bytes) -> tuple[str, str]:
    try:
        with zipfile.ZipFile(__import__("io").BytesIO(data)) as archive:
            names = archive.namelist()
            if "model.json" not in names:
                return "ERROR", "Tabula ZIP missing model.json"
            info = archive.getinfo("model.json")
            if info.file_size > MAX_JSON_ZIP_FILE:
                return "ERROR", "Tabula model.json unexpectedly large"
            if archive.testzip() is not None:
                return "ERROR", "Tabula ZIP CRC failure"
            model = json.loads(archive.read("model.json"))
            if not isinstance(model, dict) or not isinstance(model.get("cubes"), list):
                return "ERROR", "Tabula model.json lacks cubes list"
            return "PASS", f"Tabula ZIP/CRC+model.json validated, {len(model['cubes'])} root cubes"
    except (zipfile.BadZipFile, json.JSONDecodeError, UnicodeDecodeError, ValueError, KeyError) as exc:
        return "ERROR", f"Tabula ZIP/model invalid: {type(exc).__name__}: {str(exc)[:100]}"

def audit_java(data: bytes, path: str, repo_label: str) -> tuple[str, str]:
    try:
        code = data.decode("utf-8")
    except UnicodeDecodeError:
        return "ERROR", "Java source is not UTF-8"
    package = re.search(r"(?m)^\s*package\s+([\w.]+)\s*;", code)
    if "/src/main/java/" in "/" + path:
        expect = path.split("src/main/java/", 1)[-1]
        if "/" in expect and expect != "module-info.java":
            pkg = expect.rsplit("/", 1)[0].replace("/", ".")
            if not package or package.group(1) != pkg:
                return "ERROR", f"Package/path mismatch: expected {pkg}, found {package.group(1) if package else 'none'}"
    found = []
    for pattern, desc in HIGH_RISK:
        hits = len(pattern.findall(code))
        if hits:
            found.append(f"{desc} ({hits})")
    todo = len(re.findall(r"\b(?:TODO|FIXME|XXX|HACK)\b", code))
    if todo:
        found.append(f"technical-debt markers ({todo})")
    if found:
        return "REVIEW", "; ".join(found[:7]) + " [manual review; compiler validates syntax]"
    return "PASS", "UTF-8; package path checked; syntax covered by separate Gradle build"

def audit_one(scope: str, dir_: Path, name: str) -> dict:
    path = dir_ / name
    display = name if scope == "auren" else "vendor/iceandfire-ce/" + name
    record = dict(path=display, scope=scope, extension=path.suffix.lower() or "(none)",
                  bytes=0, sha256="", status="", finding="")
    try:
        if not path.is_file() or path.is_symlink():
            record.update(status="ERROR", finding="Tracked path missing or symlink (not byte-audited)")
            return record
        data = path.read_bytes()
        record["bytes"] = len(data)
        record["sha256"] = hashlib.sha256(data).hexdigest()
        if not data:
            record.update(status="REVIEW", finding="Empty tracked file; intended emptiness needs review")
            return record
        suffix = path.suffix.lower()
        if suffix == ".png":
            status, finding = audit_png(data)
        elif suffix == ".tbl":
            status, finding = audit_tabula(data)
        elif suffix == ".ogg":
            status = "PASS" if data[:4] == b"OggS" else "ERROR"
            finding = "Ogg container capture header found; audio not decoded" if status == "PASS" else "Missing OggS header"
        elif suffix in {".json", ".mcmeta"}:
            try:
                doc = json.loads(data.decode("utf-8-sig"))
                status, finding = "PASS", "Valid JSON / UTF-8"
                if doc is None:
                    status, finding = "REVIEW", "Valid JSON null; investigate intended resource"
            except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                status, finding = "ERROR", f"Invalid JSON/UTF-8: {exc}"
        elif suffix == ".java":
            status, finding = audit_java(data, name, scope)
        elif suffix == ".toml":
            try:
                tomllib.loads(data.decode("utf-8"))
                status, finding = "PASS", "Valid TOML syntax"
            except (UnicodeDecodeError, tomllib.TOMLDecodeError) as exc:
                status, finding = "ERROR", f"TOML parse failure: {exc}"
        elif suffix == ".nbt":
            if data[:2] == b"\x1f\x8b":
                try:
                    uncompressed = gzip.decompress(data)
                    status, finding = "PASS", f"Valid gzip stream, {len(uncompressed)} bytes; NBT semantics unverified"
                except Exception as exc:
                    status, finding = "ERROR", f"NBT gzip invalid: {exc}"
            else:
                status, finding = "REVIEW", "Non-gzip NBT, format semantics not checked"
        elif suffix in CODE_EXTENSIONS or path.name in {"LICENSE", "NOTICE", "gradlew", "gradlew.bat", "settings.gradle", "build.gradle"}:
            try:
                text = data.decode("utf-8-sig")
                status, finding = "PASS", "UTF-8 text; language semantics not checked"
                if suffix == ".py":
                    compile(text, name, "exec", dont_inherit=True)
                    finding = "Python syntax compiled successfully (not executed)"
                if suffix in {".yml", ".yaml"}:
                    status = "REVIEW"
                    finding = "YAML UTF-8 only; requires workflow/external parser for semantics"
            except (UnicodeDecodeError, SyntaxError) as exc:
                status, finding = "ERROR", f"Text/syntax invalid: {exc}"
        else:
            status, finding = "REVIEW", f"Bytes hashed; extension {suffix or '(none)'} has no format-specific decoder"
        record.update(status=status, finding=finding)
    except Exception as exc:
        record.update(status="ERROR", finding=f"Unexpected check failure: {type(exc).__name__}: {str(exc)[:160]}")
    return record

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="build/reports/file_audit")
    ap.add_argument("--fail-on-error", action="store_true")
    args = ap.parse_args()
    out = ROOT / args.out
    out.mkdir(parents=True, exist_ok=True)
    root_files = [name for name in git_files(ROOT) if name != "vendor/iceandfire-ce"]
    ce_files = git_files(ORIGINAL)
    rows = [audit_one("auren", ROOT, p) for p in root_files]
    rows.append(dict(path="vendor/iceandfire-ce", scope="auren", extension="(gitlink)",
                     bytes=0, sha256=git_sha(ORIGINAL), status="PASS",
                     finding="Entire upstream repository pinned at this gitlink SHA; source files audited separately"))
    rows.extend(audit_one("iceandfire-ce", ORIGINAL, p) for p in ce_files)
    casefold = defaultdict(list)
    for r in rows:
        casefold[(r["scope"], r["path"].casefold())].append(r["path"])
    for r in rows:
        if len(casefold[(r["scope"], r["path"].casefold())]) > 1:
            r["status"], r["finding"] = "ERROR", "Case-insensitive path collision"
    rows.sort(key=lambda x: x["path"])
    total = Counter(x["status"] for x in rows)
    scopes = Counter(x["scope"] for x in rows)
    extension_counts = Counter(x["extension"] for x in rows)
    fields = ("path", "scope", "extension", "bytes", "sha256", "status", "finding")
    with (out / "every_file.csv").open("w", encoding="utf-8", newline="") as fp:
        writer = csv.DictWriter(fp, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)
    with (out / "every_file.jsonl").open("w", encoding="utf-8") as fp:
        for row in rows:
            fp.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
    metadata = {
        "total": len(rows),
        "scopes": dict(scopes),
        "statuses": dict(total),
        "extensions": dict(extension_counts),
        "auren_commit": git_sha(ROOT),
        "ce_commit": git_sha(ORIGINAL),
        "methodology": "Actual bytes inspected for every tracked file; CRC/JSON/ZIP checks where applicable. "
                       "Not a semantic manual code review and not Minecraft gameplay testing."
    }
    (out / "stats.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    flagged = [x for x in rows if x["status"] != "PASS"]
    lines = [
        "# File-by-file structural audit — Auren + original CE",
        "",
        f"Root commit: {metadata['auren_commit']}",
        f"Original CE: {metadata['ce_commit']}",
        f"**Files inspected individually:** {len(rows)}",
        f"Scope counts: {dict(scopes)}",
        f"Statuses: {dict(total)}",
        "",
        "## Interpreting statuses",
        "- PASS: relevant structural bytes, formats or static syntax checks passed.",
        "- REVIEW: format is not fully decoded or source contains markers requiring a human.",
        "- ERROR: structural or parse validation failed; inspect before merging.",
        "",
        "## Coverage and limits",
        "- Every tracked file received its own path, actual SHA-256, size, format, status, finding.",
        "- PNG signature, chunk integrity and CRC are validated; pixels/render output are NOT decoded.",
        "- Tabula ZIP archives are opened, CRC-tested and model.json cubes parsed.",
        "- All JSON and mcmeta are parsed; Java packages and selected risky patterns are checked.",
        "- Java compilation is covered independently by the Gradle CI for both projects.",
        "- Audio Ogg headers are checked but playback and sound-event references are NOT proven.",
        "- An OK Java status does not prove behavior, performance, multiplayer security or correctness.",
        "- Nothing was removed, rewritten, auto-fixed or executed from the audited files.",
        "",
        "## All non-PASS findings (each file separately)",
        ""
    ]
    if not flagged:
        lines.append("None detected by these static checks.")
    else:
        for row in flagged:
            lines.append(f"- **{row['status']}** \`{row['path']}\` — {row['finding']}")
    lines.extend(["", "## Full inventory", "",
                  "See every_file.csv or every_file.jsonl inside the same artifact for every PASS, REVIEW and ERROR,",
                  "including every PNG, OGG, JSON, TBL, Java and supporting source file.", ""])
    (out / "README.md").write_text("\n".join(lines), encoding="utf-8")
    print(f"PER-FILE AUDIT: {len(rows)} files, scopes={dict(scopes)}, statuses={dict(total)}")
    print(f"TOP EXTENSIONS: {extension_counts.most_common(12)}")
    print(f"OUTPUT: {out}")
    print(f"NON-PASS: {len(flagged)}")
    for row in flagged[:18]:
        print(f"  {row['status']}: {row['path']}: {row['finding']}")
    if len(flagged) > 18:
        print(f"  ... {len(flagged)-18} additional individual findings in every_file.csv")
    return 1 if args.fail_on_error and total["ERROR"] else 0

if __name__ == "__main__":
    sys.exit(main())
