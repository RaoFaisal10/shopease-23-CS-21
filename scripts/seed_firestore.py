#!/usr/bin/env python3
"""Safely import the supplied ShopEase sample catalog into the configured Firestore.

Requires the *private* Firebase Admin service account key supplied by the project
owner. Never embed that key in the Android app or commit it to GitHub.
"""

import argparse
import json
import re
import sys
from pathlib import Path

PROJECT_ID = "shop-ease-228e9"
DEFAULT_DATA = Path(__file__).resolve().parent / "products_seed.json"


def slug(title):
    """Stable, readable document IDs: runs are idempotent."""
    return re.sub(r"-+", "-", re.sub(r"[^a-z0-9]+", "-", title.lower())).strip("-")


def load_products(path):
    products = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(products, list) or not products:
        raise ValueError("The seed data must be a nonempty JSON array.")
    seen = set()
    for idx, item in enumerate(products, start=1):
        if not isinstance(item, dict):
            raise ValueError(f"Product #{idx} is not an object")
        for key in ("title", "description", "category", "imageUrl"):
            if not isinstance(item.get(key), str) or not item[key].strip():
                raise ValueError(f"Product #{idx} needs a nonempty string '{key}'")
        for key in ("price", "stock"):
            if isinstance(item.get(key), bool) or not isinstance(item.get(key), int) or item[key] < 0:
                raise ValueError(f"Product #{idx} needs a nonnegative integer '{key}'")
        if not item["imageUrl"].startswith("https://"):
            raise ValueError(f"Product #{idx} must have an HTTPS imageUrl")
        document_id = slug(item["title"])
        if not document_id or document_id in seen:
            raise ValueError(f"Duplicate or invalid document ID: {document_id}")
        seen.add(document_id)
    return products


def main():
    parser = argparse.ArgumentParser(description="Load the eight ShopEase catalog items into Cloud Firestore without manual entry.")
    parser.add_argument("--key", type=Path, help="Path to your Firebase project's private Admin SDK JSON file")
    parser.add_argument("--data", type=Path, default=DEFAULT_DATA, help="Override the bundled products_seed.json file")
    parser.add_argument("--dry-run", action="store_true", help="Validate the products without connecting to Firebase")
    args = parser.parse_args()
    products = load_products(args.data)
    if args.dry_run:
        for product in products:
            print(f"Would import: {slug(product['title'])} — {product['title']}")
        print(f"Ready: {len(products)} validated products; no Firebase changes made.")
        return
    if args.key is None or not args.key.is_file():
        parser.error("Provide your Firebase Admin SDK key: --key /path/to/service-account.json")
    keydata = json.loads(args.key.read_text(encoding="utf-8"))
    if keydata.get("type") != "service_account":
        parser.error("That file is not an Admin SDK service-account JSON file (not google-services.json).")
    if keydata.get("project_id") != PROJECT_ID:
        parser.error(f"Wrong Firebase project. Expected {PROJECT_ID}; key is for {keydata.get('project_id')!r}.")

    try:
        import firebase_admin
        from firebase_admin import credentials, firestore
        from google.api_core.exceptions import AlreadyExists
    except ImportError as exc:
        parser.error("Install the firebase-admin package first (or run import_products.sh): " + str(exc))

    app = firebase_admin.initialize_app(credentials.Certificate(str(args.key)), {"projectId": PROJECT_ID})
    db = firestore.client(app=app)
    catalog = db.collection("products")
    try:
        existing = list(catalog.stream())
        existing_titles = {doc.to_dict().get("title", "").strip().casefold() for doc in existing}
        print(f"Connected to {PROJECT_ID}. Existing catalog documents: {len(existing)}")
        added = skipped = 0
        for product in products:
            title_key = product["title"].strip().casefold()
            if title_key in existing_titles:
                print(f"SKIP: {product['title']} (already exists)")
                skipped += 1
                continue
            try:
                catalog.document(slug(product["title"])).create(product)
                print(f"ADDED: {product['title']}")
                added += 1
                existing_titles.add(title_key)
            except AlreadyExists:
                print(f"SKIP: {product['title']} (document ID already exists)")
                skipped += 1
        print(f"\nFinished. Added: {added}; skipped: {skipped}.")
        print("Open ShopEase and tap 'Try again'.")
    finally:
        firebase_admin.delete_app(app)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
    except Exception as exc:
        print(f"Firebase import failed: {exc}", file=sys.stderr)
        print("Check Firestore is enabled, the key belongs to the intended project, and you have internet.", file=sys.stderr)
        sys.exit(1)
