package ai.deepcode.android.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ai.deepcode.android.R
import ai.deepcode.android.data.local.Profile
import ai.deepcode.android.data.local.ProfileManager
import ai.deepcode.android.ui.theme.*
import java.io.FileNotFoundException

/** Resolves the accent theme a profile should be rendered with. */
internal fun profileAccent(profile: Profile?): AccentTheme {
    val id = profile?.accentId
    if (!id.isNullOrBlank()) {
        AccentThemes.firstOrNull { it.id == id }?.let { return it }
    }
    return ActiveAccent
}

@Composable
fun LoginScreen(
    profileManager: ProfileManager,
    onProfileSelected: () -> Unit
) {
    var profiles by remember { mutableStateOf(profileManager.getProfiles()) }
    var showOnboarding by remember { mutableStateOf(false) }
    var profileToUnlock by remember { mutableStateOf<Profile?>(null) }

    fun refresh() {
        profiles = profileManager.getProfiles()
    }

    val firstRun = profiles.isEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        LoginAnimatedBackground()

        if (profileToUnlock != null) {
            LockScreen(
                profileManager = profileManager,
                profile = profileToUnlock!!,
                onDismiss = { profileToUnlock = null },
                onUnlocked = {
                    val selectedId = profileToUnlock?.id
                    profileToUnlock = null
                    if (selectedId != null) profileManager.setActiveProfile(selectedId)
                    onProfileSelected()
                }
            )
        } else if (firstRun || showOnboarding) {
            OnboardingFlow(
                profileManager = profileManager,
                isFirstRun = firstRun,
                onBack = if (firstRun) null else ({ showOnboarding = false }),
                onDone = {
                    refresh()
                    showOnboarding = false
                    profileManager.ensureFirstProfile()
                    onProfileSelected()
                }
            )
        } else {
            ProfilePickerContent(
                profileManager = profileManager,
                profiles = profiles,
                onSelect = { profile ->
                    if (profileManager.isPinSecured(profile)) {
                        profileToUnlock = profile
                    } else {
                        profileManager.setActiveProfile(profile.id)
                        onProfileSelected()
                    }
                },
                onAddNew = { showOnboarding = true },
                onDelete = {
                    profileManager.deleteProfile(it.id)
                    refresh()
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
//  ANIMATED HERO BACKGROUND  (accent gradient + drifting aurora orbs)
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun LoginAnimatedBackground() {
    val accent = ActiveAccent
    val c1 = accent.primary
    val c2 = accent.primaryGradientEnd
    val base = if (isDarkThemeActive) Color(0xFF090A0F) else Color(0xFFF6F6F9)

    val transition = rememberInfiniteTransition(label = "aurora")

    // Dreamy pulse for the glow field
    val pulse by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Slow horizontal drift of the orbs
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )

    Box(modifier = Modifier.fillMaxSize().background(base)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawRect(
                brush = Brush.verticalGradient(
                    listOf(c1.copy(alpha = 0.16f), Color.Transparent, base),
                    startY = 0f,
                    endY = h
                )
            )

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c2.copy(alpha = 0.22f * pulse), Color.Transparent)
                ),
                radius = w * 0.75f,
                center = Offset(w * (0.15f + drift * 0.06f), h * 0.10f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c1.copy(alpha = 0.16f * pulse), Color.Transparent)
                ),
                radius = w * 0.7f,
                center = Offset(w * (0.85f - drift * 0.06f), h * 0.82f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c1.copy(alpha = 0.08f * pulse), Color.Transparent)
                ),
                radius = w * 0.5f,
                center = Offset(w * 0.5f, h * 0.45f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
//  PROFILE PICKER  (existing profiles + "add new")
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun ProfilePickerContent(
    profileManager: ProfileManager,
    profiles: List<Profile>,
    onSelect: (Profile) -> Unit,
    onAddNew: () -> Unit,
    onDelete: (Profile) -> Unit
) {
    val accentedHero = rememberInfiniteTransition(label = "heroText")
    val heroGlow by accentedHero.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "glow"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 3 }
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(CardGradients.primary))
                    .graphicsLayer {
                        this.scaleX = 1f + 0.04f * (heroGlow - 0.6f)
                        this.scaleY = 1f + 0.04f * (heroGlow - 0.6f)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Welcome back",
            color = AppWhite,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.graphicsLayer { alpha = 0.7f + 0.3f * heroGlow }
        )
        Text(
            text = "Choose a profile to continue — or create a fresh one.",
            color = AppMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 26.dp)
        )

        profiles.forEachIndexed { index, profile ->
            ProfileCardAdvanced(
                profile = profile,
                profileManager = profileManager,
                isActive = profile.id == profileManager.getActiveProfileId(),
                onClick = { onSelect(profile) },
                onDelete = { onDelete(profile) },
                modifier = Modifier.staggeredEntrance(index)
            )
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .depthPill(
                    shape = RoundedCornerShape(16.dp),
                    elevation = 2.5.dp,
                    customGradient = if (isDarkThemeActive) listOf(DepthTokens.PillGradientTopDark, DepthTokens.PillGradientBottomDark) else null,
                    highlightAlpha = 0.25f,
                    isDark = isDarkThemeActive
                )
                .bouncyClickable(provideHaptic = true, onClick = onAddNew),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, null, tint = AppPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Create New Profile", fontWeight = FontWeight.Bold, color = AppPrimary)
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

/** Staggered fade-in + slide-up entrance for profile cards. */
@Composable
private fun Modifier.staggeredEntrance(index: Int): Modifier {
    val transition = remember { Animatable(0f) }
    LaunchedEffect(index) {
        transition.animateTo(
            targetValue = 1f,
            animationSpec = tween(520, delayMillis = index * 110, easing = FastOutSlowInEasing)
        )
    }
    return graphicsLayer {
        alpha = transition.value
        translationY = (1f - transition.value) * 44f
    }
}

@Composable
private fun ProfileCardAdvanced(
    profile: Profile,
    profileManager: ProfileManager,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val accent = profileAccent(profile)
    val secured = profileManager.isPinSecured(profile)
    val cardAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.96f,
        animationSpec = tween(300), label = "cardAlpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (isActive) accent.primary.copy(alpha = 0.10f) else AppCard,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isActive) accent.primary.copy(alpha = 0.45f) else AppBorder.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accent-ringed avatar
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(2.dp, accent.primary.copy(alpha = 0.7f), CircleShape)
                    .padding(2.dp)
            ) {
                ProfileAvatar(profile = profile, profileManager = profileManager, size = 46.dp)
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.name.ifBlank { "User" },
                        color = AppWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (secured) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = accent.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = profile.role?.takeIf { it.isNotBlank() } ?: "DeepCode profile",
                    color = AppMuted,
                    fontSize = 11.sp
                )
            }

            if (isActive) {
                Icon(Icons.Default.CheckCircle, null, tint = accent.primary, modifier = Modifier.size(22.dp))
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
                    tint = AppMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Delete (small, subtle)
            IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = AppMuted.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = AppSurface,
            title = { Text("Delete ${profile.name}?", color = AppWhite) },
            text = {
                Text(
                    "This removes the profile and its avatar. Per-profile keys are kept.",
                    color = AppMuted, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("Delete", color = AppDestructive, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = AppPrimary) }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
//  ONBOARDING WIZARD  (first-run & "create new profile")
// ─────────────────────────────────────────────────────────────────────
private val RoleSuggestions = listOf("Developer", "Builder", "Explorer", "Creator", "Student", "Curious", "Hacker", "Writer")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingFlow(
    profileManager: ProfileManager,
    isFirstRun: Boolean,
    onBack: (() -> Unit)?,
    onDone: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var accentId by remember { mutableStateOf(ActiveAccent.id) }
    var avatarBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pinEnabled by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var useBio by remember { mutableStateOf(true) }
    var pinError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val bioAvailable = remember { deviceSupportsBiometric(context) }

    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { input ->
                    avatarBitmap = BitmapFactory.decodeStream(input)
                }
            } catch (_: Exception) {}
        }
    }

    fun createProfile() {
        val n = name.trim().ifBlank { "User" }
        val r = role.trim()
        val newProfile = Profile(
            name = n,
            role = r.ifBlank { null },
            accentId = accentId,
            pinLength = if (pinEnabled) pin.length else 4,
            useBiometric = useBio && bioAvailable
        )
        val saved = profileManager.addProfile(newProfile)
        avatarBitmap?.let { bm ->
            val path = profileManager.saveAvatar(saved.id, bm)
            profileManager.updateProfile(saved.copy(avatarPath = path))
        }
        if (pinEnabled && pin.isNotEmpty()) {
            profileManager.setProfilePin(saved.id, pin, pin.length)
        }
        profileManager.setActiveProfile(saved.id)
        onDone()
    }

    fun next() {
        if (step == 2) {
            if (pinEnabled) {
                if (pin.length < 4 || pin.length > 6) {
                    pinError = "PIN must be 4–6 digits"; return
                }
                if (pin != pinConfirm) {
                    pinError = "PINs don't match"; return
                }
            }
        }
        pinError = null
        step++
        if (step > 2) createProfile()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (isFirstRun) "Get started" else "New profile", color = AppWhite) },
                navigationIcon = {
                    if (onBack != null || step > 0) {
                        IconButton(onClick = { if (step > 0) { step--; pinError = null } else onBack?.invoke() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppWhite)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = { (step + 1) / 3f },
                color = ActiveAccent.primary,
                trackColor = AppBorder.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.height(28.dp))

            when (step) {
                0 -> OnboardNameStep(name, { name = it }, role, { role = it })
                1 -> OnboardLookStep(avatarBitmap, { pickLauncher.launch("image/*") }, accentId, { accentId = it })
                2 -> OnboardSecurityStep(
                    pinEnabled = pinEnabled,
                    onPinEnabled = { pinEnabled = it; pinError = null },
                    pin = pin, onPin = { pin = it; pinError = null },
                    pinConfirm = pinConfirm, onPinConfirm = { pinConfirm = it; pinError = null },
                    useBio = useBio, onUseBio = { useBio = it },
                    isBiometricAvailable = bioAvailable
                )
            }

            if (pinError != null) {
                Spacer(Modifier.height(12.dp))
                Text(pinError!!, color = AppDestructive, fontSize = 12.sp, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .depthPill(
                        shape = RoundedCornerShape(16.dp),
                        elevation = 4.dp,
                        customGradient = listOf(ActiveAccent.primary, ActiveAccent.primaryGradientEnd),
                        highlightAlpha = 0.40f,
                        isDark = true
                    )
                    .bouncyClickable(
                        enabled = step != 0 || name.isNotBlank() || isFirstRun,
                        provideHaptic = true
                    ) { next() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (step < 2) "Continue"
                    else if (!pinEnabled) "Create profile"
                    else "Secure & create",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Onboarding step 0: name + role ──
@Composable
private fun OnboardNameStep(
    name: String,
    onName: (String) -> Unit,
    role: String,
    onRole: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Branded DeepCode Logo
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            ActiveAccent.primary.copy(alpha = 0.25f),
                            ActiveAccent.primaryGradientEnd.copy(alpha = 0.10f)
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            ActiveAccent.primary.copy(alpha = 0.6f),
                            ActiveAccent.primaryGradientEnd.copy(alpha = 0.2f)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = "DeepCode Logo",
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "DeepCode",
            color = AppWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )
        Text(
            text = "AI Coding & Automation Workspace",
            color = ActiveAccent.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 2.dp, bottom = 22.dp)
        )

        Text("What should we call you?", color = AppWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Pick a handle and a vibe.", color = AppMuted, fontSize = 13.sp,
             modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onName,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Your name", color = AppMuted) },
            leadingIcon = { Icon(Icons.Default.Person, null, tint = AppMuted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = AppWhite, unfocusedTextColor = AppWhite,
                focusedBorderColor = AppPrimary, unfocusedBorderColor = AppBorder,
                focusedContainerColor = AppSurface, unfocusedContainerColor = AppSurface
            ),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(Modifier.height(18.dp))
        Text("Choose a role (optional)", color = AppMuted, fontSize = 12.sp,
             modifier = Modifier.fillMaxWidth().padding(start = 4.dp))
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(RoleSuggestions) { r ->
                val selected = role == r
                AssistChip(
                    onClick = { onRole(if (selected) "" else r) },
                    label = { Text(r, color = if (selected) AppPrimary else AppMuted) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selected) AppPrimary.copy(alpha = 0.15f) else AppSurface
                    ),
                    border = BorderStroke(1.dp, if (selected) AppPrimary.copy(alpha = 0.6f) else AppBorder)
                )
            }
        }
    }
}

// ── Onboarding step 1: avatar + accent theme ──
@Composable
private fun OnboardLookStep(
    avatarBitmap: Bitmap?,
    onPickAvatar: () -> Unit,
    accentId: String,
    onAccent: (String) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Make it yours", color = AppWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Add a photo and pick your accent.", color = AppMuted, fontSize = 13.sp,
             modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(AppSurface)
                .border(2.dp, AppPrimary.copy(alpha = 0.6f), CircleShape)
                .clickable(onClick = onPickAvatar),
            contentAlignment = Alignment.Center
        ) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap!!.asImageBitmap(),
                    contentDescription = "Avatar",
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .size(26.dp).clip(CircleShape).background(AppPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            } else {
                Icon(Icons.Default.CameraAlt, null, tint = AppMuted, modifier = Modifier.size(34.dp))
            }
        }
        Text("Tap to add a photo", color = AppMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))

        Spacer(Modifier.height(28.dp))
        Text("Accent theme", color = AppWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(AccentThemes) { t ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(t.primary, t.primaryGradientEnd)))
                            .border(
                                2.dp,
                                if (t.id == accentId) AppWhite else Color.Transparent,
                                CircleShape
                            )
                            .clickable { onAccent(t.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (t.id == accentId) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Text(t.displayName, color = if (t.id == accentId) AppWhite else AppMuted, fontSize = 10.sp,
                         modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

// ── Onboarding step 2: optional security ──
@Composable
private fun OnboardSecurityStep(
    pinEnabled: Boolean,
    onPinEnabled: (Boolean) -> Unit,
    pin: String,
    onPin: (String) -> Unit,
    pinConfirm: String,
    onPinConfirm: (String) -> Unit,
    useBio: Boolean,
    onUseBio: (Boolean) -> Unit,
    isBiometricAvailable: Boolean
) {
    // Keys for PIN fields
    val pinKey = remember(pinEnabled) { "pin$pinEnabled" }
    val confirmKey = remember(pinEnabled) { "confirm$pinEnabled" }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Lock your profile", color = AppWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Optional — protect this profile with a PIN.", color = AppMuted, fontSize = 13.sp,
             modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = AppCard,
            border = BorderStroke(1.dp, AppBorder.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = AppPrimary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Enable PIN lock", color = AppWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Require a PIN before entering", color = AppMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = pinEnabled,
                        onCheckedChange = onPinEnabled,
                        colors = SwitchDefaults.colors(checkedTrackColor = AppPrimary)
                    )
                }

                AnimatedVisibility(
                    visible = pinEnabled,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 },
                    exit = fadeOut(tween(200))
                ) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        PinField("Enter PIN (4–6 digits)", pin, onPin, pinKey)
                        Spacer(Modifier.height(10.dp))
                        PinField("Confirm PIN", pinConfirm, onPinConfirm, confirmKey)
                    }
                }
            }
        }

        if (isBiometricAvailable) {
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AppCard,
                border = BorderStroke(1.dp, AppBorder.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Fingerprint, null, tint = AppPrimary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Biometric unlock", color = AppWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Use fingerprint / face to unlock", color = AppMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = useBio && pinEnabled,
                        onCheckedChange = onUseBio,
                        enabled = pinEnabled,
                        colors = SwitchDefaults.colors(checkedTrackColor = AppPrimary)
                    )
                }
            }
        }
    }
}

@Composable
private fun PinField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    rememberKey: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }.take(6)
            onValue(digits)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, color = AppMuted) },
        singleLine = true,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppWhite, unfocusedTextColor = AppWhite,
            focusedBorderColor = AppPrimary, unfocusedBorderColor = AppBorder,
            focusedContainerColor = AppSurface, unfocusedContainerColor = AppSurface
        ),
        shape = RoundedCornerShape(14.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────
//  LOCK SCREEN  (PIN + optional biometric unlock)
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun LockScreen(
    profileManager: ProfileManager,
    profile: Profile,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val accent = profileAccent(profile)
    val context = LocalContext.current
    val faActivity = remember { resolveFragmentActivity(context) }
    val canBio = remember {
        faActivity != null && deviceSupportsBiometric(context) && profile.useBiometric
    }

    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showBio by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val targetLen = profile.pinLength.coerceIn(4, 6)

    // Auto-submit once the PIN reaches the required length.
    LaunchedEffect(entered) {
        if (entered.length == targetLen) {
            kotlinx.coroutines.delay(80)
            if (profileManager.verifyPin(profile, entered)) {
                error = null
                onUnlocked()
            } else {
                error = "Incorrect PIN — try again"
                shake.snapTo(0f)
                shake.animateTo(1f, tween(80))
                shake.animateTo(-1f, tween(80))
                shake.animateTo(0.5f, tween(80))
                shake.animateTo(0f, tween(80))
                entered = ""
                showBio = true
            }
        }
    }

    fun onDigit(d: String) {
        if (entered.length >= targetLen) return
        entered += d
        error = null
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(36.dp))

            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppMuted)
            }
            Spacer(Modifier.height(12.dp))

            // Breathing lock icon
            val breathe = rememberInfiniteTransition(label = "breathe")
            val s by breathe.animateFloat(
                initialValue = 1f, targetValue = 1.06f,
                animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "scale"
            )
            Icon(
                Icons.Default.Lock,
                contentDescription = "Locked",
                tint = accent.primary,
                modifier = Modifier.size(58.dp).scale(s)
            )

            Spacer(Modifier.height(18.dp))
            ProfileAvatar(profile = profile, profileManager = profileManager, size = 64.dp)
            Spacer(Modifier.height(10.dp))
            Text(profile.name.ifBlank { "User" }, color = AppWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Enter your PIN to unlock", color = AppMuted, fontSize = 12.sp,
                 modifier = Modifier.padding(top = 2.dp, bottom = 22.dp))

            // Dots
            Box(
                modifier = Modifier.graphicsLayer {
                    translationX = shake.value * -14f
                },
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(profile.pinLength.coerceIn(4, 6)) { i ->
                        val filled = i < entered.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (filled) accent.primary else AppBorder.copy(alpha = 0.5f))
                        )
                    }
                }
            }

            if (error != null) {
                Text(error!!, color = AppDestructive, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
            }

            if (showBio && canBio) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { showBio = false }) {
                    Text("Use PIN", color = AppMuted, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (showBio && canBio) {
                BiometricUnlock(
                    profile = profile,
                    onSuccess = {
                        error = null
                        onUnlocked()
                    },
                    onError = { code, msg ->
                        if (code == BiometricPrompt.ERROR_NEGATIVE_BUTTON || code == BiometricPrompt.ERROR_USER_CANCELED || code == BiometricPrompt.ERROR_CANCELED) {
                            showBio = false
                        }
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            PinPad(
                onDigit = ::onDigit,
                onBackspace = { entered = entered.dropLast(1); error = null }
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun ProfileAvatar(profile: Profile, profileManager: ProfileManager, size: androidx.compose.ui.unit.Dp) {
    val bmp = remember(profile.id, profile.avatarPath) { profileManager.getAvatarBitmap(profile.id) }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = profile.name,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        val accent = profileAccent(profile)
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(accent.primary, accent.primaryGradientEnd))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = profile.name.take(1).uppercase().ifEmpty { "U" },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45).sp
            )
        }
    }
}

fun deviceSupportsBiometric(context: android.content.Context): Boolean {
    val bm = BiometricManager.from(context)
    return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
}

fun resolveFragmentActivity(context: android.content.Context): androidx.fragment.app.FragmentActivity? {
    var ctx = context
    while (ctx is android.content.ContextWrapper) {
        if (ctx is androidx.fragment.app.FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun PinPad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { item ->
                    if (item.isEmpty()) {
                        Spacer(Modifier.size(56.dp))
                    } else if (item == "⌫") {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .clickable { onBackspace() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Backspace, "Backspace", tint = AppWhite)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(AppCard)
                                .border(1.dp, AppBorder, CircleShape)
                                .clickable { onDigit(item) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(item, color = AppWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BiometricUnlock(
    profile: Profile,
    onSuccess: () -> Unit,
    onError: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock ${profile.name}")
            .setSubtitle("Touch the fingerprint sensor")
            .setNegativeButtonText("Use PIN")
            .build()
    }
    val bioPrompt = remember(context) {
        val activity = resolveFragmentActivity(context)
        if (activity != null) {
            BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        onError(errorCode, errString.toString())
                    }
                })
        } else null
    }
    LaunchedEffect(Unit) {
        bioPrompt?.authenticate(promptInfo)
    }
}
