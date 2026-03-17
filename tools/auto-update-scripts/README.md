# Qortal Auto-Update Publisher Scripts

This toolkit modernizes and automates the Qortal auto-update process. It includes:

- A Bash script (`build-auto-update.sh`) to build and push the update
- A Python script (`publish-auto-update.py`) to publish the auto-update transaction
- Full support for dry-run mode, interactive or scripted use, and secure key input

---

## 🧰 Prerequisites

- You must be a **non-admin member** of the Qortal `dev` group
- A Qortal core node must be running locally (default API port: `12391`)
- You need the latest version of the `qortal` repo cloned locally

---

## 🚀 Workflow Overview

### 1. Run the Build Script

This script:
- Auto-increments the version in `pom.xml`
- Rebuilds the JAR file
- XORs it into a `.update` file
- Creates a new `auto-update-<hash>` branch with only the update
- Pushes it to the repo

```bash
./tools/auto-update-scripts/build-auto-update.sh
```

You'll be prompted to:
- Confirm or modify the version number
- Push the version tag and update branch, and final commit.
- Optionally run the publisher script at the end

> ✅ Dry-run mode is supported to preview the full process.

---

### 2. Publish the Auto-Update

You can either:
- Let the build script call it for you
- Or run it manually:

```bash
# Run manually with interactive key prompt and auto-detected latest update:
python3 tools/auto-update-scripts/publish-auto-update.py

# Or specify a commit hash:
python3 tools/auto-update-scripts/publish-auto-update.py 0b37666d

# Or pass both from another script:
python3 tools/auto-update-scripts/publish-auto-update.py <privkey> <commit_hash>
```

> 🔐 Private key is always prompted securely unless passed explicitly (e.g. from automation).

This script will:
- Detect the latest `auto-update-<hash>` branch (or use the one you specify)
- Validate that the commit exists
- Restore the `.update` file if missing
- Compute its SHA256 hash
- Build and sign the transaction
- Submit it to your local node

> ✅ `--dry-run` is supported to show what would happen without sending anything.

---

## 🛠 Advanced Options

- Log files are created in `~/qortal-auto-update-logs` by default
- You can override the log directory interactively
- Branch naming is standardized: `auto-update-<short-commit-hash>`
- The `.update` file is XOR-obfuscated using Qortal’s built-in logic
- Your commit must already exist on the main repo (e.g. via push or PR merge)

---

## 📌 Notes

- **Do not use Git LFS** — Qortal nodes download `.update` files using raw HTTP from GitHub
We may build LFS support in the future, but for now it is NOT utilized, and will NOT work. 
(Other locations for the publish of the .update file will be utilized in the future, 
preferably utilizing QDN via gateway nodes, until auto-update setup can be re-written to
leverage QDN directly.) 
- GitHub will warn if `.update` files exceed 50MB, but auto-update still works.
(In the past there HAVE been issues with accounts getting banned due to publish of .update file,
however, as of recently (April 2025) it seems they are only warning, and not banning. But we 
will be modifying the need for this in the future anyway.) 
- Update mirrors will be added in the future, and others can be added in settings as well.

---

## ✅ Example End-to-End (Manual)

```bash
cd ~/git-repos/qortal
./tools/auto-update-scripts/build-auto-update.sh
# follow prompts...

# then manually publish:
python3 tools/auto-update-scripts/publish-auto-update.py
```

---

## 🧪 Test Without Sending

```bash
./build-auto-update.sh   # enable dry-run when prompted
# OR
python3 publish-auto-update.py 0b37666d --dry-run
```

---

## 🙌 Contributors

Modernization by [@crowetic](https://github.com/crowetic) 
Based on original Perl scripts by Qortal core devs, specifically @catbref.

---

Questions or issues? Drop into the Qortal Dev group on Discord, Q-Chat,  or reach out directly via Q-Mail to 'crowetic'.

