SHOPEASE — AUTOMATIC FIRESTORE PRODUCT IMPORT (ONE TIME)

This importer writes the eight product documents already supplied with the ShopEase
Assignment 2 project into Cloud Firestore. No manual typing, no Android code
changes, and NO changes to the secure Firestore rules are required.

1) In Firebase Console, select ShopEase project shop-ease-228e9.
   Open Project settings (gear icon) > Service accounts > Generate new private key.
   Download the JSON. KEEP IT PRIVATE. It is NOT app/google-services.json.
   Save it to your Ubuntu Downloads folder, never inside a GitHub repository.

2) Extract this ZIP into ~/Downloads and open a terminal inside the
   ShopEase_Firestore_OneClick_Importer folder. Then run:

      bash import_products.sh /home/fasi/Downloads/YOUR_DOWNLOADED_KEY.json

   Or, if there is exactly one *firebase-adminsdk*.json in Downloads:

      bash import_products.sh

   The first run creates a small Python virtual environment and installs the
   Firebase Admin SDK. Ubuntu may need: sudo apt install python3-venv

3) Sign in to ShopEase and tap 'Try again'. You should see the catalog.

The script checks the Firebase project ID, validates the sample JSON, and skips
products already in Firestore (including manually added products with Auto-ID).
It will never overwrite an existing product or delete records.

SECURITY
Do not send your downloaded private key in this chat. Do not push it to GitHub,
zip it with the Android project, or add it to the mobile app. Delete the key
from Downloads when the import is done, or store it in a protected location.
Do not change Firestore rules to 'allow write: if true'. Firebase Admin uses
server credentials so the existing secure rules can remain unchanged.

TEST WITHOUT FIREBASE
  python3 seed_firestore.py --dry-run

If products still don't appear, verify Firestore (default) database and rules
were published from the ShopEase firebase.rules file, then use app Logcat
with tag ShopEaseFirestore to distinguish permission from networking issues.
