package co.tawipay.topupdesk

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Forest = Color(0xFF416B32)
private val Leaf = Color(0xFFD8F0C7)
private val Cream = Color(0xFFF8F8F0)
private val Ink = Color(0xFF20261D)
private val Muted = Color(0xFF73796F)
private val Surface = Color(0xFFF4F7F3)
private val Border = Color(0xFFDDE3D9)

private val TopUpDeskColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Leaf,
    onPrimaryContainer = Color(0xFF274A20),
    background = Cream,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = Color(0xFFECEFDF),
    onSurfaceVariant = Muted,
    outline = Border,
    error = Color(0xFFB94141),
)

@Composable
fun TopUpDeskTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TopUpDeskColors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
