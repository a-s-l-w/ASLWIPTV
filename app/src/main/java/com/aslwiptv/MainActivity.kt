package com.aslwiptv

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewParent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.ui.PlayerView
import com.aslwiptv.data.AppNetwork
import com.aslwiptv.data.AppProxyType
import com.aslwiptv.data.MediaItem
import com.aslwiptv.data.MediaKind
import com.aslwiptv.data.M3uParser
import com.aslwiptv.data.M3uRepository
import com.aslwiptv.data.PlaylistProfile
import com.aslwiptv.data.PlaylistProfileStore
import com.aslwiptv.data.PlaylistSourceType
import com.aslwiptv.data.ProxySettings
import com.aslwiptv.data.XtreamCredentials
import com.aslwiptv.data.XtreamRepository
import com.aslwiptv.player.PlaybackController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import javax.net.ssl.SSLException

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
        playback = PlaybackController(this, network)
        setContent { AslwTheme { AslwApp(playback, network) } }
    }
    override fun onStop() { playback.player.pause(); super.onStop() }
    override fun onDestroy() { playback.release(); super.onDestroy() }
}

@Composable
private fun AslwTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Purple, secondary = Blue, background = Ink, surface = Panel,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = Color.White,
        onPrimaryContainer = Color.White,
        onSecondaryContainer = Color.White,
        onTertiary = Color.White,
        onTertiaryContainer = Color.White,
        onError = Color.White,
        onErrorContainer = Color.White
    )) {
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}

private enum class Section(val label: String, val icon: ImageVector) {
    LIVE("Diretta TV", Icons.Default.Tv), MOVIES("Film", Icons.Default.Movie),
    SERIES("Serie TV", Icons.Default.VideoLibrary), SETTINGS("Impostazioni", Icons.Default.Settings)
}

@Composable
private fun AslwApp(playback: PlaybackController, network: AppNetwork) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val xtream = remember(network) { XtreamRepository(network.apiClient) }
    val m3u = remember(network) { M3uRepository(network.apiClient) }
    var section by remember { mutableStateOf(Section.LIVE) }
    var authenticated by remember { mutableStateOf(false) }
    var showSettingsBeforeLogin by remember { mutableStateOf(false) }
    var lastSection by remember { mutableStateOf(Section.LIVE) }
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
    var profileName by remember { mutableStateOf("") }
    var pendingFileProfileName by remember { mutableStateOf("") }
    var profiles by remember { mutableStateOf<List<PlaylistProfile>>(emptyList()) }
    var profilesLoaded by remember { mutableStateOf(false) }
    var activeProfileId by remember { mutableStateOf<String?>(null) }
    var playlist by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var playerFullscreen by remember { mutableStateOf(false) }
    var seriesSelection by remember { mutableStateOf<MediaItem?>(null) }
    var seriesEpisodes by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var seriesLoading by remember { mutableStateOf(false) }
    var seriesError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(context) {
        runCatching { PlaylistProfileStore.load(context.applicationContext) }
            .onSuccess { library ->
                profiles = library.profiles
                activeProfileId = library.activeProfileId
                playlist = library.profiles.firstOrNull { it.id == library.activeProfileId }?.channels.orEmpty()
                profilesLoaded = true
            }
            .onFailure { error ->
                sourceError = error.message ?: "Impossibile leggere le liste salvate."
                profilesLoaded = true
            }
    }

    DisposableEffect(playback.player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playerError = playbackErrorMessage(error)
            }
        }
        playback.player.addListener(listener)
        onDispose { playback.player.removeListener(listener) }
    }

    fun importPlaylist(
        name: String,
        sourceType: PlaylistSourceType,
        serverUrl: String = "",
        accountUsername: String = "",
        accountPassword: String = "",
        sourceUrl: String = "",
        loader: suspend () -> Result<List<MediaItem>>
    ) {
        if (loading) return
        if (name.isBlank()) {
            sourceError = "Inserisci un nome per questa lista o account."
            return
        }
        scope.launch {
            loading = true
            sourceError = null
            try {
                loader().fold(
                    onSuccess = { channels ->
                        val profile = PlaylistProfile(
                            id = UUID.randomUUID().toString(),
                            name = name.trim(),
                            sourceType = sourceType,
                            serverUrl = serverUrl,
                            username = accountUsername,
                            password = accountPassword,
                            playlistUrl = sourceUrl,
                            channels = channels
                        )
                        val updatedProfiles = profiles + profile
                        PlaylistProfileStore.save(context.applicationContext, updatedProfiles, profile.id)
                        profiles = updatedProfiles
                        profilesLoaded = true
                        activeProfileId = profile.id
                        playlist = channels
                        authenticated = true
                        showSettingsBeforeLogin = false
                        playing = null
                        playerFullscreen = false
                        playerError = null
                        playback.player.stop()
                        playback.player.clearMediaItems()
                        section = Section.LIVE
                        showSetup = false
                        profileName = ""
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
        if (uri != null) importPlaylist(pendingFileProfileName, PlaylistSourceType.M3U_FILE) {
            runCatching { readLocalM3u(context, uri) }
        }
    }

    fun openSetup() {
        sourceError = null
        profileName = "Account ${profiles.size + 1}"
        showSetup = true
    }

    fun loginWithProfile(profile: PlaylistProfile) {
        if (loading || (authenticated && activeProfileId == profile.id)) return
        scope.launch {
            loading = true
            sourceError = null
            val freshResult = when (profile.sourceType) {
                PlaylistSourceType.XTREAM -> xtream.loadCatalog(XtreamCredentials(profile.serverUrl, profile.username, profile.password))
                PlaylistSourceType.M3U_URL -> m3u.load(profile.playlistUrl)
                PlaylistSourceType.M3U_FILE -> Result.success(profile.channels)
            }
            val freshChannels = freshResult.getOrNull()
            val usedCache = freshChannels == null && profile.channels.isNotEmpty()
            val channels = freshChannels ?: profile.channels
            if (channels.isEmpty()) {
                sourceError = freshResult.exceptionOrNull()?.message ?: "Questa lista non contiene canali."
                loading = false
                return@launch
            }

            val refreshedProfile = profile.copy(channels = channels)
            val updatedProfiles = profiles.map { if (it.id == profile.id) refreshedProfile else it }
            profiles = updatedProfiles
            activeProfileId = profile.id
            playlist = channels
            authenticated = true
            showSettingsBeforeLogin = false
            section = Section.LIVE
            playing = null
            playerFullscreen = false
            playerError = null
            playback.player.stop()
            playback.player.clearMediaItems()
            val saveError = runCatching {
                PlaylistProfileStore.save(context.applicationContext, updatedProfiles, profile.id)
            }.exceptionOrNull()
            sourceError = when {
                usedCache -> "Non riesco ad aggiornare la sorgente; mostro i canali salvati. Verifica rete, proxy o VPN."
                saveError != null -> "Canali caricati, ma non ho potuto salvare la lista aggiornata."
                else -> null
            }
            loading = false
        }
    }

    fun openSettings() {
        if (authenticated) {
            if (section != Section.SETTINGS) lastSection = section
            section = Section.SETTINGS
        } else {
            showSettingsBeforeLogin = true
        }
    }

    fun closeSettings() {
        if (authenticated) section = lastSection else showSettingsBeforeLogin = false
    }

    fun navigateTo(target: Section) {
        if (target != Section.SETTINGS) lastSection = target
        section = target
    }

    fun logout() {
        authenticated = false
        showSettingsBeforeLogin = false
        playerFullscreen = false
        playing = null
        playerError = null
        seriesSelection = null
        playback.player.stop()
        playback.player.clearMediaItems()
        section = Section.LIVE
    }

    fun startPlayback(item: MediaItem) {
        if (playing?.id == item.id) {
            playerFullscreen = true
            return
        }
        playerError = null
        playing = item
        playerFullscreen = false
        playback.play(item)
    }

    fun openItem(item: MediaItem) {
        val seriesId = item.seriesId
        if (seriesId == null || item.streamUrl.isNotBlank()) {
            startPlayback(item)
            return
        }
        val profile = profiles.firstOrNull { it.id == activeProfileId }
        if (profile == null || profile.sourceType != PlaylistSourceType.XTREAM) {
            seriesError = "Gli episodi di questa serie non sono disponibili nella sorgente."
            seriesSelection = item
            return
        }
        seriesSelection = item
        seriesEpisodes = emptyList()
        seriesError = null
        seriesLoading = true
        scope.launch {
            xtream.loadSeriesEpisodes(
                XtreamCredentials(profile.serverUrl, profile.username, profile.password),
                seriesId
            ).fold(
                onSuccess = { seriesEpisodes = it },
                onFailure = { seriesError = it.message ?: "Impossibile caricare gli episodi." }
            )
            seriesLoading = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Ink).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val availableWidth = maxWidth
        val compact = availableWidth < 600.dp
        if (!authenticated) {
            if (showSettingsBeforeLogin) {
                SettingsContent(advanced, { advanced = it }, ::openSetup, ::closeSettings, network)
            } else {
                LoginScreen(
                    profiles = profiles,
                    selectedProfileId = activeProfileId,
                    profilesLoaded = profilesLoaded,
                    loading = loading,
                    error = sourceError,
                    onSelectProfile = { activeProfileId = it.id },
                    onLogin = ::loginWithProfile,
                    onAddAccount = ::openSetup,
                    onSettings = ::openSettings
                )
            }
        } else if (compact) {
            Column(Modifier.fillMaxSize()) {
                AppHeader(true, profiles, activeProfileId, ::loginWithProfile, ::openSetup, ::openSettings, ::logout)
                if (sourceError != null) StatusBanner(sourceError!!, onDismiss = { sourceError = null }, modifier = Modifier.padding(horizontal = 16.dp))
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    CurrentSection(section, playlist, advanced, { advanced = it }, ::openSetup, ::closeSettings, network, ::openItem)
                }
                if (playing != null && !playerFullscreen && section != Section.SETTINGS) CompactPlayerBar(
                    item = playing!!,
                    playback = playback,
                    error = playerError,
                    advanced = advanced,
                    onFullscreen = { playerFullscreen = true },
                    onRetry = { playerError = null; playback.play(playing!!) }
                )
                NavigationBar(containerColor = Color(0xFF0D0F18)) {
                    listOf(Section.LIVE, Section.MOVIES, Section.SERIES).forEach { item ->
                        NavigationBarItem(
                            selected = section == item,
                            onClick = { navigateTo(item) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, fontSize = 9.sp) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = Color(0xFF0D0F18), header = { BrandMark() }) {
                    listOf(Section.LIVE, Section.MOVIES, Section.SERIES).forEach { item ->
                        NavigationRailItem(
                            selected = section == item,
                            onClick = { navigateTo(item) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label, fontSize = 10.sp) },
                            alwaysShowLabel = true
                        )
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    AppHeader(false, profiles, activeProfileId, ::loginWithProfile, ::openSetup, ::openSettings, ::logout)
                    if (sourceError != null) StatusBanner(sourceError!!, onDismiss = { sourceError = null }, modifier = Modifier.padding(horizontal = 22.dp))
                    if (playing != null && !playerFullscreen && section != Section.SETTINGS && availableWidth >= 720.dp) {
                        Row(
                            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(Modifier.weight(0.58f).fillMaxHeight()) {
                                CurrentSection(section, playlist, advanced, { advanced = it }, ::openSetup, ::closeSettings, network, ::openItem)
                            }
                            Box(Modifier.weight(0.42f).fillMaxHeight()) {
                                PlayerPreviewPane(
                                    item = playing!!,
                                    playback = playback,
                                    error = playerError,
                                    advanced = advanced,
                                    compact = false,
                                    onFullscreen = { playerFullscreen = true },
                                    onRetry = { playerError = null; playback.play(playing!!) }
                                )
                            }
                        }
                    } else {
                        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                CurrentSection(section, playlist, advanced, { advanced = it }, ::openSetup, ::closeSettings, network, ::openItem)
                            }
                            if (playing != null && !playerFullscreen && section != Section.SETTINGS) CompactPlayerBar(
                                item = playing!!,
                                playback = playback,
                                error = playerError,
                                advanced = advanced,
                                onFullscreen = { playerFullscreen = true },
                                onRetry = { playerError = null; playback.play(playing!!) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (authenticated && playing != null && playerFullscreen) PlayerDialog(
        item = playing!!,
        playback = playback,
        error = playerError,
        advanced = advanced,
        onAdvanced = { advanced = it },
        onRetry = { playerError = null; playback.play(playing!!) },
        onDismiss = { playerFullscreen = false }
    )

    if (seriesSelection != null) SeriesEpisodesDialog(
        series = seriesSelection!!,
        episodes = seriesEpisodes,
        loading = seriesLoading,
        error = seriesError,
        onPlay = { episode -> seriesSelection = null; startPlayback(episode) },
        onDismiss = { seriesSelection = null }
    )

    if (showSetup) {
        AddSourceDialog(
            profileName = profileName,
            onProfileNameChange = { profileName = it; sourceError = null },
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
            onSaveXtream = { name ->
                importPlaylist(name, PlaylistSourceType.XTREAM, server, username, password) {
                    xtream.loadCatalog(XtreamCredentials(server, username, password))
                }
            },
            onSaveM3u = { name ->
                importPlaylist(name, PlaylistSourceType.M3U_URL, sourceUrl = playlistUrl) { m3u.load(playlistUrl) }
            },
            onPickFile = { name ->
                if (name.isBlank()) sourceError = "Inserisci un nome per questa lista o account."
                else {
                    pendingFileProfileName = name
                    filePicker.launch(arrayOf("*/*"))
                }
            }
        )
    }
}

@Composable
private fun LoginScreen(
    profiles: List<PlaylistProfile>,
    selectedProfileId: String?,
    profilesLoaded: Boolean,
    loading: Boolean,
    error: String?,
    onSelectProfile: (PlaylistProfile) -> Unit,
    onLogin: (PlaylistProfile) -> Unit,
    onAddAccount: () -> Unit,
    onSettings: () -> Unit
) {
    val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId }
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Ink, Panel, Color(0xFF241139))))) {
        Image(
            painter = painterResource(R.drawable.aslwiptv_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.align(Alignment.BottomEnd).sizeIn(maxWidth = maxWidth * 0.8f, maxHeight = maxHeight * 0.55f).alpha(0.055f)
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                Modifier.fillMaxWidth().widthIn(max = 420.dp).height(210.dp),
                color = Color.White,
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 4.dp
            ) {
                Image(
                    painter = painterResource(R.drawable.aslwiptv_logo),
                    contentDescription = "Logo ASLW IPTV",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(12.dp)
                )
            }
            Text("La tua TV, in un unico posto", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Text("Accedi con un account salvato o aggiungi una sorgente Xtream o M3U.", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
            if (!profilesLoaded) {
                CircularProgressIndicator(color = Purple)
                Text("Caricamento account salvati…", color = Muted, fontSize = 12.sp)
            } else if (profiles.isNotEmpty()) {
                ProfilePicker(
                    profiles = profiles,
                    selectedProfileId = selectedProfileId,
                    onSelect = onSelectProfile,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                    labelWhenEmpty = "Scegli un account"
                )
                Button(
                    onClick = { selectedProfile?.let(onLogin) },
                    enabled = selectedProfile != null && !loading,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple)
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (loading) "Accesso in corso…" else "Accedi")
                }
                OutlinedButton(onClick = onAddAccount, enabled = !loading, modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Aggiungi un altro account o lista")
                }
            } else {
                Text("Non hai ancora salvato un account.", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Button(onClick = onAddAccount, enabled = !loading, modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).heightIn(min = 52.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Aggiungi account Xtream o M3U")
                }
            }
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
            TextButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Impostazioni rete e proxy")
            }
            Text("Usa solo sorgenti e contenuti per cui hai l’autorizzazione.", color = Muted, fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ProfilePicker(
    profiles: List<PlaylistProfile>,
    selectedProfileId: String?,
    onSelect: (PlaylistProfile) -> Unit,
    modifier: Modifier = Modifier,
    labelWhenEmpty: String = "Nessuna lista"
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = profiles.firstOrNull { it.id == selectedProfileId }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), enabled = profiles.isNotEmpty()) {
            Icon(Icons.Default.AccountCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(selected?.name ?: labelWhenEmpty, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Seleziona account")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) },
                    trailingIcon = if (profile.id == selectedProfileId) ({ Icon(Icons.Default.Check, contentDescription = "Selezionato") }) else null,
                    onClick = { expanded = false; onSelect(profile) }
                )
            }
        }
    }
}

@Composable
private fun AppHeader(
    compact: Boolean,
    profiles: List<PlaylistProfile>,
    activeProfileId: String?,
    onSelectProfile: (PlaylistProfile) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val padding = if (compact) 16.dp else 22.dp
    if (compact) {
        Column(Modifier.fillMaxWidth().padding(horizontal = padding, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark()
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("ASLW IPTV", fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.sp)
                    Text("STREAMING, SENZA COMPROMESSI", color = Muted, fontSize = 8.sp, letterSpacing = 1.sp)
                }
                IconButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = "Aggiungi account", tint = Purple) }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Impostazioni") }
                IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, contentDescription = "Esci") }
            }
            ProfilePicker(profiles, activeProfileId, onSelectProfile, Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = padding, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark()
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("ASLW IPTV", fontWeight = FontWeight.Black, fontSize = 19.sp, letterSpacing = 1.sp)
                Text("STREAMING, SENZA COMPROMESSI", color = Muted, fontSize = 8.sp, letterSpacing = 1.2.sp)
            }
            ProfilePicker(profiles, activeProfileId, onSelectProfile, Modifier.widthIn(min = 150.dp, max = 220.dp))
            IconButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = "Aggiungi account", tint = Purple) }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Impostazioni") }
            IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, contentDescription = "Esci") }
        }
    }
}

@Composable
private fun BrandMark() {
    Box(Modifier.size(40.dp).background(Color.White, RoundedCornerShape(9.dp)).padding(3.dp)) {
        Image(painterResource(R.drawable.aslwiptv_logo), contentDescription = "Logo ASLW IPTV", contentScale = ContentScale.Fit)
    }
}

@Composable
private fun CurrentSection(
    section: Section,
    playlist: List<MediaItem>,
    advanced: Boolean,
    onAdvanced: (Boolean) -> Unit,
    onSetup: () -> Unit,
    onSettingsBack: () -> Unit,
    network: AppNetwork,
    onPlay: (MediaItem) -> Unit
) {
    when (section) {
        Section.LIVE -> MediaLibrary("Diretta TV", playlist.filter { it.kind == MediaKind.LIVE }, onPlay, onSetup)
        Section.MOVIES -> MediaLibrary("Film", playlist.filter { it.kind == MediaKind.MOVIE }, onPlay, onSetup)
        Section.SERIES -> MediaLibrary("Serie TV", playlist.filter { it.kind == MediaKind.SERIES }, onPlay, onSetup)
        Section.SETTINGS -> SettingsContent(advanced, onAdvanced, onSetup, onSettingsBack, network)
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
        Text("ASLW IPTV è un lettore: aggiungi playlist e flussi per cui possiedi i diritti di accesso.", color = Muted, fontSize = 12.sp)
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
private fun SettingsContent(advanced: Boolean, onAdvanced: (Boolean) -> Unit, onSetup: () -> Unit, onBack: () -> Unit, network: AppNetwork) {
    val scope = rememberCoroutineScope()
    val saved = remember(network) { network.proxySettings() }
    var proxyEnabled by remember(saved) { mutableStateOf(saved.enabled) }
    var proxyType by remember(saved) { mutableStateOf(saved.type) }
    var proxyHost by remember(saved) { mutableStateOf(saved.host) }
    var proxyPort by remember(saved) { mutableStateOf(saved.port.toString()) }
    var proxyUsername by remember(saved) { mutableStateOf(saved.username) }
    var proxyPassword by remember(saved) { mutableStateOf(saved.password) }
    var typeMenu by remember { mutableStateOf(false) }
    var proxyMessage by remember { mutableStateOf<String?>(null) }
    var testingConnection by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Indietro") }
            Text("Impostazioni", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
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
        OutlinedButton(
            onClick = {
                if (testingConnection) return@OutlinedButton
                scope.launch {
                    testingConnection = true
                    proxyMessage = null
                    val result = network.testConnectivity()
                    proxyMessage = result.fold({ it }, { it.message ?: "Verifica della connessione non riuscita." })
                    testingConnection = false
                }
            },
            enabled = !testingConnection
        ) {
            if (testingConnection) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.NetworkCheck, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (testingConnection) "Verifica in corso…" else "Verifica rete e proxy")
        }
        Text("La verifica usa le impostazioni proxy salvate e la rete/VPN Android attiva.", color = Muted, fontSize = 11.sp)
        if (proxyMessage != null) {
            val savedMessage = listOf("salvato", "disattivato", "funzionanti").any { proxyMessage!!.contains(it, ignoreCase = true) }
            Text(proxyMessage!!, color = if (savedMessage) Purple else MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Button(onClick = onSetup) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Gestisci sorgenti") }
        Text("Versione 0.4.1 · ASLW IPTV", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun AddSourceDialog(
    profileName: String,
    onProfileNameChange: (String) -> Unit,
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
    onSaveXtream: (String) -> Unit,
    onSaveM3u: (String) -> Unit,
    onPickFile: (String) -> Unit
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
                OutlinedTextField(
                    value = profileName,
                    onValueChange = onProfileNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome account o lista") },
                    singleLine = true,
                    enabled = !loading
                )
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
                    OutlinedButton(onClick = { onPickFile(profileName) }, modifier = Modifier.fillMaxWidth(), enabled = !loading) {
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
                        onClick = { if (selected == 0) onSaveXtream(profileName) else onSaveM3u(profileName) },
                        enabled = !loading && profileName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Purple)
                    ) { Text(if (loading) "Caricamento…" else "Salva") }
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp).background(Color(0xFF351C2A), RoundedCornerShape(12.dp)).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, color = Color(0xFFFFCDD2), fontSize = 12.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Chiudi messaggio", tint = Color.White) }
    }
}

@Composable
private fun CompactPlayerBar(
    item: MediaItem,
    playback: PlaybackController,
    error: String?,
    advanced: Boolean,
    onFullscreen: () -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        color = Panel,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { player = playback.player; useController = advanced } },
                update = { it.useController = advanced },
                onRelease = { it.player = null },
                modifier = Modifier.width(110.dp).height(64.dp)
            )
            Column(Modifier.weight(1f)) {
                Text("IN RIPRODUZIONE", color = Muted, fontSize = 8.sp, letterSpacing = 1.sp)
                Text(item.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (error != null) IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, contentDescription = "Riprova", tint = Purple) }
            IconButton(onClick = onFullscreen) { Icon(Icons.Default.Fullscreen, contentDescription = "Apri a schermo intero", tint = Purple) }
        }
    }
}

@Composable
private fun SeriesEpisodesDialog(
    series: MediaItem,
    episodes: List<MediaItem>,
    loading: Boolean,
    error: String?,
    onPlay: (MediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    val heightLimit = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().widthIn(max = 620.dp).heightIn(max = heightLimit).padding(16.dp),
            color = Panel,
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Serie TV", color = Muted, fontSize = 10.sp)
                        Text(series.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Chiudi episodi") }
                }
                Spacer(Modifier.height(10.dp))
                when {
                    loading -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        Text("Caricamento episodi…", color = Muted, fontSize = 12.sp)
                    }
                    error != null -> Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    episodes.isEmpty() -> Text("Nessun episodio disponibile.", color = Muted, fontSize = 13.sp)
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(episodes, key = { it.id }) { episode ->
                            Row(
                                Modifier.fillMaxWidth().background(Panel2, RoundedCornerShape(12.dp)).clickable { onPlay(episode) }.focusable().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Purple)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(episode.title, fontWeight = FontWeight.SemiBold)
                                    Text(episode.category, color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerPreviewPane(
    item: MediaItem,
    playback: PlaybackController,
    error: String?,
    advanced: Boolean,
    compact: Boolean,
    onFullscreen: () -> Unit,
    onRetry: () -> Unit
) {
    var isPlaying by remember(playback.player) { mutableStateOf(playback.player.isPlaying) }
    DisposableEffect(playback.player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        playback.player.addListener(listener)
        onDispose { playback.player.removeListener(listener) }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().then(if (compact) Modifier.wrapContentHeight() else Modifier.fillMaxHeight()),
        color = Panel,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("In riproduzione", color = Muted, fontSize = 10.sp)
                    Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onFullscreen) {
                    Icon(Icons.Default.Fullscreen, contentDescription = "Apri a schermo intero", tint = Purple)
                }
            }
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { player = playback.player; useController = advanced } },
                update = { it.useController = advanced },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxWidth().then(
                    if (compact) Modifier.height(180.dp) else Modifier.aspectRatio(16f / 9f)
                )
            )
            if (!advanced && error == null) {
                TextButton(onClick = { if (isPlaying) playback.player.pause() else playback.player.play() }) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (isPlaying) "Pausa" else "Riprendi")
                }
            }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                TextButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Riprova")
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
    var isPlaying by remember(playback.player) { mutableStateOf(playback.player.isPlaying) }
    DisposableEffect(playback.player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        playback.player.addListener(listener)
        onDispose { playback.player.removeListener(listener) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val dialogView = LocalView.current
        val dialogWindow = remember(dialogView) {
            var parent: ViewParent? = dialogView.parent
            var foundWindow: Window? = null
            while (parent != null && foundWindow == null) {
                if (parent is DialogWindowProvider) foundWindow = parent.window
                parent = (parent as? View)?.parent
            }
            foundWindow
        }
        val systemBars = WindowInsetsCompat.Type.systemBars()
        val restoreSystemBars = remember(dialogWindow) {
            dialogWindow?.decorView?.rootWindowInsets?.let { insets ->
                WindowInsetsCompat.toWindowInsetsCompat(insets, dialogWindow.decorView).isVisible(systemBars)
            } ?: true
        }
        DisposableEffect(dialogWindow) {
            val controller = dialogWindow?.let { WindowCompat.getInsetsController(it, it.decorView) }
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(systemBars)
            onDispose {
                if (restoreSystemBars) controller?.show(systemBars)
            }
        }
        Surface(Modifier.fillMaxSize(), color = Color.Black, shape = RoundedCornerShape(0.dp)) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx -> PlayerView(ctx).apply { player = playback.player; useController = advanced } },
                    update = { it.useController = advanced },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize()
                )
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0x99090A10)).padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Esci da schermo intero", tint = Color.White) }
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x99090A10)).padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Controlli avanzati", fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("Seek, velocità e tracce se supportati dallo stream", color = Muted, fontSize = 11.sp)
                    }
                    Switch(checked = advanced, onCheckedChange = onAdvanced)
                    if (!advanced) {
                        IconButton(onClick = { if (isPlaying) playback.player.pause() else playback.player.play() }) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "Pausa" else "Riprendi", tint = Color.White)
                        }
                    }
                    if (error != null) {
                        IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, contentDescription = "Riprova", tint = Color.White) }
                    }
                }
                if (error != null) {
                    Text(
                        error,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp).background(Color(0xCC090A10), RoundedCornerShape(12.dp)).padding(16.dp)
                    )
                }
            }
        }
    }
}

private fun playbackErrorMessage(error: PlaybackException): String {
    val causes = generateSequence(error.cause) { it.cause }.take(12).toList()
    val httpError = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
    val message = when {
        httpError?.responseCode == 401 || httpError?.responseCode == 403 ->
            "Il provider ha negato l’accesso al flusso (HTTP ${httpError.responseCode}). Verifica account, indirizzo e limiti del provider."
        httpError?.responseCode == 404 -> "Il flusso non esiste più sul server (HTTP 404). Aggiorna la playlist."
        httpError != null -> "Il server ha risposto con HTTP ${httpError.responseCode}."
        causes.any { it is UnknownHostException } -> "Non riesco a trovare il server del canale. Controlla l’indirizzo e la connessione DNS."
        causes.any { it is ConnectException } -> "Il server del canale rifiuta la connessione. Controlla rete, proxy e porta del provider."
        causes.any { it is SocketTimeoutException } -> "Il server non ha risposto in tempo. Riprova o verifica la rete e il proxy."
        causes.any { it is SSLException } -> "La connessione HTTPS non è riuscita. Verifica il certificato o usa l’indirizzo HTTPS fornito dal provider."
        else -> "Connessione al flusso non riuscita (${error.errorCodeName.replace('_', ' ').lowercase()})."
    }
    val causeType = causes.firstOrNull()?.javaClass?.simpleName?.takeIf { it.isNotBlank() }
    val diagnostic = causeType?.let { " Dettaglio: $it." }.orEmpty()
    return "$message$diagnostic Controlla URL, proxy e VPN; alcune playlist richiedono header Referer o User-Agent."
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


