package com.inventorytransfer.app.pdf

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.oned.Code128Writer
import com.google.zxing.oned.EAN13Writer
import com.google.zxing.oned.EAN8Writer
import com.google.zxing.oned.UPCAWriter
import com.google.zxing.oned.UPCEWriter
import com.inventorytransfer.app.data.InventoryIndex

/**
 * Turns a scanned barcode value back into bars, using the same symbology it most
 * likely came from (UPC-A / EAN-13 / UPC-E / EAN-8), or Code 128 for anything else.
 * Scanning the printed bars yields exactly the original value, so it matches the inventory.
 */
object InvoiceBarcode {

    /** Module pattern (true = black bar) without quiet zones, or null if it can't be encoded. */
    fun modules(value: String): BooleanArray? {
        val v = value.trim()
        if (v.isEmpty()) return null
        val format = formatFor(v) ?: return null
        val writer = when (format) {
            BarcodeFormat.UPC_A -> UPCAWriter()
            BarcodeFormat.EAN_13 -> EAN13Writer()
            BarcodeFormat.UPC_E -> UPCEWriter()
            BarcodeFormat.EAN_8 -> EAN8Writer()
            else -> Code128Writer()
        }
        return runCatching {
            val matrix = writer.encode(v, format, 0, 1, mapOf(EncodeHintType.MARGIN to 0))
            BooleanArray(matrix.width) { matrix.get(it, 0) }
        }.getOrNull()
    }

    internal fun formatFor(v: String): BarcodeFormat? {
        if (v.all(Char::isDigit)) {
            when (v.length) {
                12 -> if (validGtin(v)) return BarcodeFormat.UPC_A
                13 -> if (validGtin(v)) return BarcodeFormat.EAN_13
                8 -> {
                    val upcA = InventoryIndex.expandUpcE(v)
                    if (upcA != null && validGtin(upcA)) return BarcodeFormat.UPC_E
                    if (validGtin(v)) return BarcodeFormat.EAN_8
                }
            }
        }
        return if (v.all { it.code in 32..126 }) BarcodeFormat.CODE_128 else null
    }

    /** Standard GS1 mod-10 check digit validation. */
    internal fun validGtin(code: String): Boolean {
        val digits = code.map { it - '0' }
        val body = digits.dropLast(1).reversed()
        val sum = body.withIndex().sumOf { (i, d) -> if (i % 2 == 0) d * 3 else d }
        return (10 - sum % 10) % 10 == digits.last()
    }
}
