package com.miqat.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)
val CardGrey = Color(0xFF202020)
val LightGrey = Color(0xFFBBBBBB)
val DividerGrey = Color(0xFF3A3A3A)

// Permanent dark theme: there is deliberately no light scheme and no system-based switching.
private val MiqatColors = darkColorScheme(
    primary = White, onPrimary = Black,
    secondary = White, onSecondary = Black,
    background = Black, onBackground = White,
    surface = Black, onSurface = White,
    surfaceVariant = CardGrey, onSurfaceVariant = LightGrey,
    surfaceContainer = CardGrey, surfaceContainerHigh = CardGrey, surfaceContainerHighest = CardGrey,
    secondaryContainer = CardGrey, onSecondaryContainer = White,
    outline = DividerGrey, outlineVariant = DividerGrey
)

@Composable
fun MiqatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MiqatColors, content = content)
}

@Composable
fun MiqatCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = CardGrey, contentColor = White)
    val shape = RoundedCornerShape(16.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

@Composable
fun MiqatButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = White),
        border = BorderStroke(1.dp, White)
    ) { Text(text) }
}
