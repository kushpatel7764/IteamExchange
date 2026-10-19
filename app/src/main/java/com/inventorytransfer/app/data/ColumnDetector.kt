package com.inventorytransfer.app.data

/** Which columns of the POS export hold the barcode, item name and (optionally) cost. */
data class ColumnMapping(
    val headerRow: Int,
    val upcCol: Int,
    val nameCol: Int,
    val costCol: Int?,
)

data class DetectionResult(
    val sheetIndex: Int,
    val headerRow: Int,
    /** Display labels for every column, e.g. "B: Description". */
    val columnLabels: List<String>,
    val upcCol: Int?,
    val nameCol: Int?,
    val costCol: Int?,
)

/**
 * Guesses the barcode / name / cost columns from the header row of a POS export.
 * Every POS names these differently, so the UI always lets the user confirm or change the guess.
 */
object ColumnDetector {

    fun detect(sheets: List<Sheet>): DetectionResult {
        val results = sheets.mapIndexed { i, sheet -> detect(sheet.rows, i) }
        return results.firstOrNull { it.upcCol != null && it.nameCol != null }
            ?: results.firstOrNull { it.columnLabels.isNotEmpty() }
            ?: results.first()
    }

    fun detect(rows: List<List<String>>, sheetIndex: Int = 0): DetectionResult {
        // Pick the header row: the one in the first 25 rows where barcode + name headers score highest.
        var bestRow = rows.indexOfFirst { r -> r.any { it.isNotBlank() } }.coerceAtLeast(0)
        var bestScore = 0
        for (i in 0 until minOf(rows.size, 25)) {
            val r = rows[i]
            val score = (r.maxOfOrNull(::barcodeScore) ?: 0) + (r.maxOfOrNull(::nameScore) ?: 0)
            if (score > bestScore) { bestScore = score; bestRow = i }
        }

        val header = rows.getOrNull(bestRow).orEmpty()
        val width = rows.take(200).maxOfOrNull { it.size } ?: 0
        val labels = (0 until width).map { c ->
            val h = header.getOrNull(c).orEmpty().ifBlank { "(no header)" }
            "${columnLetter(c)}: $h"
        }

        val sample = rows.drop(bestRow + 1).take(300)
        var upc = bestColumn(header, ::barcodeScore)
        // A "barcode" header whose column holds no codes is really data, not a header.
        if (upc != null && sample.isNotEmpty() && codeRatio(sample, upc) < 0.3) {
            upc = guessBarcodeColumnFromData(sample, width) ?: upc
        }
        var name = bestColumn(header, ::nameScore)?.takeIf { it != upc }
        val cost = bestColumn(header, ::costScore)?.takeIf { it != upc && it != name }

        // No usable headers: fall back to looking at the data itself.
        if (upc == null) upc = guessBarcodeColumnFromData(sample, width)
        if (name == null) name = guessNameColumnFromData(sample, width, exclude = upc)

        return DetectionResult(sheetIndex, bestRow, labels, upc, name, cost)
    }

    private fun bestColumn(header: List<String>, score: (String) -> Int): Int? {
        var best: Int? = null
        var bestScore = 0
        header.forEachIndexed { i, h ->
            val s = score(h)
            if (s > bestScore) { bestScore = s; best = i }
        }
        return best
    }

    private fun norm(h: String) = h.lowercase().replace(Regex("[^a-z0-9#]+"), " ").trim()

    internal fun barcodeScore(header: String): Int {
        val h = norm(header)
        if (h.isEmpty()) return 0
        val words = h.split(' ')
        return when {
            "upc" in words || h.startsWith("upc") -> 100
            h.contains("barcode") || h.contains("bar code") -> 95
            "ean" in words || "gtin" in words -> 85
            "plu" in words -> 60
            h.contains("item code") || h.contains("product code") -> 50
            "sku" in words -> 40
            h == "code" || h.contains("item number") || h.contains("item #") || h.contains("item no") -> 30
            else -> 0
        }
    }

    internal fun nameScore(header: String): Int {
        val h = norm(header)
        if (h.isEmpty()) return 0
        val notTheItem = listOf("vendor", "supplier", "department", "dept", "category", "brand", "store", "customer")
        if (notTheItem.any { h.contains(it) }) return 0
        return when {
            h.contains("item name") || h.contains("product name") -> 100
            h == "name" || h == "description" -> 95
            h.contains("description") || h == "desc" || h.contains("item desc") -> 85
            h.contains("name") -> 70
            h == "item" || h == "product" -> 50
            else -> 0
        }
    }

    internal fun costScore(header: String): Int {
        val h = norm(header)
        return when {
            h.contains("case cost") || h.contains("total cost") || h.contains("ext") -> 0
            h == "cost" || h.contains("unit cost") -> 100
            h.contains("avg cost") || h.contains("average cost") -> 90
            h.contains("cost") -> 70
            else -> 0
        }
    }

    private fun codeRatio(sample: List<List<String>>, col: Int): Double =
        sample.count { r ->
            val v = r.getOrNull(col).orEmpty().replace(" ", "").replace("-", "")
            v.length >= 4 && v.count(Char::isDigit) >= v.length - 2
        }.toDouble() / sample.size

    private fun guessBarcodeColumnFromData(sample: List<List<String>>, width: Int): Int? {
        if (sample.isEmpty()) return null
        val counts = IntArray(width)
        for (r in sample) for (c in r.indices) {
            val v = r[c].replace(" ", "").replace("-", "")
            if (v.length in 6..14 && v.all(Char::isDigit)) counts[c]++
        }
        val best = counts.indices.maxByOrNull { counts[it] } ?: return null
        return best.takeIf { counts[it] >= sample.size / 2 }
    }

    private fun guessNameColumnFromData(sample: List<List<String>>, width: Int, exclude: Int?): Int? {
        if (sample.isEmpty()) return null
        val letterTotals = IntArray(width)
        for (r in sample) for (c in r.indices) letterTotals[c] += r[c].count(Char::isLetter)
        return letterTotals.indices.filter { it != exclude }.maxByOrNull { letterTotals[it] }
            ?.takeIf { letterTotals[it] > 0 }
    }

    fun columnLetter(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append('A' + rem)
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }
}
