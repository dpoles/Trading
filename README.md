# PaperTrader

App Android (Kotlin + Jetpack Compose) per paper trading di azioni/ETF.

## Funzioni
- Watchlist con prezzi live (simulati), preferiti
- Grafico a candele con SMA20 e RSI(14)
- Portafoglio in 4 viste a tabella come il foglio: Lotti, Riepilogo, Mesi, Versamenti (nessun ordine di compra/vendita)
- Alert di prezzo con notifiche (controllati ad app aperta)
- Stato salvato in locale

## Avvio
Apri la cartella in Android Studio (Koala o successivo), sync Gradle, Run su emulatore/dispositivo (minSdk 26).

## Dati
`MarketEngine` genera prezzi con random walk. Per dati reali va sostituito con un feed
(Alpaca, Finnhub, Twelve Data...) che alimenti lo stesso `StateFlow<Map<String, List<Candle>>>`.

## Import dal foglio Google "Balance"
Tab **Portafoglio → Importa da Sheets**: legge i tab `Azioni` e `Monetari` (ISIN, Qtà, Val Acq e "Saldo Disponibile"),
aggrega i lotti per ISIN, mostra un'anteprima con le differenze e sostituisce il portafoglio solo dopo conferma.
Accesso in sola lettura (`spreadsheets.readonly`); l'ID del foglio è in `SheetImport.kt`.
ISIN riconosciuti: VWCE (IE00BK5BQT80) e XEON (LU0290358497); gli altri vengono segnalati come "saltati".

### Configurazione Google (una tantum)
1. console.cloud.google.com → nuovo progetto → abilita **Google Sheets API**.
2. Schermata consenso OAuth: tipo *External*, aggiungi il tuo account Google come utente di test e lo scope `.../auth/spreadsheets.readonly`.
3. Credenziali → *ID client OAuth* → tipo **Android**, package `app.papertrader`, SHA-1 del keystore di debug
   (`./gradlew signingReport`).
4. Nessun file da copiare nell'app: il client viene riconosciuto da package + SHA-1.
