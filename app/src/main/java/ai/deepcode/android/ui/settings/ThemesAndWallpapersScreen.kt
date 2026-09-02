package ai.deepcode.android.ui.settings

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.ui.components.*
import ai.deepcode.android.ui.theme.*
import java.io.File
import java.io.FileOutputStream

data class WallpaperOption(
    val id: String,
    val title: String,
    val description: String,
    val gradientColors: List<Color>? = null
)

val PresetWallpapers = listOf(
    WallpaperOption("default", "Pitch Dark", "Solid dark theme background", listOf(Color(0xFF0D0D12), Color(0xFF0D0D12))),
    WallpaperOption("space", "Deep Space", "Subtle deep purple cosmos gradient", listOf(Color(0xFF161033), Color(0xFF0D0A1C), Color(0xFF05040B))),
    WallpaperOption("nebula", "Midnight Nebula", "Vibrant ambient aura glow", listOf(Color(0xFF26123D), Color(0xFF120E29), Color(0xFF080714))),
    WallpaperOption("emerald", "Cyber Emerald", "Matrix inspired dark green gradient", listOf(Color(0xFF0A261E), Color(0xFF071410), Color(0xFF040A08))),
    WallpaperOption("carbon", "Carbon Tech", "Minimalist sleek charcoal mesh", listOf(Color(0xFF1C1E2B), Color(0xFF11121C), Color(0xFF090A10)))
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
    var refreshKey by remember { mutableIntStateOf(0) }

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
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppCard)
                        .border(1.dp, AppBorder, RoundedCornerShape(10.dp))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppWhite, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = "Themes & Wallpapers",
                    color = AppWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // Live Preview Card
            item {
                Text("Live Chat Preview", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppWhite)
                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, AppBorder, RoundedCornerShape(20.dp))
                ) {
                    // Wallpaper background preview
                    val customFile = remember(customWallpaperPath, refreshKey) {
                        if (customWallpaperPath.isNotEmpty()) File(customWallpaperPath) else null
                    }
                    if (isDarkThemeActive && selectedWallpaper == "custom" && customFile != null && customFile.exists()) {
                        val bm = remember(customFile.absolutePath, refreshKey) {
                            try { BitmapFactory.decodeFile(customFile.absolutePath) } catch (_: Exception) { null }
                        }
                        if (bm != null) {
                            Image(
                                bitmap = bm.asImageBitmap(),
                                contentDescription = "Wallpaper",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else if (isDarkThemeActive) {
                        val currentWallpaperOpt = PresetWallpapers.find { it.id == selectedWallpaper } ?: PresetWallpapers.first()
                        val colors = currentWallpaperOpt.gradientColors ?: listOf(Color(0xFF0D0D12), Color(0xFF0D0D12))
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(colors))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFF6F6F9))
                        )
                    }

                    // Overlay sample chat bubbles
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // User bubble
                        Box(
                            modifier = Modifier
                                .align(Alignment.End)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                                .background(AppPrimary)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text("Hey DeepCode! Generate a poem for me 📜", color = Color.White, fontSize = 12.sp)
                        }

                        // Assistant bubble
                        Box(
                            modifier = Modifier
                                .align(Alignment.Start)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                                .background(AppCard)
                                .border(1.dp, AppBorder, RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text("Here is your requested poem! ✨", color = AppWhite, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Section 1: Theme Mode
            item {
                Text("Theme Mode", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppWhite)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("system" to "System", "dark" to "Dark", "light" to "Light").forEach { (valKey, label) ->
                        val isSelected = themeMode == valKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AppPrimary.copy(alpha = 0.15f) else AppCard)
                                .border(1.dp, if (isSelected) AppPrimary else AppBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    themeMode = valKey
                                    securePrefs.saveSetting("theme", valKey)
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) AppPrimary else AppMuted
                            )
                        }
                    }
                }
            }

            // Section 2: Accent Theme Swatches
            item {
                Text("Accent Theme", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppWhite)
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AccentThemes) { accent ->
                        val isSelected = activeAccentId == accent.id
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    activeAccentId = accent.id
                                    securePrefs.saveSetting("accent", accent.id)
                                }
                                .padding(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(accent.primary, accent.primaryGradientEnd)))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) AppWhite else AppBorder,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(accent.displayName, color = if (isSelected) AppWhite else AppMuted, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Section 3: Wallpaper Choice
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Chat Wallpaper", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppWhite)
                    if (isDarkThemeActive) {
                        TextButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                            Icon(Icons.Default.AddPhotoAlternate, null, tint = AppPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("+ Custom Photo", color = AppPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (!isDarkThemeActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppCard)
                            .border(1.dp, AppBorder, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = AppMuted, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Wallpapers are disabled in Light Mode for clean readability.",
                                color = AppMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PresetWallpapers.forEach { wp ->
                            val isSelected = selectedWallpaper == wp.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) AppPrimary.copy(alpha = 0.12f) else AppCard)
                                    .border(1.dp, if (isSelected) AppPrimary else AppBorder, RoundedCornerShape(14.dp))
                                    .clickable {
                                        selectedWallpaper = wp.id
                                        securePrefs.saveSetting("chat_wallpaper", wp.id)
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Brush.verticalGradient(wp.gradientColors ?: listOf(Color(0xFF0D0D12), Color(0xFF0D0D12))))
                                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text(wp.title, color = AppWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(wp.description, color = AppMuted, fontSize = 11.sp)
                                    }
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, "Selected", tint = AppPrimary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        if (customWallpaperPath.isNotEmpty()) {
                            val isSelected = selectedWallpaper == "custom"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) AppPrimary.copy(alpha = 0.12f) else AppCard)
                                    .border(1.dp, if (isSelected) AppPrimary else AppBorder, RoundedCornerShape(14.dp))
                                    .clickable {
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
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AppCard),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val f = File(customWallpaperPath)
                                        val bm = if (f.exists()) try { BitmapFactory.decodeFile(f.absolutePath) } catch (_: Exception) { null } else null
                                        if (bm != null) {
                                            Image(bitmap = bm.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        } else {
                                            Icon(Icons.Default.Image, null, tint = AppPrimary, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text("Custom Photo Wallpaper", color = AppWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("From gallery", color = AppMuted, fontSize = 11.sp)
                                    }
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, "Selected", tint = AppPrimary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
