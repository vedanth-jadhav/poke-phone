package co.interaction.pokephone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assistant
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import co.interaction.pokephone.capture.CaptureActivity
import co.interaction.pokephone.network.PokePhoneBackend
import co.interaction.pokephone.notify.NotificationHelper
import co.interaction.pokephone.settings.AppPreferences
import co.interaction.pokephone.ui.Amber
import co.interaction.pokephone.ui.Ice
import co.interaction.pokephone.ui.Ink
import co.interaction.pokephone.ui.Line
import co.interaction.pokephone.ui.Panel
import co.interaction.pokephone.ui.PokeTheme
import co.interaction.pokephone.ui.TextMuted
import co.interaction.pokephone.ui.TextPrimary
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val backend = PokePhoneBackend()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PokeTheme {
                MainScreen(
                    backend = backend,
                    openAssistantSettings = ::openAssistantSettings,
                    openCapture = { source -> openCapture(source) }
                )
            }
        }
    }

    private fun openAssistantSettings() {
        val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        runCatching { startActivity(intent) }.onFailure {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun openCapture(source: String) {
        startActivity(
            Intent(this, CaptureActivity::class.java)
                .putExtra(CaptureActivity.EXTRA_SOURCE, source)
        )
    }
}

@Composable
private fun MainScreen(
    backend: PokePhoneBackend,
    openAssistantSettings: () -> Unit,
    openCapture: (String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()
    var backendUrl by remember { mutableStateOf(prefs.backendUrl) }
    var uploadToken by remember { mutableStateOf(prefs.uploadToken) }
    var status by remember { mutableStateOf("Not tested") }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        status = if (granted) "Microphone permission granted" else "Microphone permission denied"
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        status = if (granted) "Notification permission granted" else "Notification permission denied"
    }

    LaunchedEffect(Unit) {
        NotificationHelper.ensureChannels(context)
    }

    Scaffold(containerColor = Ink) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Ink, androidx.compose.ui.graphics.Color(0xFF0D1015), Ink)
                    )
                )
                .padding(padding)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Header()

                SetupCard(
                    backendUrl = backendUrl,
                    onBackendUrlChange = { backendUrl = it },
                    uploadToken = uploadToken,
                    onUploadTokenChange = { uploadToken = it },
                    onSave = {
                        prefs.backendUrl = backendUrl
                        prefs.uploadToken = uploadToken
                        status = "Saved on phone"
                    }
                )

                ActionGrid(
                    onMic = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            status = "Microphone already granted"
                        }
                    },
                    onNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            val shown = NotificationHelper.showReadyNotification(context)
                            status = if (shown) "Ready notification shown" else "Notification unavailable"
                        }
                    },
                    onAssistant = openAssistantSettings,
                    onCapture = { openCapture("manual") }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink),
                        onClick = {
                            prefs.backendUrl = backendUrl
                            prefs.uploadToken = uploadToken
                            scope.launch {
                                status = "Testing backend..."
                                val result = backend.health(backendUrl, uploadToken)
                                status = if (result.ok) {
                                    "Backend OK"
                                } else {
                                    "Backend failed: ${result.statusCode} ${result.body.take(80)}"
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Test backend")
                    }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Line),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        onClick = {
                            prefs.backendUrl = backendUrl
                            prefs.uploadToken = uploadToken
                            val shown = NotificationHelper.showReadyNotification(context)
                            status = if (shown) "Ready notification shown" else "Grant notification permission first"
                        }
                    ) {
                        Icon(Icons.Rounded.Notifications, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Ready alert", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Panel,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Line)
                ) {
                    Text(
                        modifier = Modifier.padding(14.dp),
                        text = status,
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Poke Phone",
            color = TextPrimary,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp
        )
        Text(
            text = "Hold power. Ramble. Send raw audio to Poke.",
            color = TextMuted,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun SetupCard(
    backendUrl: String,
    onBackendUrlChange: (String) -> Unit,
    uploadToken: String,
    onUploadTokenChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Panel,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Line)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = backendUrl,
                onValueChange = onBackendUrlChange,
                label = { Text("Worker URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Uri
                )
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = uploadToken,
                onValueChange = onUploadTokenChange,
                label = { Text("Upload token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Password
                )
            )
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = Ice, contentColor = Ink),
                onClick = onSave
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Save settings")
            }
        }
    }
}

@Composable
private fun ActionGrid(
    onMic: () -> Unit,
    onNotifications: () -> Unit,
    onAssistant: () -> Unit,
    onCapture: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SetupAction("Mic", Icons.Rounded.Mic, onMic, Modifier.weight(1f))
            SetupAction("Assistant", Icons.Rounded.Assistant, onAssistant, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SetupAction("Notify", Icons.Rounded.Notifications, onNotifications, Modifier.weight(1f))
            SetupAction("Start", Icons.Rounded.Send, onCapture, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SetupAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        modifier = modifier.height(54.dp),
        border = BorderStroke(1.dp, Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
        onClick = onClick
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
