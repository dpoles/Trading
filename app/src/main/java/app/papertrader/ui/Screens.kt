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
    var target by remember { mutableStateOf("") }
    Scaffold(
        topBar = { TopAppBar(title = { Text(symbol) }, navigationIcon = {
            OutlinedButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text("Indietro") }
        }) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(money(last), style = MaterialTheme.typography.headlineMedium)
            }
            item { CandleChart(list) }
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
