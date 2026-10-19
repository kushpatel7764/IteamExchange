# IteamExchange

Scan items being moved between stores, look up their names from the POS inventory
export, enter cost and quantity, and produce a PDF transfer invoice.

## Build & install
Open this folder in Android Studio and press Run, or from a terminal:

```
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

A ready-built APK is at `apk/IteamExchange.apk` (copy it to a phone and open it to install; allow "install unknown apps").

## Using it
1. **Import inventory file**: pick the POS export (`.xlsx` or `.csv`). The app guesses the
   barcode, name and cost columns. Check them in the dialog and tap Import. The data is
   stored on the phone, so you only re-import when the inventory changes.
2. **Start Inventory Transfer**: enter the From and To store names.
3. **Scan item**: the camera reads the barcode, the name is filled in from the inventory,
   and you enter the unit cost and quantity. A Bluetooth or USB scanner also works:
   scan into the "Type or scan barcode" box.
4. **Submit**: shows the total cost and creates the invoice PDF, which you can then open,
   share or email, or save to Downloads.

Old `.xls` files are not supported. Re-save them as `.xlsx` or `.csv`.
`samples/sample_pos_inventory.xlsx` is a small test file.
