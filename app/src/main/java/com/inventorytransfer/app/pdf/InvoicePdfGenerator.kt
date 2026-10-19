package com.inventorytransfer.app.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.inventorytransfer.app.data.TransferSession
import java.io.File
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Draws a transfer invoice on US Letter pages using Android's built-in PdfDocument. */
object InvoicePdfGenerator {

    private const val PAGE_W = 612
    private const val PAGE_H = 792
    private const val MARGIN = 40f
    private const val ROW_LINE_H = 13f

    // Table column left edges and widths (sum = 532 = page width minus margins).
    private val colX = floatArrayOf(40f, 66f, 266f, 366f, 406f, 484f)
    private val colW = floatArrayOf(26f, 200f, 100f, 40f, 78f, 88f)
    private val headers = listOf("#", "Item", "UPC / Barcode", "Qty", "Unit Cost", "Total")
    private val rightAligned = setOf(3, 4, 5)

    private val money: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)

    fun fmt(v: BigDecimal): String = money.format(v)

    fun generate(session: TransferSession, invoiceNumber: String, outFile: File): File {
        val doc = PdfDocument()
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.DKGRAY }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
        val bold = Paint(body).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val headerText = Paint(bold).apply { color = Color.WHITE; textSize = 9.5f }
        val fill = Paint().apply { color = Color.rgb(21, 101, 192) }
        val zebra = Paint().apply { color = Color.rgb(242, 245, 250) }
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.7f }

        val dateText = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.US).format(Date())

        var pageNo = 0
        var page: PdfDocument.Page? = null
        lateinit var canvas: Canvas
        var y = 0f

        fun drawTableHeader() {
            canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + 18f, fill)
            headers.forEachIndexed { i, h -> drawCell(canvas, h, i, y + 12.5f, headerText) }
            y += 22f
        }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            canvas = page!!.canvas
            y = MARGIN
            if (pageNo == 1) {
                canvas.drawText("INVENTORY TRANSFER INVOICE", MARGIN, y + 16f, title)
                canvas.drawText("Invoice #: $invoiceNumber", PAGE_W - MARGIN - bold.measureText("Invoice #: $invoiceNumber"), y + 6f, bold)
                canvas.drawText(dateText, PAGE_W - MARGIN - body.measureText(dateText), y + 20f, body)
                y += 40f

                val half = (PAGE_W - 2 * MARGIN) / 2
                canvas.drawText("TRANSFER FROM", MARGIN, y, label)
                canvas.drawText("TRANSFER TO", MARGIN + half, y, label)
                y += 14f
                canvas.drawText(session.fromStore.ifBlank { "-" }, MARGIN, y, bold)
                canvas.drawText(session.toStore.ifBlank { "-" }, MARGIN + half, y, bold)
                y += 16f
                if (session.preparedBy.isNotBlank()) {
                    canvas.drawText("Prepared by: ${session.preparedBy}", MARGIN, y, body)
                    y += 16f
                }
                y += 8f
            } else {
                canvas.drawText("Invoice #: $invoiceNumber (continued)", MARGIN, y + 10f, bold)
                y += 24f
            }
            val footer = "Page $pageNo"
            canvas.drawText(footer, PAGE_W - MARGIN - label.measureText(footer), PAGE_H - 20f, label)
            drawTableHeader()
        }

        newPage()

        session.lines.forEachIndexed { index, l ->
            val nameLines = wrap(l.name.ifBlank { "(unnamed item)" }, colW[1] - 6f, body).take(3)
            val rowH = nameLines.size * ROW_LINE_H + 6f
            if (y + rowH > PAGE_H - 60f) newPage()

            if (index % 2 == 1) canvas.drawRect(MARGIN, y - 2f, PAGE_W - MARGIN, y + rowH - 2f, zebra)
            val baseline = y + 9f
            drawCell(canvas, "${index + 1}", 0, baseline, body)
            nameLines.forEachIndexed { i, t -> drawCell(canvas, t, 1, baseline + i * ROW_LINE_H, body) }
            drawCell(canvas, l.barcode, 2, baseline, body)
            drawCell(canvas, l.qty.toString(), 3, baseline, body)
            drawCell(canvas, fmt(l.unitCost), 4, baseline, body)
            drawCell(canvas, fmt(l.lineTotal), 5, baseline, body)
            y += rowH
            canvas.drawLine(MARGIN, y - 2f, PAGE_W - MARGIN, y - 2f, line)
        }

        // Totals + signatures need ~150pt.
        if (y + 150f > PAGE_H - 40f) newPage()
        y += 12f
        val totalsX = colX[3]
        canvas.drawText("Line items:", totalsX, y, body)
        drawRight(canvas, session.lines.size.toString(), y, body)
        y += 16f
        canvas.drawText("Total quantity:", totalsX, y, body)
        drawRight(canvas, session.totalQty.toString(), y, body)
        y += 8f
        canvas.drawLine(totalsX, y, PAGE_W - MARGIN, y, line)
        y += 18f
        val totalPaint = Paint(bold).apply { textSize = 13f }
        canvas.drawText("TOTAL COST:", totalsX, y, totalPaint)
        drawRight(canvas, fmt(session.totalCost), y, totalPaint)

        y += 60f
        val sigW = 200f
        canvas.drawLine(MARGIN, y, MARGIN + sigW, y, Paint().apply { strokeWidth = 0.8f })
        canvas.drawLine(PAGE_W - MARGIN - sigW, y, PAGE_W - MARGIN, y, Paint().apply { strokeWidth = 0.8f })
        y += 12f
        canvas.drawText("Sent by (signature / date)", MARGIN, y, label)
        canvas.drawText("Received by (signature / date)", PAGE_W - MARGIN - sigW, y, label)

        page?.let { doc.finishPage(it) }
        outFile.parentFile?.mkdirs()
        outFile.outputStream().use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    private fun drawCell(canvas: Canvas, text: String, col: Int, baseline: Float, paint: Paint) {
        val maxW = colW[col] - 6f
        val t = ellipsize(text, maxW, paint)
        val x = if (col in rightAligned) colX[col] + colW[col] - 4f - paint.measureText(t) else colX[col] + 3f
        canvas.drawText(t, x, baseline, paint)
    }

    private fun drawRight(canvas: Canvas, text: String, baseline: Float, paint: Paint) {
        canvas.drawText(text, PAGE_W - MARGIN - 4f - paint.measureText(text), baseline, paint)
    }

    private fun ellipsize(text: String, maxW: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxW) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxW) end--
        return text.substring(0, end) + "…"
    }

    private fun wrap(text: String, maxW: Float, paint: Paint): List<String> {
        val result = ArrayList<String>()
        var current = ""
        for (word in text.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxW || current.isEmpty()) {
                current = candidate
            } else {
                result += current
                current = word
            }
        }
        if (current.isNotEmpty()) result += current
        return result.ifEmpty { listOf("") }
    }
}
