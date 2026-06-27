package com.example

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.io.OutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var darkTheme by remember { mutableStateOf(true) }
            MyApplicationTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RzKreditApp(
                        darkTheme = darkTheme,
                        onThemeToggle = { darkTheme = !darkTheme }
                    )
                }
            }
        }
    }
}

@Composable
fun RzKreditApp(
    darkTheme: Boolean,
    onThemeToggle: () -> Unit,
    viewModel: PeminjamViewModel = viewModel()
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Observe DB states
    val peminjamList by viewModel.peminjamList.collectAsStateWithLifecycle()
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    // Setup Live Clock
    var currentClockText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy • HH:mm:ss 'WIB'", Locale("id", "ID"))
        while (true) {
            currentClockText = sdf.format(Date())
            delay(1000)
        }
    }

    // Auto-dismiss Custom Toast Notification
    LaunchedEffect(viewModel.toastMessage) {
        if (viewModel.toastMessage != null) {
            delay(3500)
            viewModel.clearToast()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!viewModel.isLoggedIn) {
            // LOGIN SCREEN
            LoginScreen(
                usernameInput = viewModel.usernameInput,
                onUsernameChange = { viewModel.usernameInput = it },
                passwordInput = viewModel.passwordInput,
                onPasswordChange = { viewModel.passwordInput = it },
                isPasswordVisible = viewModel.isPasswordVisible,
                onTogglePassword = { viewModel.togglePasswordVisibility() },
                loginError = viewModel.loginError,
                onLoginClick = {
                    keyboardController?.hide()
                    viewModel.login()
                },
                darkTheme = darkTheme
            )
        } else {
            // DASHBOARD SCREEN
            DashboardScreen(
                darkTheme = darkTheme,
                onThemeToggle = onThemeToggle,
                clockText = currentClockText,
                stats = stats,
                peminjamList = peminjamList,
                historyList = historyList,
                viewModel = viewModel,
                onLogout = { viewModel.logout() }
            )
        }

        // Custom Toast Card Overlay
        AnimatedVisibility(
            visible = viewModel.toastMessage != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp, start = 16.dp, end = 16.dp)
        ) {
            viewModel.toastMessage?.let { msg ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (darkTheme) Color(0xFF0F172A) else Color(0xFFFFFFFF),
                        contentColor = if (darkTheme) Color.White else Color(0xFF090D16)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    modifier = Modifier
                        .widthIn(max = 500.dp)
                        .border(1.5.dp, RzGreen, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(RzGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = msg,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("toast_message")
                        )
                    }
                }
            }
        }

        // DELETE CONFIRMATION MODAL
        if (viewModel.showDeleteConfirm) {
            DeleteConfirmDialog(
                targetNama = viewModel.deleteTargetNama,
                sandiInput = viewModel.deleteSandiInput,
                onSandiChange = { viewModel.deleteSandiInput = it },
                errorText = viewModel.deleteError,
                onDismiss = { viewModel.tutupDeleteConfirm() },
                onConfirm = { viewModel.eksekusiHapusDebitur() },
                darkTheme = darkTheme
            )
        }

        // RECEIPT STRUK PREVIEW MODAL
        if (viewModel.showStrukPreview) {
            viewModel.strukTargetPeminjam?.let { p ->
                StrukPreviewDialog(
                    peminjam = p,
                    onDismiss = { viewModel.tutupStrukPreview() },
                    darkTheme = darkTheme,
                    hitungAngsuran = { viewModel.hitungAngsuranLokal(it) }
                )
            }
        }

        // CLOUD SYNC & SPREADSHEET CONFIGURATION DIALOG
        if (viewModel.showSyncSettings) {
            CloudSyncSettingsDialog(
                currentSpreadsheetUrl = viewModel.spreadsheetUrl,
                currentWebAppUrl = viewModel.webAppUrl,
                onDismiss = { viewModel.showSyncSettings = false },
                onSave = { sheetUrl, scriptUrl ->
                    viewModel.saveSyncPrefs(sheetUrl, scriptUrl)
                    viewModel.showSyncSettings = false
                },
                darkTheme = darkTheme
            )
        }
    }
}

@Composable
fun LoginScreen(
    usernameInput: String,
    onUsernameChange: (String) -> Unit,
    passwordInput: String,
    onPasswordChange: (String) -> Unit,
    isPasswordVisible: Boolean,
    onTogglePassword: () -> Unit,
    loginError: String?,
    onLoginClick: () -> Unit,
    darkTheme: Boolean
) {
    val containerBg = if (darkTheme) RzDarkPanel else Color.White
    val textMain = if (darkTheme) RzDarkTextMain else RzLightTextMain
    val textSub = if (darkTheme) RzDarkTextSub else RzLightTextSub
    val borderCol = if (darkTheme) RzDarkBorder else RzLightBorder

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (darkTheme) RzDarkBg else RzLightBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = containerBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .border(1.dp, borderCol, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Official Logo Frame (loaded locally for instant offline loading)
                Image(
                    painter = painterResource(id = R.drawable.logo_rzkredit),
                    contentDescription = "RzKredit Logo",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(3.dp, RzAmber, CircleShape)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Black, color = textMain)) {
                            append("RZK")
                        }
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = RzAmber)) {
                            append("redit")
                        }
                    },
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Username Input
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "USERNAME",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = textSub,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = onUsernameChange,
                        placeholder = { Text("Ketik username Anda") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RzBlue,
                            unfocusedBorderColor = borderCol,
                            focusedTextColor = textMain,
                            unfocusedTextColor = textMain,
                            focusedPlaceholderColor = textSub.copy(alpha = 0.6f),
                            unfocusedPlaceholderColor = textSub.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("username_input"),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Password Input
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "SANDI",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = textSub,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = onPasswordChange,
                        placeholder = { Text("••••••••") },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = onTogglePassword) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPasswordVisible) "Sembunyikan Sandi" else "Tampilkan Sandi",
                                    tint = textSub
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RzBlue,
                            unfocusedBorderColor = borderCol,
                            focusedTextColor = textMain,
                            unfocusedTextColor = textMain,
                            focusedPlaceholderColor = textSub.copy(alpha = 0.6f),
                            unfocusedPlaceholderColor = textSub.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input"),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { onLoginClick() })
                    )
                }

                if (loginError != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = loginError,
                        color = RzRose,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onLoginClick,
                    colors = ButtonDefaults.buttonColors(containerColor = RzGreen),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("login_button")
                ) {
                    Text(
                        text = "MASUK DASHBOARD",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    darkTheme: Boolean,
    onThemeToggle: () -> Unit,
    clockText: String,
    stats: DashboardStats,
    peminjamList: List<Peminjam>,
    historyList: List<HistorySetoran>,
    viewModel: PeminjamViewModel,
    onLogout: () -> Unit
) {
    val scrollState = rememberScrollState()
    val textMain = if (darkTheme) RzDarkTextMain else RzLightTextMain
    val textSub = if (darkTheme) RzDarkTextSub else RzLightTextSub
    val borderCol = if (darkTheme) RzDarkBorder else RzLightBorder

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(if (darkTheme) RzDarkBg else RzLightBg)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Main Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (darkTheme) RzDarkPanel else Color.White)
                        .border(1.dp, borderCol, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo loaded locally for instant offline loading
                    Image(
                        painter = painterResource(id = R.drawable.logo_rzkredit),
                        contentDescription = "RzKredit Logo",
                        modifier = Modifier
                            .size(45.dp)
                            .clip(CircleShape)
                            .border(2.dp, RzAmber, CircleShape)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Black, color = textMain)) {
                                        append("RZK")
                                    }
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = RzAmber)) {
                                        append("redit")
                                    }
                                },
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Mobile",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = RzBlue
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(RzGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = clockText,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = textSub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Top Action Controls
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onThemeToggle,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (darkTheme) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f))
                                .size(38.dp)
                        ) {
                            Text(text = if (darkTheme) "🌙" else "☀️", fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onLogout,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(RzRose.copy(alpha = 0.1f))
                                .border(1.dp, RzRose.copy(alpha = 0.2f), CircleShape)
                                .size(38.dp)
                        ) {
                            Text("🚪", fontSize = 16.sp)
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Keep bottom clean and spacing correct with Safe Navigation padding
            Spacer(modifier = Modifier.navigationBarsPadding())
        },
        containerColor = if (darkTheme) RzDarkBg else RzLightBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            // STATISTICS WIDGET GRID
            StatsWidgetGrid(stats = stats, darkTheme = darkTheme)

            Spacer(modifier = Modifier.height(16.dp))

            // TRANSACTION FORMS (Pendaftaran & Setoran)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Layout dynamically expands side-by-side or stacks on narrow devices
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val useRow = maxWidth > 700.dp
                    if (useRow) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ContractRegistrationForm(viewModel = viewModel, darkTheme = darkTheme)
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                InstallmentPaymentForm(viewModel = viewModel, activeDebtors = peminjamList, darkTheme = darkTheme)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ContractRegistrationForm(viewModel = viewModel, darkTheme = darkTheme)
                            InstallmentPaymentForm(viewModel = viewModel, activeDebtors = peminjamList, darkTheme = darkTheme)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // DATABASE NASABAH AKTIF
            ActiveDebtorsDatabaseCard(
                peminjamList = peminjamList,
                viewModel = viewModel,
                darkTheme = darkTheme
            )

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun StatsWidgetGrid(stats: DashboardStats, darkTheme: Boolean) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatWidget(
                title = "DEBITUR",
                value = stats.totalNasabah.toString(),
                emblem = "👥",
                emblemColor = RzBlue,
                darkTheme = darkTheme
            )
            StatWidget(
                title = "MASUK",
                value = formatRupiah(stats.totalUangMasuk),
                emblem = "💰",
                emblemColor = RzGreen,
                darkTheme = darkTheme
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatWidget(
                title = "PIUTANG",
                value = formatRupiah(stats.totalUangDihutang),
                emblem = "📊",
                emblemColor = RzAmber,
                darkTheme = darkTheme
            )
            StatWidget(
                title = "SALDO",
                value = formatRupiah(stats.totalPiutang),
                emblem = "📉",
                emblemColor = RzRose,
                darkTheme = darkTheme
            )
        }
    }
}

@Composable
fun StatWidget(
    title: String,
    value: String,
    emblem: String,
    emblemColor: Color,
    darkTheme: Boolean
) {
    val textMain = if (darkTheme) RzDarkTextMain else RzLightTextMain
    val textSub = if (darkTheme) RzDarkTextSub else RzLightTextSub
    val borderCol = if (darkTheme) RzDarkBorder else RzLightBorder

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (darkTheme) RzDarkPanel.copy(alpha = 0.85f) else Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderCol, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                    color = textSub,
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(emblemColor.copy(alpha = 0.12f))
                        .border(1.dp, emblemColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emblem, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = emblemColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ContractRegistrationForm(
    viewModel: PeminjamViewModel,
    darkTheme: Boolean
) {
    val textMain = if (darkTheme) RzDarkTextMain else RzLightTextMain
    val textSub = if (darkTheme) RzDarkTextSub else RzLightTextSub
    val borderCol = if (darkTheme) RzDarkBorder else RzLightBorder

    var isNominalExpanded by remember { mutableStateOf(false) }
    var isTenorExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (darkTheme) RzDarkPanel else Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderCol, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "1. Pendaftaran Peminjam Baru",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = RzBlue
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text("KONTRAK BARU", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textSub)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Nama Debitur
            Text("NAMA LENGKAP", fontSize = 10.sp, fontWeight = FontWeight.Black, color = textSub)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = viewModel.regNama,
                onValueChange = { viewModel.regNama = it },
                placeholder = { Text("Contoh: Lutfia Karim") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RzBlue,
                    unfocusedBorderColor = borderCol,
                    focusedTextColor = textMain,
                    unfocusedTextColor = textMain
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reg_name_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Nominal Dropdown
            Text("NOMINAL PEMINJAMAN", fontSize = 10.sp, fontWeight = FontWeight.Black, color = textSub)
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = formatRupiah(viewModel.regNominal) + " (Angsuran: " + formatRupiahShort(viewModel.hitungAngsuranLokal(viewModel.regNominal)) + ")",
                    onValueChange = {},
                    readOnly = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RzBlue,
                        unfocusedBorderColor = borderCol,
                        focusedTextColor = textMain,
                        unfocusedTextColor = textMain
                    ),
                    trailingIcon = {
                        IconButton(onClick = { isNominalExpanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Pilih Plafon")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isNominalExpanded = true }
                        .testTag("reg_nominal_select")
                )

                DropdownMenu(
                    expanded = isNominalExpanded,
                    onDismissRequest = { isNominalExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    val plafons = listOf(
                        1000000.0, 1500000.0, 2000000.0, 2500000.0,
                        3000000.0, 3500000.0, 4000000.0, 4500000.0, 5000000.0
                    )
                    plafons.forEach { p ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${formatRupiah(p)} (Angsuran: ${formatRupiahShort(viewModel.hitungAngsuranLokal(p))})",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            onClick = {
                                viewModel.regNominal = p
                                isNominalExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tenor Dropdown
            Text("KONTRAK TENOR", fontSize = 10.sp, fontWeight = FontWeight.Black, color = textSub)
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = "${viewModel.regTenor} Bulan ( ${viewModel.regTenor / 12} Tahun )",
                    onValueChange = {},
                    readOnly = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RzBlue,
                        unfocusedBorderColor = borderCol,
                        focusedTextColor = textMain,
                        unfocusedTextColor = textMain
                    ),
                    trailingIcon = {
                        IconButton(onClick = { isTenorExpanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Pilih Tenor")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isTenorExpanded = true }
                        .testTag("reg_tenor_select")
                )

                DropdownMenu(
                    expanded = isTenorExpanded,
                    onDismissRequest = { isTenorExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("12 Bulan (1 Tahun)", fontWeight = FontWeight.Bold) },
                        onClick = {
                            viewModel.regTenor = 12
                            isTenorExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("24 Bulan (2 Tahun)", fontWeight = FontWeight.Bold) },
                        onClick = {
                            viewModel.regTenor = 24
                            isTenorExpanded = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { viewModel.simpanPeminjamBaru() },
                colors = ButtonDefaults.buttonColors(containerColor = RzBlue),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_contract_button")
            ) {
                Text("Simpan Kontrak Baru", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
        }
    }
}

@Composable
fun InstallmentPaymentForm(
    viewModel: PeminjamViewModel,
    activeDebtors: List<Peminjam>,
    darkTheme: Boolean
) {
    val textMain = if (darkTheme) RzDarkTextMain else RzLightTextMain
    val textSub = if (darkTheme) RzDarkTextSub else RzLightTextSub
    val borderCol = if (darkTheme) RzDarkBorder else RzLightBorder

    var isDebtorExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (darkTheme) RzDarkPanel else Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RzGreen.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "2. Input Setoran Angsuran",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = RzGreen
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(RzGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text("SETORAN", fontSize = 9.sp, fontWeight = FontWeight.Black, color = RzGreen)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Monitor Screen
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (darkTheme) Color(0xFF03050A) else Color(0xFF1E293B))
                    .border(1.5.dp, RzGreen, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "ANGSURAN PER BULAN (RP)",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    color = RzGreen,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Left
                )
                Spacer(modifier = Modifier.height(4.dp))
                val displayAmount = viewModel.setorJumlah.toDoubleOrNull() ?: 0.0
                Text(
                    text = if (displayAmount > 0.0) formatRupiahShort(displayAmount) else "0",
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    color = RzGreen,
                    modifier = Modifier.testTag("installment_screen_value")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Select Debtor Dropdown
            Text("PILIH NAMA PEMINJAM", fontSize = 10.sp, fontWeight = FontWeight.Black, color = textSub)
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = if (viewModel.setorNama.isEmpty()) "-- Pilih Nasabah --" else viewModel.setorNama,
                    onValueChange = {},
                    readOnly = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RzGreen,
                        unfocusedBorderColor = borderCol,
                        focusedTextColor = textMain,
                        unfocusedTextColor = textMain
                    ),
                    trailingIcon = {
                        IconButton(onClick = { isDebtorExpanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Pilih Debitur")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDebtorExpanded = true }
                        .testTag("setor_debtor_select")
                )

                DropdownMenu(
                    expanded = isDebtorExpanded,
                    onDismissRequest = { isDebtorExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text("-- Pilih Nasabah --", color = textSub) },
                        onClick = {
                            viewModel.otomatisIsiAngsuran("")
                            isDebtorExpanded = false
                        }
                    )
                    activeDebtors.forEach { d ->
                        DropdownMenuItem(
                            text = { Text(d.nama, fontWeight = FontWeight.Bold) },
                            onClick = {
                                viewModel.otomatisIsiAngsuran(d.nama)
                                isDebtorExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Setoran Amount Input
            Text("JUMLAH SETORAN (RP)", fontSize = 10.sp, fontWeight = FontWeight.Black, color = textSub)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = viewModel.setorJumlah,
                onValueChange = { viewModel.setorJumlah = it },
                placeholder = { Text("Akan terisi otomatis") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RzGreen,
                    unfocusedBorderColor = borderCol,
                    focusedTextColor = textMain,
                    unfocusedTextColor = textMain
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setor_amount_input")
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { viewModel.simpanSetoranTunai() },
                colors = ButtonDefaults.buttonColors(containerColor = RzGreen),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_setoran_button")
            ) {
                Text("Simpan Setoran Tunai", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
        }
    }
}

@Composable
fun ActiveDebtorsDatabaseCard(
    peminjamList: List<Peminjam>,
    viewModel: PeminjamViewModel,
    darkTheme: Boolean
) {
    val context = LocalContext.current
    val textMain = if (darkTheme) RzDarkTextMain else RzLightTextMain
    val textSub = if (darkTheme) RzDarkTextSub else RzLightTextSub
    val borderCol = if (darkTheme) RzDarkBorder else RzLightBorder
    val tableBg = if (darkTheme) RzDarkLedger else Color(0xFFE9EFF6)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (darkTheme) RzDarkPanel else Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderCol, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Card Title & Quick Header Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📜 DATABASE NASABAH AKTIF",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textMain
                    )
                    Text(
                        text = "Terhubung secara Real-Time",
                        fontSize = 11.sp,
                        color = textSub
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Sync Data Action
                    IconButton(
                        onClick = {
                            if (!viewModel.isSyncing) {
                                viewModel.syncDataWithSpreadsheet()
                            }
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(RzBlue.copy(alpha = 0.12f))
                            .border(1.dp, RzBlue.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .size(34.dp)
                    ) {
                        if (viewModel.isSyncing) {
                            CircularProgressIndicator(
                                color = RzBlue,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text("⚡", fontSize = 12.sp)
                        }
                    }

                    // Cloud Settings Configuration Button
                    IconButton(
                        onClick = {
                            viewModel.showSyncSettings = true
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.12f))
                            .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .size(34.dp)
                    ) {
                        Text("⚙", fontSize = 12.sp)
                    }

                    // CSV Exporter
                    IconButton(
                        onClick = {
                            exportCsvReport(context, peminjamList) { msg ->
                                viewModel.showToast(msg)
                            }
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(RzGreen.copy(alpha = 0.12f))
                            .border(1.dp, RzGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .size(34.dp)
                    ) {
                        Text("📊", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Debtors Table Layout
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(tableBg)
                    .border(1.dp, borderCol, RoundedCornerShape(12.dp))
            ) {
                // Table Row Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (darkTheme) Color.Black.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("DEBITUR / PLAFON", fontSize = 11.sp, fontWeight = FontWeight.Black, color = textSub, modifier = Modifier.weight(2.5f))
                    Text("TENOR", fontSize = 11.sp, fontWeight = FontWeight.Black, color = textSub, modifier = Modifier.weight(1.3f), textAlign = TextAlign.Center)
                    Text("TUNGGAKAN", fontSize = 11.sp, fontWeight = FontWeight.Black, color = textSub, modifier = Modifier.weight(2.2f), textAlign = TextAlign.End)
                    Text("AKSI", fontSize = 11.sp, fontWeight = FontWeight.Black, color = textSub, modifier = Modifier.weight(1.5f), textAlign = TextAlign.Center)
                }

                if (peminjamList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tidak ada debitur aktif.\nDaftarkan kontrak baru di atas.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSub,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    peminjamList.forEach { d ->
                        HorizontalDivider(color = borderCol, thickness = 0.5.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Debitur & Plafon
                            Column(modifier = Modifier.weight(2.5f)) {
                                Text(
                                    text = d.nama,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = RzBlue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatRupiah(d.nominal),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textMain
                                )
                            }

                            // Tenor / Sisa Tenor
                            Column(
                                modifier = Modifier.weight(1.3f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (darkTheme) Color.White.copy(alpha = 0.05f) else Color.White)
                                        .border(1.dp, borderCol, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("${d.tenor} bln", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textMain)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(RzGreen.copy(alpha = 0.12f))
                                        .border(1.dp, RzGreen.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("${d.sisaTenor} sisa", fontSize = 9.5.sp, fontWeight = FontWeight.Black, color = RzGreen)
                                }
                            }

                            // Tunggakan / Sisa Tagihan
                            Text(
                                text = formatRupiah(d.sisaTagihan),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Black,
                                color = RzRose,
                                modifier = Modifier.weight(2.2f),
                                textAlign = TextAlign.End
                            )

                            // Actions
                            Row(
                                modifier = Modifier.weight(1.5f),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Print Struk Icon
                                IconButton(
                                    onClick = { viewModel.cetakStruk(d) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Text("🖨️", fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                // Trash Delete Icon
                                IconButton(
                                    onClick = { viewModel.requestHapusDebitur(d.nama) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Text("🗑️", fontSize = 13.sp)
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
fun DeleteConfirmDialog(
    targetNama: String,
    sandiInput: String,
    onSandiChange: (String) -> Unit,
    errorText: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    darkTheme: Boolean
) {
    val textMain = if (darkTheme) RzDarkTextMain else RzLightTextMain
    val textSub = if (darkTheme) RzDarkTextSub else RzLightTextSub

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (darkTheme) Color(0xFF060D1B) else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .border(2.dp, RzBlue, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(RzBlue.copy(alpha = 0.15f))
                            .border(2.dp, RzBlue.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🔑", fontSize = 24.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "OTORISASI HAPUS DATA",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = textMain
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Masukkan sandi administrator untuk melenyapkan debitur atas nama:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = textSub,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = targetNama.uppercase(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = RzBlue,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Sandi Password Input field
                    OutlinedTextField(
                        value = sandiInput,
                        onValueChange = onSandiChange,
                        placeholder = { Text("••••••••") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RzBlue,
                            unfocusedBorderColor = if (darkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
                            focusedTextColor = textMain,
                            unfocusedTextColor = textMain,
                            focusedPlaceholderColor = textSub.copy(alpha = 0.5f),
                            unfocusedPlaceholderColor = textSub.copy(alpha = 0.5f)
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_auth_password_input")
                    )

                    if (errorText != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = errorText,
                            color = RzRose,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f),
                                contentColor = textMain
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text("BATAL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onConfirm,
                            colors = ButtonDefaults.buttonColors(containerColor = RzBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("delete_auth_confirm_button")
                        ) {
                            Text("HAPUS", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StrukPreviewDialog(
    peminjam: Peminjam,
    onDismiss: () -> Unit,
    darkTheme: Boolean,
    hitungAngsuran: (Double) -> Double
) {
    val context = LocalContext.current
    val textMain = if (darkTheme) RzDarkTextMain else RzLightTextMain
    val textSub = if (darkTheme) RzDarkTextSub else RzLightTextSub

    val systemDate = remember {
        val sdf = SimpleDateFormat("dd MMM yyyy (HH:mm)", Locale("id", "ID"))
        sdf.format(Date())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (darkTheme) Color(0xFF060D1B) else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .border(2.dp, RzBlue, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Title Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📄 PREVIEW BUKTI STRUK",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = RzBlue
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = textSub)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Dynamic canvas-drawn or Composable layout representing the cash slip!
                    // Let's render a gorgeous slip design in Compose
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF03050A))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "RZ-KREDIT // BUKTI RESMI",
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            color = RzBlue,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "SISTEM PENCATATAN FINANSIAL TERVERIFIKASI",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = RzDarkTextSub,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Dotted divisor
                        Canvas(modifier = Modifier.fillMaxWidth().height(1.dp)) {
                            drawRect(color = Color(0xFF1E293B))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Waktu Terbit :", fontSize = 11.sp, color = RzDarkTextSub, fontFamily = FontFamily.Monospace)
                            Text(systemDate, fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        StrukLine(label = "Nama Debitur", value = peminjam.nama.uppercase(), isBlue = true)
                        StrukLine(label = "Plafon Pinjaman", value = formatRupiah(peminjam.nominal))
                        StrukLine(label = "Tenor Keseluruhan", value = "${peminjam.tenor} Bulan")
                        StrukLine(label = "Sisa Tenor Berjalan", value = "${peminjam.sisaTenor} Bulan")
                        StrukLine(label = "Angsuran Per Bulan", value = formatRupiah(hitungAngsuran(peminjam.nominal)))

                        Spacer(modifier = Modifier.height(14.dp))

                        Canvas(modifier = Modifier.fillMaxWidth().height(1.dp)) {
                            drawRect(color = Color(0xFF1E293B))
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Balance block
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(RzBlue.copy(alpha = 0.12f))
                                .border(1.dp, RzBlue, RoundedCornerShape(6.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("TOTAL TUNGGAKAN :", fontSize = 10.sp, color = RzBlue, fontWeight = FontWeight.Black)
                                Text(
                                    text = formatRupiah(peminjam.sisaTagihan),
                                    fontSize = 16.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Dokumen ini sah diterbitkan oleh RzKredit Mobile Server.",
                            fontSize = 8.5.sp,
                            color = RzDarkTextSub,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "https://rzkredit.com // RzFinTech",
                            fontSize = 8.5.sp,
                            color = RzDarkTextSub,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (darkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f),
                                contentColor = textMain
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(44.dp)
                        ) {
                            Text("TUTUP", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                generateAndSaveReceiptBitmap(context, peminjam, systemDate, hitungAngsuran(peminjam.nominal))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RzBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(2f)
                                .height(44.dp)
                                .testTag("download_receipt_button")
                        ) {
                            Text("📥 SIMPAN JPG", fontSize = 11.5.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StrukLine(label: String, value: String, isBlue: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.5.sp, color = RzDarkTextSub, fontFamily = FontFamily.Monospace)
        Text(
            text = value,
            fontSize = 11.5.sp,
            color = if (isBlue) RzBlue else Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

// FORMATTER HELPERS
fun formatRupiah(value: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    format.maximumFractionDigits = 0
    return format.format(value).replace("Rp", "Rp ")
}

fun formatRupiahShort(value: Double): String {
    return if (value >= 1000000) {
        val mill = value / 1000000
        val formatted = if (mill % 1.0 == 0.0) mill.toInt().toString() else String.format("%.1f", mill)
        "Rp ${formatted}jt"
    } else if (value >= 1000) {
        val k = value / 1000
        val formatted = if (k % 1.0 == 0.0) k.toInt().toString() else String.format("%.1f", k)
        "${formatted}k"
    } else {
        value.toInt().toString()
    }
}

// REAL EXPORTER TO CSV FILE
fun exportCsvReport(context: Context, list: List<Peminjam>, notify: (String) -> Unit) {
    if (list.isEmpty()) {
        notify("⚠️ Tidak ada data untuk diekspor!")
        return
    }

    val csvBuilder = StringBuilder()
    csvBuilder.append("NAMA DEBITUR,PLAFON PINJAMAN,TENOR KONTRAK,SISA TENOR,SISA TAGIHAN\n")
    list.forEach { r ->
        val cleanName = r.nama.replace("\"", "\"\"")
        csvBuilder.append("\"$cleanName\",${r.nominal.toInt()},${r.tenor},${r.sisaTenor},${r.sisaTagihan.toInt()}\n")
    }

    val filename = "Laporan_RzKredit_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { stream ->
                    stream.write(csvBuilder.toString().toByteArray())
                }
                notify("📊 Laporan CSV disimpan di folder Downloads!")
            } else {
                notify("❌ Gagal menyimpan laporan.")
            }
        } else {
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(directory, filename)
            file.writeText(csvBuilder.toString())
            notify("📊 Laporan CSV disimpan: ${file.absolutePath}")
        }
    } catch (e: Exception) {
        notify("❌ Error: ${e.message}")
    }
}

// REAL BITMAP GRAPHICS GENERATOR & EXPORTER (FOR RECEIPTS / STRUK)
fun generateAndSaveReceiptBitmap(context: Context, peminjam: Peminjam, systemDate: String, monthlyAngsuran: Double) {
    val width = 600
    val height = 750
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val paintBg = Paint().apply { color = 0xFF060D1B.toInt() }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBg)

    val paintBorder = Paint().apply {
        color = 0xFF0EA5E9.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    canvas.drawRect(15f, 15f, (width - 15).toFloat(), (height - 15).toFloat(), paintBorder)

    val paintText = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    // Title
    paintText.textSize = 28f
    paintText.color = 0xFF0EA5E9.toInt()
    paintText.isFakeBoldText = true
    canvas.drawText("RZ-KREDIT // BUKTI RESMI", (width / 2).toFloat(), 70f, paintText)

    // Subtitle
    paintText.textSize = 12f
    paintText.color = 0xFF94A3B8.toInt()
    paintText.isFakeBoldText = false
    canvas.drawText("SISTEM PENCATATAN FINANSIAL TERVERIFIKASI", (width / 2).toFloat(), 95f, paintText)

    // Divider
    val paintDivider = Paint().apply {
        color = 0xFF1E293B.toInt()
        strokeWidth = 2f
    }
    canvas.drawLine(40f, 125f, (width - 40).toFloat(), 125f, paintDivider)

    // System Date
    paintText.textSize = 15f
    paintText.textAlign = Paint.Align.LEFT
    paintText.color = 0xFF64748B.toInt()
    canvas.drawText("Waktu Terbit :", 40f, 170f, paintText)

    paintText.textAlign = Paint.Align.RIGHT
    paintText.color = 0xFFF8FAFC.toInt()
    paintText.isFakeBoldText = true
    canvas.drawText(systemDate, (width - 40).toFloat(), 170f, paintText)

    // Data Rows
    var yPos = 230f
    fun drawRow(lbl: String, valStr: String, isBlue: Boolean = false) {
        paintText.textAlign = Paint.Align.LEFT
        paintText.color = 0xFF94A3B8.toInt()
        paintText.isFakeBoldText = false
        canvas.drawText(lbl, 40f, yPos, paintText)

        paintText.textAlign = Paint.Align.RIGHT
        paintText.color = if (isBlue) 0xFF0EA5E9.toInt() else 0xFFF8FAFC.toInt()
        paintText.isFakeBoldText = true
        canvas.drawText(valStr, (width - 40).toFloat(), yPos, paintText)

        yPos += 45f
    }

    drawRow("Nama Debitur", peminjam.nama.uppercase(), true)
    drawRow("Plafon Pinjaman", formatRupiah(peminjam.nominal))
    drawRow("Tenor Keseluruhan", "${peminjam.tenor} Bulan")
    drawRow("Sisa Tenor Berjalan", "${peminjam.sisaTenor} Bulan")
    drawRow("Angsuran Per Bulan", formatRupiah(monthlyAngsuran))

    // Divider
    canvas.drawLine(40f, yPos, (width - 40).toFloat(), yPos, paintDivider)
    yPos += 55f

    // Total box background
    val paintBox = Paint().apply { color = 0x1F0EA5E9.toInt() }
    canvas.drawRect(40f, yPos - 35f, (width - 40).toFloat(), yPos + 50f, paintBox)
    val paintBoxBorder = Paint().apply {
        color = 0xFF0EA5E9.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    canvas.drawRect(40f, yPos - 35f, (width - 40).toFloat(), yPos + 50f, paintBoxBorder)

    paintText.textAlign = Paint.Align.LEFT
    paintText.color = 0xFF0EA5E9.toInt()
    paintText.isFakeBoldText = true
    paintText.textSize = 15f
    canvas.drawText("TOTAL TUNGGAKAN :", 60f, yPos + 10f, paintText)

    paintText.textAlign = Paint.Align.RIGHT
    paintText.color = 0xFFF8FAFC.toInt()
    paintText.textSize = 24f
    canvas.drawText(formatRupiah(peminjam.sisaTagihan), (width - 60).toFloat(), yPos + 14f, paintText)

    yPos += 120f

    // Footer
    paintText.textAlign = Paint.Align.CENTER
    paintText.color = 0xFF475569.toInt()
    paintText.textSize = 11f
    paintText.isFakeBoldText = false
    canvas.drawText("Dokumen ini sah diterbitkan oleh RzKredit Mobile Server.", (width / 2).toFloat(), yPos, paintText)
    canvas.drawText("https://rzkredit.com // RzFinTech", (width / 2).toFloat(), yPos + 22f, paintText)

    // Save Bitmap to Storage
    val filename = "STRUK_${peminjam.nama.uppercase().replace(" ", "_")}_${System.currentTimeMillis()}.jpg"
    try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RzKredit")
            }
        }

        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (imageUri != null) {
            resolver.openOutputStream(imageUri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            }
            Toast.makeText(context, "🖨️ Struk disimpan ke Galeri Foto!", Toast.LENGTH_LONG).show()

            // Trigger standard system sharesheet to share receipt directly with customers
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Bukti Struk"))
        } else {
            Toast.makeText(context, "❌ Gagal membuat entri file gambar.", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "❌ Error saving image: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun CloudSyncSettingsDialog(
    currentSpreadsheetUrl: String,
    currentWebAppUrl: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    darkTheme: Boolean
) {
    var spreadsheetInput by remember { mutableStateOf(currentSpreadsheetUrl) }
    var webAppInput by remember { mutableStateOf(currentWebAppUrl) }
    var showInstructions by remember { mutableStateOf(true) }
    
    val context = LocalContext.current
    val containerBg = if (darkTheme) RzDarkPanel else Color.White
    val textMain = if (darkTheme) RzDarkTextMain else RzLightTextMain
    val textSub = if (darkTheme) RzDarkTextSub else RzLightTextSub
    val borderCol = if (darkTheme) RzDarkBorder else RzLightBorder

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = containerBg),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp)
                .border(1.dp, borderCol, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "☁️ SPREADSHEET SINKRONISASI",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = textMain
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text("❌", fontSize = 12.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = "Tautkan aplikasi RzKredit Anda secara langsung ke Google Sheets untuk mengunduh, mengunggah, dan menyinkronkan data debitur & riwayat pembayaran.",
                    fontSize = 12.sp,
                    color = textSub
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Spreadsheet URL Field
                Text(
                    text = "🔗 LINK GOOGLE SPREADSHEET",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textSub
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = spreadsheetInput,
                    onValueChange = { spreadsheetInput = it },
                    placeholder = { Text("https://docs.google.com/spreadsheets/d/...", color = textSub.copy(alpha = 0.5f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("spreadsheet_url_input"),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = textMain),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RzBlue,
                        unfocusedBorderColor = borderCol
                    )
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Open Spreadsheet Button
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(spreadsheetInput.trim()))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "⚠️ Link tidak valid!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RzBlue.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🌐 Buka Spreadsheet di Browser", color = RzBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(18.dp))

                // Apps Script Web App URL Field
                Text(
                    text = "⚙️ WEB APP URL (GOOGLE APPS SCRIPT)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textSub
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = webAppInput,
                    onValueChange = { webAppInput = it },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec", color = textSub.copy(alpha = 0.5f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("web_app_url_input"),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = textMain),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RzBlue,
                        unfocusedBorderColor = borderCol
                    )
                )
                
                Spacer(modifier = Modifier.height(18.dp))
                
                // Expandable Instruction Section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showInstructions = !showInstructions },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = if (darkTheme) Color.Black.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📖 Panduan Menghubungkan Google Sheet",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (darkTheme) RzBlue else RzPrimaryLight
                            )
                            Text(if (showInstructions) "▲" else "▼", fontSize = 10.sp, color = textSub)
                        }
                        
                        if (showInstructions) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Langkah-langkah:\n" +
                                        "1. Pastikan Spreadsheet Anda memiliki dua sheet bernama \"Peminjam\" dan \"HistorySetoran\".\n" +
                                        "2. Masuk ke menu Extensions > Apps Script pada Spreadsheet Anda.\n" +
                                        "3. Hapus kode bawaan, lalu salin dan tempel kode Apps Script RzKredit (tombol salin di bawah).\n" +
                                        "4. Klik 'Deploy' > 'New deployment' > Pilih tipe 'Web App'.\n" +
                                        "5. Di bagian 'Execute as', pilih 'Me' (email Anda). Di bagian 'Who has access', pilih 'Anyone'.\n" +
                                        "6. Klik Deploy, salin tautan Web App yang diberikan, lalu paste ke kotak WEB APP URL di atas.\n" +
                                        "7. Ubah General Access Spreadsheet Anda menjadi 'Anyone with the link' (Viewer) agar data bisa diunduh oleh aplikasi.",
                                fontSize = 11.sp,
                                color = textSub,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clipData = android.content.ClipData.newPlainText("RzKredit Apps Script", getAppsScriptCode())
                                    clipboardManager.setPrimaryClip(clipData)
                                    Toast.makeText(context, "📋 Kode Apps Script berhasil disalin!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RzBlue.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("📋 Salin Kode Apps Script Khusus", color = RzBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Save and Cancel actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textMain),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderCol)
                    ) {
                        Text("Batal", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onSave(spreadsheetInput, webAppInput) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_settings_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = RzGreen)
                    ) {
                        Text("Simpan", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun getAppsScriptCode(): String {
    return """
function doGet(e) {
  var action = e.parameter.action;
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Peminjam");
  if (!sheet) {
    sheet = SpreadsheetApp.getActiveSpreadsheet().insertSheet("Peminjam");
    sheet.appendRow(["Nama Lengkap", "Nominal Peminjam", "Tenor (Bulan)", "Angsuran Per Bulan", "Total Tagihan", "Total Setoran", "Sisa Tagihan", "Sisa Tenor"]);
  }
  
  if (action === "read") {
    var data = sheet.getDataRange().getValues();
    var headers = data[0];
    var list = [];
    for (var i = 1; i < data.length; i++) {
      var row = data[i];
      var item = {};
      for (var j = 0; j < headers.length; j++) {
        item[headers[j]] = row[j];
      }
      list.push(item);
    }
    return ContentService.createTextOutput(JSON.stringify(list)).setMimeType(ContentService.MimeType.JSON);
  }
  return ContentService.createTextOutput("Invalid Action");
}

function doPost(e) {
  var params = JSON.parse(e.postData.contents);
  var action = params.action;
  var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("Peminjam");
  if (!sheet) {
    sheet = SpreadsheetApp.getActiveSpreadsheet().insertSheet("Peminjam");
    sheet.appendRow(["Nama Lengkap", "Nominal Peminjam", "Tenor (Bulan)", "Angsuran Per Bulan", "Total Tagihan", "Total Setoran", "Sisa Tagihan", "Sisa Tenor"]);
  }
  
  if (action === "add") {
    sheet.appendRow([params.nama, params.nominal, params.tenor, params.angsuran, params.totalTagihan, params.terbayar, params.sisaTagihan, params.sisaTenor]);
    return ContentService.createTextOutput("Success Add");
  } else if (action === "setoran") {
    var data = sheet.getDataRange().getValues();
    for (var i = 1; i < data.length; i++) {
      if (data[i][0].toString().toLowerCase() === params.nama.toString().toLowerCase()) {
        sheet.getRange(i + 1, 6).setValue(params.terbayar);
        sheet.getRange(i + 1, 7).setValue(params.sisaTagihan);
        sheet.getRange(i + 1, 8).setValue(params.sisaTenor);
        
        // Append to HistorySetoran sheet
        var histSheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName("HistorySetoran");
        if (!histSheet) {
          histSheet = SpreadsheetApp.getActiveSpreadsheet().insertSheet("HistorySetoran");
          histSheet.appendRow(["Tanggal", "Nama Peminjam", "Jumlah Setoran"]);
        }
        
        var formattedDate = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), "M/d/yyyy HH:mm:ss");
        histSheet.appendRow([formattedDate, params.nama, params.jumlahSetoran]);
        
        return ContentService.createTextOutput("Success Setoran");
      }
    }
    return ContentService.createTextOutput("Not Found");
  } else if (action === "delete") {
    var data = sheet.getDataRange().getValues();
    for (var i = 1; i < data.length; i++) {
      if (data[i][0].toString().toLowerCase() === params.nama.toString().toLowerCase()) {
        sheet.deleteRow(i + 1);
        return ContentService.createTextOutput("Success Delete");
      }
    }
    return ContentService.createTextOutput("Not Found");
  }
  return ContentService.createTextOutput("Invalid Action");
}
    """.trimIndent()
}
