package com.breathe2unlock

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.breathe2unlock.breathing.AudioBreathMonitor
import com.breathe2unlock.breathing.BreathCycleFusionEngine
import com.breathe2unlock.breathing.CameraBreathPreview
import com.breathe2unlock.usage.UsageStatsReader
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Breathe2UnlockApp() }
    }
}

private val AppGreen = Color(0xFF217A5B)
private val PaleGreen = Color(0xFFEAF5EF)
private val Ink = Color(0xFF18352B)
private val Muted = Color(0xFF63756D)

@Composable
private fun Breathe2UnlockApp() {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val usageReader = remember { UsageStatsReader(context) }
    val preferences = remember { context.getSharedPreferences("breathe2unlock", Activity.MODE_PRIVATE) }

    var usageAccess by remember { mutableStateOf(usageReader.hasUsageAccess()) }
    var usageMinutes by remember { mutableIntStateOf(0) }
    var dailyLimit by remember { mutableIntStateOf(preferences.getInt("daily_limit_minutes", 120)) }
    var unlockUntil by remember { mutableLongStateOf(preferences.getLong("unlock_until_ms", 0L)) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showBreathingSession by remember { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }

    val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    val hasMicrophone = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    val hasSensorPermissions = hasCamera && hasMicrophone

    val sensorPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val cameraGranted = result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val microphoneGranted = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (cameraGranted && microphoneGranted) {
            permissionMessage = null
            showBreathingSession = true
        } else {
            permissionMessage = "Camera and microphone access are needed for combined breathing detection. You can enable them in Android app settings."
        }
    }

    val startBreathing = {
        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val microphoneGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (cameraGranted && microphoneGranted) {
            showBreathingSession = true
        } else {
            sensorPermissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    // Refresh while the app is visible and again when the user returns from Settings.
    LaunchedEffect(usageAccess) {
        while (isActive) {
            usageAccess = usageReader.hasUsageAccess()
            if (usageAccess) {
                usageMinutes = (usageReader.todayUsageMillis() / 60_000L).toInt()
            }
            nowMs = System.currentTimeMillis()
            delay(15_000L)
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usageAccess = usageReader.hasUsageAccess()
                if (usageAccess) usageMinutes = (usageReader.todayUsageMillis() / 60_000L).toInt()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val accessActive = nowMs < unlockUntil
    val focusGateActive = usageAccess && usageMinutes >= dailyLimit && !accessActive

    BreatheTheme {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            when {
                showBreathingSession -> BreathingSessionScreen(
                    onComplete = {
                        val newUnlockUntil = System.currentTimeMillis() + 5 * 60_000L
                        unlockUntil = newUnlockUntil
                        nowMs = System.currentTimeMillis()
                        preferences.edit().putLong("unlock_until_ms", newUnlockUntil).apply()
                        showBreathingSession = false
                        Toast.makeText(context, "5-minute in-app access restored", Toast.LENGTH_SHORT).show()
                    },
                    onCancel = { showBreathingSession = false }
                )
                focusGateActive -> FocusGateScreen(
                    usageMinutes = usageMinutes,
                    limitMinutes = dailyLimit,
                    permissionMessage = permissionMessage,
                    onStart = startBreathing
                )
                else -> DashboardScreen(
                    usageAccess = usageAccess,
                    usageMinutes = usageMinutes,
                    dailyLimit = dailyLimit,
                    accessActive = accessActive,
                    unlockUntil = unlockUntil,
                    nowMs = nowMs,
                    permissionMessage = permissionMessage,
                    onOpenUsageSettings = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        } catch (_: Exception) {
                            Toast.makeText(context, "Usage Access settings are unavailable on this device", Toast.LENGTH_LONG).show()
                        }
                    },
                    onDailyLimitChange = { newLimit ->
                        dailyLimit = newLimit
                        preferences.edit().putInt("daily_limit_minutes", newLimit).apply()
                    },
                    onStartBreathing = startBreathing
                )
            }
        }
    }
}

@Composable
private fun BreatheTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = AppGreen,
        onPrimary = Color.White,
        secondary = Color(0xFF5A8C75),
        background = Color(0xFFF7FAF8),
        surface = Color.White,
        onSurface = Ink,
        onSurfaceVariant = Muted,
        outline = Color(0xFFD9E5DE)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun DashboardScreen(
    usageAccess: Boolean,
    usageMinutes: Int,
    dailyLimit: Int,
    accessActive: Boolean,
    unlockUntil: Long,
    nowMs: Long,
    permissionMessage: String?,
    onOpenUsageSettings: () -> Unit,
    onDailyLimitChange: (Int) -> Unit,
    onStartBreathing: () -> Unit
) {
    val progress = if (dailyLimit > 0) (usageMinutes.toFloat() / dailyLimit).coerceIn(0f, 1f) else 0f
    val minutesLeft = ((unlockUntil - nowMs).coerceAtLeast(0L) / 60_000L).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Breathe2Unlock", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink)
        Text("Make room for a mindful pause.", style = MaterialTheme.typography.bodyLarge, color = Muted)

        Card(
            colors = CardDefaults.cardColors(containerColor = PaleGreen),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("TODAY'S PHONE USE", style = MaterialTheme.typography.labelMedium, color = Muted, fontWeight = FontWeight.Bold)
                Text(if (usageAccess) formatDuration(usageMinutes) else "Not connected", style = MaterialTheme.typography.headlineLarge, color = Ink, fontWeight = FontWeight.Bold)
                if (usageAccess) {
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth().height(9.dp),
                        color = AppGreen,
                        trackColor = Color(0xFFD5E7DC)
                    )
                    Text("${usageMinutes} of ${dailyLimit} minutes", color = Muted, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Connect Usage Access to read today's foreground app time.", color = Muted)
                    Button(onClick = onOpenUsageSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("Enable Usage Access")
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Daily usage limit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${dailyLimit} minutes (${dailyLimit / 60}h ${dailyLimit % 60}m)", style = MaterialTheme.typography.headlineSmall, color = AppGreen, fontWeight = FontWeight.Bold)
                Slider(
                    value = dailyLimit.toFloat(),
                    onValueChange = { onDailyLimitChange((it / 30f).roundToInt() * 30) },
                    valueRange = 30f..360f,
                    steps = 10
                )
                Text("The focus gate appears inside Breathe2Unlock when this total is reached.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (accessActive) {
            Card(colors = CardDefaults.cardColors(containerColor = PaleGreen), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(AppGreen, CircleShape))
                    Text("  In-app access restored · about $minutesLeft min remaining", color = Ink, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Button(
            onClick = onStartBreathing,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Start breathing exercise · 10 cycles", fontWeight = FontWeight.SemiBold)
        }

        if (!permissionMessage.isNullOrBlank()) {
            Text(permissionMessage, color = Color(0xFF9B4F36), style = MaterialTheme.typography.bodySmall)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Personal phone mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Android does not let a normal personal app hard-lock every other app. This MVP tracks usage and gates access inside Breathe2Unlock only. Your Home button, system settings and emergency calling remain under Android's control.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Text("Your camera and microphone are used only during an exercise. Raw video and audio are not saved or uploaded.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FocusGateScreen(
    usageMinutes: Int,
    limitMinutes: Int,
    permissionMessage: String?,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(94.dp).background(PaleGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("Breathe", color = AppGreen, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(22.dp))
        Text("Time for a breathing break", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "Today's tracked use is ${formatDuration(usageMinutes)}. Your configured limit is ${formatDuration(limitMinutes)}.",
            color = Muted
        )
        Spacer(Modifier.height(22.dp))
        Text("Complete 10 detected breathing cycles to restore 5 minutes of access inside this app.", color = Ink)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Begin breathing verification")
        }
        if (!permissionMessage.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(permissionMessage, color = Color(0xFF9B4F36))
        }
        Spacer(Modifier.height(22.dp))
        Text("This is a personal-mode focus prompt, not a system-wide phone lock. You can always leave the app or use Android emergency functions.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BreathingSessionScreen(
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    var cycleCount by remember { mutableIntStateOf(0) }
    var cameraStatus by remember { mutableStateOf("Starting camera…") }
    var audioError by remember { mutableStateOf<String?>(null) }
    var completionHandled by remember { mutableStateOf(false) }

    val detector = remember {
        BreathCycleFusionEngine { newCount ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                cycleCount = newCount
            }
        }
    }
    val audioMonitor = remember {
        AudioBreathMonitor(
            onRms = { detector.addAudioRms(it) },
            onError = { message ->
                android.os.Handler(android.os.Looper.getMainLooper()).post { audioError = message }
            }
        )
    }

    DisposableEffect(Unit) {
        detector.reset()
        audioMonitor.start()
        onDispose {
            audioMonitor.stop()
            detector.reset()
        }
    }

    LaunchedEffect(cycleCount) {
        if (cycleCount >= 10 && !completionHandled) {
            completionHandled = true
            delay(350L)
            onComplete()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Breathing verification", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Ink)
            OutlinedButton(onClick = onCancel) { Text("Close") }
        }
        Text("Breathe normally. Do not force deep breaths or hold your breath.", color = Muted)

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CameraBreathPreview(
                    onMetric = { detector.addCameraMetric(it) },
                    onStatus = { cameraStatus = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(cameraStatus, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = PaleGreen), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DETECTED CYCLES", style = MaterialTheme.typography.labelMedium, color = Muted, fontWeight = FontWeight.Bold)
                Text("$cycleCount / 10", style = MaterialTheme.typography.displayMedium, color = AppGreen, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(
                    progress = (cycleCount / 10f).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = AppGreen,
                    trackColor = Color(0xFFD5E7DC)
                )
                Text("One cycle means an inhale followed by an exhale.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Microphone", fontWeight = FontWeight.SemiBold, color = Ink)
                Text(
                    audioError ?: "Listening for breath-related sound variation. Audio is processed in memory and not recorded to a file.",
                    color = if (audioError == null) Muted else Color(0xFF9B4F36),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Text(
            "Experimental sensor fusion: the prototype looks for torso-motion oscillation and recent microphone energy variation. It can miss breaths or count unrelated movement/noise. Keep your upper body visible, place the phone steadily, and use a quiet room.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall
        )
        Text("Stop immediately if you feel dizzy, short of breath or uncomfortable.", color = Color(0xFF9B4F36), style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatDuration(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
