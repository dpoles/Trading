package app.papertrader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val engine = MarketEngine(viewModelScope)
    private val store = PortfolioStore(app)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<PortfolioState> = _state.asStateFlow()
    val candles: StateFlow<Map<String, List<Candle>>> = engine.candles

    init {
        Notifier.createChannel(app)
        viewModelScope.launch { engine.candles.collect { checkAlerts(it) } }
    }

    /** Dati di partenza: l'ultima lettura del foglio inclusa nell'app (asset), poi sostituiti dall'import. */
    private fun seedSnapshot(): SheetSnapshot? = runCatching {
        val body = app.assets.open("seed_tabs.json").bufferedReader().use { it.readText() }
        SheetParser.parse(SheetsClient.parseBody(body))
    }.getOrNull()

    private fun initialState(): PortfolioState {
        val saved = store.load() ?: PortfolioState()
        return if (saved.snapshot == null) saved.copy(snapshot = seedSnapshot()) else saved
    }

    private fun mutate(block: (PortfolioState) -> PortfolioState) {
        _state.update(block)
        store.save(_state.value)
    }

    fun toggleWatch(symbol: String) = mutate {
        it.copy(
            watchlist = if (symbol in it.watchlist) it.watchlist - symbol else it.watchlist + symbol,
        )
    }

    fun addAlert(symbol: String, above: Boolean, target: Double) = mutate {
        it.copy(alerts = it.alerts + PriceAlert(System.currentTimeMillis(), symbol, above, target))
    }

    fun removeAlert(id: Long) = mutate { it.copy(alerts = it.alerts.filter { a -> a.id != id }) }

    /** Torna ai dati inclusi nell'app. */
    fun reset() = mutate { PortfolioState(snapshot = seedSnapshot()) }

    sealed interface ImportUi {
        data object Idle : ImportUi
        data object Loading : ImportUi
        data class Preview(val data: SheetSnapshot) : ImportUi
        data class Error(val message: String) : ImportUi
    }

    private val _import = MutableStateFlow<ImportUi>(ImportUi.Idle)
    val importState: StateFlow<ImportUi> = _import.asStateFlow()

    fun importLoading() { _import.value = ImportUi.Loading }
    fun importFailed(msg: String) { _import.value = ImportUi.Error(msg) }
    fun importDismiss() { _import.value = ImportUi.Idle }

    /** Legge il foglio e mostra l'anteprima; non modifica nulla finché non confermi. */
    fun importFetch(accessToken: String) {
        _import.value = ImportUi.Loading
        viewModelScope.launch {
            _import.value = runCatching { SheetParser.parse(SheetsClient.fetch(accessToken)) }
                .fold({ ImportUi.Preview(it) }, { ImportUi.Error(it.message ?: "Errore di lettura") })
        }
    }

    fun importConfirm() {
        val p = (_import.value as? ImportUi.Preview)?.data ?: return
        mutate { it.copy(snapshot = p) }
        _import.value = ImportUi.Idle
    }

    private fun checkAlerts(all: Map<String, List<Candle>>) {
        val pending = _state.value.alerts.filter { !it.triggered }
        if (pending.isEmpty()) return
        val fired = pending.filter { a ->
            val p = all[a.symbol]?.lastOrNull()?.close ?: return@filter false
            if (a.above) p >= a.target else p <= a.target
        }
        if (fired.isEmpty()) return
        val ids = fired.map { it.id }.toSet()
        mutate { s -> s.copy(alerts = s.alerts.map { if (it.id in ids) it.copy(triggered = true) else it }) }
        fired.forEach {
            val dir = if (it.above) "sopra" else "sotto"
            Notifier.notify(app, it.id.toInt(), "${it.symbol} ${dir} ${money(it.target)}",
                "Prezzo attuale: ${money(all[it.symbol]!!.last().close)}")
        }
    }
}
