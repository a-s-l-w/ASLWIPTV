# ASLWIPTV — Android starter project

Progetto originale Android (Kotlin + Jetpack Compose + Media3), creato da zero. L'APK ricevuto è stato considerato solo come riferimento generale: nessun codice, asset, libreria o branding è stato estratto o riutilizzato.

## Stato attuale: prototipo 0.2.0

Incluso:
- struttura Android multi-form-factor (telefono/tablet e launcher Android TV)
- UI dark antracite / viola elettrico / blu
- navigazione Home, Live TV, Film, Serie, EPG e Impostazioni
- player Media3/ExoPlayer con supporto HLS e DASH tramite moduli dedicati
- importazione di playlist M3U da URL o file locale
- collegamento Xtream Codes con caricamento dei canali Live TV
- supporto a sorgenti HTTP e HTTPS
- proxy applicativo HTTP o SOCKS condiviso da importazione e player; autenticazione disponibile per proxy HTTP
- compatibilità con la VPN gestita da Android: l’app non rileva né blocca le VPN di terze parti
- controlli Media3 integrati e modalità di interfaccia avanzata/compatta
- navigazione adattiva: barra inferiore su telefoni e rail laterale su schermi ampi/TV
- configurazione Gradle e documentazione

Non ancora completato:
- salvataggio persistente di playlist e profili (la sorgente importata resta in memoria fino alla chiusura dell’app)
- caricamento categorie VOD/serie, dettagli, stagioni ed episodi
- EPG XMLTV, ricerca, preferiti, cronologia e sincronizzazione
- selezione tracce/decoder e test su TV reali e dispositivi diversi
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
- HTTP non cifra credenziali né flussi. Preferire HTTPS quando il provider lo supporta.
- le credenziali Xtream restano in memoria e sono rimosse dalla schermata dopo l’importazione; non sono salvate come profilo.
- le impostazioni del proxy sono memorizzate nello spazio privato dell’app. L’autenticazione del proxy è supportata per HTTP; SOCKS è senza credenziali.
- il proxy app-level non sostituisce la VPN: la rete/VPN di Android continua a instradare il traffico dell’app.
- il client Xtream carica Live TV. VOD, serie, EPG e requisiti specifici dei singoli provider necessitano di ulteriori verifiche.
- Per la versione pubblicabile vanno aggiunti gli avvisi di licenza richiesti dalle dipendenze effettivamente distribuite. Non sostituire né rimuovere licenze di terze parti.
- Utilizzare esclusivamente playlist e flussi per i quali si dispone dei diritti o dell'autorizzazione.

## Dipendenze principali
AndroidX / Jetpack Compose, AndroidX Media3, OkHttp, Kotlin Coroutines. Consultare le rispettive licenze e includere i notice richiesti nella distribuzione.

