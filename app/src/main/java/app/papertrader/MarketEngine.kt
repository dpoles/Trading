package app.papertrader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Random

/**
 * Sorgente dati simulata (random walk). Sostituibile con un feed reale
 * (Alpaca, Finnhub, Twelve Data...) mantenendo lo stesso StateFlow di candele.
 */
class MarketEngine(scope: CoroutineScope) {
    private val rnd = Random(42)
    private val maxCandles = 300
    private val ticksPerCandle = 10

    private val _candles = MutableStateFlow(seedHistory())
    val candles: StateFlow<Map<String, List<Candle>>> = _candles.asStateFlow()

    init {
        scope.launch {
            var tick = 0
            while (true) {
                delay(1000)
                tick++
                step(newCandle = tick % ticksPerCandle == 0)
            }
        }
    }

    private fun seedHistory(): Map<String, List<Candle>> {
        val now = System.currentTimeMillis()
        return UNIVERSE.associate { s ->
            var price = s.basePrice
            val list = ArrayList<Candle>()
            for (i in 120 downTo 1) {
                val open = price
                val close = open * (1 + rnd.nextGaussian() * 0.006)
                val hi = maxOf(open, close) * (1 + rnd.nextDouble() * 0.003)
                val lo = minOf(open, close) * (1 - rnd.nextDouble() * 0.003)
                list += Candle(now - i * 10_000L, open, hi, lo, close)
                price = close
            }
            s.symbol to list
        }
    }

    private fun step(newCandle: Boolean) {
        val now = System.currentTimeMillis()
        _candles.value = _candles.value.mapValues { (_, list) ->
            val last = list.last()
            val close = last.close * (1 + rnd.nextGaussian() * 0.0015)
            val updated = last.copy(
                high = maxOf(last.high, close),
                low = minOf(last.low, close),
                close = close,
            )
            val result = list.dropLast(1) + updated
            if (newCandle) {
                (result + Candle(now, close, close, close, close)).takeLast(maxCandles)
            } else {
                result
            }
        }
    }
}
