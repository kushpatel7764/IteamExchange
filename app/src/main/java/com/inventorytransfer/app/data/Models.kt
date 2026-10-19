package com.inventorytransfer.app.data

import java.math.BigDecimal
import java.math.RoundingMode

data class InventoryItem(
    val upc: String,
    val name: String,
    val cost: BigDecimal?,
)

data class TransferLine(
    val id: Long,
    val barcode: String,
    val name: String,
    val qty: Int,
    val unitCost: BigDecimal,
) {
    val lineTotal: BigDecimal get() = unitCost.multiply(BigDecimal(qty)).setScale(2, RoundingMode.HALF_UP)
}

data class TransferSession(
    val fromStore: String,
    val toStore: String,
    val preparedBy: String,
    val startedAt: Long,
    val lines: List<TransferLine> = emptyList(),
) {
    val totalQty: Int get() = lines.sumOf { it.qty }
    val totalCost: BigDecimal get() = lines.fold(BigDecimal.ZERO) { acc, l -> acc + l.lineTotal }
}

/** Parses user/spreadsheet money text like "$1,234.50" into a BigDecimal. */
fun parseMoney(text: String): BigDecimal? {
    val cleaned = text.trim().replace(Regex("[^0-9.\\-]"), "")
    if (cleaned.isEmpty() || cleaned == "." || cleaned == "-") return null
    return cleaned.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }
}
