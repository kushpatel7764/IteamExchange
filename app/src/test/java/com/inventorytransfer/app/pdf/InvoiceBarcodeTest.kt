package com.inventorytransfer.app.pdf

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class InvoiceBarcodeTest {

    /** Renders the bars to pixels and decodes them, like a scanner reading the invoice. */
    private fun roundTrip(value: String): Pair<String, BarcodeFormat> {
        val bars = InvoiceBarcode.modules(value)
        assertNotNull("could not encode $value", bars)
        val scale = 3
        val quiet = 12
        val width = (bars!!.size + 2 * quiet) * scale
        val height = 60
        val pixels = IntArray(width * height) { idx ->
            val module = (idx % width) / scale - quiet
            if (module in bars.indices && bars[module]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
        val result = MultiFormatReader().decode(bitmap, mapOf(DecodeHintType.TRY_HARDER to true))
        return result.text to result.barcodeFormat
    }

    @Test fun upcA() = assertEquals("012345678905" to BarcodeFormat.UPC_A, roundTrip("012345678905"))

    @Test fun ean13() = assertEquals("7894900011517" to BarcodeFormat.EAN_13, roundTrip("7894900011517"))

    @Test fun upcE() = assertEquals("04963406" to BarcodeFormat.UPC_E, roundTrip("04963406"))

    @Test fun ean8() = assertEquals("96385074" to BarcodeFormat.EAN_8, roundTrip("96385074"))

    @Test fun invalidCheckDigitFallsBackToCode128() =
        assertEquals("012345678901" to BarcodeFormat.CODE_128, roundTrip("012345678901"))

    @Test fun alphanumeric() = assertEquals("ABC-123" to BarcodeFormat.CODE_128, roundTrip("ABC-123"))
}
