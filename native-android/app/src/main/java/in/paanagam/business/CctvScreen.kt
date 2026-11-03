package `in`.paanagam.business

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import `in`.paanagam.business.data.CctvAuditEvent
import `in`.paanagam.business.data.PaanagamDatabase
import `in`.paanagam.business.data.StaffMember
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val CctvInk = Color(0xFF2B1B12)
private val CctvGold = Color(0xFFB87928)
private val CctvPanel = Color(0xFF3A2519)

@Composable
fun CctvScreen(db: PaanagamDatabase, currentStaff: StaffMember) {
    val context = LocalContext.current
    val events by db.dao().cctvAuditEvents().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("/stream1") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Camera not connected") }
    var showSettings by remember { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    val player = remember { ExoPlayer.Builder(context).build() }

    LaunchedEffect(Unit) {
        val saved = runCatching { CctvSecureConfig.load(context) }.getOrNull()
        if (saved != null) {
            host = saved.getOrNull(0).orEmpty()
            path = saved.getOrNull(1).orEmpty().ifBlank { "/stream1" }
            username = saved.getOrNull(2).orEmpty()
            password = saved.getOrNull(3).orEmpty()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                status = when (playbackState) {
                    Player.STATE_BUFFERING -> "Connecting to camera…"
                    Player.STATE_READY -> "Live camera connected"
                    Player.STATE_ENDED -> "Camera stream ended"
                    else -> status
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                status = "Stream error: check camera IP, credentials and RTSP path."
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    fun connectCamera() {
        val cleanHost = host.trim()
        val cleanPath = path.trim().let { if (it.startsWith("/")) it else "/$it" }
        if (cleanHost.isBlank() || cleanPath.isBlank()) {
            status = "Enter the camera IP address and RTSP stream path."
            return
        }
        runCatching {
            CctvSecureConfig.save(context, listOf(cleanHost, cleanPath, username, password).joinToString("\n"))
            val auth = if (username.isNotBlank()) "${android.net.Uri.encode(username)}:${android.net.Uri.encode(password)}@" else ""
            val uri = "rtsp://${auth}$cleanHost:554$cleanPath"
            player.setMediaSource(RtspMediaSource.Factory().createMediaSource(MediaItem.fromUri(uri)))
            player.prepare()
            player.playWhenReady = true
            showSettings = false
            status = "Connecting to camera…"
            zoom = 1f
            panX = 0f
            panY = 0f
        }.onFailure { status = it.message ?: "Could not start camera." }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("360° Camera & POS Audit", color = CctvInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Administrator controls • local network camera", color = Color(0xFF718078), style = MaterialTheme.typography.labelSmall)
            }
            Icon(Icons.Default.Security, contentDescription = "Admin camera security", tint = CctvGold)
        }

        Card(colors = CardDefaults.cardColors(containerColor = CctvPanel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Live camera", color = Color.White, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showSettings = !showSettings }) { Text(if (showSettings) "Hide settings" else "Camera settings", color = CctvGold) }
                }
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 210.dp, max = 340.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = panX
                            translationY = panY
                        }.pointerInput(Unit) {
                            detectTransformGestures { _, pan, scale, _ ->
                                zoom = (zoom * scale).coerceIn(1f, 5f)
                                panX = (panX + pan.x).coerceIn(-size.width.toFloat() * 2f, size.width.toFloat() * 2f)
                                panY = (panY + pan.y).coerceIn(-size.height.toFloat() * 2f, size.height.toFloat() * 2f)
                            }
                        },
                        factory = { viewContext ->
                            PlayerView(viewContext).apply {
                                this.player = player
                                useController = false
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            }
                        },
                        update = { it.player = player }
                    )
                    if (player.mediaItemCount == 0) {
                        Text("Connect your 360° IP camera to view the live feed", color = Color.White)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(status, color = Color(0xFFFFE5B5), style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { zoom = (zoom / 1.25f).coerceAtLeast(1f); if (zoom == 1f) { panX = 0f; panY = 0f } }) { Text("−", color = CctvGold) }
                        Text("${(zoom * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = { zoom = (zoom * 1.25f).coerceAtMost(5f) }) { Text("+", color = CctvGold) }
                        IconButton(onClick = { zoom = 1f; panX = 0f; panY = 0f }) { Icon(Icons.Default.Refresh, contentDescription = "Reset zoom", tint = CctvGold) }
                    }
                }
                Text("Pinch or drag to digitally zoom and pan. Fisheye dewarping and optical/PTZ zoom depend on the camera model.", color = Color(0xFFD8C5A8), style = MaterialTheme.typography.bodySmall)
            }
        }

        if (showSettings) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Camera connection", color = CctvInk, fontWeight = FontWeight.Bold)
                    OutlinedTextField(host, { host = it }, label = { Text("Camera IP address") }, placeholder = { Text("192.168.1.100") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(path, { path = it }, label = { Text("RTSP stream path") }, placeholder = { Text("/stream1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(username, { username = it }, label = { Text("Camera username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text("Camera password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { connectCamera() }, modifier = Modifier.fillMaxWidth()) { Text("Connect camera") }
                    Text("Camera credentials are encrypted with an Android Keystore AES-GCM key on this device.", color = Color(0xFF718078), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Text("Cancellation / refund evidence bookmarks", color = CctvInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("A local audit bookmark is created automatically when an administrator or cashier records a completed-sale refund. It stores the transaction and exact camera time for footage review.", color = Color(0xFF718078), style = MaterialTheme.typography.bodySmall)
        if (events.isEmpty()) {
            Text("No cancellation or refund events yet.", color = Color(0xFF718078))
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events, key = { it.id }) { event ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(event.eventType.replace("_", " "), color = CctvInk, fontWeight = FontWeight.Bold)
                                Text("₹%.2f".format(event.amountPaise / 100.0), color = CctvGold, fontWeight = FontWeight.Bold)
                            }
                            Text("Bill ${event.saleId.take(8).uppercase(Locale.ROOT)} • ${event.cashierName}", color = CctvInk, style = MaterialTheme.typography.bodySmall)
                            Text(SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()).format(Date(event.createdAt)), color = Color(0xFF718078), style = MaterialTheme.typography.labelSmall)
                            Text("Reason: ${event.reason}", color = CctvInk, style = MaterialTheme.typography.bodySmall)
                            Text("Camera bookmark: ${event.cameraBookmarkStatus}", color = Color(0xFF718078), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private object CctvSecureConfig {
    private const val PREFS = "paanagam_cctv_secure"
    private const val KEY = "camera_config"
    private const val ALIAS = "paanagam_cctv_config_aes"

    fun save(context: Context, plain: String) {
        val key = getKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    fun load(context: Context): List<String>? {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > 12) { "Saved camera configuration is invalid." }
        val iv = payload.copyOfRange(0, 12)
        val ciphertext = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8).split("\n", limit = 4)
    }

    private fun getKey(): SecretKey {
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
