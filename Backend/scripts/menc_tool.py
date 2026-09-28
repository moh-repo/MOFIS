#!/usr/bin/env python3
"""
menc_tool.py - decrypt (or encrypt) files in the MOHE at-rest format outside the
application (security plan S-13: the restore drill runs on a host that has the
backup and the key but not the application).

Format (util/crypto/FileCipher.java):
    version 1:  b"MENC" | 0x01 | nonce(12) | AES-256-GCM ciphertext+tag       key id 1
    version 2:  b"MENC" | 0x02 | keyId(1) | nonce(12) | AES-256-GCM ciphertext+tag

Keys come from --keys "id:base64[,id:base64]" or the environment:
MOHE_FILE_ENCRYPTION_KEYS (same shape) and/or MOHE_FILE_ENCRYPTION_KEY (key id 1).

Usage:
    python menc_tool.py decrypt <in> <out> [--keys ...]
    python menc_tool.py encrypt <in> <out> --key-id N [--keys ...]
    python menc_tool.py info <in>
Exit codes: 0 ok, 2 usage/key error, 3 wrong key or corrupt file.
Needs: pip install cryptography
"""
import argparse
import base64
import os
import sys

try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
except ImportError:  # pragma: no cover
    print("menc_tool: the 'cryptography' package is required (pip install cryptography)", file=sys.stderr)
    sys.exit(2)

MAGIC = b"MENC"
NONCE = 12


def load_keys(spec):
    keys = {}
    single = os.environ.get("MOHE_FILE_ENCRYPTION_KEY", "").strip()
    if single:
        keys[1] = base64.b64decode(single)
    for source in (os.environ.get("MOHE_FILE_ENCRYPTION_KEYS", ""), spec or ""):
        for entry in source.split(","):
            entry = entry.strip()
            if not entry:
                continue
            kid, _, b64 = entry.partition(":")
            keys[int(kid)] = base64.b64decode(b64.strip())
    for kid, k in keys.items():
        if len(k) != 32:
            raise SystemExit(f"menc_tool: key {kid} must be 32 bytes, got {len(k)}")
    return keys


def header(data):
    if len(data) < 5 or data[:4] != MAGIC:
        return None
    version = data[4]
    if version == 1:
        return 1, 1, 5
    if version == 2:
        return 2, data[5], 6
    raise SystemExit(f"menc_tool: unknown MENC version {version}")


def decrypt(src, dst, keys):
    data = open(src, "rb").read()
    h = header(data)
    if h is None:
        # Not one of ours: copy through, the caller decides whether that is fine.
        open(dst, "wb").write(data)
        return None
    version, kid, offset = h
    if kid not in keys:
        raise SystemExit(f"menc_tool: file is under key id {kid}, which is not configured (have {sorted(keys)})")
    nonce = data[offset:offset + NONCE]
    body = data[offset + NONCE:]
    try:
        plain = AESGCM(keys[kid]).decrypt(nonce, body, None)
    except Exception:
        print("menc_tool: decryption failed - wrong key or corrupt file", file=sys.stderr)
        sys.exit(3)
    open(dst, "wb").write(plain)
    return kid


def encrypt(src, dst, keys, kid):
    if kid not in keys:
        raise SystemExit(f"menc_tool: key id {kid} is not configured")
    nonce = os.urandom(NONCE)
    body = AESGCM(keys[kid]).encrypt(nonce, open(src, "rb").read(), None)
    with open(dst, "wb") as out:
        out.write(MAGIC)
        if kid == 1:
            out.write(bytes([1]))
        else:
            out.write(bytes([2, kid]))
        out.write(nonce)
        out.write(body)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("mode", choices=["decrypt", "encrypt", "info"])
    ap.add_argument("src")
    ap.add_argument("dst", nargs="?")
    ap.add_argument("--keys", default="")
    ap.add_argument("--key-id", type=int, default=1)
    args = ap.parse_args()

    if args.mode == "info":
        h = header(open(args.src, "rb").read(16))
        print("plaintext" if h is None else f"MENC version {h[0]}, key id {h[1]}")
        return
    if not args.dst:
        ap.error("dst is required")
    keys = load_keys(args.keys)
    if not keys:
        raise SystemExit("menc_tool: no keys (set MOHE_FILE_ENCRYPTION_KEY(S) or pass --keys)")
    if args.mode == "decrypt":
        kid = decrypt(args.src, args.dst, keys)
        print("copied plaintext" if kid is None else f"decrypted with key id {kid}")
    else:
        encrypt(args.src, args.dst, keys, args.key_id)
        print(f"encrypted with key id {args.key_id}")


if __name__ == "__main__":
    main()
