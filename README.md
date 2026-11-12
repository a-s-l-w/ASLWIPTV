# ASLWIPTV — Android starter project

Progetto originale Android (Kotlin + Jetpack Compose + Media3), creato da zero. L'APK ricevuto è stato considerato solo come riferimento generale: nessun codice, asset, libreria o branding è stato estratto o riutilizzato.

## Stato attuale: prototipo 0.1.0

Incluso:
- struttura Android multi-form-factor (telefono/tablet e launcher Android TV)
- UI dark antracite / viola elettrico / blu
- navigazione Home, Live TV, Film, Serie, EPG e Impostazioni
- player Media3/ExoPlayer con supporto HLS e DASH tramite moduli dedicati
- parser M3U/M3U8 di base
- client iniziale Xtream Codes per recupero canali live via HTTPS
- controlli Media3 integrati e modalità di interfaccia avanzata/compatta
- configurazione Gradle e documentazione

Non ancora completato:
- persistenza sicura delle credenziali e gestione profili
- UI effettiva per importare URL/file M3U e connessione Xtream
- caricamento categorie VOD/serie, dettagli, stagioni ed episodi
- EPG XMLTV, ricerca, preferiti, cronologia e sincronizzazione
- player fullscreen dedicato, selezione tracce/decoder e test su TV reali
- test automatici, accessibilità, telemetria crash opt-in e firma release

## Requisiti
- Android Studio recente
- JDK 17
- Android SDK Platform 36
- Gradle 8.13 (wrapper da generare con Android Studio o `gradle wrapper --gradle-version 8.13`)

Aprire la cartella `ASLWIPTV` in Android Studio, sincronizzare Gradle e avviare la variante `debug`.

## Note tecniche
- Media3 1.11.1 è usato per la riproduzione. HLS e DASH sono gestiti dai moduli Media3 ufficiali.
- I buffer iniziali nel controller sono valori di partenza, non una promessa di eliminare buffering: vanno misurati e tarati su stream e reti reali.
- Il client Xtream è un primo adattatore e richiede test con server autorizzati e diversi formati di risposta.
- Per la versione pubblicabile vanno aggiunti gli avvisi di licenza richiesti dalle dipendenze effettivamente distribuite. Non sostituire né rimuovere licenze di terze parti.
- Utilizzare esclusivamente playlist e flussi per i quali si dispone dei diritti o dell'autorizzazione.

## Dipendenze principali
AndroidX / Jetpack Compose, AndroidX Media3, OkHttp, Kotlin Coroutines. Consultare le rispettive licenze e includere i notice richiesti nella distribuzione.
