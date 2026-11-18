package com.streambox.tvonline

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null
    private var onStepChannelCallback: ((Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                StreamBoxScreen(
                    onPlayerReady = { exoPlayer = it },
                    onRegisterChannelStepper = { onStepChannelCallback = it },
                    onEnterPip = { enterPip() }
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_DPAD_UP -> {
                onStepChannelCallback?.invoke(-1)
                true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_DPAD_DOWN -> {
                onStepChannelCallback?.invoke(1)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
    }
}

@Composable
fun StreamBoxScreen(
    onPlayerReady: (ExoPlayer) -> Unit,
    onRegisterChannelStepper: ((Int) -> Unit) -> Unit,
    onEnterPip: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var channels by remember {
        mutableStateOf(
            listOf(
                TvChannel(
                    "1", 1, "Первый Информационный 24", "🇷🇺 Россия",
                    "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                    listOf(EpgProgram("14:00 - 15:00", "Главные новости часа", "Прямой эфир информационной службы", 65))
                ),
                TvChannel(
                    "2", 2, "Red Bull TV HD Live", "🌍 Спорт",
                    "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8",
                    listOf(EpgProgram("LIVE", "Мировая серия: Прямая трансляция", "Международный спортивный эфир", 50))
                ),
                TvChannel(
                    "3", 3, "Qazaqstan / Khabar 24", "🇰🇿 Казахстан",
                    "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
                    listOf(EpgProgram("LIVE", "Новости Казахстана и мира", "Прямое вещание из Астаны", 75))
                ),
                TvChannel(
                    "4", 4, "Discovery & Space HD", "🇺🇸 США",
                    "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
                    listOf(EpgProgram("14:00 - 15:00", "Как устроена Вселенная", "Документальный цикл", 40))
                )
            )
        )
    }

    var currentChannel by remember { mutableStateOf(channels.first()) }
    var selectedCategory by remember { mutableStateOf("Все") }
    var searchQuery by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(setOf<String>()) }
    var showCountryDialog by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Готов к просмотру") }

    val countryList = remember {
        listOf(
            "ru" to "🇷🇺 Россия",
            "kz" to "🇰🇿 Казахстан",
            "by" to "🇧🇾 Беларусь",
            "ua" to "🇺🇦 Украина",
            "uz" to "🇺🇿 Узбекистан",
            "am" to "🇦🇲 Армения",
            "ge" to "🇬🇪 Грузия",
            "az" to "🇦🇿 Азербайджан",
            "tr" to "🇹🇷 Турция",
            "de" to "🇩🇪 Германия",
            "us" to "🇺🇸 США",
            "uk" to "🇬🇧 Британия",
            "fr" to "🇫🇷 Франция",
            "it" to "🇮🇹 Италия",
            "es" to "🇪🇸 Испания",
            "jp" to "🇯🇵 Япония",
            "kr" to "🇰🇷 Южная Корея"
        )
    }

    val categories = remember(channels) {
        listOf("Все", "Избранное") + channels.map { it.category }.distinct()
    }

    val filteredChannels = remember(channels, selectedCategory, searchQuery, favorites) {
        channels.filter { ch ->
            val matchCat = when (selectedCategory) {
                "Все" -> true
                "Избранное" -> favorites.contains(ch.id)
                else -> ch.category == selectedCategory
            }
            val matchQuery = searchQuery.isBlank() ||
                ch.name.contains(searchQuery, ignoreCase = true) ||
                (ch.epg.firstOrNull()?.title?.contains(searchQuery, ignoreCase = true) == true)
            matchCat && matchQuery
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            onPlayerReady(this)
        }
    }

    LaunchedEffect(Unit) {
        onRegisterChannelStepper { delta ->
            if (filteredChannels.isNotEmpty()) {
                val idx = filteredChannels.indexOfFirst { it.id == currentChannel.id }.coerceAtLeast(0)
                val nextIdx = (idx + delta + filteredChannels.size) % filteredChannels.size
                currentChannel = filteredChannels[nextIdx]
            }
        }
    }

    LaunchedEffect(currentChannel) {
        val item = MediaItem.Builder()
            .setUri(currentChannel.streamUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
        exoPlayer.setMediaItem(item)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun loadCountryChannels(isoCode: String, countryTitle: String) {
        showCountryDialog = false
        statusText = "Загрузка каналов: $countryTitle..."
        scope.launch {
            val url = "https://iptv-org.github.io/iptv/countries/${isoCode.lowercase()}.m3u"
            val loaded = withContext(Dispatchers.IO) {
                try {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    val raw = conn.inputStream.bufferedReader().use { it.readText() }
                    M3uPlaylistParser.parse(raw).take(120).map { it.copy(category = countryTitle) }
                } catch (e: Exception) {
                    emptyList()
                }
            }
            if (loaded.isNotEmpty()) {
                channels = loaded + channels
                selectedCategory = countryTitle
                currentChannel = loaded.first()
                statusText = "Загружено каналов ($countryTitle): ${loaded.size}"
            } else {
                statusText = "Не удалось скачать список $countryTitle — проверьте интернет"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CH ${currentChannel.number} • ${currentChannel.name}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = statusText,
                    color = Color(0xFF38BDF8),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showCountryDialog = true }) {
                    Text("🌍 Страны")
                }
                Button(onClick = onEnterPip) {
                    Text("PiP")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Поиск канала или передачи...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredChannels, key = { it.id }) { ch ->
                val isSelected = ch.id == currentChannel.id
                val isFav = favorites.contains(ch.id)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { currentChannel = ch },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFF0369A1) else Color(0xFF1E293B)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "#${ch.number} ${ch.name}",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${ch.category} • ${ch.epg.firstOrNull()?.title ?: "Прямой эфир"}",
                                color = Color(0xFFCBD5E1),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(
                            onClick = {
                                favorites = if (isFav) favorites - ch.id else favorites + ch.id
                            }
                        ) {
                            Text(if (isFav) "★" else "☆", color = Color(0xFFFBBF24))
                        }
                    }
                }
            }
        }
    }

    if (showCountryDialog) {
        AlertDialog(
            onDismissRequest = { showCountryDialog = false },
            title = { Text("🌍 Выберите страну (IPTV-ORG)") },
            text = {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(countryList) { (code, title) ->
                        TextButton(
                            onClick = { loadCountryChannels(code, title) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$title (${code.uppercase()})", color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCountryDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }
}
