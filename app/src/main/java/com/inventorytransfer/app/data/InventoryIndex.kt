package com.inventorytransfer.app.data

/**
 * Fast barcode -> item lookup that tolerates the usual mismatches between what a
 * scanner reads and what a POS stores: leading zeros, missing check digits,
 * EAN-13 vs UPC-A, and UPC-E (short codes on cans/small packs) vs UPC-A.
 */
class InventoryIndex(val items: List<InventoryItem>) {

    private val byKey = HashMap<String, InventoryItem>(items.size * 2)
    private val byKeyWithoutCheck = HashMap<String, InventoryItem>(items.size * 2)

    init {
        for (item in items) {
            val key = normalize(item.upc) ?: continue
            byKey.putIfAbsent(key, item)
            if (key.length >= 11 && key.all(Char::isDigit)) byKeyWithoutCheck.putIfAbsent(key.dropLast(1), item)
        }
    }

    val size: Int get() = items.size

    fun lookup(scanned: String): InventoryItem? {
        val candidates = candidateKeys(scanned)
        for (c in candidates) byKey[c]?.let { return it }
        // Scanned/typed code is missing its check digit but the POS stores it.
        for (c in candidates) byKeyWithoutCheck[c]?.let { return it }
        return null
    }

    companion object {
        /** Digits-only codes lose leading zeros; anything else is compared case-insensitively. */
        fun normalize(raw: String): String? {
            val s = raw.trim()
            if (s.isEmpty()) return null
            val compact = s.replace(" ", "").replace("-", "")
            if (compact.isNotEmpty() && compact.all(Char::isDigit)) {
                return compact.trimStart('0').ifEmpty { "0" }
            }
            return s.uppercase()
        }

        internal fun candidateKeys(scanned: String): List<String> {
            val base = normalize(scanned) ?: return emptyList()
            val keys = LinkedHashSet<String>()
            keys += base
            val digits = scanned.trim().replace(" ", "").replace("-", "")
            if (digits.all(Char::isDigit) && digits.isNotEmpty()) {
                expandUpcE(digits)?.let { upcA ->
                    keys += upcA.trimStart('0')
                    keys += upcA.dropLast(1).trimStart('0')
                }
                // POS stored a full UPC/EAN without its check digit.
                if (base.length >= 11) keys += base.dropLast(1).trimStart('0')
            }
            return keys.filter { it.isNotEmpty() }
        }

        /** Expands an 8-digit UPC-E code (number system 0/1) into its 12-digit UPC-A form. */
        fun expandUpcE(code: String): String? {
            if (code.length != 8 || !code.all(Char::isDigit)) return null
            val ns = code[0]
            if (ns != '0' && ns != '1') return null
            val d = code.substring(1, 7)
            val check = code[7]
            val body = when (d[5]) {
                '0', '1', '2' -> "${d[0]}${d[1]}${d[5]}0000${d[2]}${d[3]}${d[4]}"
                '3' -> "${d[0]}${d[1]}${d[2]}00000${d[3]}${d[4]}"
                '4' -> "${d[0]}${d[1]}${d[2]}${d[3]}00000${d[4]}"
                else -> "${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}0000${d[5]}"
            }
            return "$ns$body$check"
        }
    }
}
