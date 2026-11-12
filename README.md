# ASLW IPTV — Android app

Progetto originale Android (Kotlin + Jetpack Compose + Media3), creato da zero. L'APK ricevuto è stato considerato solo come riferimento generale: nessun codice, asset, libreria o branding è stato estratto o riutilizzato.

## Stato attuale: prototipo 0.4.3

Incluso:
- struttura Android multi-form-factor (telefono/tablet e launcher Android TV)
- UI dark antracite / viola elettrico / blu
- navigazione dopo l’accesso tra Diretta TV, Film e Serie TV
- player Media3/ExoPlayer con supporto HLS e DASH tramite moduli dedicati
- importazione di playlist M3U da URL o file locale
- collegamento Xtream Codes con caricamento di Live TV, film e serie TV
- profili multipli per account Xtream e playlist M3U, selezionabili dall’intestazione
- salvataggio dei profili e dei canali importati, con cifratura AES-GCM tramite Android Keystore
- schermata di accesso iniziale con selezione account e impostazioni rete/proxy prima dell’accesso
- navigazione dopo l’accesso limitata a Diretta TV, Film e Serie TV
- cataloghi Xtream per Live TV, film e serie; selezione degli episodi per le serie
- anteprima player a destra su tablet/TV e mini player sui telefoni
- supporto agli header Referer/User-Agent definiti nelle playlist M3U
- logo fornito integrato nel launcher e nell’interfaccia
- supporto a sorgenti HTTP e HTTPS
- proxy applicativo HTTP o SOCKS condiviso da importazione e player; autenticazione disponibile per proxy HTTP
- compatibilità con la VPN gestita da Android: l’app non rileva né blocca le VPN di terze parti
- controlli Media3 integrati e modalità di interfaccia avanzata/compatta
- navigazione adattiva: barra inferiore su telefoni e rail laterale su schermi ampi/TV
- configurazione Gradle e documentazione

Non ancora completato:
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
- credenziali, URL sorgente e URL dei canali salvati sono cifrati nello spazio privato dell’app con una chiave Android Keystore; le liste M3U da file mantengono la copia dei canali importati.
- Le playlist M3U possono includere `#EXTVLCOPT:http-user-agent`, `http-referrer` e `http-origin`; le richieste del canale usano gli header trovati.
- I cataloghi Xtream richiedono che il provider supporti le azioni standard `get_live_streams`, `get_vod_streams`, `get_series` e `get_series_info`.
- le impostazioni del proxy sono memorizzate nello spazio privato dell’app. L’autenticazione del proxy è supportata per HTTP; SOCKS è senza credenziali.
- il proxy app-level non sostituisce la VPN: la rete/VPN di Android continua a instradare il traffico dell’app.
- il client Xtream carica Live TV. VOD, serie, EPG e requisiti specifici dei singoli provider necessitano di ulteriori verifiche.
- Per la versione pubblicabile vanno aggiunti gli avvisi di licenza richiesti dalle dipendenze effettivamente distribuite. Non sostituire né rimuovere licenze di terze parti.
- Utilizzare esclusivamente playlist e flussi per i quali si dispone dei diritti o dell'autorizzazione.

## Dipendenze principali
AndroidX / Jetpack Compose, AndroidX Media3, OkHttp, Kotlin Coroutines. Consultare le rispettive licenze e includere i notice richiesti nella distribuzione.

