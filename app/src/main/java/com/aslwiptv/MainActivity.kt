package com.aslwiptv

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.aslwiptv.data.AppNetwork
import com.aslwiptv.data.AppProxyType
import com.aslwiptv.data.MediaItem
import com.aslwiptv.data.MediaKind
import com.aslwiptv.data.M3uParser
import com.aslwiptv.data.M3uRepository
import com.aslwiptv.data.ProxySettings
import com.aslwiptv.data.XtreamCredentials
import com.aslwiptv.data.XtreamRepository
import com.aslwiptv.player.PlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private val Ink = Color(0xFF090A10)
private val Panel = Color(0xFF141622)
private val Panel2 = Color(0xFF1B1E2D)
private val Purple = Color(0xFFA78BFA)
private val Blue = Color(0xFF60A5FA)
private val Muted = Color(0xFF969AAF)

class MainActivity : ComponentActivity() {
    private lateinit var network: AppNetwork
    private lateinit var playback: PlaybackController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        network = AppNetwork(this)
        playback = PlaybackController(this, network.client)
        setContent { AslwTheme { AslwApp(playback, network) } }
    }
    override fun onStop() { playback.player.pause(); super.onStop() }
    override fun onDestroy() { playback.release(); super.onDestroy() }
}

@Composable
private fun AslwTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Purple, secondary = Blue, background = Ink, surface = Panel,
        onPrimary = Color(0xFF171020), onBackground = Color.White, onSurface = Color.White
    ), content = content)
}

private enum class Section(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home), LIVE("Live TV", Icons.Default.Tv), MOVIES("Film", Icons.Default.Movie),
    SERIES("Serie", Icons.Default.VideoLibrary), GUIDE("EPG", Icons.Default.Schedule), SETTINGS("Impostazioni", Icons.Default.Settings)
}

@Composable
private fun AslwApp(playback: PlaybackController, network: AppNetwork) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val xtream = remember(network) { XtreamRepository(network.apiClient) }
    val m3u = remember(network) { M3uRepository(network.apiClient) }
    var section by remember { mutableStateOf(Section.HOME) }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<MediaItem?>(null) }
    var advanced by remember { mutableStateOf(true) }
    var showSetup by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var sourceError by remember { mutableStateOf<String?>(null) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var playlistUrl by remember { mutableStateOf("") }
    var playlist by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    DisposableEffect(playback.player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playerError = "Riproduzione interrotta: ${error.errorCodeName.replace('_', ' ').lowercase()}. Verifica rete, proxy e accesso al canale."
            }
        }
        playback.player.addListener(listener)
        onDispose { playback.player.removeListener(listener) }
    }

    fun importPlaylist(loader: suspend () -> Result<List<MediaItem>>) {
        if (loading) return
        scope.launch {
            loading = true
            sourceError = null
            try {
                loader().fold(
                    onSuccess = { channels ->
                        playlist = channels
                        section = Section.LIVE
                        showSetup = false
                        server = ""
                        username = ""
                        password = ""
                        playlistUrl = ""
                    },
                    onFailure = { error -> sourceError = error.message ?: "Importazione non riuscita." }
                )
            } catch (error: Exception) {
                sourceError = error.message ?: "Importazione non riuscita."
            } finally {
                loading = false
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importPlaylist { runCatching { readLocalM3u(context, uri) } }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Ink)) {
        val compact = maxWidth < 600.dp
        if (compact) {
            Column(Modifier.fillMaxSize()) {
                AppHeader(onAdd = { sourceError = null; showSetup = true }, compact = true)
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    CurrentSection(section, playlist, advanced, { advanced = it }, { showSetup = true }, { section = it }, network) { item ->
                        playerError = null
                        playing = item
                        playback.play(item)
                    }
                }
                NavigationBar(containerColor = Color(0xFF0D0F18)) {
                    listOf(Section.HOME, Section.LIVE, Section.MOVIES).forEach { item ->
                        NavigationBarItem(
                            selected = section == item,
                            onClick = { section = item },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, fontSize = 9.sp) },
                            alwaysShowLabel = false
                        )
                    }
                    Box {
                        NavigationBarItem(
                            selected = section in listOf(Section.SERIES, Section.GUIDE, Section.SETTINGS),
                            onClick = { moreMenuExpanded = true },
                            icon = { Icon(Icons.Default.MoreHoriz, contentDescription = "Altre sezioni") },
                            label = { Text("Altro", fontSize = 9.sp) },
                            alwaysShowLabel = false
                        )
                        DropdownMenu(expanded = moreMenuExpanded, onDismissRequest = { moreMenuExpanded = false }) {
                            listOf(Section.SERIES, Section.GUIDE, Section.SETTINGS).forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.label) },
                                    leadingIcon = { Icon(item.icon, contentDescription = null) },
                                    onClick = { section = item; moreMenuExpanded = false }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = Color(0xFF0D0F18), header = {
                    Box(Modifier.padding(vertical = 22.dp).size(48.dp).background(Brush.linearGradient(listOf(Purple, Blue)), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                        Text("A", fontSize = 27.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }) {
                    Section.entries.forEach { item ->
                        NavigationRailItem(
                            selected = section == item,
                            onClick = { section = item },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, fontSize = 10.sp) },
                            alwaysShowLabel = false
                        )
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    AppHeader(onAdd = { sourceError = null; showSetup = true }, compact = false)
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)) {
                        CurrentSection(section, playlist, advanced, { advanced = it }, { showSetup = true }, { section = it }, network) { item ->
                            playerError = null
                            playing = item
                            playback.play(item)
                        }
                    }
                }
            }
        }
    }

    if (playing != null) PlayerDialog(
        item = playing!!,
        playback = playback,
        error = playerError,
        advanced = advanced,
        onAdvanced = { advanced = it },
        onRetry = { playerError = null; playback.play(playing!!) },
        onDismiss = { playing = null; playerError = null; playback.player.pause() }
    )

    if (showSetup) {
        AddSourceDialog(
            server = server,
            onServerChange = { server = it },
            username = username,
            onUsernameChange = { username = it },
            password = password,
            onPasswordChange = { password = it },
            playlistUrl = playlistUrl,
            onPlaylistUrlChange = { playlistUrl = it },
            loading = loading,
            error = sourceError,
            onClearError = { sourceError = null },
            onDismiss = { if (!loading) showSetup = false },
            onSaveXtream = { importPlaylist { xtream.loadLive(XtreamCredentials(server, username, password)) } },
            onSaveM3u = { importPlaylist { m3u.load(playlistUrl) } },
            onPickFile = { filePicker.launch(arrayOf("*/*")) }
        )
    }
}

@Composable
private fun AppHeader(onAdd: () -> Unit, compact: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("ASLWIPTV", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 1.5.sp)
            Text("STREAMING, SENZA COMPROMESSI", color = Muted, fontSize = 9.sp, letterSpacing = 1.4.sp)
        }
        if (compact) IconButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = "Aggiungi sorgente", tint = Purple) }
        else TextButton(onClick = onAdd) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Aggiungi playlist") }
    }
}

@Composable
private fun CurrentSection(
    section: Section,
    playlist: List<MediaItem>,
    advanced: Boolean,
    onAdvanced: (Boolean) -> Unit,
    onSetup: () -> Unit,
    onNavigate: (Section) -> Unit,
    network: AppNetwork,
    onPlay: (MediaItem) -> Unit
) {
    when (section) {
        Section.HOME -> HomeContent(
            onLive = { onNavigate(Section.LIVE) },
            onMovies = { onNavigate(Section.MOVIES) },
            onSetup = onSetup,
            count = playlist.size,
            onOpenLive = { playlist.firstOrNull { it.kind == MediaKind.LIVE }?.let(onPlay) }
        )
        Section.LIVE -> MediaLibrary("Live TV", playlist.filter { it.kind == MediaKind.LIVE }, onPlay, onSetup)
        Section.MOVIES -> MediaLibrary("Film", playlist.filter { it.kind == MediaKind.MOVIE }, onPlay, onSetup)
        Section.SERIES -> MediaLibrary("Serie", playlist.filter { it.kind == MediaKind.SERIES }, onPlay, onSetup)
        Section.GUIDE -> EmptyState("Guida EPG", "La guida programmi sarà collegata alla sorgente EPG della playlist.", Icons.Default.Schedule)
        Section.SETTINGS -> SettingsContent(advanced, onAdvanced, onSetup, network)
    }
}

@Composable
private fun HomeContent(onLive: () -> Unit, onMovies: () -> Unit, onSetup: () -> Unit, count: Int, onOpenLive: () -> Unit) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val compactWidth = screenWidth < 390
    val heroTextSize = if (screenWidth < 360) 24.sp else 28.sp
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.fillMaxWidth().height(if (compactWidth) 210.dp else 190.dp).background(Brush.linearGradient(listOf(Color(0xFF292044), Color(0xFF172A46), Panel)), RoundedCornerShape(24.dp)).padding(22.dp)) {
            Column(Modifier.align(Alignment.CenterStart)) {
                Text("IL TUO INTRATTENIMENTO", color = Purple, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                Spacer(Modifier.height(8.dp)); Text("Tutto quello che\nvuoi guardare.", color = Color.White, fontSize = heroTextSize, fontWeight = FontWeight.ExtraBold, lineHeight = heroTextSize * 1.15f)
                Spacer(Modifier.height(14.dp)); Button(onClick = if (count > 0) onOpenLive else onSetup, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text(if (count > 0) "Guarda Live TV" else "Configura sorgente") }
            }
        }
        Text("Esplora", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureCard("Live TV", "Canali in diretta", Icons.Default.Tv, Modifier.weight(1f), onLive)
            FeatureCard("Film", "La tua videoteca", Icons.Default.Movie, Modifier.weight(1f), onMovies)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("ELEMENTI CARICATI", count.toString(), Modifier.weight(1f))
            StatCard("PIATTAFORMA", "Android", Modifier.weight(1f))
        }
        Text("ASLWIPTV è un lettore: aggiungi playlist e flussi per cui possiedi i diritti di accesso.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun FeatureCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.background(Panel, RoundedCornerShape(18.dp)).border(1.dp, Panel2, RoundedCornerShape(18.dp)).clickable(onClick = onClick).focusable().padding(16.dp)) {
        Icon(icon, null, tint = Purple, modifier = Modifier.size(28.dp)); Spacer(Modifier.height(14.dp))
        Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Panel, RoundedCornerShape(16.dp)).padding(15.dp)) {
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp); Spacer(Modifier.height(5.dp)); Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MediaLibrary(title: String, items: List<MediaItem>, onPlay: (MediaItem) -> Unit, onSetup: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(14.dp))
        if (items.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.PlayCircle, null, tint = Purple, modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(12.dp))
                Text("Nessun contenuto caricato", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Text("Aggiungi una playlist per iniziare.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                Button(onClick = onSetup) { Text("Aggiungi sorgente") }
            }
        }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).clickable { onPlay(item) }.focusable().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).background(Panel2, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, null, tint = Purple) }
                    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.SemiBold); Text(item.category, color = Muted, fontSize = 11.sp) }
                    Icon(Icons.Default.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = Purple, modifier = Modifier.size(42.dp)); Spacer(Modifier.height(12.dp)); Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp)); Text(description, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsContent(advanced: Boolean, onAdvanced: (Boolean) -> Unit, onSetup: () -> Unit, network: AppNetwork) {
    val saved = remember(network) { network.proxySettings() }
    var proxyEnabled by remember(saved) { mutableStateOf(saved.enabled) }
    var proxyType by remember(saved) { mutableStateOf(saved.type) }
    var proxyHost by remember(saved) { mutableStateOf(saved.host) }
    var proxyPort by remember(saved) { mutableStateOf(saved.port.toString()) }
    var proxyUsername by remember(saved) { mutableStateOf(saved.username) }
    var proxyPassword by remember(saved) { mutableStateOf(saved.password) }
    var typeMenu by remember { mutableStateOf(false) }
    var proxyMessage by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Impostazioni", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Controlli avanzati", fontWeight = FontWeight.SemiBold)
                Text("Mostra le opzioni aggiuntive del player", color = Muted, fontSize = 11.sp)
            }
            Switch(checked = advanced, onCheckedChange = onAdvanced)
        }
        Text("Rete e proxy", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Usa proxy per l’app", fontWeight = FontWeight.SemiBold)
                Text("Playlist, Xtream e riproduzione", color = Muted, fontSize = 11.sp)
            }
            Switch(checked = proxyEnabled, onCheckedChange = { proxyEnabled = it; proxyMessage = null })
        }
        Box {
            OutlinedButton(onClick = { typeMenu = true }) { Text("Tipo: ${proxyType.name}") }
            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                AppProxyType.entries.forEach { type ->
                    DropdownMenuItem(text = { Text(type.name) }, onClick = { proxyType = type; typeMenu = false })
                }
            }
        }
        OutlinedTextField(proxyHost, { proxyHost = it; proxyMessage = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Indirizzo proxy") }, singleLine = true)
        OutlinedTextField(proxyPort, { proxyPort = it.filter(Char::isDigit).take(5); proxyMessage = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Porta") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(proxyUsername, { proxyUsername = it; proxyMessage = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Username proxy (opzionale)") }, singleLine = true)
        OutlinedTextField(proxyPassword, { proxyPassword = it; proxyMessage = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Password proxy (opzionale)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        Text("HTTP supporta l’autenticazione. SOCKS è disponibile senza credenziali.", color = Muted, fontSize = 12.sp)
        Text("La VPN di Android resta attiva: il traffico dell’app segue la rete di sistema.", color = Muted, fontSize = 12.sp)
        Button(onClick = {
            val settings = ProxySettings(proxyEnabled, proxyType, proxyHost, proxyPort.toIntOrNull() ?: 0, proxyUsername, proxyPassword)
            val validation = settings.validate()
            val socksAuthError = proxyEnabled && proxyType == AppProxyType.SOCKS && (proxyUsername.isNotBlank() || proxyPassword.isNotBlank())
            when {
                validation != null -> proxyMessage = validation
                socksAuthError -> proxyMessage = "Rimuovi le credenziali o scegli un proxy HTTP."
                else -> {
                    network.saveProxySettings(settings)
                    proxyMessage = if (proxyEnabled) "Proxy salvato per le connessioni successive." else "Proxy disattivato."
                }
            }
        }) { Text("Salva proxy") }
        if (proxyMessage != null) {
            val savedMessage = proxyMessage!!.contains("salvato", ignoreCase = true) || proxyMessage!!.contains("disattivato", ignoreCase = true)
            Text(proxyMessage!!, color = if (savedMessage) Purple else MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Button(onClick = onSetup) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Gestisci sorgenti") }
        Text("Versione 0.2.0 · ASLWIPTV", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun AddSourceDialog(
    server: String,
    onServerChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    playlistUrl: String,
    onPlaylistUrlChange: (String) -> Unit,
    loading: Boolean,
    error: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onSaveXtream: () -> Unit,
    onSaveM3u: () -> Unit,
    onPickFile: () -> Unit
) {
    var selected by remember { mutableIntStateOf(0) }
    val heightLimit = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).heightIn(max = heightLimit).padding(16.dp),
            color = Panel,
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aggiungi sorgente", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, enabled = !loading) { Icon(Icons.Default.Close, contentDescription = "Chiudi") }
                }
                TabRow(selectedTabIndex = selected, containerColor = Panel) {
                    Tab(selected == 0, onClick = { selected = 0; onClearError() }, text = { Text("Xtream") })
                    Tab(selected == 1, onClick = { selected = 1; onClearError() }, text = { Text("M3U") })
                }
                if (selected == 0) {
                    Text("Accedi con i dati del tuo provider.", color = Muted, fontSize = 12.sp)
                    Text("HTTP non cifra credenziali o flussi. Preferisci HTTPS quando disponibile.", color = Muted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = server,
                        onValueChange = { onServerChange(it); onClearError() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("URL server (http:// o https://)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { onUsernameChange(it); onClearError() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Username") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { onPasswordChange(it); onClearError() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                } else {
                    Text("Importa una playlist da URL o da un file sul dispositivo.", color = Muted, fontSize = 12.sp)
                    Text("I flussi HTTP sono consentiti; il loro traffico non è cifrato.", color = Muted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = playlistUrl,
                        onValueChange = { onPlaylistUrlChange(it); onClearError() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("URL M3U (http:// o https://)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    OutlinedButton(onClick = onPickFile, modifier = Modifier.fillMaxWidth(), enabled = !loading) {
                        Icon(Icons.Default.FolderOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scegli file M3U")
                    }
                }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, enabled = !loading) { Text("Annulla") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { if (selected == 0) onSaveXtream() else onSaveM3u() },
                        enabled = !loading,
                        colors = ButtonDefaults.buttonColors(containerColor = Purple)
                    ) { Text(if (loading) "Caricamento…" else "Salva") }
                }
            }
        }
    }
}

@Composable
private fun PlayerDialog(
    item: MediaItem,
    playback: PlaybackController,
    error: String?,
    advanced: Boolean,
    onAdvanced: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val heightLimit = (LocalConfiguration.current.screenHeightDp * 0.92f).dp
    var isPlaying by remember(playback.player) { mutableStateOf(playback.player.isPlaying) }
    DisposableEffect(playback.player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        playback.player.addListener(listener)
        onDispose { playback.player.removeListener(listener) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().widthIn(max = 960.dp).heightIn(max = heightLimit).padding(12.dp),
            color = Panel,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Chiudi player") }
                }
                AndroidView(
                    factory = { ctx -> PlayerView(ctx).apply { player = playback.player; useController = advanced } },
                    update = { it.useController = advanced },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Controlli avanzati", fontWeight = FontWeight.SemiBold)
                        Text("Seek, velocità e tracce se supportati dallo stream", color = Muted, fontSize = 11.sp)
                    }
                    Switch(checked = advanced, onCheckedChange = onAdvanced)
                }
                if (!advanced) {
                    Button(onClick = { if (isPlaying) playback.player.pause() else playback.player.play() }) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isPlaying) "Pausa" else "Riprendi")
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    TextButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Riprova")
                    }
                }
            }
        }
    }
}

private suspend fun readLocalM3u(context: Context, uri: Uri): List<MediaItem> = withContext(Dispatchers.IO) {
    val input = context.contentResolver.openInputStream(uri) ?: error("Impossibile aprire il file selezionato.")
    val maximumBytes = 32 * 1024 * 1024
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    input.use { stream ->
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            check(output.size() + count <= maximumBytes) { "Il file supera il limite di 32 MB." }
            output.write(buffer, 0, count)
        }
    }
    val channels = M3uParser.parse(output.toString(Charsets.UTF_8.name()))
    check(channels.isNotEmpty()) { "Il file non contiene canali M3U validi." }
    channels
}

