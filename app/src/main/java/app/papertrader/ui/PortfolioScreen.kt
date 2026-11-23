package app.papertrader.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.papertrader.AppViewModel
import app.papertrader.GoogleAuth
import app.papertrader.SheetSnapshot
import app.papertrader.money
import app.papertrader.pct
import app.papertrader.qtyText
import java.text.DateFormat
import java.time.YearMonth
import java.util.Date

private data class Cell(val text: String, val color: Color? = null, val bold: Boolean = false)

private fun m(v: Double?) = v?.let { money(it) } ?: "—"
private fun p(v: Double?) = v?.let { pct(it * 100) } ?: "—"
private fun tone(v: Double?): Color? = when {
    v == null -> null
    v >= 0 -> Green
    else -> Red
}

/** Tabella stile foglio: intestazione, righe con bordo, scorrimento orizzontale. */
@Composable
private fun SheetTable(headers: List<String>, widths: List<Dp>, rows: List<List<Cell>>) {
    val total = widths.fold(0.dp) { a, b -> a + b }
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row(Modifier.width(total).background(MaterialTheme.colorScheme.surfaceVariant)) {
            headers.forEachIndexed { i, h ->
                Text(
                    h, Modifier.width(widths[i]).padding(horizontal = 8.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    textAlign = if (i == 0) TextAlign.Start else TextAlign.End, maxLines = 1,
                )
            }
        }
        rows.forEach { r ->
            Row(Modifier.width(total)) {
                r.forEachIndexed { i, c ->
                    Text(
                        c.text, Modifier.width(widths[i]).padding(horizontal = 8.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.color ?: Color.Unspecified,
                        fontWeight = if (c.bold) FontWeight.Bold else null,
                        textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(Modifier.width(total))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val snap = state.snapshot
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
    ImportDialog(importUi, snap, vm::importConfirm, vm::importDismiss)

    Scaffold(topBar = {
        TopAppBar(title = { Text("Portafoglio") }, actions = {
            OutlinedButton(onClick = { startImport() }, modifier = Modifier.padding(end = 8.dp)) { Text("Importa da Sheets") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (snap == null) {
                Text("Nessun dato. Usa \"Importa da Sheets\".", Modifier.padding(16.dp))
                return@Column
            }
            val titles = listOf("Lotti", "Riepilogo", "Mesi", "Versamenti")
            TabRow(selectedTabIndex = tab) {
                titles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                when (tab) {
                    0 -> LotsView(snap)
                    1 -> SummaryView(snap)
                    2 -> MonthsView(snap)
                    else -> DepositsView(snap)
                }
                Text(
                    "Dati importati il " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(snap.importedAt)),
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun LotsView(s: SheetSnapshot) {
    val value = s.lots.sumOf { it.value ?: 0.0 }
    val cost = s.lots.sumOf { it.buyValue }
    val diff = value - cost
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Valore titoli: ${money(value)}", style = MaterialTheme.typography.titleMedium)
        Text("Investito: ${money(cost)}")
        Text("Differenza: ${money(diff)} (${pct(if (cost != 0.0) diff / cost * 100 else 0.0)})", color = tone(diff) ?: Color.Unspecified)
        Text("Liquidità: ${money(s.cash)}")
    }
    val headers = listOf("Titolo", "Data", "Prz Acq", "Qtà", "Val Acq", "Prz Act", "Val Act", "Diff", "% Int", "Net")
    val widths = listOf(150.dp, 90.dp, 80.dp, 55.dp, 100.dp, 80.dp, 100.dp, 90.dp, 65.dp, 90.dp)
    val rows = s.lots.map { l ->
        listOf(
            Cell(l.name), Cell(l.date), Cell(m(l.buyPrice)), Cell(qtyText(l.qty)), Cell(m(l.buyValue)),
            Cell(m(l.price)), Cell(m(l.value)), Cell(m(l.diff), tone(l.diff)),
            Cell(p(l.ret), tone(l.ret)), Cell(m(l.net), tone(l.net)),
        )
    } + s.summaries.filter { it.label.contains("TOTALE INVESTITO") }.map { t ->
        listOf(
            Cell(t.label.substringBefore(" –") + " tot.", bold = true), Cell(""), Cell(""),
            Cell(t.qty?.let { qtyText(it) } ?: "", bold = true), Cell(m(t.buyValue), bold = true),
            Cell(""), Cell(m(t.value), bold = true), Cell(m(t.diff), tone(t.diff), true),
            Cell(p(t.ret), tone(t.ret), true), Cell(m(t.net), tone(t.net), true),
        )
    }
    SheetTable(headers, widths, rows)
}

@Composable
private fun SummaryView(s: SheetSnapshot) {
    val headers = listOf("Voce", "Qtà", "Val Acq", "Val Act", "Diff", "% Int", "Net")
    val widths = listOf(200.dp, 55.dp, 100.dp, 100.dp, 90.dp, 65.dp, 90.dp)
    val rows = s.summaries.map { t ->
        listOf(
            Cell(t.label, bold = true), Cell(t.qty?.let { qtyText(it) } ?: "—"), Cell(m(t.buyValue)),
            Cell(m(t.value)), Cell(m(t.diff), tone(t.diff)), Cell(p(t.ret), tone(t.ret)), Cell(m(t.net), tone(t.net)),
        )
    }
    SheetTable(headers, widths, rows)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Saldo disponibile: ${money(s.cash)}", style = MaterialTheme.typography.titleMedium)
        s.stampDuty?.let { Text("Imposte bollo: ${money(it)}") }
    }
}

@Composable
private fun MonthsView(s: SheetSnapshot) {
    val now = YearMonth.now().toString()
    val headers = listOf("Mese", "Valore netto", "Variazione", "Crescita")
    val widths = listOf(110.dp, 120.dp, 110.dp, 80.dp)
    val rows = s.months.map { r ->
        val label = r.month.substring(5) + "/" + r.month.substring(0, 4) + if (r.month > now) " (stima)" else ""
        listOf(Cell(label), Cell(m(r.netValue)), Cell(m(r.variation), tone(r.variation)), Cell(p(r.growth), tone(r.growth)))
    }
    SheetTable(headers, widths, rows)
}

@Composable
private fun DepositsView(s: SheetSnapshot) {
    val headers = listOf("Data", "Importo", "Cumulato", "Origine", "Note")
    val widths = listOf(100.dp, 100.dp, 110.dp, 90.dp, 140.dp)
    val rows = s.deposits.map { d ->
        listOf(Cell(d.date), Cell(m(d.amount), tone(d.amount)), Cell(m(d.cumulative)), Cell(d.source), Cell(d.note))
    }
    SheetTable(headers, widths, rows)
}

@Composable
private fun ImportDialog(
    ui: AppViewModel.ImportUi,
    current: SheetSnapshot?,
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
            val n = ui.data
            fun titoli(x: SheetSnapshot?) = x?.lots?.sumOf { it.value ?: 0.0 }
            fun inv(x: SheetSnapshot?) = x?.lots?.sumOf { it.buyValue }
            AlertDialog(
                onDismissRequest = onDismiss,
                confirmButton = { TextButton(onClick = onConfirm) { Text("Sostituisci") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
                title = { Text("Anteprima import") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Liquidità: ${m(current?.cash)} → ${money(n.cash)}")
                        Text("Investito: ${m(inv(current))} → ${money(inv(n)!!)}")
                        Text("Valore titoli: ${m(titoli(current))} → ${money(titoli(n)!!)}")
                        Text("Lotti: ${current?.lots?.size ?: 0} → ${n.lots.size}")
                        Text("Versamenti: ${current?.deposits?.size ?: 0} → ${n.deposits.size}")
                        Text("Mesi: ${current?.months?.size ?: 0} → ${n.months.size}")
                    }
                },
            )
        }
    }
}
