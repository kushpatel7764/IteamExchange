package com.inventorytransfer.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class InventoryLogicTest {

    private val index = InventoryIndex(
        listOf(
            InventoryItem("012345678905", "Full UPC with check digit", null),
            InventoryItem("04963406", "Stored as UPC-E", null),
            InventoryItem("7894900011517", "EAN-13 item", null),
            InventoryItem("01200000129", "Stored without check digit (UPC-E expands to this)", null),
            InventoryItem("ABC-123", "Alphanumeric code", null),
        )
    )

    @Test fun exactAndLeadingZeroMatches() {
        assertEquals("Full UPC with check digit", index.lookup("012345678905")?.name)
        assertEquals("Full UPC with check digit", index.lookup("0012345678905")?.name) // EAN-13 form
        assertEquals("Full UPC with check digit", index.lookup("12345678905")?.name)
    }

    @Test fun scanIncludesCheckDigitPosDoesNot() {
        assertEquals("Stored without check digit (UPC-E expands to this)", index.lookup("012000001291")?.name)
    }

    @Test fun typedWithoutCheckDigitPosHasIt() {
        assertEquals("Full UPC with check digit", index.lookup("01234567890")?.name)
    }

    @Test fun upcEExpansion() {
        assertEquals("012345000065", InventoryIndex.expandUpcE("01234565"))
        assertEquals("012000001291", InventoryIndex.expandUpcE("01212901"))
        // Scanned short UPC-E matched to a POS entry stored as UPC-A without check digit
        assertEquals("Stored without check digit (UPC-E expands to this)", index.lookup("01212901")?.name)
    }

    @Test fun alphanumericAndMisses() {
        assertNotNull(index.lookup("abc-123"))
        assertNull(index.lookup("999999999999"))
    }

    @Test fun columnDetection() {
        val rows = listOf(
            listOf("Store Inventory Report"),
            listOf(),
            listOf("Dept", "UPC", "Description", "Vendor Name", "Unit Cost", "Retail Price"),
            listOf("Snacks", "012345678905", "Chips 1oz", "Frito", "0.45", "1.29"),
        )
        val d = ColumnDetector.detect(rows)
        assertEquals(2, d.headerRow)
        assertEquals(1, d.upcCol)
        assertEquals(2, d.nameCol)
        assertEquals(4, d.costCol)
    }

    @Test fun columnDetectionWithoutHeaders() {
        val rows = (1..10).map { listOf("$it", "04963406000$it", "Item number $it", "1.00") }
        val d = ColumnDetector.detect(rows)
        assertEquals(1, d.upcCol)
        assertEquals(2, d.nameCol)
    }

    @Test fun numberFormatting() {
        assertEquals("12345678905", SpreadsheetReader.formatNumber("1.2345678905E10"))
        assertEquals("4.5", SpreadsheetReader.formatNumber("4.50"))
        assertEquals(27, SpreadsheetReader.columnIndex("AB12"))
    }

    @Test fun csv() {
        val rows = SpreadsheetReader.readCsv("UPC,Name\n\"0123\",\"Soda, 12 pack\"\r\n456,Gum".byteInputStream())
        assertEquals(listOf("0123", "Soda, 12 pack"), rows[1])
        assertEquals(listOf("456", "Gum"), rows[2])
    }

    @Test fun readsSampleXlsx() {
        val sample = listOf(File("../samples/sample_pos_inventory.xlsx"), File("samples/sample_pos_inventory.xlsx"))
            .firstOrNull { it.exists() } ?: return
        val sheets = SpreadsheetReader.readXlsx(sample)
        val d = ColumnDetector.detect(sheets)
        val rows = sheets[d.sheetIndex].rows
        val upc = d.upcCol!!
        val name = d.nameCol!!
        val items = rows.drop(d.headerRow + 1).map { InventoryItem(it[upc], it[name], null) }
        val idx = InventoryIndex(items)
        assertEquals("Coca-Cola Classic 12oz Can", idx.lookup("049000028904")?.name)
    }
}
