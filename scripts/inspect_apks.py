#!/usr/bin/env python3
"""Inspect actual APK hashes and 16-KiB native/ZIP alignment without platform binaries."""
import hashlib
import json
import pathlib
import struct
import sys
import zipfile


def load_alignments(data):
    if data[:4] != b"\x7fELF":
        raise ValueError("Native library is not ELF")
    endian = "<" if data[5] == 1 else ">"
    if data[4] == 2:
        offset = struct.unpack_from(endian + "Q", data, 32)[0]
        size, count = struct.unpack_from(endian + "HH", data, 54)
        alignment_offset, alignment_type = 48, "Q"
    elif data[4] == 1:
        offset = struct.unpack_from(endian + "I", data, 28)[0]
        size, count = struct.unpack_from(endian + "HH", data, 42)
        alignment_offset, alignment_type = 28, "I"
    else:
        raise ValueError("Unsupported ELF class")
    alignments = []
    for index in range(count):
        entry = offset + index * size
        if struct.unpack_from(endian + "I", data, entry)[0] == 1:
            alignments.append(struct.unpack_from(endian + alignment_type, data, entry + alignment_offset)[0])
    if not alignments or min(alignments) < 16384:
        raise ValueError("Native LOAD segments are not aligned for 16-KiB pages")
    return alignments


def inspect(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    libraries = []
    with zipfile.ZipFile(path) as archive, path.open("rb") as handle:
        corrupt = archive.testzip()
        if corrupt:
            raise ValueError(f"Corrupt APK entry: {corrupt}")
        for entry in archive.infolist():
            if not entry.filename.startswith("lib/") or not entry.filename.endswith(".so"):
                continue
            alignments = load_alignments(archive.read(entry))
            handle.seek(entry.header_offset + 26)
            name_length, extra_length = struct.unpack("<HH", handle.read(4))
            offset = entry.header_offset + 30 + name_length + extra_length
            if entry.compress_type == zipfile.ZIP_STORED and offset % 16384:
                raise ValueError(f"Uncompressed library lacks 16-KiB ZIP alignment: {entry.filename}")
            libraries.append({"entry": entry.filename, "load_alignments": alignments,
                              "zip_data_offset": offset, "compression": entry.compress_type})
        notices = [n for n in archive.namelist() if n.startswith("assets/licenses/")]
    return {"apk": str(path), "bytes": path.stat().st_size, "sha256": digest.hexdigest(),
            "native_libraries": libraries, "license_assets": notices}


if __name__ == "__main__":
    try:
        if len(sys.argv) < 2:
            raise ValueError("Provide one or more APK paths")
        print(json.dumps([inspect(pathlib.Path(p)) for p in sys.argv[1:]], indent=2))
    except (OSError, ValueError, struct.error, zipfile.BadZipFile) as error:
        print(f"APK inspection failed: {error}", file=sys.stderr)
        sys.exit(1)
