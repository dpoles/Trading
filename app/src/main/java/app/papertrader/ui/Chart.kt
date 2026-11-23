package app.papertrader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.papertrader.Candle
import app.papertrader.rsi
import app.papertrader.sma
import java.util.Locale

val Green = Color(0xFF26A69A)
val Red = Color(0xFFEF5350)
private val SmaColor = Color(0xFFFFB74D)
private val RsiColor = Color(0xFF64B5F6)

@Composable
fun CandleChart(all: List<Candle>, visible: Int = 60, modifier: Modifier = Modifier) {
    if (all.size < 2) return
    val closes = all.map { it.close }
    val sma20 = sma(closes, 20).takeLast(minOf(visible, all.size))
    val rsi14 = rsi(closes).takeLast(minOf(visible, all.size))
    val candles = all.takeLast(visible)
    val lastRsi = rsi14.lastOrNull()

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(260.dp)) {
            val hi = candles.maxOf { it.high }
            val lo = candles.minOf { it.low }
            val range = (hi - lo).takeIf { it > 0 } ?: 1.0
            fun y(p: Double) = (size.height * (1 - (p - lo) / range)).toFloat()
            val slot = size.width / candles.size
            val bodyW = slot * 0.65f

            candles.forEachIndexed { i, c ->
                val x = slot * i + slot / 2
                val color = if (c.close >= c.open) Green else Red
                drawLine(color, Offset(x, y(c.high)), Offset(x, y(c.low)), strokeWidth = 2f)
                val top = y(maxOf(c.open, c.close))
                val h = maxOf(y(minOf(c.open, c.close)) - top, 2f)
                drawRect(color, Offset(x - bodyW / 2, top), Size(bodyW, h))
            }
            var prev: Offset? = null
            sma20.forEachIndexed { i, v ->
                if (v != null) {
                    val p = Offset(slot * i + slot / 2, y(v))
                    prev?.let { drawLine(SmaColor, it, p, strokeWidth = 3f) }
                    prev = p
                }
            }
        }
        Text(
            "Candele 10s · SMA20 (arancio)",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        Canvas(Modifier.fillMaxWidth().height(80.dp).padding(top = 8.dp)) {
            val slot = size.width / rsi14.size
            fun y(v: Double) = (size.height * (1 - v / 100)).toFloat()
            listOf(30.0, 70.0).forEach {
                drawLine(Color.Gray.copy(alpha = 0.5f), Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 1f)
            }
            var prev: Offset? = null
            rsi14.forEachIndexed { i, v ->
                if (v != null) {
                    val p = Offset(slot * i + slot / 2, y(v))
                    prev?.let { drawLine(RsiColor, it, p, strokeWidth = 3f) }
                    prev = p
                }
            }
        }
        Text(
            "RSI(14): " + (lastRsi?.let { String.format(Locale.US, "%.1f", it) } ?: "—"),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
