package app.upsc365.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.upsc365.engine.Engine

data class Pal(
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    val line: Color,
    val text: Color,
    val dim: Color,
    val accent: Color,
    val onAccent: Color,
    val good: Color,
    val warn: Color,
    val bad: Color,
    val dark: Boolean
)

val DarkPal = Pal(
    bg = Color(0xFF0D0E10), surface = Color(0xFF15171A), surfaceHi = Color(0xFF1D2024),
    line = Color(0xFF2A2E34), text = Color(0xFFECEDEF), dim = Color(0xFF9096A0),
    accent = Color(0xFF8C9EFF), onAccent = Color(0xFF0D0E10), good = Color(0xFF5FD0A5),
    warn = Color(0xFFF2B84B), bad = Color(0xFFF07178), dark = true
)

val LightPal = Pal(
    bg = Color(0xFFF6F6F3), surface = Color(0xFFFFFFFF), surfaceHi = Color(0xFFEEEEEA),
    line = Color(0xFFDDDDD8), text = Color(0xFF15171A), dim = Color(0xFF6B7078),
    accent = Color(0xFF4F5FD6), onAccent = Color(0xFFFFFFFF), good = Color(0xFF1E9E74),
    warn = Color(0xFFB7791F), bad = Color(0xFFD64550), dark = false
)

val LocalPal = staticCompositionLocalOf { DarkPal }

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    val pal = if (dark) DarkPal else LightPal
    val scheme = if (dark) {
        darkColorScheme(
            primary = pal.accent, onPrimary = pal.onAccent, background = pal.bg,
            onBackground = pal.text, surface = pal.surface, onSurface = pal.text
        )
    } else {
        lightColorScheme(
            primary = pal.accent, onPrimary = pal.onAccent, background = pal.bg,
            onBackground = pal.text, surface = pal.surface, onSurface = pal.text
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = pal.bg.toArgb()
            window.navigationBarColor = pal.bg.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalPal provides pal) {
            Surface(modifier = Modifier.fillMaxSize(), color = pal.bg, content = content)
        }
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), color = LocalPal.current.dim, fontSize = 11.sp, letterSpacing = 1.2.sp,
        fontWeight = FontWeight.Medium, modifier = modifier
    )
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(16.dp)
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(p.surface)
        .border(1.dp, borderColor ?: p.line, shape)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(modifier = clickable.padding(16.dp), content = content)
}

@Composable
fun Pill(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun Tick(done: Boolean, half: Boolean = false, size: Dp = 28.dp, onClick: () -> Unit) {
    val p = LocalPal.current
    val ring = if (done) p.accent else p.dim
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (done) p.accent else Color.Transparent)
            .border(1.5.dp, ring, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (done) {
            Text(if (half) "½" else "✓", color = p.onAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Dot(done: Boolean) {
    val p = LocalPal.current
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (done) p.accent else Color.Transparent)
            .border(1.dp, if (done) p.accent else p.line, CircleShape)
    )
}

@Composable
fun Bar(fraction: Float, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(p.line)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(p.accent)
        )
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Button(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent)
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    OutlinedButton(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, p.line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = p.text)
    ) { Text(text) }
}

/** Text field that keeps its own text and writes every change through [onSave]. */
@Composable
fun FieldBox(label: String, initial: String, singleLine: Boolean = true, onSave: (String) -> Unit) {
    val p = LocalPal.current
    var text by remember { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onSave(it) },
        label = { Text(label) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = p.accent, unfocusedBorderColor = p.line,
            focusedTextColor = p.text, unfocusedTextColor = p.text,
            cursorColor = p.accent, focusedLabelColor = p.accent, unfocusedLabelColor = p.dim
        )
    )
}

/** One study unit (Mathematics or GS) with completion controls. */
@Composable
fun UnitCard(
    label: String,
    title: String,
    meta: String,
    chips: List<Pair<String, Color>>,
    lines: List<Pair<String, String>>,
    objective: String,
    status: Engine.Status,
    blocked: String?,
    onOpenBlocked: (() -> Unit)?,
    onDone: () -> Unit,
    onSkim: () -> Unit,
    onUndo: () -> Unit,
    onOpen: () -> Unit
) {
    val p = LocalPal.current
    Panel(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(label)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { chip -> Pill(chip.first, chip.second) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (meta.isNotEmpty()) {
            Text(meta, color = p.dim, fontSize = 13.sp)
        }
        if (lines.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            lines.forEach { line ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(line.first, color = p.dim, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                    Text(line.second, color = p.text, fontSize = 13.sp)
                }
            }
        }
        if (objective.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Objective: $objective", color = p.dim, fontSize = 13.sp)
        }
        if (blocked != null) {
            Spacer(Modifier.height(10.dp))
            Text(blocked, color = p.warn, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (onOpenBlocked != null) {
                Spacer(Modifier.height(6.dp))
                GhostButton("Open prerequisite", onOpenBlocked)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status == Engine.Status.PENDING) {
                PrimaryButton("Done", onDone)
                GhostButton("Skim", onSkim)
            } else {
                Text(
                    if (status == Engine.Status.DONE) "Completed" else "Skimmed",
                    color = p.good, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                GhostButton("Undo", onUndo)
            }
        }
    }
}
