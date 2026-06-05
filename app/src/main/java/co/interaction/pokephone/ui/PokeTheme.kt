package co.interaction.pokephone.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF090A0D)
val InkLift = Color(0xFF12141A)
val Panel = Color(0xFF171920)
val Line = Color(0xFF2C3039)
val TextPrimary = Color(0xFFF4F0E8)
val TextMuted = Color(0xFFA9ABB2)
val Amber = Color(0xFFF2B763)
val Ice = Color(0xFF8ED8FF)
val Red = Color(0xFFFF7F6E)

@Composable
fun PokeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Amber,
            secondary = Ice,
            background = Ink,
            surface = InkLift,
            surfaceVariant = Panel,
            outline = Line,
            onPrimary = Ink,
            onSecondary = Ink,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
            onSurfaceVariant = TextMuted,
            error = Red,
            onError = Ink
        ),
        content = content
    )
}
