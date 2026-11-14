package com.smartmed.dispenser.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.smartmed.dispenser.model.Compartment
import com.smartmed.dispenser.model.MedicineSchedule
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val ForestTeal = Color(0xFF173B37)
private val MutedTeal = Color(0xFF2D534F)
private val SoftMint = Color(0xFF8AA8A4)
private val WarmGold = Color(0xFFEBB864)
private val SoftCard = Color(0xFFF4F6F8)
private val InkDark = Color(0xFF182221)
private val InkMuted = Color(0xFF7D8A8E)
private val PhoneGreen = Color(0xFF6FD285)

private val SwatchPalette = listOf(
    0xFF6EE7E7L,
    0xFFE06C75L,
    0xFFEBB864L,
    0xFF9BE2B6L,
    0xFFA9B8F5L,
    0xFF8CC5F8L
)

private enum class AppPage {
    Home,
    AddMedicine,
    Settings
}

@Composable
fun DispenserAppRoot(
    state: DispenserUiState,
    viewModel: DispenserViewModel,
    onGoogleSignInClick: () -> Unit,
    onFullSignOut: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.statusMessage, state.errorMessage) {
        val msg = state.errorMessage ?: state.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    if (!state.isSignedIn) {
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
            WelcomeAuthScreen(
                isLoading = state.isLoading,
                modifier = Modifier.padding(padding),
                onGoogleSignInClick = onGoogleSignInClick,
                onEmailSignIn = viewModel::signIn,
                onEmailRegister = viewModel::register
            )
        }
        return
    }

    var activePage by remember { mutableStateOf(AppPage.Home) }

    Scaffold(
        containerColor = Color.White,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            CurvedBottomBar(
                activePage = activePage,
                onSelectPage = { activePage = it }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = activePage,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    val enterSlide = slideInHorizontally(
                        animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)
                    ) { fullWidth -> if (forward) fullWidth / 8 else -fullWidth / 8 }
                    val exitSlide = slideOutHorizontally(
                        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
                    ) { fullWidth -> if (forward) -fullWidth / 8 else fullWidth / 8 }
                    (enterSlide + fadeIn(tween(160))) togetherWith (exitSlide + fadeOut(tween(130)))
                },
                label = "windowTransition"
            ) { page ->
                when (page) {
                    AppPage.Home -> HomeDashboardScreen(
                        state = state,
                        onNavigate = { activePage = it },
                        onMarkTaken = { compId, qty -> viewModel.markDoseTakenManually(compId, qty) },
                        onRefillContainer = { compId -> viewModel.refillContainerToFull(compId) },
                        onToggleSchedule = viewModel::toggleSchedule,
                        onDeleteSchedule = viewModel::deleteSchedule
                    )

                    AppPage.AddMedicine -> AddCapsulesScreen(
                        compartments = state.compartments.take(3),
                        onBack = { activePage = AppPage.Home },
                        onSave = { compId, name, dosage, form, colorHex, time, tabs, days, notify ->
                            viewModel.saveMedicineAndSchedule(
                                compId, name, dosage, form, colorHex, time, tabs, days, notify
                            )
                            activePage = AppPage.Home
                        }
                    )

                    AppPage.Settings -> SettingsHardwareScreen(
                        state = state,
                        onSignOut = onFullSignOut,
                        onCopyApiKey = { viewModel.showStatusMessage("Permanent API key copied") },
                        onUpdatePreferences = { low, buz, sync ->
                            viewModel.setPreference(lowStockAlerts = low, buzzer = buz, autoSync = sync)
                        },
                        onRefillContainer = { compId -> viewModel.refillContainerToFull(compId) },
                        onUpdateCompartment = viewModel::updateCompartment
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Screen 1: Welcome / Google Sign-In ("Your Personal Drug Cabinet")
// -----------------------------------------------------------------------------
@Composable
private fun WelcomeAuthScreen(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onGoogleSignInClick: () -> Unit,
    onEmailSignIn: (String, String) -> Unit,
    onEmailRegister: (String, String, String) -> Unit
) {
    var showEmailDialog by remember { mutableStateOf(false) }
    var isRegisterMode by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ForestTeal)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Your Personal",
            color = Color.White,
            fontSize = 31.sp,
            fontWeight = FontWeight.Bold
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Drug Cabinet",
                color = Color.White,
                fontSize = 31.sp,
                fontWeight = FontWeight.Bold
            )
            Canvas(
                modifier = Modifier
                    .width(185.dp)
                    .height(12.dp)
            ) {
                val p = Path().apply {
                    moveTo(0f, size.height * 0.7f)
                    cubicTo(
                        size.width * 0.25f, 0f,
                        size.width * 0.45f, size.height,
                        size.width * 0.65f, size.height * 0.3f
                    )
                    cubicTo(
                        size.width * 0.8f, 0f,
                        size.width * 0.9f, size.height * 0.85f,
                        size.width, size.height * 0.4f
                    )
                }
                drawPath(
                    path = p,
                    color = WarmGold,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Be in control of your meds",
            color = Color(0xFFC4D3D0),
            fontSize = 14.sp
        )

        Spacer(Modifier.height(20.dp))
        WelcomeIllustration(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier
                    .size(56.dp)
                    .clickable(enabled = !isLoading) { onGoogleSignInClick() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(26.dp)) {
                        val stroke = 4.5.dp.toPx()
                        drawArc(Color(0xFFEA4335), -145f, 110f, false, style = Stroke(stroke))
                        drawArc(Color(0xFFFBBC05), 145f, 70f, false, style = Stroke(stroke))
                        drawArc(Color(0xFF34A853), 40f, 105f, false, style = Stroke(stroke))
                        drawArc(Color(0xFF4285F4), -10f, 55f, false, style = Stroke(stroke))
                        drawLine(
                            color = Color(0xFF4285F4),
                            start = Offset(size.width * 0.52f, size.height * 0.5f),
                            end = Offset(size.width * 0.94f, size.height * 0.5f),
                            strokeWidth = stroke
                        )
                    }
                }
            }

            Button(
                onClick = onGoogleSignInClick,
                enabled = !isLoading,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarmGold,
                    contentColor = InkDark
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text(
                    if (isLoading) "Signing in..." else "Get started",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(-45f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Already have an account? ",
                color = Color(0xFFC4D3D0),
                fontSize = 13.sp
            )
            Text(
                text = "Sign in",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clickable {
                    isRegisterMode = false
                    showEmailDialog = true
                }
            )
        }
    }

    if (showEmailDialog) {
        EmailAuthDialog(
            isRegisterMode = isRegisterMode,
            isLoading = isLoading,
            onToggleMode = { isRegisterMode = !isRegisterMode },
            onDismiss = { showEmailDialog = false },
            onSignIn = { e, p ->
                showEmailDialog = false
                onEmailSignIn(e, p)
            },
            onRegister = { e, p, n ->
                showEmailDialog = false
                onEmailRegister(e, p, n)
            }
        )
    }
}

@Composable
private fun WelcomeIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.55f
        val phoneW = size.width * 0.44f
        val phoneH = size.height * 0.62f

        drawCircle(
            color = Color(0xFFF0C258),
            radius = 22.dp.toPx(),
            center = Offset(cx - phoneW * 0.72f, cy - phoneH * 0.42f)
        )
        drawCircle(InkDark, 2.dp.toPx(), Offset(cx - phoneW * 0.76f, cy - phoneH * 0.45f))
        drawCircle(InkDark, 2.dp.toPx(), Offset(cx - phoneW * 0.68f, cy - phoneH * 0.45f))

        drawRoundRect(
            color = Color(0xFF8A79EB),
            topLeft = Offset(cx - phoneW * 0.92f, cy - phoneH * 0.14f),
            size = Size(phoneW * 0.65f, phoneH * 0.25f),
            cornerRadius = CornerRadius(18.dp.toPx())
        )

        drawRoundRect(
            color = Color(0xFFE76F82),
            topLeft = Offset(cx - phoneW * 0.88f, cy + phoneH * 0.16f),
            size = Size(phoneW * 0.52f, phoneH * 0.2f),
            cornerRadius = CornerRadius(16.dp.toPx())
        )

        drawRoundRect(
            color = Color(0xFFD8E874),
            topLeft = Offset(cx - 10.dp.toPx(), cy - phoneH * 0.72f),
            size = Size(58.dp.toPx(), 42.dp.toPx()),
            cornerRadius = CornerRadius(14.dp.toPx())
        )

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(cx + phoneW * 0.38f, cy - phoneH * 0.34f),
            size = Size(72.dp.toPx(), 64.dp.toPx()),
            cornerRadius = CornerRadius(18.dp.toPx())
        )
        val crossCx = cx + phoneW * 0.38f + 36.dp.toPx()
        val crossCy = cy - phoneH * 0.34f + 32.dp.toPx()
        drawLine(Color(0xFF4D5CEB), Offset(crossCx - 12.dp.toPx(), crossCy), Offset(crossCx + 12.dp.toPx(), crossCy), 7.dp.toPx(), StrokeCap.Round)
        drawLine(Color(0xFF4D5CEB), Offset(crossCx, crossCy - 12.dp.toPx()), Offset(crossCx, crossCy + 12.dp.toPx()), 7.dp.toPx(), StrokeCap.Round)

        val skinColor = Color(0xFFF3C09E)
        drawRoundRect(
            color = skinColor,
            topLeft = Offset(cx - phoneW * 0.66f, cy + phoneH * 0.12f),
            size = Size(phoneW * 0.36f, phoneH * 0.42f),
            cornerRadius = CornerRadius(24.dp.toPx())
        )
        drawRoundRect(
            color = skinColor,
            topLeft = Offset(cx + phoneW * 0.3f, cy + phoneH * 0.12f),
            size = Size(phoneW * 0.36f, phoneH * 0.42f),
            cornerRadius = CornerRadius(24.dp.toPx())
        )

        drawRoundRect(
            color = PhoneGreen,
            topLeft = Offset(cx - phoneW / 2f, cy - phoneH / 2f),
            size = Size(phoneW, phoneH),
            cornerRadius = CornerRadius(24.dp.toPx())
        )
        drawCircle(InkDark, 3.5.dp.toPx(), Offset(cx - phoneW * 0.15f, cy - phoneH * 0.34f))
        drawCircle(InkDark, 3.5.dp.toPx(), Offset(cx + phoneW * 0.15f, cy - phoneH * 0.34f))
        drawArc(
            color = InkDark,
            startAngle = 15f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(cx - 14.dp.toPx(), cy - phoneH * 0.28f),
            size = Size(28.dp.toPx(), 16.dp.toPx()),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        for (row in 0..2) {
            for (col in 0..3) {
                drawCircle(
                    color = ForestTeal,
                    radius = 3.5.dp.toPx(),
                    center = Offset(
                        cx - phoneW * 0.27f + col * (phoneW * 0.18f),
                        cy + phoneH * 0.08f + row * (phoneH * 0.11f)
                    )
                )
            }
        }
    }
}

@Composable
private fun EmailAuthDialog(
    isRegisterMode: Boolean,
    isLoading: Boolean,
    onToggleMode: () -> Unit,
    onDismiss: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isRegisterMode) "Create MediKit Account" else "Sign In to MediKit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isRegisterMode) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Your Name") },
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (6+ chars)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                TextButton(onClick = onToggleMode) {
                    Text(if (isRegisterMode) "Already have an account? Sign in" else "Need an account? Register")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isRegisterMode) onRegister(email, password, fullName)
                    else onSignIn(email, password)
                },
                enabled = !isLoading && email.isNotBlank() && password.length >= 6,
                colors = ButtonDefaults.buttonColors(containerColor = ForestTeal)
            ) {
                Text(if (isRegisterMode) "Register" else "Sign In", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// -----------------------------------------------------------------------------
// Screen 2: Home Dashboard (Matches Left Screen of Provided Reference Design)
// -----------------------------------------------------------------------------
@Composable
private fun HomeDashboardScreen(
    state: DispenserUiState,
    onNavigate: (AppPage) -> Unit,
    onMarkTaken: (Int, Int) -> Unit,
    onRefillContainer: (Int) -> Unit,
    onToggleSchedule: (String, Boolean) -> Unit,
    onDeleteSchedule: (String) -> Unit
) {
    val firstName = remember(state.userName) {
        state.userName.trim().substringBefore(" ").ifBlank { "Friend" }
    }
    val monthOptions = remember {
        val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val cal = Calendar.getInstance()
        val current = fmt.format(cal.time)
        cal.add(Calendar.MONTH, 1)
        val next = fmt.format(cal.time)
        cal.add(Calendar.MONTH, -2)
        val prev = fmt.format(cal.time)
        listOf(current, next, prev)
    }
    var monthIndex by remember { mutableIntStateOf(0) }
    val dateItems = remember { buildWeekDates() }
    var selectedDateIndex by remember { mutableIntStateOf(1) }
    var scheduleOffset by remember { mutableIntStateOf(0) }

    val enabledSchedules = remember(state.schedules) {
        val active = state.schedules.filter { it.enabled }
        if (active.isNotEmpty()) active else state.schedules
    }
    val orderedSchedules = remember(enabledSchedules, scheduleOffset) {
        if (enabledSchedules.isEmpty()) emptyList()
        else {
            val shift = scheduleOffset % enabledSchedules.size
            enabledSchedules.drop(shift) + enabledSchedules.take(shift)
        }
    }

    val containers = remember(state.compartments) { state.compartments.take(3) }
    val totalRemaining = remember(containers) { containers.sumOf { it.remainingQuantity } }
    val totalDropped = remember(containers) {
        containers.sumOf { comp ->
            val cap = if (comp.initialQuantity > 0) comp.initialQuantity else 5
            (cap - comp.remainingQuantity).coerceAtLeast(0)
        }
    }
    val lowStockCount = remember(containers) { containers.count { it.isLowStock } }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header row: Avatar portrait + "Hi, <Name> 👋" + Bell Alert icon
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onNavigate(AppPage.Settings) }
                ) {
                    PortraitAvatar(modifier = Modifier.size(46.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = InkDark)) {
                                append("Hi, ")
                            }
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = InkDark)) {
                                append("$firstName 👋")
                            }
                        },
                        fontSize = 21.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(index = 7)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = "Drop Logs & Alerts",
                        tint = InkDark,
                        modifier = Modifier.size(25.dp)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 6.dp, end = 6.dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (lowStockCount > 0) Color(0xFFEF4444) else InkDark)
                    )
                }
            }
        }

        // 2. Month Header
        item {
            val parts = monthOptions[monthIndex].split(" ")
            val mName = parts.firstOrNull() ?: "November"
            val mYear = parts.getOrNull(1) ?: "2026"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    monthIndex = (monthIndex + 1) % monthOptions.size
                }
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = InkDark)) {
                            append("$mName ")
                        }
                        withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = InkDark)) {
                            append(mYear)
                        }
                    },
                    fontSize = 16.sp
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Change month",
                    tint = InkDark,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 3. 7 Vertical-Oval Date Pills
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                dateItems.forEachIndexed { idx, (dayNum, wkName) ->
                    val selected = idx == selectedDateIndex
                    Column(
                        modifier = Modifier
                            .width(60.dp)
                            .height(88.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(if (selected) ForestTeal else SoftCard)
                            .clickable { selectedDateIndex = idx },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = dayNum,
                            color = if (selected) Color.White else InkDark,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = wkName,
                            color = if (selected) Color(0xFFB5C7C4) else InkMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 4. "To take" header + circular < > arrows
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "To take",
                    color = InkDark,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircleArrowButton(
                        icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        onClick = {
                            if (enabledSchedules.isNotEmpty()) {
                                scheduleOffset = (scheduleOffset - 1 + enabledSchedules.size) % enabledSchedules.size
                            }
                        }
                    )
                    CircleArrowButton(
                        icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        onClick = {
                            if (enabledSchedules.isNotEmpty()) {
                                scheduleOffset = (scheduleOffset + 1) % enabledSchedules.size
                            }
                        }
                    )
                }
            }
        }

        // 5. Dose cards ("Probiotic, 250mg" + gold peek "Loratadine. 10mg ✓")
        itemsIndexed(
            items = orderedSchedules,
            key = { index, sched -> "sched_${sched.id}_$index" }
        ) { idx, sched ->
            val comp = containers.firstOrNull { it.id == sched.compartmentId }
                ?: Compartment(
                    id = sched.compartmentId,
                    medicineName = "Container ${sched.compartmentId}",
                    initialQuantity = 5,
                    remainingQuantity = 5,
                    lowStockLimit = 1
                )
            ToTakeDoseCard(
                schedule = sched,
                compartment = comp,
                defaultPeekGold = idx == 1,
                onConfirmTaken = { onMarkTaken(comp.id, sched.tabletsPerDose) }
            )
        }

        // 6. Merged Section 1: Real-Time 3 Medicine Containers (5 Pills Each)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Medicine Containers",
                    color = InkDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ForestTeal
                ) {
                    Text(
                        text = "$totalRemaining/15 left · $totalDropped dropped",
                        color = WarmGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        itemsIndexed(
            items = containers,
            key = { _, comp -> "container_${comp.id}" }
        ) { _, comp ->
            val capacity = if (comp.initialQuantity > 0) comp.initialQuantity else 5
            val remaining = comp.remainingQuantity.coerceIn(0, capacity)
            val dropped = (capacity - remaining).coerceAtLeast(0)

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = SoftCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                PillArtwork(
                                    pillForm = comp.pillForm,
                                    accentColor = Color(comp.colorHex),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Container ${comp.id} · ${comp.pillForm}",
                                    color = InkMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${comp.medicineName} (${comp.dosage})",
                                    color = InkDark,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (comp.isLowStock) Color(0xFFFEE2E2) else Color.White
                        ) {
                            Text(
                                text = "$remaining/$capacity left",
                                color = if (comp.isLowStock) Color(0xFFDC2626) else ForestTeal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // 5 Visual Pill Dots + Exact Remaining / Dropped Count
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            for (slotPill in 1..capacity) {
                                val filled = slotPill <= remaining
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(if (filled) ForestTeal else Color.White)
                                        .border(
                                            width = 1.5.dp,
                                            color = if (filled) ForestTeal else Color(0xFFCBD5E1),
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                        Text(
                            text = "$dropped dropped",
                            color = InkMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Manual Drop + Refill to 5 Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onMarkTaken(comp.id, 1) },
                            enabled = remaining > 0,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ForestTeal,
                                disabledContainerColor = Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (remaining > 0) "Drop 1 Pill" else "Empty",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        if (remaining < capacity) {
                            Button(
                                onClick = { onRefillContainer(comp.id) },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WarmGold,
                                    contentColor = InkDark
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text("Refill to 5", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // 7. Merged Section 2: Automated Schedules
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Automated Schedules",
                    color = InkDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = WarmGold,
                    modifier = Modifier.clickable { onNavigate(AppPage.AddMedicine) }
                ) {
                    Text(
                        text = "+ Automate",
                        color = InkDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (state.schedules.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = SoftCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No automated schedule set. Tap + below to schedule which container dispenses at what time.",
                        color = InkMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            itemsIndexed(
                items = state.schedules,
                key = { idx, sched -> "auto_${sched.id}_$idx" }
            ) { _, sched ->
                val comp = containers.firstOrNull { it.id == sched.compartmentId }
                val medName = comp?.medicineName ?: "Container ${sched.compartmentId}"
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = SoftCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = ForestTeal)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "${format12h(sched.time)} · $medName",
                                    fontWeight = FontWeight.Bold,
                                    color = InkDark,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Container ${sched.compartmentId} · Drop ${sched.tabletsPerDose} pill(s) · ${sched.activeDays.joinToString()}",
                                    fontSize = 11.sp,
                                    color = InkMuted
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MediKitIconSwitch(
                                checked = sched.enabled,
                                icon = Icons.Rounded.Notifications,
                                onCheckedChange = { onToggleSchedule(sched.id, it) }
                            )
                            IconButton(onClick = { onDeleteSchedule(sched.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete schedule", tint = InkMuted)
                            }
                        }
                    }
                }
            }
        }

        // 8. Merged Section 3: Real-Time Pill Drop Logs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Real-Time Pill Drop Logs",
                    color = InkDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(shape = RoundedCornerShape(14.dp), color = SoftCard) {
                    Text(
                        text = "${state.dispensingHistory.size} drops",
                        color = ForestTeal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        if (state.dispensingHistory.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = SoftCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No pills dropped yet. Tap 'Drop 1 Pill' on any container or ✓ on a card above to record a real-time drop.",
                        color = InkMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            itemsIndexed(
                items = state.dispensingHistory,
                key = { idx, ev -> "drop_${ev.id}_$idx" }
            ) { _, ev ->
                val remNow = containers.firstOrNull { it.id == ev.compartmentId }?.remainingQuantity
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = SoftCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(WarmGold),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = InkDark)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Container ${ev.compartmentId} · ${ev.medicineName}",
                                fontWeight = FontWeight.Bold,
                                color = InkDark,
                                fontSize = 14.sp
                            )
                            Text(
                                text = buildString {
                                    append("Dropped ${ev.detectedQuantity} pill(s) · ${formatTimestamp(ev.timestamp)}")
                                    if (remNow != null) append(" · $remNow/5 left")
                                },
                                fontSize = 11.sp,
                                color = InkMuted
                            )
                        }
                        Text(
                            text = ev.status.uppercase(Locale.US),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestTeal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortraitAvatar(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFFF6E3D3))
    ) {
        val w = size.width
        val h = size.height
        // Shirt shoulders
        drawCircle(
            color = Color(0xFFB5545D),
            radius = w * 0.42f,
            center = Offset(w * 0.5f, h * 0.96f)
        )
        // Face
        drawCircle(
            color = Color(0xFFE7B697),
            radius = w * 0.24f,
            center = Offset(w * 0.5f, h * 0.46f)
        )
        // Hair crown
        drawArc(
            color = Color(0xFF3E2924),
            startAngle = 190f,
            sweepAngle = 160f,
            useCenter = true,
            topLeft = Offset(w * 0.25f, h * 0.16f),
            size = Size(w * 0.5f, h * 0.36f)
        )
    }
}

@Composable
private fun CircleArrowButton(
    icon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .border(1.dp, Color(0xFFE0E4E8), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = InkDark, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ToTakeDoseCard(
    schedule: MedicineSchedule,
    compartment: Compartment,
    defaultPeekGold: Boolean,
    onConfirmTaken: () -> Unit
) {
    var revealed by remember(schedule.id, defaultPeekGold) { mutableStateOf(defaultPeekGold) }
    val endPadding by animateDpAsState(
        targetValue = if (revealed) 68.dp else 0.dp,
        label = "doseCardReveal"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(106.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Warm Gold right action card exposed behind the pill card
        if (endPadding > 0.dp) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 38.dp)
                    .clip(RoundedCornerShape(34.dp))
                    .background(WarmGold)
                    .clickable {
                        onConfirmTaken()
                        revealed = false
                    }
                    .padding(end = 22.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Mark dose taken",
                    tint = InkDark,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // Foreground pill card with white circular left cutout halo + 3D pill artwork
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = endPadding)
                .pointerInput(schedule.id) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (dragAmount < -12f) revealed = true
                        else if (dragAmount > 12f) revealed = false
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = if (revealed) 28.dp else 0.dp)
                    .clip(RoundedCornerShape(34.dp))
                    .background(SoftCard)
                    .clickable {
                        if (revealed) {
                            onConfirmTaken()
                            revealed = false
                        } else {
                            revealed = true
                        }
                    }
                    .padding(start = if (revealed) 56.dp else 94.dp, end = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${schedule.tabletsPerDose} pill, once per day",
                        color = InkMuted,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "${compartment.medicineName}. ${compartment.dosage}",
                        color = InkDark,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = InkDark,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = format12h(schedule.time),
                            color = InkDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Left circular pill icon (with crisp white outer ring cutout when peeked!)
            Box(
                modifier = Modifier
                    .padding(start = if (revealed) 0.dp else 14.dp)
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(if (revealed) SoftCard else Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    PillArtwork(
                        pillForm = compartment.pillForm,
                        accentColor = Color(compartment.colorHex),
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PillArtwork(
    pillForm: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (pillForm == "Tablets") {
            val r = size.minDimension * 0.42f
            drawCircle(color = Color(0xFFE0E5EA), radius = r, center = Offset(center.x + 2f, center.y + 3f))
            drawCircle(color = Color.White, radius = r)
            drawCircle(color = Color(0xFFD5DCE2), radius = r, style = Stroke(width = 1.5.dp.toPx()))
            drawLine(
                color = Color(0xFFBAC3CA),
                start = Offset(center.x - r * 0.65f, center.y),
                end = Offset(center.x + r * 0.65f, center.y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        } else if (pillForm == "Drops") {
            val p = Path().apply {
                moveTo(center.x, size.height * 0.1f)
                cubicTo(size.width * 0.85f, size.height * 0.52f, size.width * 0.82f, size.height * 0.9f, center.x, size.height * 0.9f)
                cubicTo(size.width * 0.18f, size.height * 0.9f, size.width * 0.15f, size.height * 0.52f, center.x, size.height * 0.1f)
            }
            drawPath(p, color = accentColor)
        } else {
            rotate(degrees = -42f) {
                val w = size.width * 0.44f
                val h = size.height * 0.84f
                val left = center.x - w / 2f
                val top = center.y - h / 2f
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(left, top),
                    size = Size(w, h * 0.56f),
                    cornerRadius = CornerRadius(w / 2f)
                )
                drawRoundRect(
                    color = Color(0xFFF6F7F9),
                    topLeft = Offset(left, top + h * 0.44f),
                    size = Size(w, h * 0.56f),
                    cornerRadius = CornerRadius(w / 2f)
                )
                drawRoundRect(
                    color = Color(0xFFD8DEE4),
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w / 2f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Bottom Navigation Bar (3 Tabs + Flowing Liquid Water-Drop & Wave Motion)
// -----------------------------------------------------------------------------
@Composable
private fun CurvedBottomBar(
    activePage: AppPage,
    onSelectPage: (AppPage) -> Unit
) {
    val tabCount = 3f
    val targetFraction = (activePage.ordinal + 0.5f) / tabCount
    val headFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 780f),
        label = "waterDomeHead"
    )
    val tailFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 340f),
        label = "waterDomeTail"
    )

    var previousPage by remember { mutableStateOf(activePage) }
    val waveAnim = remember { Animatable(1f) }

    LaunchedEffect(activePage) {
        if (activePage != previousPage) {
            previousPage = activePage
            waveAnim.snapTo(0f)
            waveAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .navigationBarsPadding()
            .height(64.dp)
            .clipToBounds()
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val tabWidth = w / tabCount
            val settlePeakY = 7.dp.toPx()

            // Crisp top divider line across the full width
            drawLine(
                color = ForestTeal.copy(alpha = 0.08f),
                start = Offset(0f, 1.dp.toPx()),
                end = Offset(w, 1.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )

            val headX = w * headFraction
            val tailX = w * tailFraction
            val spread = abs(headX - tailX)
            val motion = (spread / (tabWidth * 0.65f)).coerceIn(0f, 1f)
            val baseDomeW = tabWidth * 1.35f

            if (motion <= 0.005f) {
                val startX = headX - baseDomeW / 2f
                val endX = headX + baseDomeW / 2f
                val domePath = Path().apply {
                    moveTo(startX, h)
                    cubicTo(
                        startX + baseDomeW * 0.24f, h,
                        headX - baseDomeW * 0.29f, settlePeakY,
                        headX, settlePeakY
                    )
                    cubicTo(
                        headX + baseDomeW * 0.29f, settlePeakY,
                        endX - baseDomeW * 0.24f, h,
                        endX, h
                    )
                    close()
                }
                drawPath(domePath, color = ForestTeal)
            } else {
                val moveDir = if (headX >= tailX) 1f else -1f
                val wavePhase = (waveAnim.value * 2.5f * PI).toFloat()
                val leftBase = min(headX, tailX) - baseDomeW * 0.50f
                val rightBase = max(headX, tailX) + baseDomeW * 0.50f

                // 1. Secondary translucent water wave trailing & oscillating behind the drop
                val trailCrestX = (tailX * 0.62f + headX * 0.38f) -
                    moveDir * 10.dp.toPx() * motion +
                    cos(wavePhase) * 8.dp.toPx() * motion
                val trailLeft = min(leftBase - 14.dp.toPx() * motion, trailCrestX - baseDomeW * 0.52f)
                val trailRight = max(rightBase + 14.dp.toPx() * motion, trailCrestX + baseDomeW * 0.52f)
                val trailLeftSpan = (trailCrestX - trailLeft).coerceAtLeast(1f)
                val trailRightSpan = (trailRight - trailCrestX).coerceAtLeast(1f)
                val trailPeakY = (settlePeakY + 3.dp.toPx() + 3.5.dp.toPx() * cos(wavePhase) * motion)
                    .coerceAtLeast(settlePeakY)

                val trailPath = Path().apply {
                    moveTo(trailLeft, h)
                    cubicTo(
                        trailLeft + trailLeftSpan * 0.46f, h,
                        trailCrestX - trailLeftSpan * 0.56f, trailPeakY,
                        trailCrestX, trailPeakY
                    )
                    cubicTo(
                        trailCrestX + trailRightSpan * 0.56f, trailPeakY,
                        trailRight - trailRightSpan * 0.46f, h,
                        trailRight, h
                    )
                    close()
                }
                drawPath(trailPath, color = MutedTeal.copy(alpha = 0.28f * motion))

                // 2. Primary stretched liquid wave body gliding to clicked tab
                val crestX = (headX * 0.68f + tailX * 0.32f) + sin(wavePhase) * 6.dp.toPx() * motion
                val leftSpan = (crestX - leftBase).coerceAtLeast(1f)
                val rightSpan = (rightBase - crestX).coerceAtLeast(1f)
                val peakY = (settlePeakY + 8.dp.toPx() * motion + 2.5.dp.toPx() * sin(wavePhase) * motion)
                    .coerceAtLeast(settlePeakY)

                val domePath = Path().apply {
                    moveTo(leftBase, h)
                    cubicTo(
                        leftBase + leftSpan * 0.48f, h,
                        crestX - leftSpan * 0.58f, peakY,
                        crestX, peakY
                    )
                    cubicTo(
                        crestX + rightSpan * 0.58f, peakY,
                        rightBase - rightSpan * 0.48f, h,
                        rightBase, h
                    )
                    close()
                }
                drawPath(domePath, color = ForestTeal)

                // 3. Small trailing water droplets that pinch off & merge back into the dome
                val dropAlpha = (motion * 0.9f).coerceIn(0f, 1f)
                val drop1X = tailX - moveDir * (20.dp.toPx() * motion + 4.dp.toPx() * sin(wavePhase))
                val drop1Y = (h - 20.dp.toPx() - 4.dp.toPx() * abs(sin(wavePhase)))
                    .coerceIn(10.dp.toPx(), h - 6.dp.toPx())
                drawCircle(
                    color = ForestTeal.copy(alpha = dropAlpha),
                    radius = (4.5.dp.toPx() * motion).coerceAtLeast(0.1f),
                    center = Offset(drop1X, drop1Y)
                )

                val drop2X = tailX - moveDir * (34.dp.toPx() * motion + 5.dp.toPx() * cos(wavePhase))
                val drop2Y = (h - 12.dp.toPx() - 3.dp.toPx() * abs(cos(wavePhase)))
                    .coerceIn(12.dp.toPx(), h - 4.dp.toPx())
                drawCircle(
                    color = MutedTeal.copy(alpha = dropAlpha * 0.75f),
                    radius = (3.dp.toPx() * motion).coerceAtLeast(0.1f),
                    center = Offset(drop2X, drop2Y)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavIcon(
                icon = Icons.Rounded.Home,
                label = "Home",
                selected = activePage == AppPage.Home,
                modifier = Modifier.weight(1f),
                onClick = { onSelectPage(AppPage.Home) }
            )
            BottomNavIcon(
                icon = Icons.Rounded.Add,
                label = "Automate",
                selected = activePage == AppPage.AddMedicine,
                iconSize = 27.dp,
                modifier = Modifier.weight(1f),
                onClick = { onSelectPage(AppPage.AddMedicine) }
            )
            BottomNavIcon(
                icon = Icons.Rounded.Settings,
                label = "Settings",
                selected = activePage == AppPage.Settings,
                modifier = Modifier.weight(1f),
                onClick = { onSelectPage(AppPage.Settings) }
            )
        }
    }
}

@Composable
private fun BottomNavIcon(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    onClick: () -> Unit
) {
    val iconColor by animateColorAsState(
        targetValue = if (selected) Color.White else InkMuted,
        animationSpec = tween(durationMillis = 160),
        label = "navIconColor"
    )
    val iconOffsetY by animateDpAsState(
        targetValue = if (selected) (-1).dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 600f),
        label = "navIconOffsetY"
    )
    val dotColor by animateColorAsState(
        targetValue = if (selected) WarmGold else Color.Transparent,
        animationSpec = tween(durationMillis = 150),
        label = "navDotColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .offset(y = iconOffsetY)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(iconSize)
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .size(width = if (selected) 10.dp else 4.dp, height = 4.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
    }
}

// -----------------------------------------------------------------------------
// Screen 3: "Capsules" Add Medicine & Schedule (Matches Right Reference Screen)
// -----------------------------------------------------------------------------
@Composable
private fun AddCapsulesScreen(
    compartments: List<Compartment>,
    onBack: () -> Unit,
    onSave: (Int, String, String, String, Long, String, Int, List<String>, Boolean) -> Unit
) {
    val forms = listOf("Tablets", "Capsules", "Drops")
    var selectedForm by remember { mutableStateOf("Capsules") }
    var selectedComp by remember { mutableIntStateOf(2) }
    var pillName by remember { mutableStateOf(compartments.getOrNull(1)?.medicineName ?: "Loratadine") }
    var dosage by remember { mutableStateOf(compartments.getOrNull(1)?.dosage ?: "10mg") }
    var timeText by remember { mutableStateOf("09:00") }
    var tabletsPerDose by remember { mutableIntStateOf(1) }
    var notifyEnabled by remember { mutableStateOf(true) }
    var selectedColor by remember { mutableStateOf(SwatchPalette[2]) }
    var showMedicineDropdown by remember { mutableStateOf(false) }
    val presetMedicines = remember {
        listOf(
            "Loratadine" to "10mg",
            "Probiotic" to "250mg",
            "Vitamin D3" to "1000IU",
            "Aspirin" to "75mg",
            "Metformin" to "500mg",
            "Omega-3" to "500mg"
        )
    }
    val selectedDays = remember { mutableStateListOf("Wed") }
    val allDays = listOf("Tue", "Wed", "Thu", "Fri", "Sat", "Sun", "Mon")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = ForestTeal,
                    shape = RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp)
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 34.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        forms.forEach { form ->
                            val active = form == selectedForm
                            Text(
                                text = form,
                                color = if (active) Color.White else SoftMint,
                                fontSize = if (active) 18.sp else 15.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.clickable { selectedForm = form }
                            )
                        }
                    }
                    Spacer(Modifier.width(40.dp))
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = (-32).dp)
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFC2D4D1))
                            .clickable { selectedForm = "Tablets" },
                        contentAlignment = Alignment.Center
                    ) {
                        PillArtwork("Tablets", Color.White, Modifier.size(40.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .background(Color(selectedColor)),
                        contentAlignment = Alignment.Center
                    ) {
                        PillArtwork(
                            pillForm = selectedForm,
                            accentColor = Color(0xFFE7EAEE),
                            modifier = Modifier.size(86.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .offset(x = 32.dp)
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFB3CCC8))
                            .clickable { selectedForm = "Drops" },
                        contentAlignment = Alignment.Center
                    ) {
                        PillArtwork("Drops", Color.White, Modifier.size(36.dp))
                    }
                }
            }

            // Smooth white upward circular notch at the bottom center of dark-teal header
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(66.dp)
                    .height(20.dp)
            ) {
                val notchPath = Path().apply {
                    moveTo(0f, size.height)
                    cubicTo(
                        size.width * 0.25f, size.height,
                        size.width * 0.28f, 0f,
                        size.width * 0.5f, 0f
                    )
                    cubicTo(
                        size.width * 0.72f, 0f,
                        size.width * 0.75f, size.height,
                        size.width, size.height
                    )
                    close()
                }
                drawPath(notchPath, color = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Pills name", color = InkDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Box(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = pillName,
                    onValueChange = { pillName = it },
                    leadingIcon = { Icon(Icons.Rounded.Medication, contentDescription = null, tint = InkMuted) },
                    trailingIcon = {
                        IconButton(onClick = { showMedicineDropdown = !showMedicineDropdown }) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Select medicine", tint = InkMuted)
                        }
                    },
                    placeholder = { Text("Loratadine") },
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SoftCard,
                        unfocusedContainerColor = SoftCard,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                DropdownMenu(
                    expanded = showMedicineDropdown,
                    onDismissRequest = { showMedicineDropdown = false }
                ) {
                    presetMedicines.forEach { (medName, medDosage) ->
                        DropdownMenuItem(
                            text = { Text("$medName ($medDosage)") },
                            onClick = {
                                pillName = medName
                                dosage = medDosage
                                showMedicineDropdown = false
                            }
                        )
                    }
                }
            }

            // Slot 1..6 pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (1..6).forEach { slot ->
                    val selected = selectedComp == slot
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) ForestTeal else SoftCard,
                        modifier = Modifier.clickable {
                            selectedComp = slot
                            compartments.getOrNull(slot - 1)?.let {
                                pillName = it.medicineName
                                dosage = it.dosage
                            }
                        }
                    ) {
                        Text(
                            text = "Slot $slot",
                            color = if (selected) Color.White else InkDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Dosage") },
                    shape = RoundedCornerShape(18.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SoftCard,
                        unfocusedContainerColor = SoftCard,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                TextField(
                    value = timeText,
                    onValueChange = { timeText = it },
                    label = { Text("Time (HH:mm)") },
                    shape = RoundedCornerShape(18.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SoftCard,
                        unfocusedContainerColor = SoftCard,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(SoftCard)
                        .padding(horizontal = 8.dp)
                ) {
                    Text("-", fontWeight = FontWeight.Bold, modifier = Modifier.clickable { if (tabletsPerDose > 1) tabletsPerDose-- }.padding(6.dp))
                    Text("$tabletsPerDose", fontWeight = FontWeight.Bold)
                    Text("+", fontWeight = FontWeight.Bold, modifier = Modifier.clickable { if (tabletsPerDose < 10) tabletsPerDose++ }.padding(6.dp))
                }
            }

            // Notification row + signature Forest-Teal bell toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Notification", color = InkDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                MediKitIconSwitch(
                    checked = notifyEnabled,
                    icon = Icons.Rounded.Notifications,
                    onCheckedChange = { notifyEnabled = it }
                )
            }

            // Weekday Vertical-Oval Pills (Tue / Wed ✓ / Thu / Fri ...)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                allDays.forEach { day ->
                    val active = selectedDays.contains(day)
                    Column(
                        modifier = Modifier
                            .width(56.dp)
                            .height(94.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(if (active) ForestTeal else SoftCard)
                            .clickable {
                                if (active && selectedDays.size > 1) selectedDays.remove(day)
                                else if (!active) selectedDays.add(day)
                            }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = day,
                            color = if (active) Color.White else InkMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (active) WarmGold else Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = if (active) InkDark else Color(0xFFC2C9CE),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            Text("Colors", color = InkDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SwatchPalette.forEach { colorLong ->
                    val isSel = selectedColor == colorLong
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(colorLong))
                            .then(
                                if (isSel) Modifier.border(3.dp, ForestTeal, CircleShape)
                                else Modifier
                            )
                            .clickable { selectedColor = colorLong }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    onSave(
                        selectedComp,
                        pillName.ifBlank { "Medicine $selectedComp" },
                        dosage.ifBlank { "250mg" },
                        selectedForm,
                        selectedColor,
                        if (isValidHhMm(timeText)) timeText.trim() else "09:00",
                        tabletsPerDose,
                        selectedDays.toList(),
                        notifyEnabled
                    )
                },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ForestTeal),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text("Save to Drug Cabinet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Page 2: 6-Compartment Smart Rotary Cabinet Page
// -----------------------------------------------------------------------------
@Composable
private fun CabinetCompartmentsScreen(
    state: DispenserUiState,
    onOpenAdd: () -> Unit,
    onTakeOne: (Int) -> Unit,
    onAddRefill: (Int, Int) -> Unit,
    onUpdateCompartment: (Int, String, Int, Int, Boolean) -> Unit
) {
    var editingComp by remember { mutableStateOf<Compartment?>(null) }
    val totalRemaining = remember(state.compartments) { state.compartments.sumOf { it.remainingQuantity } }
    val lowSlots = remember(state.compartments) { state.compartments.count { it.isLowStock } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ForestTeal, RoundedCornerShape(30.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "6-Slot Drug Cabinet",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${state.deviceState.deviceId} · $totalRemaining pills stocked",
                                color = Color(0xFFC4D3D0),
                                fontSize = 12.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = WarmGold,
                            modifier = Modifier.clickable { onOpenAdd() }
                        ) {
                            Text(
                                text = "+ Assign",
                                color = InkDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Visual 6-compartment rotary tray indicator
                        Canvas(modifier = Modifier.size(66.dp)) {
                            val r = size.minDimension / 2f
                            drawCircle(color = MutedTeal, radius = r)
                            for (slot in 0 until 6) {
                                val angle = -90f + slot * 60f
                                val rad = Math.toRadians(angle.toDouble())
                                val cx = center.x + (r * 0.58f * kotlin.math.cos(rad)).toFloat()
                                val cy = center.y + (r * 0.58f * kotlin.math.sin(rad)).toFloat()
                                val comp = state.compartments.getOrNull(slot)
                                drawCircle(
                                    color = if (comp?.isLowStock == true) Color(0xFFFCA5A5) else WarmGold,
                                    radius = 7.dp.toPx(),
                                    center = Offset(cx, cy)
                                )
                            }
                            drawCircle(color = ForestTeal, radius = 9.dp.toPx())
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (lowSlots > 0) "$lowSlots compartment(s) need refill" else "All 6 slots well stocked",
                                color = if (lowSlots > 0) Color(0xFFFCA5A5) else PhoneGreen,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Stepper positions selected slot above the drop gate & IR counter verifies dispensed quantity.",
                                color = Color(0xFFC4D3D0),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // White notch at bottom center matching design motif
                Canvas(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(56.dp)
                        .height(16.dp)
                ) {
                    val p = Path().apply {
                        moveTo(0f, size.height)
                        cubicTo(size.width * 0.25f, size.height, size.width * 0.28f, 0f, size.width * 0.5f, 0f)
                        cubicTo(size.width * 0.72f, 0f, size.width * 0.75f, size.height, size.width, size.height)
                        close()
                    }
                    drawPath(p, color = Color.White)
                }
            }
        }

        itemsIndexed(state.compartments, key = { i, c -> "cab_${c.id}_$i" }) { _, comp ->
            val denominator = maxOf(comp.initialQuantity, comp.remainingQuantity, 1)
            val progress = (comp.remainingQuantity.toFloat() / denominator.toFloat()).coerceIn(0f, 1f)

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = SoftCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                PillArtwork(
                                    pillForm = comp.pillForm,
                                    accentColor = Color(comp.colorHex),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Compartment ${comp.id} · ${comp.pillForm}", color = InkMuted, fontSize = 11.sp)
                                Text(
                                    text = "${comp.medicineName} (${comp.dosage})",
                                    color = InkDark,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        IconButton(onClick = { editingComp = comp }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit compartment", tint = ForestTeal)
                        }
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        color = if (comp.isLowStock) Color(0xFFEF4444) else ForestTeal,
                        trackColor = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${comp.remainingQuantity} pills left (limit ${comp.lowStockLimit})",
                            color = if (comp.isLowStock) Color(0xFFDC2626) else InkDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                modifier = Modifier.clickable(enabled = comp.remainingQuantity > 0) {
                                    onTakeOne(comp.id)
                                }
                            ) {
                                Text(
                                    text = "Take 1",
                                    color = ForestTeal,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = WarmGold,
                                modifier = Modifier.clickable { onAddRefill(comp.id, 10) }
                            ) {
                                Text(
                                    text = "+10 Refill",
                                    color = InkDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editingComp?.let { target ->
        var editName by remember(target.id) { mutableStateOf(target.medicineName) }
        var editInitial by remember(target.id) { mutableStateOf(target.initialQuantity.toString()) }
        var editLimit by remember(target.id) { mutableStateOf(target.lowStockLimit.toString()) }
        var resetStock by remember(target.id) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { editingComp = null },
            title = { Text("Edit Compartment ${target.id}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Medicine Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editInitial,
                        onValueChange = { editInitial = it.filter(Char::isDigit) },
                        label = { Text("Capacity / Initial Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editLimit,
                        onValueChange = { editLimit = it.filter(Char::isDigit) },
                        label = { Text("Low-Stock Alert Limit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Reset remaining to initial stock", fontSize = 13.sp)
                        MediKitIconSwitch(
                            checked = resetStock,
                            icon = Icons.Rounded.Refresh,
                            onCheckedChange = { resetStock = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateCompartment(
                            target.id,
                            editName.ifBlank { target.medicineName },
                            editInitial.toIntOrNull() ?: target.initialQuantity,
                            editLimit.toIntOrNull() ?: target.lowStockLimit,
                            resetStock
                        )
                        editingComp = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestTeal)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingComp = null }) { Text("Cancel") }
            }
        )
    }
}

// -----------------------------------------------------------------------------
// Page 3: Adherence History, Active Schedules & Refills Page
// -----------------------------------------------------------------------------
@Composable
private fun AdherenceHistoryScreen(
    state: DispenserUiState,
    onToggleSchedule: (String, Boolean) -> Unit,
    onDeleteSchedule: (String) -> Unit
) {
    var filterTab by remember { mutableStateOf("All") }
    val filters = listOf("All", "Schedules", "Dispensed", "Refills")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Summary Hero Card
        item {
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = ForestTeal,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Activity & Adherence",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatMiniPill("Doses Log", "${state.dispensingHistory.size}", Modifier.weight(1f))
                        StatMiniPill("Schedules", "${state.schedules.size}", Modifier.weight(1f))
                        StatMiniPill("Refills", "${state.refillHistory.size}", Modifier.weight(1f))
                    }
                }
            }
        }

        // Pill filter strip
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                filters.forEach { tab ->
                    val selected = tab == filterTab
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) ForestTeal else SoftCard,
                        modifier = Modifier.clickable { filterTab = tab }
                    ) {
                        Text(
                            text = tab,
                            color = if (selected) Color.White else InkDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }

        if (filterTab == "All" || filterTab == "Schedules") {
            item {
                Text("Active Medicine Schedules", fontWeight = FontWeight.Bold, color = InkDark, fontSize = 16.sp)
            }
            itemsIndexed(state.schedules, key = { idx, s -> "act_s_${s.id}_$idx" }) { _, sched ->
                val comp = state.compartments.getOrNull(sched.compartmentId - 1)
                val name = comp?.medicineName ?: "Compartment ${sched.compartmentId}"
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = SoftCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = ForestTeal)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("${format12h(sched.time)} · $name", fontWeight = FontWeight.Bold, color = InkDark)
                                Text(
                                    text = "Slot ${sched.compartmentId} · ${sched.tabletsPerDose} pill(s) · ${sched.activeDays.joinToString()}",
                                    fontSize = 11.sp,
                                    color = InkMuted
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MediKitIconSwitch(
                                checked = sched.enabled,
                                icon = Icons.Rounded.Notifications,
                                onCheckedChange = { onToggleSchedule(sched.id, it) }
                            )
                            IconButton(onClick = { onDeleteSchedule(sched.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = InkMuted)
                            }
                        }
                    }
                }
            }
        }

        if (filterTab == "All" || filterTab == "Dispensed") {
            item {
                Text("IR-Verified Dispensing History", fontWeight = FontWeight.Bold, color = InkDark, fontSize = 16.sp)
            }
            if (state.dispensingHistory.isEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(24.dp), color = SoftCard, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No doses logged yet. Tap ✓ on any Dashboard pill card or 'Take 1' in Cabinet.",
                            color = InkMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                itemsIndexed(state.dispensingHistory, key = { idx, ev -> "ev_${ev.id}_$idx" }) { _, ev ->
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = SoftCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(WarmGold),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = InkDark)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("C${ev.compartmentId}: ${ev.medicineName}", fontWeight = FontWeight.Bold, color = InkDark)
                                Text(
                                    text = "Verified ${ev.detectedQuantity}/${ev.scheduledQuantity} pill(s) · ${formatTimestamp(ev.timestamp)}",
                                    fontSize = 12.sp,
                                    color = InkMuted
                                )
                            }
                            Text(ev.status.uppercase(Locale.US), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ForestTeal)
                        }
                    }
                }
            }
        }

        if (filterTab == "All" || filterTab == "Refills") {
            item {
                Text("Refill Log", fontWeight = FontWeight.Bold, color = InkDark, fontSize = 16.sp)
            }
            itemsIndexed(state.refillHistory, key = { idx, rf -> "rf_${rf.id}_$idx" }) { _, rf ->
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = WarmGold.copy(alpha = 0.22f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = ForestTeal)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Refill Slot ${rf.compartmentId} (${rf.medicineName}): +${rf.quantityAdded} pills", fontWeight = FontWeight.Bold, color = InkDark)
                            Text(formatTimestamp(rf.timestamp), fontSize = 12.sp, color = InkMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMiniPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MutedTeal,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
        ) {
            Text(value, color = WarmGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(label, color = Color(0xFFD5E2E0), fontSize = 11.sp)
        }
    }
}

// -----------------------------------------------------------------------------
// Page 4: Settings (Matches MediKit Design Language + API Key Management)
// -----------------------------------------------------------------------------
@Composable
private fun SettingsHardwareScreen(
    state: DispenserUiState,
    onSignOut: () -> Unit,
    onRegenerateApiKey: () -> Unit,
    onCopyApiKey: () -> Unit,
    onPairDevice: (String, String) -> Unit,
    onUpdatePreferences: (Boolean, Boolean, Boolean) -> Unit,
    onOpenCabinet: () -> Unit
) {
    var showRawApiKey by remember { mutableStateOf(false) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }
    var showHardwareGuide by remember { mutableStateOf(false) }
    var editDeviceId by remember(state.deviceState.deviceId) {
        mutableStateOf(state.deviceState.deviceId.ifBlank { "MEDI_001" })
    }
    var editDeviceSecret by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Forest-Teal User Profile Hero Header Card with White Circular Cutout
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ForestTeal, RoundedCornerShape(32.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                PortraitAvatar(modifier = Modifier.size(50.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = state.userName.ifBlank { "MediKit User" },
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = state.userEmail.ifBlank { "Google Account Linked" },
                                    color = Color(0xFFC4D3D0),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = WarmGold,
                            modifier = Modifier.clickable { onSignOut() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = InkDark, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Sign Out", color = InkDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MutedTeal) {
                            Text(
                                text = "UID: ${state.userId.take(10)}...",
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MutedTeal,
                            modifier = Modifier.clickable { onOpenCabinet() }
                        ) {
                            Text(
                                text = "6-Slot Dispenser",
                                color = WarmGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Canvas(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(56.dp)
                        .height(16.dp)
                ) {
                    val p = Path().apply {
                        moveTo(0f, size.height)
                        cubicTo(size.width * 0.25f, size.height, size.width * 0.28f, 0f, size.width * 0.5f, 0f)
                        cubicTo(size.width * 0.72f, 0f, size.width * 0.75f, size.height, size.width, size.height)
                        close()
                    }
                    drawPath(p, color = Color.White)
                }
            }
        }

        // 2. API Key Management Section (Masked, Show/Hide, Copy, Status, Created Date, Regenerate)
        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = SoftCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(ForestTeal),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Key, contentDescription = null, tint = WarmGold, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("API Key Management", color = InkDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Created ${formatEpochMs(state.apiKeyCreatedAt)}", color = InkMuted, fontSize = 11.sp)
                            }
                        }

                        val isActive = state.apiKeyStatus.equals("active", ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isActive) ForestTeal else Color(0xFFB91C1C)
                        ) {
                            Text(
                                text = state.apiKeyStatus.uppercase(Locale.US),
                                color = if (isActive) WarmGold else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Key pill display with Show/Hide toggle
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (state.apiKey.isBlank()) {
                                    "Syncing..."
                                } else if (showRawApiKey) {
                                    state.apiKey
                                } else {
                                    maskApiKey(state.apiKey)
                                },
                                color = InkDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showRawApiKey = !showRawApiKey }) {
                                Icon(
                                    imageVector = if (showRawApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showRawApiKey) "Hide API Key" else "Show API Key",
                                    tint = ForestTeal
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (state.apiKey.isNotBlank()) {
                                    clipboard.setText(AnnotatedString(state.apiKey))
                                    onCopyApiKey()
                                }
                            },
                            enabled = state.apiKey.isNotBlank(),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WarmGold, contentColor = InkDark),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy Key", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showRegenerateConfirm = true },
                            enabled = !state.isLoading && state.apiKey.isNotBlank(),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = ForestTeal, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Regenerate", color = ForestTeal, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Dispenser & Notification Preferences (Matching Notification Switch Aesthetic)
        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = SoftCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Alerts & Dispenser Controls", color = InkDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    SettingsToggleRow(
                        icon = Icons.Rounded.NotificationsActive,
                        title = "Low-Stock Push Alerts",
                        subtitle = "Notify when pills fall below limit",
                        checked = state.lowStockAlertsEnabled,
                        onCheckedChange = {
                            onUpdatePreferences(it, state.buzzerEnabled, state.autoSyncEnabled)
                        }
                    )
                    SettingsToggleRow(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        title = "Buzzer & LCD Reminder",
                        subtitle = "Sound chime on ESP8266 at dose time",
                        checked = state.buzzerEnabled,
                        onCheckedChange = {
                            onUpdatePreferences(state.lowStockAlertsEnabled, it, state.autoSyncEnabled)
                        }
                    )
                    SettingsToggleRow(
                        icon = Icons.Rounded.Sync,
                        title = "Realtime Firebase Sync",
                        subtitle = "Mirror live telemetry & refills automatically",
                        checked = state.autoSyncEnabled,
                        onCheckedChange = {
                            onUpdatePreferences(state.lowStockAlertsEnabled, state.buzzerEnabled, it)
                        }
                    )
                }
            }
        }

        // 4. Separate Hardware Device Credentials & Setup Info
        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = SoftCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Memory, contentDescription = null, tint = ForestTeal)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("ESP8266 Hardware Pairing", color = InkDark, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Isolated device credentials", color = InkMuted, fontSize = 11.sp)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (state.deviceState.isOnline) Color(0xFFD1FAE5) else Color.White
                        ) {
                            Text(
                                text = if (state.deviceState.isOnline) "ONLINE" else "READY",
                                color = ForestTeal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    TextField(
                        value = editDeviceId,
                        onValueChange = { editDeviceId = it },
                        label = { Text("Device ID (e.g. MEDI_001)") },
                        shape = RoundedCornerShape(20.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    TextField(
                        value = editDeviceSecret,
                        onValueChange = { editDeviceSecret = it },
                        label = { Text("Device Secret") },
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(20.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (editDeviceId.isNotBlank() && editDeviceSecret.isNotBlank()) {
                                    onPairDevice(editDeviceId, editDeviceSecret)
                                    editDeviceSecret = ""
                                }
                            },
                            enabled = editDeviceId.isNotBlank() && editDeviceSecret.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestTeal),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Device", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { showHardwareGuide = true },
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Rounded.Info, contentDescription = null, tint = ForestTeal, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Setup Guide", color = ForestTeal)
                        }
                    }
                }
            }
        }
    }

    if (showRegenerateConfirm) {
        AlertDialog(
            onDismissRequest = { showRegenerateConfirm = false },
            title = { Text("Regenerate API Key?") },
            text = {
                Text(
                    "Regenerating your API key will immediately revoke the current key (${maskApiKey(state.apiKey)}). Update any hardware/client using the previous key."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRegenerateConfirm = false
                        showRawApiKey = false
                        onRegenerateApiKey()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C))
                ) {
                    Text("Regenerate", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showHardwareGuide) {
        AlertDialog(
            onDismissRequest = { showHardwareGuide = false },
            title = { Text("ESP8266 & Firebase Sync") },
            text = {
                Text(
                    "• Firebase RTDB: users/${state.userId}/apiKey & device\n" +
                        "• Firestore: compartments, schedules, dispensingHistory & refillHistory\n" +
                        "• Hardware Pinout: Stepper D5/D6, Servo D7, IR Sensor D4, RTC D1/D2, Buzzer D0.\n\n" +
                        "Your personal API key stays separated from the ESP8266 Device Secret."
                )
            },
            confirmButton = {
                Button(
                    onClick = { showHardwareGuide = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestTeal)
                ) {
                    Text("Got it", color = Color.White)
                }
            }
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = ForestTeal, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = InkDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = InkMuted, fontSize = 11.sp)
            }
        }
        MediKitIconSwitch(
            checked = checked,
            icon = icon,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun MediKitIconSwitch(
    checked: Boolean,
    icon: ImageVector,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) ForestTeal else Color(0xFFD5DCE0))
            .clickable { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) ForestTeal else InkMuted,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

private fun maskApiKey(key: String): String {
    if (key.length <= 8) return "••••••••"
    val prefix = if (key.startsWith("SMD-")) "SMD-" else key.take(4)
    val tail = key.takeLast(4)
    return "$prefix••••••••••••$tail"
}

private fun formatEpochMs(epochMs: Long): String {
    if (epochMs <= 0L) return "Just now"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
}

private fun buildWeekDates(): List<Pair<String, String>> {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_MONTH, -1)
    val dayFmt = SimpleDateFormat("dd", Locale.getDefault())
    val wkFmt = SimpleDateFormat("EEE", Locale.getDefault())
    return (0 until 7).map {
        val d = dayFmt.format(cal.time)
        val w = wkFmt.format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, 1)
        d to w
    }
}

private fun isValidHhMm(value: String): Boolean {
    val parts = value.trim().split(":")
    if (parts.size != 2) return false
    val h = parts[0].toIntOrNull() ?: return false
    val m = parts[1].toIntOrNull() ?: return false
    return h in 0..23 && m in 0..59
}

private fun format12h(hhMm: String): String {
    val parts = hhMm.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return hhMm
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val amPm = if (h < 12) "am" else "pm"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return String.format(Locale.US, "%02d:%02d %s", h12, m, amPm)
}

private fun formatTimestamp(ts: Timestamp?): String {
    if (ts == null) return "Just now"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(ts.toDate())
}
