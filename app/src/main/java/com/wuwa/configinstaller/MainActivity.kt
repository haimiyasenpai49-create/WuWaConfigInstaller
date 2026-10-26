package com.wuwa.configinstaller

import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

// ====== SESUAIKAN ======
const val GAME_PKG = "com.kurogame.wutheringwaves.global"
// Verifikasi path ini di perangkatmu (folder Saved/Config/Android)
val GAME_PKGS = listOf(GAME_PKG, "com.kurogame.mingchao", "com.tencent.tmgp.kurogame.mingchao")
const val CONFIG_SUB = "files/UE4Game/Client/Client/Saved/Config/Android"
const val CONFIG_FILE = "Engine.ini"
const val DISCORD = "https://discord.gg/EFaCxujTcC"
// =======================

private val BG = Color(0xFF0B1220); private val CARD = Color(0xE6111A2B)
private val STROKE = Color(0xFF22304A); private var BLUE by mutableStateOf(Color(0xFF4F7BF7))
private val SUB = Color(0xFF8DA2C8); private val GREEN = Color(0xFF22C493)

/** Sisipkan r.ScreenPercentage ke [SystemSettings] (atau buat section baru). */
fun injectScale(ini: String, pct: Int): String =
    if (ini.contains("[SystemSettings]"))
        ini.replaceFirst("[SystemSettings]", "[SystemSettings]\nr.ScreenPercentage=$pct")
    else ini + "\n[SystemSettings]\nr.ScreenPercentage=$pct\n"

fun loadWall(ctx: android.content.Context): ImageBitmap? {
    val f = File(ctx.filesDir, "wallpaper.jpg")
    if (!f.exists()) return null
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var n = 1
    while (maxOf(o.outWidth, o.outHeight) / n > 1600) n *= 2
    return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = n })?.asImageBitmap()
}

fun ramGib(ctx: android.content.Context): Double {
    val mi = android.app.ActivityManager.MemoryInfo()
    (ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(mi)
    return mi.totalMem / 1073741824.0
}

object Sh {
    /** Jalankan perintah shell lewat Shizuku (newProcess bersifat private -> reflection). */
    fun run(cmd: String): Pair<Int, String> {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText() + p.errorStream.bufferedReader().readText()
        return p.waitFor() to out
    }
    fun state(): Int = when {
        !Shizuku.pingBinder() -> 0                                   // belum jalan
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> 2 // siap
        else -> 1                                                    // perlu izin
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        BLUE = Color(getSharedPreferences("theme", 0).getLong("accent", 0xFF4F7BF7))
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App() } }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf(if (ramGib(ctx) >= 10.0) "flagship" else "balanced") }
    var quality by remember { mutableStateOf(mapOf("flagship" to "high", "balanced" to "high")) }
    var scale by remember { mutableFloatStateOf(85f) }
    var st by remember { mutableIntStateOf(Sh.state()) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val prefs = remember { ctx.getSharedPreferences("theme", 0) }
    var wall by remember { mutableStateOf(loadWall(ctx)) }
    var dim by remember { mutableFloatStateOf(prefs.getFloat("dim", 0.55f)) }
    var showTheme by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) scope.launch {
            val b = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openInputStream(u)?.use { i ->
                        File(ctx.filesDir, "wallpaper.jpg").outputStream().use { o -> i.copyTo(o) } }
                    loadWall(ctx)
                }.getOrNull()
            }
            if (b != null) wall = b
        }
    }

    DisposableEffect(Unit) {
        val l1 = Shizuku.OnBinderReceivedListener { st = Sh.state() }
        val l2 = Shizuku.OnBinderDeadListener { st = Sh.state() }
        val l3 = Shizuku.OnRequestPermissionResultListener { _, _ -> st = Sh.state() }
        Shizuku.addBinderReceivedListenerSticky(l1); Shizuku.addBinderDeadListener(l2)
        Shizuku.addRequestPermissionResultListener(l3)
        onDispose {
            Shizuku.removeBinderReceivedListener(l1); Shizuku.removeBinderDeadListener(l2)
            Shizuku.removeRequestPermissionResultListener(l3)
        }
    }

    fun install() = scope.launch {
        busy = true
        val dir = "${profile}_${quality[profile]}"
        msg = withContext(Dispatchers.IO) {
            runCatching {
                val files = ctx.assets.list(dir).orEmpty()
                if (files.isEmpty()) return@runCatching "Config $dir belum tersedia."
                val (_, found) = Sh.run("for p in ${GAME_PKGS.joinToString(" ")}; do [ -d /sdcard/Android/data/\$p ] && echo /sdcard/Android/data/\$p/$CONFIG_SUB && break; done")
                val CONFIG_DIR = found.trim().lineSequence().firstOrNull().orEmpty()
                if (CONFIG_DIR.isEmpty()) return@runCatching "Game Wuthering Waves tidak ditemukan. Jalankan game sekali dulu."
                val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                val cmd = StringBuilder("mkdir -p '$CONFIG_DIR'")
                for (name in files) {
                    var bytes = ctx.assets.open("$dir/$name").readBytes()
                    if (name == "Engine.ini") bytes = injectScale(String(bytes), scale.toInt()).toByteArray()
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val f = "$CONFIG_DIR/$name"
                    cmd.append(" && ( [ -f '$f' ] && cp '$f' '$f.bak.$ts' || true )")
                    cmd.append(" && echo '$b64' | base64 -d > '$f'")
                }
                val (code, out) = Sh.run(cmd.toString())
                if (code == 0) "✓ ${files.size} file config terpasang (backup dibuat)" else "Gagal: $out"
            }.getOrElse { "Error: ${it.message}" }
        }
        busy = false
    }

    var auto by remember { mutableStateOf(prefs.getBoolean("auto", true)) }
    var ran by remember { mutableStateOf(false) }
    LaunchedEffect(st) {
        if (st == 1) Shizuku.requestPermission(100)
        if (st == 2 && auto && !ran) { ran = true; install() }
    }

    Box(Modifier.fillMaxSize().background(BG)) {
    wall?.let {
        Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(16.dp).statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(Icons.Default.SportsEsports, 56)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("WuWa Config\nInstaller", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("CONFIG MANAGER  •  ANDROID", fontSize = 11.sp, color = BLUE)
            }
            Pill("Shizuku")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pilih profil config", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = { showTheme = true }) { Icon(Icons.Default.Palette, "Tampilan", tint = BLUE) }
        }

        ProfileCard("flagship", "Flagship", "Config visual stabil", Icons.Default.Monitor,
            "Menggunakan config grafik flagship dengan kualitas bagus & stabil.",
            profile == "flagship", quality["flagship"]!!,
            { profile = "flagship" }) { quality = quality + ("flagship" to it) }
        ProfileCard("balanced", "Balanced", "Config balanced stabil", Icons.Default.Balance,
            "Menyeimbangkan kualitas visual dan kestabilan FPS.",
            profile == "balanced", quality["balanced"]!!,
            { profile = "balanced" }) { quality = quality + ("balanced" to it) }

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CARD)
            .border1().padding(16.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AspectRatio, null, tint = BLUE)
                    Spacer(Modifier.width(10.dp))
                    Text("Downscale Resolution", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Text("${scale.toInt()}%", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Slider(scale, { scale = it }, valueRange = 50f..100f, steps = 9,
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFBFD0FF), activeTrackColor = BLUE))
            }
        }

        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CARD).border1().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VerifiedUser, null, tint = BLUE, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Status Shizuku", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(if (st == 2) "Siap memasang config." else "Hubungkan Shizuku untuk mengaktifkan pemasangan config.",
                    color = SUB, fontSize = 13.sp)
            }
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (st == 2) GREEN else Color(0xFFE5A23C)))
            Spacer(Modifier.width(6.dp))
            Text(when (st) { 2 -> "Terhubung"; 1 -> "Perlu izin"; else -> "Belum terhubung" }, color = SUB, fontSize = 12.sp)
        }

        Button(
            onClick = {
                when (st) {
                    0 -> msg = "Buka aplikasi Shizuku dan jalankan service-nya dulu."
                    1 -> Shizuku.requestPermission(100)
                    else -> install()
                }
            },
            enabled = !busy, modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = BLUE)
        ) {
            Icon(Icons.Default.Settings, null); Spacer(Modifier.width(10.dp))
            Text(if (busy) "Memasang..." else "Pasang Config", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        if (msg.isNotEmpty()) Text(msg, color = SUB, fontSize = 13.sp)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pasang otomatis saat dibuka", color = Color.White, modifier = Modifier.weight(1f))
            Switch(auto, { auto = it; prefs.edit().putBoolean("auto", it).apply() })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Backup otomatis", color = Color.White, modifier = Modifier.weight(1f))
            Text("Sebelum pemasangan", color = BLUE)
        }

        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CARD).border1()
            .clickable { uri.openUri(DISCORD) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBox(Icons.Default.Forum, 48); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Discord Repository", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("discord.gg/EFaCxujTcC", color = BLUE, fontSize = 14.sp)
                Text("Join server kami untuk update config, bantuan, dan informasi terbaru.", color = SUB, fontSize = 12.sp)
            }
            Icon(Icons.Default.OpenInNew, null, tint = Color.White)
        }
        Spacer(Modifier.height(16.dp))
    }
    }

    if (showTheme) AlertDialog(
        onDismissRequest = { showTheme = false },
        containerColor = Color(0xFF111A2B),
        title = { Text("Tampilan", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Warna aksen", color = SUB)
                val colors = listOf(0xFF4F7BF7, 0xFF8B7CF6, 0xFF22C493, 0xFFF7A24F,
                    0xFFF76F8E, 0xFF38BDF8, 0xFFEF4444, 0xFFEAB308)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in colors.take(4)) Swatch(c) { BLUE = Color(c); prefs.edit().putLong("accent", c).apply() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in colors.drop(4)) Swatch(c) { BLUE = Color(c); prefs.edit().putLong("accent", c).apply() }
                }
                Text("Wallpaper", color = SUB)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pick.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = BLUE)) { Text("Pilih gambar") }
                    if (wall != null) OutlinedButton(onClick = {
                        File(ctx.filesDir, "wallpaper.jpg").delete(); wall = null
                    }) { Text("Hapus") }
                }
                if (wall != null) {
                    Text("Gelapkan wallpaper: ${(dim * 100).toInt()}%", color = SUB, fontSize = 13.sp)
                    Slider(dim, { dim = it; prefs.edit().putFloat("dim", it).apply() }, valueRange = 0f..0.9f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFBFD0FF), activeTrackColor = BLUE))
                }
            }
        },
        confirmButton = { TextButton(onClick = { showTheme = false }) { Text("Selesai", color = BLUE) } }
    )
    }

@Composable
fun Swatch(c: Long, onClick: () -> Unit) =
    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(c))
        .border(if (BLUE == Color(c)) 3.dp else 1.dp, if (BLUE == Color(c)) Color.White else STROKE, CircleShape)
        .clickable { onClick() })

@Composable
fun ProfileCard(id: String, title: String, sub: String, icon: ImageVector, desc: String,
                selected: Boolean, q: String, onSelect: () -> Unit, onQ: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CARD)
        .border(1.dp, if (selected) BLUE else STROKE, RoundedCornerShape(16.dp))
        .clickable { onSelect() }.padding(14.dp)) {
        IconBox(icon, 56); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = SUB, fontSize = 14.sp)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in listOf("high", "medium")) {
                    val on = q == k
                    Text(k.replaceFirstChar { it.uppercase() }, color = if (on) Color.White else SUB,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) BLUE else CARD)
                            .border(1.dp, if (on) BLUE else STROKE, RoundedCornerShape(50))
                            .clickable { onSelect(); onQ(k) }.padding(horizontal = 18.dp, vertical = 8.dp))
                }
            }
            Text(desc, color = SUB, fontSize = 13.sp)
        }
        RadioButton(selected, onSelect, colors = RadioButtonDefaults.colors(selectedColor = BLUE, unselectedColor = STROKE))
    }
}

@Composable fun IconBox(i: ImageVector, size: Int) =
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF16213A)), Alignment.Center) {
        Icon(i, null, tint = Color(0xFF8B7CF6), modifier = Modifier.size((size * 0.55).dp)) }

@Composable fun Pill(t: String) =
    Row(Modifier.clip(RoundedCornerShape(50)).background(CARD).border(1.dp, STROKE, RoundedCornerShape(50))
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(BLUE)); Spacer(Modifier.width(8.dp))
        Text(t, color = Color.White) }

private fun Modifier.border1() = this.then(Modifier.border(BorderStroke(1.dp, STROKE), RoundedCornerShape(16.dp)))
