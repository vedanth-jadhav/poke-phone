package co.interaction.pokephone.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import co.interaction.pokephone.network.PokePhoneBackend
import co.interaction.pokephone.settings.AppPreferences
import co.interaction.pokephone.ui.Amber
import co.interaction.pokephone.ui.Ice
import co.interaction.pokephone.ui.Ink
import co.interaction.pokephone.ui.Line
import co.interaction.pokephone.ui.Panel
import co.interaction.pokephone.ui.PokeTheme
import co.interaction.pokephone.ui.Red
import co.interaction.pokephone.ui.TextMuted
import co.interaction.pokephone.ui.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val source = intent.getStringExtra(EXTRA_SOURCE) ?: "unknown"
        setContent {
            PokeTheme {
                CaptureScreen(
                    source = source,
                    finishActivity = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_SOURCE = "source"
    }
}

private enum class CaptureStage(val label: String) {
    Preparing("ready"),
    Recording("listening"),
    Saving("saving"),
    Sending("sending"),
    Done("done"),
    Error("failed")
}

@Composable
private fun CaptureScreen(
    source: String,
    finishActivity: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val backend = remember { PokePhoneBackend() }
    val recorder = remember { AudioNoteRecorder(context) }
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(CaptureStage.Preparing) }
    var message by remember { mutableStateOf("Hold your thought. Start speaking.") }
    var amplitude by remember { mutableFloatStateOf(0f) }
    var stopRequested by remember { mutableStateOf(false) }
    var permissionReady by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionReady = granted
        if (!granted) {
            stage = CaptureStage.Error
            message = "Microphone permission is required."
        }
    }

    LaunchedEffect(permissionReady) {
        if (prefs.backendUrl.isBlank() || prefs.uploadToken.isBlank()) {
            stage = CaptureStage.Error
            message = "Open the app first and save Worker URL + upload token."
            return@LaunchedEffect
        }

        if (!permissionReady) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return@LaunchedEffect
        }

        runCatching {
            recorder.start()
        }.onFailure { error ->
            stage = CaptureStage.Error
            message = "Could not start microphone: ${error.message ?: "Android blocked recording"}"
            return@LaunchedEffect
        }

        stage = CaptureStage.Recording
        message = "Speak naturally. Silence or Stop will send it."

        var quietTicks = 0
        var elapsedTicks = 0
        while (stage == CaptureStage.Recording && !stopRequested) {
            val normalized = (recorder.maxAmplitude().toFloat() / 32767f).coerceIn(0f, 1f)
            amplitude = normalized
            elapsedTicks += 1
            if (elapsedTicks > 8 && normalized < 0.035f) quietTicks += 1 else quietTicks = 0
            if (quietTicks >= 11) stopRequested = true
            delay(180)
        }

        if (stage == CaptureStage.Recording) {
            scope.launch {
                stage = CaptureStage.Saving
                message = "Saving audio..."
                val note = runCatching { recorder.stop() }.getOrElse { error ->
                    stage = CaptureStage.Error
                    message = "Could not save audio: ${error.message ?: "recording failed"}"
                    return@launch
                }

                stage = CaptureStage.Sending
                message = "Sending to Poke..."
                val result = backend.uploadVoiceNote(
                    baseUrl = prefs.backendUrl,
                    uploadToken = prefs.uploadToken,
                    audioFile = note.file,
                    durationMs = note.durationMs,
                    source = source
                )
                note.file.delete()

                if (result.ok) {
                    stage = CaptureStage.Done
                    message = "Sent to Poke."
                    delay(850)
                    finishActivity()
                } else {
                    stage = CaptureStage.Error
                    message = "Send failed: ${result.statusCode} ${result.body.take(90)}"
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (stage == CaptureStage.Recording || stage == CaptureStage.Preparing) {
                recorder.cancel()
            }
        }
    }

    CaptureVisual(
        stage = stage,
        message = message,
        amplitude = amplitude,
        onStop = { stopRequested = true },
        onClose = {
            recorder.cancel()
            finishActivity()
        }
    )
}

@Composable
private fun CaptureVisual(
    stage: CaptureStage,
    message: String,
    amplitude: Float,
    onStop: () -> Unit,
    onClose: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "shimmer")
    val shimmer by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer-progress"
    )
    val collapse by animateFloatAsState(
        targetValue = if (stage == CaptureStage.Saving || stage == CaptureStage.Sending || stage == CaptureStage.Done) 1f else 0f,
        animationSpec = tween(620),
        label = "collapse"
    )
    val pulse by animateFloatAsState(
        targetValue = if (stage == CaptureStage.Recording) amplitude else 0.12f,
        animationSpec = tween(180),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDD050609))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val edgeWidth = 5.dp.toPx()
            val inset = edgeWidth / 2f
            val glow = 0.35f + shimmer * 0.55f + pulse * 0.35f
            val shimmerOffset = shimmer * size.height
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Amber.copy(alpha = glow), Ice.copy(alpha = 0.55f), Amber.copy(alpha = glow)),
                    start = Offset(0f, shimmerOffset - size.height),
                    end = Offset(size.width, shimmerOffset)
                ),
                topLeft = Offset(inset, inset),
                size = Size(size.width - edgeWidth, size.height - edgeWidth),
                cornerRadius = CornerRadius(34.dp.toPx()),
                style = Stroke(width = edgeWidth, cap = StrokeCap.Round)
            )

            val islandWidth = 136.dp.toPx() - collapse * 76.dp.toPx()
            val islandHeight = 42.dp.toPx() - collapse * 10.dp.toPx()
            val centerY = 58.dp.toPx() + collapse * 6.dp.toPx()
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.82f),
                topLeft = Offset((size.width - islandWidth) / 2f, centerY - islandHeight / 2f),
                size = Size(islandWidth, islandHeight),
                cornerRadius = CornerRadius(28.dp.toPx())
            )
            drawCircle(
                color = Ice.copy(alpha = 0.28f + pulse * 0.45f),
                radius = 42.dp.toPx() + pulse * 42.dp.toPx() - collapse * 48.dp.toPx(),
                center = Offset(size.width / 2f, size.height * (0.46f - collapse * 0.37f)),
                blendMode = BlendMode.Screen
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                modifier = Modifier.size(86.dp + (pulse * 24).dp),
                shape = CircleShape,
                color = if (stage == CaptureStage.Error) Red.copy(alpha = 0.95f) else Amber.copy(alpha = 0.95f),
                shadowElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        tint = Ink
                    )
                }
            }

            Text(
                text = stage.label,
                color = TextPrimary,
                fontSize = 38.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp
            )
            Text(
                text = message,
                color = TextMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f).height(54.dp),
                border = BorderStroke(1.dp, Line),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                onClick = onClose
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Cancel")
            }

            Button(
                modifier = Modifier.weight(1f).height(54.dp),
                enabled = stage == CaptureStage.Recording,
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink),
                onClick = onStop
            ) {
                Icon(Icons.Rounded.Stop, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Stop")
            }
        }
    }
}
