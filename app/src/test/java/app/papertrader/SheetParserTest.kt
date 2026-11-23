package app.papertrader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Verifica il parser sulla lettura reale del foglio Balance (asset seed_tabs.json). */
class SheetParserTest {
    private val snap: SheetSnapshot by lazy {
        val body = File("src/main/assets/seed_tabs.json").readText()
        SheetParser.parse(SheetsClient.parseBody(body), now = 0)
    }

    @Test fun cashAndStamp() {
        assertEquals(12235.79, snap.cash, 0.001)
        assertEquals(212.07, snap.stampDuty!!, 0.001)
    }

    @Test fun lots() {
        val azioni = snap.lots.filter { it.tab == "Azioni" }
        val monetari = snap.lots.filter { it.tab == "Monetari" }
        assertEquals(10, azioni.size)
        assertEquals(1, monetari.size)
        assertEquals(663.0, azioni.sumOf { it.qty }, 0.001)
        assertEquals(102713.17, azioni.sumOf { it.buyValue }, 0.01)
        assertEquals(89.0, monetari.sumOf { it.qty }, 0.001)
        assertEquals(13348.18, monetari.sumOf { it.buyValue }, 0.01)
        val first = azioni.first { it.date == "13/11/2025" }
        assertEquals(131.0, first.qty, 0.001)
        assertEquals(146.32, first.buyPrice!!, 0.001)
        assertEquals("IE00BK5BQT80", first.isin)
        assertNotNull(first.value)
    }

    @Test fun summaries() {
        val tot = snap.summaries.first { it.label == "Azioni – TOTALE INVESTITO" }
        assertEquals(663.0, tot.qty!!, 0.001)
        assertEquals(102713.17, tot.buyValue!!, 0.01)
        assertEquals(109494.45, tot.value!!, 0.01)
        assertEquals(6781.28, tot.diff!!, 0.01)
        assertTrue(snap.summaries.any { it.label.startsWith("Monetari") })
    }

    @Test fun months() {
        assertEquals("2025-01", snap.months.first().month)
        assertEquals(9991.62, snap.months.first().netValue!!, 0.01)
        assertTrue(snap.months.size >= 20)
        assertTrue(snap.months.zipWithNext().all { (a, b) -> a.month < b.month })
    }

    @Test fun deposits() {
        assertEquals(14, snap.deposits.size)
        assertEquals(5000.0, snap.deposits.first().amount, 0.001)
        assertEquals("17/01/2025", snap.deposits.first().date)
        assertEquals(111500.0, snap.deposits.last().cumulative!!, 0.001)
        assertEquals("Credit", snap.deposits.first().source)
    }
}
