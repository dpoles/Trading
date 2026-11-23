# PaperTrader

App Android (Kotlin + Jetpack Compose) per paper trading di azioni/ETF.

## Funzioni
- Watchlist con prezzi live (simulati), preferiti
- Grafico a candele con SMA20 e RSI(14)
- Ordini a mercato simulati (portafoglio importato dal foglio Balance), posizioni e P&L
- Alert di prezzo con notifiche (controllati ad app aperta)
- Stato salvato in locale

## Avvio
Apri la cartella in Android Studio (Koala o successivo), sync Gradle, Run su emulatore/dispositivo (minSdk 26).

## Dati
`MarketEngine` genera prezzi con random walk. Per dati reali va sostituito con un feed
(Alpaca, Finnhub, Twelve Data...) che alimenti lo stesso `StateFlow<Map<String, List<Candle>>>`.
