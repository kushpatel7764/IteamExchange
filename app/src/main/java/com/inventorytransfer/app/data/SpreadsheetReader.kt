package com.inventorytransfer.app.data

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.math.BigDecimal
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory

class Sheet(val name: String, val rows: List<List<String>>)

class UnsupportedFileException(message: String) : Exception(message)

/**
 * Reads .xlsx and .csv files into plain string rows without any heavy dependency
 * (Apache POI is too large and partly incompatible with Android).
 */
object SpreadsheetReader {

    fun read(input: InputStream, tempDir: File): List<Sheet> {
        val stream = BufferedInputStream(input)
        stream.mark(8)
        val magic = ByteArray(4)
        val n = stream.read(magic)
        stream.reset()

        val isZip = n >= 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
        val isOleXls = n >= 4 && magic[0] == 0xD0.toByte() && magic[1] == 0xCF.toByte() &&
            magic[2] == 0x11.toByte() && magic[3] == 0xE0.toByte()

        return when {
            isOleXls -> throw UnsupportedFileException(
                "Old Excel (.xls) files are not supported. Open the file in Excel and " +
                    "\"Save As\" .xlsx or .csv, then import again."
            )
            isZip -> {
                val tmp = File.createTempFile("import", ".xlsx", tempDir)
                try {
                    tmp.outputStream().use { stream.copyTo(it) }
                    readXlsx(tmp)
                } finally {
                    tmp.delete()
                }
            }
            else -> listOf(Sheet("CSV", readCsv(stream)))
        }
    }

    // ---------------------------------------------------------------- XLSX

    fun readXlsx(file: File): List<Sheet> = ZipFile(file).use { zip ->
        val sharedStrings = zip.getEntry("xl/sharedStrings.xml")
            ?.let { entry -> zip.getInputStream(entry).use { parseSharedStrings(it) } }
            ?: emptyList()

        val sheets = sheetPaths(zip).mapNotNull { (name, path) ->
            val entry = zip.getEntry(path) ?: return@mapNotNull null
            Sheet(name, zip.getInputStream(entry).use { parseSheet(it, sharedStrings) })
        }
        if (sheets.isEmpty()) throw UnsupportedFileException("No worksheets found in this file.")
        sheets
    }

    /** Sheet (name, zip path) pairs in workbook order. */
    private fun sheetPaths(zip: ZipFile): List<Pair<String, String>> {
        val rels = HashMap<String, String>()
        zip.getEntry("xl/_rels/workbook.xml.rels")?.let { entry ->
            zip.getInputStream(entry).use { input ->
                parse(input, object : DefaultHandler() {
                    override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                        if (local(qName) == "Relationship") {
                            val id = attrs.getValue("Id") ?: return
                            val target = attrs.getValue("Target") ?: return
                            rels[id] = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
                        }
                    }
                })
            }
        }

        val result = ArrayList<Pair<String, String>>()
        zip.getEntry("xl/workbook.xml")?.let { entry ->
            zip.getInputStream(entry).use { input ->
                parse(input, object : DefaultHandler() {
                    override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                        if (local(qName) != "sheet") return
                        val name = attrs.getValue("name") ?: "Sheet${result.size + 1}"
                        var relId: String? = null
                        for (i in 0 until attrs.length) {
                            if (local(attrs.getQName(i)) == "id") relId = attrs.getValue(i)
                        }
                        rels[relId]?.let { result += name to it }
                    }
                })
            }
        }
        if (result.isNotEmpty()) return result

        // Fallback: any worksheet files, in numeric order.
        return zip.entries().toList()
            .map { it.name }
            .filter { it.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
            .sortedBy { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            .map { it.substringAfterLast('/').removeSuffix(".xml") to it }
    }

    private fun parseSharedStrings(input: InputStream): List<String> {
        val strings = ArrayList<String>()
        parse(input, object : DefaultHandler() {
            val text = StringBuilder()
            var inT = false
            var phoneticDepth = 0

            override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                when (local(qName)) {
                    "si" -> text.setLength(0)
                    "rPh" -> phoneticDepth++
                    "t" -> inT = phoneticDepth == 0
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (local(qName)) {
                    "si" -> strings += text.toString()
                    "rPh" -> phoneticDepth--
                    "t" -> inT = false
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inT) text.append(ch, start, length)
            }
        })
        return strings
    }

    private fun parseSheet(input: InputStream, sharedStrings: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        parse(input, object : DefaultHandler() {
            var row: ArrayList<String>? = null
            var col = -1
            var type: String? = null
            val value = StringBuilder()
            var capture = false

            override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
                when (local(qName)) {
                    "row" -> { row = ArrayList(); col = -1 }
                    "c" -> {
                        col = attrs.getValue("r")?.let(::columnIndex) ?: (col + 1)
                        type = attrs.getValue("t")
                        value.setLength(0)
                    }
                    "v", "t" -> capture = true
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (local(qName)) {
                    "v", "t" -> capture = false
                    "c" -> {
                        val r = row ?: return
                        val raw = value.toString()
                        val cell = when (type) {
                            "s" -> raw.trim().toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
                            "inlineStr", "str", "e" -> raw
                            "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                            else -> formatNumber(raw)
                        }
                        while (r.size < col) r += ""
                        if (r.size == col) r += cell.trim() else r[col] = cell.trim()
                    }
                    "row" -> { row?.let { rows += it }; row = null }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (capture) value.append(ch, start, length)
            }
        })
        return rows
    }

    /** "AB12" -> 27 (zero based column). */
    internal fun columnIndex(ref: String): Int? {
        var index = 0
        var letters = 0
        for (ch in ref) {
            if (ch !in 'A'..'Z') break
            index = index * 26 + (ch - 'A' + 1)
            letters++
        }
        return if (letters == 0) null else index - 1
    }

    /** Numeric cells: "1.2345678901E11" -> "123456789010", "4.50" -> "4.5". */
    internal fun formatNumber(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return ""
        return try {
            BigDecimal(s).stripTrailingZeros().toPlainString()
        } catch (_: NumberFormatException) {
            s
        }
    }

    private fun local(qName: String) = qName.substringAfter(':')

    private fun parse(input: InputStream, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = false }
        factory.newSAXParser().parse(input, handler)
    }

    // ---------------------------------------------------------------- CSV

    fun readCsv(input: InputStream): List<List<String>> {
        var text = input.readBytes().toString(Charsets.UTF_8)
        if (text.startsWith("﻿")) text = text.substring(1)

        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val delimiter = listOf(',', ';', '\t').maxByOrNull { d -> firstLine.count { it == d } } ?: ','

        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else {
                    field.append(c)
                }
            } else when (c) {
                '"' -> inQuotes = true
                delimiter -> { row += field.toString().trim(); field.setLength(0) }
                '\r' -> {}
                '\n' -> {
                    row += field.toString().trim(); field.setLength(0)
                    rows += row; row = ArrayList()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString().trim()
            rows += row
        }
        return rows
    }
}
