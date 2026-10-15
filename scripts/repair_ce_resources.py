#!/usr/bin/env python3
"""Reproducible runtime RESOURCE repairs for pinned, otherwise untouched IceAndFire CE.

ONLY fixes 37 upstream resource findings: 18 missing .txt extensions, 11
Portuguese bestiary files encoded in cp1252 instead of UTF-8, and 8 PNG
files with incorrect chunk CRCs. All original Java classes and mod registries
remain byte-identical in the patched distribution JAR.

Run AFTER compiling original CE. This program does NOT alter the upstream
submodule, do not silently replace source bytes, and does not fake source-audit
results. LGPL-3.0-or-later attribution remains with the distributed source.
"""
from __future__ import annotations

from collections import Counter
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
ORIGINAL = ROOT / "vendor" / "iceandfire-ce"
RESOURCES = ORIGINAL / "src" / "main" / "resources"
SHA = "aa2f11a6b79acb48a1094765d610cdaca79e3d18"
EXPECTED = {"extension": 18, "encoding": 11, "png_crc": 8}
PNG_HEADER = b"\x89PNG\r\n\x1a\n"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def png_chunks(data: bytes):
    assert data.startswith(PNG_HEADER), "Invalid original PNG signature"
    pos = 8
    count = 0
    while pos + 12 <= len(data):
        n = struct.unpack_from(">I", data, pos)[0]
        typ = data[pos + 4 : pos + 8]
        end = pos + n + 12
        if end > len(data):
            raise AssertionError(f"Truncated PNG chunk {typ!r}")
        chunk = data[pos + 8 : pos + 8 + n]
        original_crc = struct.unpack_from(">I", data, pos + 8 + n)[0]
        actual_crc = zlib.crc32(typ + chunk) & 0xFFFFFFFF
        yield typ, chunk, original_crc, actual_crc
        pos = end
        count += 1
        assert count <= 300000, "Too many PNG chunks"
        if typ == b"IEND":
            assert n == 0 and pos == len(data), "Invalid PNG ending"
            return
    raise AssertionError("Missing PNG IEND")


def repair_png(data: bytes) -> tuple[bytes, list[str]]:
    chunks = list(png_chunks(data))
    assert chunks[0][0] == b"IHDR" and len(chunks[0][1]) == 13, "Bad PNG IHDR"
    assert chunks[-1][0] == b"IEND", "Bad PNG IEND"
    width, height = struct.unpack_from(">II", chunks[0][1], 0)
    assert 0 < width <= 32768 and 0 < height <= 32768, "Invalid PNG dimensions"
    idat = b"".join(payload for kind, payload, _, _ in chunks if kind == b"IDAT")
    assert idat, "Missing PNG image data"
    # Re-signing CRCs must NEVER conceal a damaged compressed pixel stream.
    try:
        stream = zlib.decompressobj()
        decoded = stream.decompress(idat, 128 * 1024 * 1024)
        decoded += stream.flush()
        assert stream.eof and not stream.unused_data, "Truncated/extra IDAT zlib bytes"
        assert decoded, "Empty decoded pixels"
    except (zlib.error, AssertionError) as exc:
        raise AssertionError(f"Original PNG pixel data invalid, refusing CRC-only repair: {exc}") from exc

    defects = [kind.decode("ascii", "replace") for kind, payload, crc, actual
               in chunks if crc != actual]
    assert defects, "Unexpectedly valid CRC; upstream file was changed"
    result = bytearray(PNG_HEADER)
    for kind, payload, _crc, actual in chunks:
        result.extend(struct.pack(">I", len(payload)))
        result.extend(kind)
        result.extend(payload)
        result.extend(struct.pack(">I", actual))
    # Built output must validate independently with the same structural check.
    assert all(saved == computed for _, _, saved, computed in png_chunks(bytes(result)))
    assert bytes(result) != data
    return bytes(result), defects


def plan_repairs() -> tuple[dict[str, bytes], dict[str, str], list[dict]]:
    """Return runtime path overrides, deleted extensionless paths, and manifest.

    Repair enumeration is strict and must be unchanged under the pinned CE SHA.
    """
    overrides: dict[str, bytes] = {}
    renames: dict[str, str] = {}
    records: list[dict] = []
    candidates = sorted((RESOURCES / "assets/iceandfire/lang/bestiary").rglob("*"))
    candidate_names = [p for p in candidates if p.is_file()]
    for p in candidate_names:
        relative = p.relative_to(RESOURCES).as_posix()
        original = p.read_bytes()
        new_name: str | None = None
        kind: str | None = None
        detail = ""
        if not p.suffix:
            new_name = relative + ".txt"
            assert not (RESOURCES / new_name).exists(), f"Name conflict: {new_name}"
            kind = "extension"
            detail = "Added required .txt extension expected by BestiaryScreen"
            try:
                original.decode("utf-8")
                fixed = original
            except UnicodeDecodeError:
                fixed = original.decode("cp1252").encode("utf-8")
                detail += " and converted text from cp1252 to UTF-8"
        else:
            assert p.suffix == ".txt", f"Unknown bestiary resource extension: {relative}"
            try:
                original.decode("utf-8")
            except UnicodeDecodeError:
                assert "/pt_br_0/" in "/" + relative, f"Non-UTF8 unexpected locale: {relative}"
                fixed = original.decode("cp1252").encode("utf-8")
                kind = "encoding"
                new_name = relative
                detail = "Converted legacy Windows-1252 Portuguese text into required UTF-8"
        if kind:
            assert new_name is not None
            assert new_name not in overrides
            fixed.decode("utf-8")
            overrides[new_name] = fixed
            if kind == "extension":
                renames[relative] = new_name
            records.append({
                "kind": kind, "original": relative, "runtime": new_name,
                "sha256_before": sha256(original), "sha256_after": sha256(fixed),
                "bytes_before": len(original), "bytes_after": len(fixed),
                "detail": detail,
            })

    icons = {"dragon_seeker.png", "epic_dragon_seeker.png",
             "godly_dragon_seeker.png", "legendary_dragon_seeker.png"}
    for p in sorted(RESOURCES.rglob("*.png")):
        if p.name not in icons:
            continue
        relative = p.relative_to(RESOURCES).as_posix()
        assert relative.startswith("assets/iceandfire/textures/item/") or \
            relative.startswith("resourcepacks/iaf_legacy/assets/iceandfire/textures/item/"), \
            f"Unexpected PNG repair location: {relative}"
        original = p.read_bytes()
        fixed, defective_chunks = repair_png(original)
        assert relative not in overrides
        overrides[relative] = fixed
        records.append({
            "kind": "png_crc", "original": relative, "runtime": relative,
            "sha256_before": sha256(original), "sha256_after": sha256(fixed),
            "bytes_before": len(original), "bytes_after": len(fixed),
            "detail": "Recalculated bad PNG chunk CRCs after validating IDAT zlib: "
                      + ",".join(defective_chunks),
        })

    counts = Counter(item["kind"] for item in records)
    assert dict(counts) == EXPECTED, (
        f"Unexpected upstream resource version or scanner mismatch: {counts} != {EXPECTED}"
    )
    assert len(set(x["original"] for x in records)) == sum(EXPECTED.values())
    assert len(set(x["runtime"] for x in records)) == sum(EXPECTED.values())
    return overrides, renames, records


def patch_jar(source_jar: Path, destination: Path, overrides: dict[str, bytes],
              renames: dict[str, str]) -> dict:
    assert source_jar.is_file(), f"Missing original compiled CE JAR: {source_jar}"
    assert source_jar.resolve() != destination.resolve(), "Never overwrite unmodified CE binary"
    destination.parent.mkdir(parents=True, exist_ok=True)
    original_classes = {}
    original_resources = set()
    with zipfile.ZipFile(source_jar) as inp, zipfile.ZipFile(
        destination, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6
    ) as out:
        source_names = inp.namelist()
        assert len(source_names) == len(set(source_names)), "Duplicate names in original CE JAR"
        assert not any(name.endswith((".SF", ".RSA", ".DSA", ".EC"))
                       for name in source_names if name.startswith("META-INF/")), \
            "Signed JAR cannot be rewritten without re-signing"
        missing = set(overrides) - set(source_names) - set(renames.values())
        assert not missing, f"Compiled CE JAR missing expected source assets: {sorted(missing)}"
        assert set(renames).issubset(source_names), "Missing extensionless original bestiary resources"

        for info in inp.infolist():
            if info.is_dir():
                out.writestr(info, b"")
                continue
            name = info.filename
            original = inp.read(name)
            original_resources.add(name)
            if name.endswith(".class"):
                original_classes[name] = sha256(original)
            if name in renames:
                # CE BestiaryScreen never asks for this extensionless name.
                continue
            data = overrides.get(name, original)
            out.writestr(info, data)

        for old, new in sorted(renames.items()):
            source_info = inp.getinfo(old)
            fixed_info = zipfile.ZipInfo(new, date_time=source_info.date_time)
            fixed_info.compress_type = source_info.compress_type
            fixed_info.external_attr = source_info.external_attr
            fixed_info.create_system = source_info.create_system
            out.writestr(fixed_info, overrides[new])

    with zipfile.ZipFile(destination) as out:
        assert out.testzip() is None, "Patched CE ZIP/JAR CRC failed"
        final_names = out.namelist()
        assert len(final_names) == len(set(final_names)), "Duplicate entries in patched JAR"
        assert len(original_classes) > 100, "Too few original CE classes"
        for name, checksum in original_classes.items():
            assert sha256(out.read(name)) == checksum, f"Original Java bytecode changed: {name}"
        for path, data in overrides.items():
            assert out.read(path) == data, f"Runtime resource mismatch: {path}"
        for old in renames:
            assert old not in final_names, f"Broken extensionless resource remains: {old}"
        for name in final_names:
            if name.endswith(".png") and (
                "dragon_seeker.png" in name or "seeker" in name
            ):
                # All affected art has valid PNG CRCs in the distributed JAR.
                from audit_each_file import audit_png
                status, finding = audit_png(out.read(name))
                assert status == "PASS", f"Invalid packaged PNG {name}: {finding}"
        for path in overrides:
            if "/lang/bestiary/" in path:
                out.read(path).decode("utf-8")

    return {
        "patched_jar": destination.name,
        "original_jar_sha256": sha256(source_jar.read_bytes()),
        "patched_jar_sha256": sha256(destination.read_bytes()),
        "preserved_classes": len(original_classes),
        "patch_count": len(overrides),
        "renamed": len(renames),
    }


def main() -> None:
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-jar", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "build/ce-native-dist")
    args = parser.parse_args()
    overrides, renames, records = plan_repairs()
    dest = args.output_dir / (args.source_jar.stem + "-auren-resources-fixed.jar")
    metadata = patch_jar(args.source_jar, dest, overrides, renames)
    metadata.update({
        "upstream_source_commit": SHA,
        "status": "REPAIRED_DISTRIBUTION_ONLY",
        "changes": dict(Counter(record["kind"] for record in records)),
        "records": records,
        "note": "Original CE source submodule and compiled reference jar untouched; "
                "only resource entries are modified in distributed test JAR. "
                "This does not resolve game startup, behavior or mod compatibility.",
    })
    (args.output_dir / "ce-resource-patch-manifest.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print("RESOURCE REPAIR PASS:", json.dumps({
        "changes": metadata["changes"],
        "preserved_original_java_classes": metadata["preserved_classes"],
        "patched_jar": str(dest),
    }, ensure_ascii=False))


if __name__ == "__main__":
    main()
