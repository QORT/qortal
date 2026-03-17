#!/usr/bin/env python3

import argparse
import subprocess
import requests
import json
import os
import sys
import time
import hashlib
from pathlib import Path

def run(cmd, cwd=None, capture_output=True):
    result = subprocess.run(cmd, shell=True, cwd=cwd, capture_output=capture_output, text=True)
    if result.returncode != 0:
        print(f"Command failed: {cmd}\n{result.stderr}")
        sys.exit(1)
    return result.stdout.strip()

def get_project_name():
    pom = Path('pom.xml')
    if not pom.exists():
        sys.exit("pom.xml not found!")
    for line in pom.read_text().splitlines():
        if '<artifactId>' in line:
            return line.strip().split('>')[1].split('<')[0]
    sys.exit("artifactId not found in pom.xml")

def get_commit_info(commit_hash=None, dry_run=False):
    if not commit_hash:
        print("No commit hash provided, detecting most recent auto-update branch...")
        run("git fetch origin")  # Ensure up-to-date

        # Get latest auto-update branch by commit date
        branches = run("git for-each-ref --sort=-committerdate --format='%(refname:short)' refs/remotes/origin/")
        for branch in branches.splitlines():
            branch = branch.strip().strip("'")
            if branch.startswith("origin/auto-update-"):
                commit_hash = branch.replace("origin/auto-update-", "")
                print(f"Found latest auto-update branch: {branch}")
                break

        if not commit_hash:
            sys.exit("No auto-update branches found.")

    # Validate and get timestamp
    if not commit_exists(commit_hash):
        sys.exit(f"Commit hash '{commit_hash}' does not exist.")

    timestamp = int(run(f"git show --no-patch --format=%ct {commit_hash}")) * 1000

    # Use the remote branch hash if available
    try:
        update_hash = run(f"git rev-parse origin/auto-update-{commit_hash}")
    except SystemExit:
        print(f"⚠️ Warning: remote branch origin/auto-update-{commit_hash} not found, using commit hash itself.")
        update_hash = run(f"git rev-parse {commit_hash}")

    return commit_hash, timestamp, update_hash

    
def commit_exists(commit_hash):
    try:
        run(f"git cat-file -t {commit_hash}")
        return True
    except SystemExit:
        return False

def get_sha256(update_file_path):
    sha256 = hashlib.sha256()
    with open(update_file_path, 'rb') as f:
        sha256.update(f.read())
    return sha256.hexdigest()

def get_public_key(base58_privkey, port):
    r = requests.post(f"http://localhost:{port}/utils/publickey", data=base58_privkey)
    r.raise_for_status()
    return r.text

def get_hex_key(base58_key, port):
    r = requests.post(f"http://localhost:{port}/utils/frombase58", data=base58_key)
    r.raise_for_status()
    return r.text

def get_address(pubkey, port):
    r = requests.get(f"http://localhost:{port}/addresses/convert/{pubkey}")
    r.raise_for_status()
    return r.text

def get_reference(address, port):
    r = requests.get(f"http://localhost:{port}/addresses/lastreference/{address}")
    r.raise_for_status()
    return r.text

def to_base58(hex_str, port):
    r = requests.get(f"http://localhost:{port}/utils/tobase58/{hex_str}")
    r.raise_for_status()
    return r.text

def sign_transaction(privkey, tx_base58, port):
    payload = json.dumps({"privateKey": privkey, "transactionBytes": tx_base58})
    headers = {"Content-Type": "application/json"}
    r = requests.post(f"http://localhost:{port}/transactions/sign", data=payload, headers=headers)
    r.raise_for_status()
    return r.text

def process_transaction(signed_tx, port):
    r = requests.post(f"http://localhost:{port}/transactions/process", data=signed_tx)
    r.raise_for_status()
    return r.text == 'true'

def decode_transaction(signed_tx, port):
    r = requests.post(f"http://localhost:{port}/transactions/decode", data=signed_tx, headers={"Content-Type": "application/json"})
    r.raise_for_status()
    return r.text

def main():
    import getpass
    parser = argparse.ArgumentParser(description="Modern auto-update publisher for Qortal")
    parser.add_argument("arg1", nargs="?", help="Private key OR commit hash")
    parser.add_argument("arg2", nargs="?", help="Commit hash if arg1 was private key")
    parser.add_argument("--port", type=int, default=12391, help="API port")
    parser.add_argument("--dry-run", action="store_true", help="Simulate without submitting transaction")
    args = parser.parse_args()

    # Handle combinations
    if args.arg1 and args.arg2:
        privkey = args.arg1
        commit_hash = args.arg2
    elif args.arg1 and not args.arg2:
        commit_hash = args.arg1
        privkey = getpass.getpass("Enter your Base58 private key: ")
    else:
        commit_hash = None  # Will auto-resolve from HEAD
        privkey = getpass.getpass("Enter your Base58 private key: ")

    # Switch to repo root
    git_root = run("git rev-parse --show-toplevel")
    os.chdir(git_root)

    project = get_project_name()

    # Resolve and verify commit
    commit_hash, timestamp, update_hash = get_commit_info(commit_hash, args.dry_run)
    if not commit_exists(commit_hash):
        sys.exit(f"Commit hash '{commit_hash}' does not exist in this repo.")

    print(f"Commit: {commit_hash}, Timestamp: {timestamp}, Auto-update hash: {update_hash}")

    
    def get_sha256(update_file_path):
    	sha256 = hashlib.sha256()
    	with open(update_file_path, 'rb') as f:
        	sha256.update(f.read())
    	return sha256.hexdigest()

    update_file = Path(f"{project}.update")
    
    if not update_file.exists():
    	print(f"{project}.update not found locally. Attempting to restore from branch auto-update-{commit_hash}...")
    	try:
    	    restore_cmd = f"git show auto-update-{commit_hash}:{project}.update > {project}.update"
    	    run(restore_cmd)
    	    print(f"✓ Restored {project}.update from branch auto-update-{commit_hash}")
    	except Exception as e:
    	    sys.exit(f"Failed to restore {project}.update: {e}")

    # Final check to ensure the file was restored
    if not update_file.exists():
    	sys.exit(f"{project}.update still not found after attempted restore")


    sha256 = get_sha256(update_file)
    print(f"Update SHA256: {sha256}")

    if args.dry_run:
        print("\n--- DRY RUN ---")
        print(f"Would use timestamp: {timestamp}")
        print(f"Would use update hash: {update_hash}")
        print(f"Would use SHA256: {sha256}")
        sys.exit(0)

    pubkey = get_public_key(privkey, args.port)
    pubkey_hex = get_hex_key(pubkey, args.port)
    address = get_address(pubkey, args.port)
    reference = get_reference(address, args.port)
    reference_hex = get_hex_key(reference, args.port)

    data_hex = f"{timestamp:016x}{update_hash}{sha256}"
    if len(data_hex) != 120:
        sys.exit("Data hex length invalid!")

    raw_tx_parts = [
    	"0000000a",                                  # type 10 ARBITRARY
   	 f"{int(time.time() * 1000):016x}",           # current timestamp
    	"00000001",                                  # dev group ID
    	reference_hex,                               # reference
    	pubkey_hex,                                  # pubkey
    	"00000000",                                  # nonce
    	"00000000",                                  # name length
    	"00000000",                                  # identifier length
    	"00000000",                                  # method (PUT)
    	"00000000",                                  # secret length
        "00000000",                                  # compression
        "00000000",                                  # number of payments
        "00000001",                                  # service ID
        "01",                                        # data type (RAW_DATA)
        f"{int(len(data_hex)//2):08x}",              # data length
        data_hex,                                    # payload
        f"{int(len(data_hex)//2):08x}",              # repeated data length
        "00000000",                                  # metadata hash length
        f"{int(0.01 * 1e8):016x}"                     # fee
    ]
    tx_hex = "".join(raw_tx_parts)


    tx_base58 = to_base58(tx_hex, args.port)
    signed_tx = sign_transaction(privkey, tx_base58, args.port)

    print("Submitting in 5 seconds... press CTRL+C to cancel")
    for i in range(5, 0, -1):
        print(f"{i}...", end='\r', flush=True)
        time.sleep(1)

    if not process_transaction(signed_tx, args.port):
        sys.exit("Transaction submission failed")

    decoded = decode_transaction(signed_tx, args.port)
    print("\nTransaction submitted successfully:")
    print(decoded)

if __name__ == "__main__":
    main()

