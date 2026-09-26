package ai.deepcode.android.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.ui.components.*
import ai.deepcode.android.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.io.FileOutputStream

data class WallpaperOption(
    val id: String,
    val title: String,
    val description: String,
    val gradientColors: List<Color>
)

val PresetWallpapers = listOf(
    WallpaperOption("default", "Pitch Dark", "Solid dark theme background", listOf(Color(0xFF0D0D12), Color(0xFF0D0D12))),
    WallpaperOption("space", "Deep Space", "Subtle deep purple cosmos", listOf(Color(0xFF161033), Color(0xFF0D0A1C), Color(0xFF05040B))),
    WallpaperOption("nebula", "Midnight Nebula", "Vibrant ambient aura glow", listOf(Color(0xFF26123D), Color(0xFF120E29), Color(0xFF080714))),
    WallpaperOption("amoled", "AMOLED Black", "Pure black power saver canvas", listOf(Color(0xFF000000), Color(0xFF000000))),
    WallpaperOption("graphite", "Graphite", "Minimalist sleek charcoal mesh", listOf(Color(0xFF1C1E2B), Color(0xFF11121C), Color(0xFF090A10))),
    WallpaperOption("aurora", "Aurora", "Ethereal emerald & teal glow", listOf(Color(0xFF0A261E), Color(0xFF071410), Color(0xFF040A08))),
    WallpaperOption("sunset", "Sunset", "Deep twilight crimson & violet", listOf(Color(0xFF2D112B), Color(0xFF1A0B1E), Color(0xFF0C0512))),
    WallpaperOption("frost", "Frost", "Cool arctic slate & navy", listOf(Color(0xFF122332), Color(0xFF0C1722), Color(0xFF060B12)))
)

@Composable
fun ThemesAndWallpapersScreen(
    repository: DeepCodeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val securePrefs = repository.securePrefs

    var themeMode by remember { mutableStateOf(securePrefs.getSetting("theme", "system")) }
    var activeAccentId by remember { mutableStateOf(securePrefs.getSetting("accent", "amber")) }
    var selectedWallpaper by remember { mutableStateOf(securePrefs.getSetting("chat_wallpaper", "default")) }
    var customWallpaperPath by remember { mutableStateOf(securePrefs.getSetting("chat_wallpaper_custom", "")) }
    var fontSize by remember { mutableStateOf(securePrefs.getSetting("font_size", "medium")) }
    var uiScale by remember { mutableStateOf(securePrefs.getSetting("ui_scale", "default")) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val refreshRateMode by ai.deepcode.android.util.RefreshRateManager.currentMode.collectAsStateWithLifecycle()
    val appliedHz by ai.deepcode.android.util.RefreshRateManager.appliedRefreshRate.collectAsStateWithLifecycle()
    val supportedRates by ai.deepcode.android.util.RefreshRateManager.supportedRates.collectAsStateWithLifecycle()

    val currentAccent = remember(activeAccentId) {
        AccentThemes.firstOrNull { it.id == activeAccentId } ?: AccentThemes.first()
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { targetUri ->
            try {
                val destDir = File(context.filesDir, "wallpapers")
                destDir.mkdirs()
                val destFile = File(destDir, "custom_chat_bg.png")
                context.contentResolver.openInputStream(targetUri)?.use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
                customWallpaperPath = destFile.absolutePath
                securePrefs.saveSetting("chat_wallpaper_custom", destFile.absolutePath)
                selectedWallpaper = "custom"
                securePrefs.saveSetting("chat_wallpaper", "custom")
                refreshKey++
                Toast.makeText(context, "Custom wallpaper applied!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load wallpaper: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = AppScreenBg,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppCard)
                        .border(1.dp, AppBorder, RoundedCornerShape(14.dp))
                        .bouncyClickable(provideHaptic = true) { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AppWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Themes & Wallpapers",
                        color = AppWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Customize the look and feel of your app",
                        color = AppMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            contentPadding = PaddingValues(top = 2.dp, bottom = 12.dp)
        ) {
            // ═══════════════════════════════════════════════
            // 1. LIVE CHAT PREVIEW CARD
            // ═══════════════════════════════════════════════
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Chat Preview",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppWhite
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .bouncyClickable(provideHaptic = true) {
                                themeMode = "system"
                                securePrefs.saveSetting("theme", "system")
                                activeAccentId = "amber"
                                securePrefs.saveSetting("accent", "amber")
                                selectedWallpaper = "default"
                                securePrefs.saveSetting("chat_wallpaper", "default")
                                fontSize = "medium"
                                securePrefs.saveSetting("font_size", "medium")
                                uiScale = "default"
                                securePrefs.saveSetting("ui_scale", "default")
                                Toast.makeText(context, "Preview reset to default", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Preview",
                            tint = currentAccent.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Reset Preview",
                            color = currentAccent.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, AppBorder, RoundedCornerShape(20.dp))
                        .background(Color(0xFF101015))
                ) {
                    // Wallpaper / Gradient layer
                    val customFile = remember(customWallpaperPath, refreshKey) {
                        if (customWallpaperPath.isNotEmpty()) File(customWallpaperPath) else null
                    }
                    if (isDarkThemeActive && selectedWallpaper == "custom" && customFile != null && customFile.exists()) {
                        val bm by produceState<Bitmap?>(initialValue = null, key1 = customFile.absolutePath, key2 = refreshKey) {
                            value = withContext(Dispatchers.IO) {
                                try {
                                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                                    BitmapFactory.decodeFile(customFile.absolutePath, opts)
                                } catch (_: Exception) { null }
                            }
                        }
                        if (bm != null) {
                            Image(
                                bitmap = bm!!.asImageBitmap(),
                                contentDescription = "Wallpaper",
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else if (isDarkThemeActive) {
                        val currentWallpaperOpt = PresetWallpapers.find { it.id == selectedWallpaper } ?: PresetWallpapers.first()
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Brush.verticalGradient(currentWallpaperOpt.gradientColors))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color(0xFFF6F6F9))
                        )
                    }

                    // Content: Interactive Preview Bubbles & Mock Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // User message bubble (accent colored)
                        Box(
                            modifier = Modifier
                                .align(Alignment.End)
                                .widthIn(max = 280.dp)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                                .background(currentAccent.primary)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Can you explain quantum computing in simple terms?",
                                    color = Color.White,
                                    fontSize = if (fontSize == "small") 11.sp else if (fontSize == "large") 13.sp else 12.sp,
                                    lineHeight = 17.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.align(Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "10:42 AM",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 10.sp
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = "Delivered",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        // Assistant message bubble (sleek dark card with sparkle)
                        Box(
                            modifier = Modifier
                                .align(Alignment.Start)
                                .widthIn(max = 300.dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                                .background(if (isDarkThemeActive) Color(0xFF16161F).copy(alpha = 0.92f) else Color.White)
                                .border(1.dp, AppBorder, RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = currentAccent.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = "Assistant",
                                        color = currentAccent.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Quantum computing harnesses the unique principles of quantum mechanics to solve complex problems exponentially faster than classical computers.",
                                    color = if (isDarkThemeActive) AppWhite else Color(0xFF18181B),
                                    fontSize = if (fontSize == "small") 11.sp else if (fontSize == "large") 13.sp else 12.sp,
                                    lineHeight = 17.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "10:42 AM",
                                    color = AppMuted,
                                    fontSize = 10.sp,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }

                        // Mock input bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(21.dp))
                                .background(if (isDarkThemeActive) Color(0xFF1C1C24).copy(alpha = 0.9f) else Color(0xFFEBEBF0))
                                .border(1.dp, AppBorder, RoundedCornerShape(21.dp))
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ask anything...",
                                color = AppMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice",
                                tint = AppMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(currentAccent.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════
            // 2. THEME MODE SEGMENTED CONTROL
            // ═══════════════════════════════════════════════
            item {
                Text(
                    text = "Theme Mode",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppWhite
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppCard)
                        .border(1.dp, AppBorder, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val options = listOf(
                        "system" to "System",
                        "dark" to "Dark",
                        "light" to "Light"
                    )
                    options.forEach { (valKey, label) ->
                        val isSelected = themeMode == valKey
                        val bg = if (isSelected) currentAccent.primary.copy(alpha = 0.22f) else Color.Transparent
                        val textCol = if (isSelected) currentAccent.primary else AppMuted
                        val borderCol = if (isSelected) currentAccent.primary.copy(alpha = 0.5f) else Color.Transparent

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(bg)
                                .border(1.dp, borderCol, RoundedCornerShape(10.dp))
                                .bouncyClickable(provideHaptic = true) {
                                    themeMode = valKey
                                    securePrefs.saveSetting("theme", valKey)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textCol
                            )
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════
            // 3. ACCENT THEME SWATCHES (10 COLORS)
            // ═══════════════════════════════════════════════
            item {
                Text(
                    text = "Accent Theme",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppWhite
                )
                Spacer(Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AccentThemes) { accent ->
                        val isSelected = activeAccentId == accent.id
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .bouncyClickable(provideHaptic = true) {
                                    activeAccentId = accent.id
                                    securePrefs.saveSetting("accent", accent.id)
                                }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) accent.primary else AppBorder,
                                        shape = CircleShape
                                    )
                                    .padding(if (isSelected) 3.dp else 0.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(accent.primary, accent.primaryGradientEnd))),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = accent.displayName,
                                color = if (isSelected) AppWhite else AppMuted,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════
            // 4. SIDE-BY-SIDE: FONT SIZE & UI SCALE
            // ═══════════════════════════════════════════════
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Card: Font Size
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppCard)
                            .border(1.dp, AppBorder, RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FormatSize,
                                    contentDescription = null,
                                    tint = currentAccent.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Font Size",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppWhite
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isDarkThemeActive) Color(0xFF181822) else Color(0xFFECECF0))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val fontOptions = listOf(
                                    "small" to "Small",
                                    "medium" to "Med",
                                    "large" to "Large"
                                )
                                fontOptions.forEach { (key, label) ->
                                    val isSelected = fontSize == key
                                    val bg = if (isSelected) currentAccent.primary.copy(alpha = 0.25f) else Color.Transparent
                                    val tc = if (isSelected) currentAccent.primary else AppMuted
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bg)
                                            .bouncyClickable(provideHaptic = true) {
                                                fontSize = key
                                                securePrefs.saveSetting("font_size", key)
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = tc
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Right Card: UI Scale
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppCard)
                            .border(1.dp, AppBorder, RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = null,
                                    tint = currentAccent.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "UI Scale",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppWhite
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isDarkThemeActive) Color(0xFF181822) else Color(0xFFECECF0))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val scaleOptions = listOf(
                                    "compact" to "Comp",
                                    "default" to "Def",
                                    "large" to "Large"
                                )
                                scaleOptions.forEach { (key, label) ->
                                    val isSelected = uiScale == key
                                    val bg = if (isSelected) currentAccent.primary.copy(alpha = 0.25f) else Color.Transparent
                                    val tc = if (isSelected) currentAccent.primary else AppMuted
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bg)
                                            .bouncyClickable(provideHaptic = true) {
                                                uiScale = key
                                                securePrefs.saveSetting("ui_scale", key)
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = tc
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════
            // 5. THEMES (PRESETS 2-COLUMN GRID)
            // ═══════════════════════════════════════════════
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Themes",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppWhite
                    )
                    TextButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = currentAccent.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "+ Custom Wallpaper",
                            color = currentAccent.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Custom wallpaper card if exists
                if (customWallpaperPath.isNotEmpty()) {
                    val isCustomSelected = selectedWallpaper == "custom"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppCard)
                            .border(
                                width = if (isCustomSelected) 2.dp else 1.dp,
                                color = if (isCustomSelected) currentAccent.primary else AppBorder,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .bouncyClickable(provideHaptic = true) {
                                selectedWallpaper = "custom"
                                securePrefs.saveSetting("chat_wallpaper", "custom")
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                val f = remember(customWallpaperPath, refreshKey) {
                                    if (customWallpaperPath.isNotEmpty()) File(customWallpaperPath) else null
                                }
                                val bm by produceState<Bitmap?>(initialValue = null, key1 = customWallpaperPath, key2 = refreshKey) {
                                    if (f != null && f.exists()) {
                                        value = withContext(Dispatchers.IO) {
                                            try {
                                                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                                                BitmapFactory.decodeFile(f.absolutePath, opts)
                                            } catch (_: Exception) { null }
                                        }
                                    }
                                }
                                if (bm != null) {
                                    Image(
                                        bitmap = bm!!.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.Image, null, tint = currentAccent.primary, modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Custom Gallery Wallpaper",
                                    color = if (isCustomSelected) currentAccent.primary else AppWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Text("Uploaded from photos", color = AppMuted, fontSize = 11.sp)
                            }
                        }
                        RadioButton(
                            selected = isCustomSelected,
                            onClick = {
                                selectedWallpaper = "custom"
                                securePrefs.saveSetting("chat_wallpaper", "custom")
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = currentAccent.primary,
                                unselectedColor = AppMuted
                            )
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // 2-Column Grid of 8 Presets
                val chunks = remember { PresetWallpapers.chunked(2) }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    chunks.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { wp ->
                                val isSelected = selectedWallpaper == wp.id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(AppCard)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) currentAccent.primary else AppBorder,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .bouncyClickable(provideHaptic = true) {
                                            selectedWallpaper = wp.id
                                            securePrefs.saveSetting("chat_wallpaper", wp.id)
                                        }
                                ) {
                                    Column {
                                        // Mini preview canvas
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(76.dp)
                                                .clip(RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp))
                                                .background(Brush.verticalGradient(wp.gradientColors))
                                                .padding(8.dp)
                                        ) {
                                            // Mock miniature bubbles
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.End)
                                                        .width(42.dp)
                                                        .height(10.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(currentAccent.primary.copy(alpha = 0.85f))
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.Start)
                                                        .width(56.dp)
                                                        .height(10.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color.White.copy(alpha = 0.22f))
                                                )
                                            }

                                            // Radio selection indicator top-right
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .align(Alignment.TopEnd)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) currentAccent.primary else Color.Black.copy(alpha = 0.5f))
                                                    .border(1.dp, if (isSelected) currentAccent.primary else Color.White.copy(alpha = 0.4f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Card details
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = wp.title,
                                                color = if (isSelected) currentAccent.primary else AppWhite,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = wp.description,
                                                color = AppMuted,
                                                fontSize = 10.sp,
                                                lineHeight = 13.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            if (rowItems.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════
            // 6. DISPLAY REFRESH RATE (60Hz – 144Hz Variable)
            // ═══════════════════════════════════════════════
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Display Refresh Rate",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppWhite
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(currentAccent.primary.copy(alpha = 0.2f))
                            .border(1.dp, currentAccent.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "⚡ ${appliedHz.toInt()} Hz",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = currentAccent.primary
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (supportedRates.isNotEmpty()) {
                    Text(
                        text = "Hardware modes detected: ${supportedRates.map { "${it.toInt()}Hz" }.distinct().joinToString(", ")}",
                        fontSize = 11.sp,
                        color = AppMuted
                    )
                }
                Spacer(Modifier.height(8.dp))

                val rateOptions = listOf(
                    Triple(
                        ai.deepcode.android.util.RefreshRateManager.MODE_DYNAMIC,
                        "Dynamic Variable (60–144Hz)",
                        "Adaptive: 144Hz on gestures & streaming, 60Hz idle"
                    ),
                    Triple(
                        ai.deepcode.android.util.RefreshRateManager.MODE_144,
                        "144 Hz Ultra High",
                        "Locked to 144Hz maximum frame rate"
                    ),
                    Triple(
                        ai.deepcode.android.util.RefreshRateManager.MODE_120,
                        "120 Hz High",
                        "Locked to 120Hz smooth refresh rate"
                    ),
                    Triple(
                        ai.deepcode.android.util.RefreshRateManager.MODE_90,
                        "90 Hz Smooth",
                        "Balanced performance and battery efficiency"
                    ),
                    Triple(
                        ai.deepcode.android.util.RefreshRateManager.MODE_60,
                        "60 Hz Standard",
                        "Standard 60Hz rate for maximum battery"
                    )
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rateOptions.forEach { (modeKey, title, desc) ->
                        val isSelected = refreshRateMode == modeKey
                        val isDynamic = modeKey == ai.deepcode.android.util.RefreshRateManager.MODE_DYNAMIC
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(AppCard)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) currentAccent.primary else AppBorder,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .bouncyClickable(provideHaptic = true) {
                                    ai.deepcode.android.util.RefreshRateManager.setMode(modeKey)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = title,
                                        color = if (isSelected) currentAccent.primary else AppWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    if (isDynamic) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(AppSuccess.copy(alpha = 0.2f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "Adaptive",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AppSuccess
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(text = desc, color = AppMuted, fontSize = 11.sp)
                            }
                            if (isSelected) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = currentAccent.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
