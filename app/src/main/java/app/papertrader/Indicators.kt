package app.papertrader

fun sma(values: List<Double>, n: Int): List<Double?> =
    values.indices.map { i ->
        if (i + 1 < n) null else values.subList(i + 1 - n, i + 1).average()
    }

/** RSI di Wilder. */
fun rsi(closes: List<Double>, n: Int = 14): List<Double?> {
    val out = MutableList<Double?>(closes.size) { null }
    if (closes.size <= n) return out
    var gain = 0.0
    var loss = 0.0
    for (i in 1..n) {
        val d = closes[i] - closes[i - 1]
        if (d >= 0) gain += d else loss -= d
    }
    gain /= n
    loss /= n
    out[n] = rsiValue(gain, loss)
    for (i in n + 1 until closes.size) {
        val d = closes[i] - closes[i - 1]
        gain = (gain * (n - 1) + maxOf(d, 0.0)) / n
        loss = (loss * (n - 1) + maxOf(-d, 0.0)) / n
        out[i] = rsiValue(gain, loss)
    }
    return out
}

private fun rsiValue(gain: Double, loss: Double): Double =
    if (loss == 0.0) 100.0 else 100 - 100 / (1 + gain / loss)
