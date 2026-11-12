package com.aslwiptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.aslwiptv.data.MediaItem
import com.aslwiptv.data.MediaKind
import com.aslwiptv.player.PlaybackController

private val Ink = Color(0xFF090A10)
private val Panel = Color(0xFF141622)
private val Panel2 = Color(0xFF1B1E2D)
private val Purple = Color(0xFFA78BFA)
private val Blue = Color(0xFF60A5FA)
private val Muted = Color(0xFF969AAF)

class MainActivity : ComponentActivity() {
    private lateinit var playback: PlaybackController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playback = PlaybackController(this)
        setContent { AslwTheme { AslwApp(playback) } }
    }
    override fun onStop() { super.onStop(); playback.player.pause() }
    override fun onDestroy() { playback.release(); super.onDestroy() }
}

@Composable
private fun AslwTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Purple, secondary = Blue, background = Ink, surface = Panel,
        onPrimary = Color(0xFF171020), onBackground = Color.White, onSurface = Color.White
    ), content = content)
}

private enum class Section(val label: String) { HOME("Home"), LIVE("Live TV"), MOVIES("Film"), SERIES("Serie"), GUIDE("EPG"), SETTINGS("Impostazioni") }

@Composable
private fun AslwApp(playback: PlaybackController) {
    var section by remember { mutableStateOf(Section.HOME) }
    var playing by remember { mutableStateOf<MediaItem?>(null) }
    var advanced by remember { mutableStateOf(true) }
    var showSetup by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var playlist by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    Row(Modifier.fillMaxSize().background(Ink)) {
        NavigationRail(containerColor = Color(0xFF0D0F18), header = {
            Box(Modifier.padding(vertical = 22.dp).size(48.dp).background(Brush.linearGradient(listOf(Purple, Blue)), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                Text("A", fontSize = 27.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
        }) {
            Section.entries.forEach { item ->
                NavigationRailItem(selected = section == item, onClick = { section = item }, icon = {
                    Icon(when (item) {
                        Section.HOME -> Icons.Default.Home; Section.LIVE -> Icons.Default.Tv; Section.MOVIES -> Icons.Default.Movie
                        Section.SERIES -> Icons.Default.VideoLibrary; Section.GUIDE -> Icons.Default.Schedule; Section.SETTINGS -> Icons.Default.Settings
                    }, contentDescription = item.label)
                }, label = { Text(item.label, fontSize = 10.sp) }, alwaysShowLabel = false)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 22.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("ASLWIPTV", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 1.5.sp)
                    Text("STREAMING, SENZA COMPROMESSI", color = Muted, fontSize = 9.sp, letterSpacing = 1.7.sp)
                }
                TextButton(onClick = { showSetup = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Aggiungi playlist") }
            }
            Spacer(Modifier.height(20.dp))
            when (section) {
                Section.HOME -> HomeContent(onLive = { section = Section.LIVE }, onMovies = { section = Section.MOVIES }, onSetup = { showSetup = true }, count = playlist.size)
                Section.LIVE -> MediaLibrary("Live TV", playlist.filter { it.kind == MediaKind.LIVE }, onPlay = { playing = it; playback.play(it) })
                Section.MOVIES -> MediaLibrary("Film", playlist.filter { it.kind == MediaKind.MOVIE }, onPlay = { playing = it; playback.play(it) })
                Section.SERIES -> MediaLibrary("Serie", playlist.filter { it.kind == MediaKind.SERIES }, onPlay = { playing = it; playback.play(it) })
                Section.GUIDE -> EmptyState("Guida EPG", "La guida programmi sarà collegata alla sorgente EPG della playlist.", Icons.Default.Schedule)
                Section.SETTINGS -> SettingsContent(advanced, { advanced = it }, { showSetup = true })
            }
        }
    }

    if (playing != null) {
        AlertDialog(onDismissRequest = { playing = null; playback.player.pause() }, containerColor = Panel,
            title = { Text(playing?.title ?: "Riproduzione") }, text = {
                Column(Modifier.fillMaxWidth()) {
                    AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = playback.player; useController = true } }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Modalità controlli", modifier = Modifier.weight(1f), color = Muted)
                        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Avanzata" else "Integrata") }
                    }
                    if (advanced) Text("Controlli Media3: seek, velocità, tracce e sottotitoli se disponibili nello stream.", color = Muted, fontSize = 12.sp)
                }
            }, confirmButton = { TextButton(onClick = { playing = null; playback.player.pause() }) { Text("Chiudi") } })
    }

    if (showSetup) {
        AlertDialog(onDismissRequest = { showSetup = false }, containerColor = Panel,
            title = { Text("Aggiungi sorgente") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Xtream Codes", color = Purple, fontWeight = FontWeight.Bold)
                    OutlinedTextField(server, { server = it }, label = { Text("URL server HTTPS") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    Text("L'importazione M3U da file o URL sarà completata nel prossimo incremento.", color = Muted, fontSize = 12.sp)
                }
            }, confirmButton = { TextButton(onClick = { showSetup = false }) { Text("Salva (prossima fase)") } }, dismissButton = { TextButton(onClick = { showSetup = false }) { Text("Annulla") } })
    }
}

@Composable
private fun HomeContent(onLive: () -> Unit, onMovies: () -> Unit, onSetup: () -> Unit, count: Int) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.fillMaxWidth().height(190.dp).background(Brush.linearGradient(listOf(Color(0xFF292044), Color(0xFF172A46), Panel)), RoundedCornerShape(24.dp)).padding(22.dp)) {
            Column(Modifier.align(Alignment.CenterStart)) {
                Text("IL TUO INTRATTENIMENTO", color = Purple, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp)); Text("Tutto quello che\nvuoi guardare.", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 32.sp)
                Spacer(Modifier.height(14.dp)); Button(onClick = onSetup, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Configura sorgente") }
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
private fun MediaLibrary(title: String, items: List<MediaItem>, onPlay: (MediaItem) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(14.dp))
        if (items.isEmpty()) EmptyState(title, "Nessun contenuto caricato. Aggiungi una playlist per iniziare.", Icons.Default.PlayCircle)
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
private fun SettingsContent(advanced: Boolean, onAdvanced: (Boolean) -> Unit, onSetup: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Impostazioni", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Controlli avanzati", fontWeight = FontWeight.SemiBold); Text("Mostra le opzioni aggiuntive del player", color = Muted, fontSize = 11.sp) }
            Switch(checked = advanced, onCheckedChange = onAdvanced)
        }
        Button(onClick = onSetup) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Gestisci sorgenti") }
        Text("Versione 0.1.0 · ASLWIPTV", color = Muted, fontSize = 11.sp)
    }
}
