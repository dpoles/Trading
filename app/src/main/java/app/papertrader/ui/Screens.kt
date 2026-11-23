package app.papertrader.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.papertrader.AppViewModel
import app.papertrader.GoogleAuth
import app.papertrader.START_CASH
import app.papertrader.UNIVERSE
import app.papertrader.money
import app.papertrader.pct
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private fun pnlColor(v: Double) = if (v >= 0) Green else Red

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val state by vm.state.collectAsState()
    val candles by vm.candles.collectAsState()
    var showAll by remember { mutableStateOf(false) }
    val symbols = if (showAll) UNIVERSE.map { it.symbol } else state.watchlist

    Scaffold(topBar = { TopAppBar(title = { Text(if (showAll) "Tutti i titoli" else "Watchlist") }, actions = {
        OutlinedButton(onClick = { showAll = !showAll }, modifier = Modifier.padding(end = 8.dp)) {
            Text(if (showAll) "Solo preferiti" else "Aggiungi titoli")
        }
    }) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(symbols) { sym ->
                val stock = UNIVERSE.first { it.symbol == sym }
                val list = candles[sym].orEmpty()
                val last = list.lastOrNull()?.close ?: 0.0
                val change = list.firstOrNull()?.open?.let { (last / it - 1) * 100 } ?: 0.0
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(sym) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.toggleWatch(sym) }) {
                        Icon(
                            if (sym in state.watchlist) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "Preferito",
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(sym, style = MaterialTheme.typography.titleMedium)
                        Text(stock.name, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(last), style = MaterialTheme.typography.titleMedium)
                        Text(pct(change), color = pnlColor(change))
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: AppViewModel, symbol: String, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val candles by vm.candles.collectAsState()
    val list = candles[symbol].orEmpty()
    val last = list.lastOrNull()?.close ?: 0.0
    val pos = state.positions.find { it.symbol == symbol }
    var qty by remember { mutableStateOf("1") }
    var target by remember { mutableStateOf("") }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun report(msg: String?, ok: String) = scope.launch { snack.showSnackbar(msg ?: ok) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(symbol) }, navigationIcon = {
            OutlinedButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text("Indietro") }
        }) },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(money(last), style = MaterialTheme.typography.headlineMedium)
                if (pos != null) {
                    val pnl = (last - pos.avgPrice) * pos.qty
                    Text("In portafoglio: ${pos.qty} @ ${money(pos.avgPrice)} · P&L ${money(pnl)}", color = pnlColor(pnl))
                }
            }
            item { CandleChart(list) }
            item {
                Text("Ordine a mercato (simulato) · Liquidità ${money(state.cash)}", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = qty, onValueChange = { qty = it.filter(Char::isDigit) },
                    label = { Text("Quantità") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { report(vm.buy(symbol, qty.toIntOrNull() ?: 0), "Acquisto eseguito") },
                        colors = ButtonDefaults.buttonColors(containerColor = Green),
                        modifier = Modifier.weight(1f),
                    ) { Text("COMPRA") }
                    Button(
                        onClick = { report(vm.sell(symbol, qty.toIntOrNull() ?: 0), "Vendita eseguita") },
                        colors = ButtonDefaults.buttonColors(containerColor = Red),
                        modifier = Modifier.weight(1f),
                    ) { Text("VENDI") }
                }
            }
            item {
                Text("Nuovo alert di prezzo", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = target, onValueChange = { target = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Prezzo target") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        target.toDoubleOrNull()?.let { vm.addAlert(symbol, true, it); target = "" }
                    }, modifier = Modifier.weight(1f)) { Text("Avvisa sopra") }
                    OutlinedButton(onClick = {
                        target.toDoubleOrNull()?.let { vm.addAlert(symbol, false, it); target = "" }
                    }, modifier = Modifier.weight(1f)) { Text("Avvisa sotto") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val state by vm.state.collectAsState()
    val candles by vm.candles.collectAsState()
    fun price(sym: String) = candles[sym]?.lastOrNull()?.close ?: 0.0
    val invested = state.positions.sumOf { it.qty * price(it.symbol) }
    val equity = state.cash + invested
    val total = equity - START_CASH
    val ctx = LocalContext.current
    val importUi by vm.importState.collectAsState()
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val token = GoogleAuth.tokenFromResult(ctx, r.resultCode, r.data)
        if (token != null) vm.importFetch(token) else vm.importFailed("Autorizzazione annullata")
    }
    fun startImport() {
        vm.importLoading()
        GoogleAuth.authorize(ctx, { consent.launch(it) }, { vm.importFetch(it) }, { vm.importFailed(it) })
    }
    ImportDialog(importUi, state.cash, state.positions, vm::importConfirm, vm::importDismiss)

    Scaffold(topBar = { TopAppBar(title = { Text("Portafoglio") }, actions = {
        OutlinedButton(onClick = { startImport() }, modifier = Modifier.padding(end = 8.dp)) { Text("Importa da Sheets") }
        OutlinedButton(onClick = { vm.reset() }, modifier = Modifier.padding(end = 8.dp)) { Text("Reset") }
    }) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Valore totale", style = MaterialTheme.typography.labelMedium)
                        Text(money(equity), style = MaterialTheme.typography.headlineMedium)
                        Text("P&L: ${money(total)} (${pct(total / START_CASH * 100)})", color = pnlColor(total))
                        Text("Liquidità: ${money(state.cash)}")
                    }
                }
            }
            item { Text("Posizioni", style = MaterialTheme.typography.titleMedium) }
            if (state.positions.isEmpty()) item { Text("Nessuna posizione aperta.") }
            items(state.positions) { p ->
                val pnl = (price(p.symbol) - p.avgPrice) * p.qty
                Row(Modifier.fillMaxWidth().clickable { onOpen(p.symbol) }.padding(vertical = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(p.symbol, style = MaterialTheme.typography.titleMedium)
                        Text("${p.qty} @ ${money(p.avgPrice)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(price(p.symbol) * p.qty))
                        Text(money(pnl), color = pnlColor(pnl))
                    }
                }
                HorizontalDivider()
            }
            item { Text("Storico operazioni", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
            items(state.trades.take(50)) { t ->
                val whenStr = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(t.time))
                Text(
                    "$whenStr  ${t.side} ${t.qty} ${t.symbol} @ ${money(t.price)}",
                    color = if (t.side == "BUY") Green else Red,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Alert") }) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp)) {
            if (state.alerts.isEmpty()) item {
                Text("Nessun alert. Aprendo un titolo puoi impostarne uno.")
            }
            items(state.alerts) { a ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${a.symbol} ${if (a.above) "≥" else "≤"} ${money(a.target)}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (a.triggered) "Scattato" else "In attesa",
                            color = if (a.triggered) Green else Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = { vm.removeAlert(a.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Elimina")
                    }
                }
                HorizontalDivider()
            }
            item {
                Text(
                    "Gli alert vengono controllati mentre l'app è aperta.",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ImportDialog(
    ui: AppViewModel.ImportUi,
    cash: Double,
    current: List<app.papertrader.Position>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (ui) {
        AppViewModel.ImportUi.Idle -> Unit
        AppViewModel.ImportUi.Loading -> AlertDialog(
            onDismissRequest = {}, confirmButton = {},
            title = { Text("Importazione") }, text = { Text("Lettura del foglio Balance…") },
        )
        is AppViewModel.ImportUi.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
            title = { Text("Import non riuscito") }, text = { Text(ui.message) },
        )
        is AppViewModel.ImportUi.Preview -> {
            val p = ui.data
            val symbols = (current.map { it.symbol } + p.positions.map { it.symbol }).distinct()
            AlertDialog(
                onDismissRequest = onDismiss,
                confirmButton = { TextButton(onClick = onConfirm) { Text("Sostituisci") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
                title = { Text("Anteprima import") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Liquidità: ${money(cash)} → ${money(p.cash)}")
                        symbols.forEach { sym ->
                            val old = current.find { it.symbol == sym }
                            val new = p.positions.find { it.symbol == sym }
                            fun d(x: app.papertrader.Position?) =
                                if (x == null) "—" else "${x.qty} @ ${money(x.avgPrice)}"
                            Text("$sym: ${d(old)} → ${d(new)}")
                        }
                        p.skipped.forEach { Text("Saltato: $it", color = Red) }
                        Text("Lo storico operazioni dell'app verrà azzerato.", style = MaterialTheme.typography.labelSmall)
                    }
                },
            )
        }
    }
}
