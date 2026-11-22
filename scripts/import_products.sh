#!/usr/bin/env bash
# One-time, safe ShopEase catalog importer. No app changes and no open-write rules.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV="${HOME}/.cache/shopease-firestore-import-venv"
KEY="${1:-}"
if [[ -z "$KEY" ]]; then
  shopt -s nullglob
  matches=("$HOME"/Downloads/*firebase-adminsdk*.json "$HOME"/Downloads/*service-account*.json "$HOME"/Downloads/serviceAccountKey.json)
  if (( ${#matches[@]} == 1 )); then
    KEY="${matches[0]}"
    printf 'Found Firebase Admin key: %s\n' "$KEY"
  elif (( ${#matches[@]} == 0 )); then
    printf '\nMissing Firebase Admin private key.\n'
    printf 'Firebase Console > Project settings > Service accounts > Generate new private key.\n'
    printf 'Keep the downloaded JSON outside the Android project (e.g. ~/Downloads).\n'
    printf 'Then rerun: bash import_products.sh /absolute/path/to/downloaded-key.json\n'
    exit 2
  else
    printf 'Multiple keys found in Downloads. Please choose one explicitly:\n'
    printf '  %s\n' "${matches[@]}"
    printf 'Run: bash import_products.sh /absolute/path/to/correct-key.json\n'
    exit 2
  fi
fi
if [[ ! -f "$KEY" ]]; then
  printf 'Key not found: %s\n' "$KEY" >&2
  exit 2
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo 'Python 3 is required. Install it with: sudo apt install python3 python3-venv' >&2
  exit 2
fi
if [[ ! -x "$VENV/bin/python" ]]; then
  mkdir -p "$(dirname "$VENV")"
  python3 -m venv "$VENV" || { echo 'Please install python3-venv (sudo apt install python3-venv) and retry.' >&2; exit 2; }
fi
if ! "$VENV/bin/python" -c 'import firebase_admin' >/dev/null 2>&1; then
  printf 'Installing Firebase Admin SDK into a dedicated local environment...\n'
  "$VENV/bin/python" -m pip install --disable-pip-version-check 'firebase-admin>=6,<8'
fi
"$VENV/bin/python" "$ROOT/seed_firestore.py" --key "$KEY" --data "$ROOT/products_seed.json"
