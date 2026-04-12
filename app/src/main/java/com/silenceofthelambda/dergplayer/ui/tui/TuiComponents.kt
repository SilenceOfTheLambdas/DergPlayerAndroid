package com.silenceofthelambda.dergplayer.ui.tui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TuiText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TuiTheme.colors.primary,
    fontWeight: FontWeight = FontWeight.Normal,
    style: TextStyle = TuiTheme.typography,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style.copy(fontWeight = fontWeight),
        maxLines = maxLines,
        overflow = overflow
    )
}

@Composable
fun TuiBorderBox(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .border(1.dp, TuiTheme.colors.primary.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        // Corners decoration
        TuiText(text = "┌", modifier = Modifier.align(Alignment.TopStart).offset(x = (-8).dp, y = (-8).dp))
        TuiText(text = "┐", modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp))
        TuiText(text = "└", modifier = Modifier.align(Alignment.BottomStart).offset(x = (-8).dp, y = 8.dp))
        TuiText(text = "┘", modifier = Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = 8.dp))

        if (title != null) {
            TuiText(
                text = " $title ",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-20).dp)
                    .background(TuiTheme.colors.background)
                    .padding(horizontal = 4.dp),
                color = TuiTheme.colors.primary,
                fontWeight = FontWeight.Bold,
                style = TuiTheme.typography.copy(fontSize = 11.sp)
            )
        }
        content()
    }
}

@Composable
fun ScanlineOverlay() {
    val scanlineColor = TuiTheme.colors.scanlineColor
    Canvas(modifier = Modifier.fillMaxSize()) {
        val lineHeight = 4.dp.toPx()
        val count = (size.height / lineHeight).toInt()
        for (i in 0..count) {
            val y = i * lineHeight
            drawLine(
                color = scanlineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
fun TuiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(TuiTheme.colors.background)
            .border(1.dp, TuiTheme.colors.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        TuiText(
            text = "[ $text ]",
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TuiProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val fontSize = TuiTheme.typography.fontSize
        // Conservative estimate for monospaced font character width
        val charWidth = with(density) { (fontSize.toPx() * 0.8f).toDp() }
        val availableWidth = maxWidth
        val segments = ((availableWidth / charWidth).toInt() - 4).coerceAtLeast(10)

        val filledSegments = (progress * segments).toInt().coerceIn(0, segments)
        val emptySegments = segments - filledSegments

        val barText = buildString {
            append("[")
            repeat(filledSegments) { append("█") }
            repeat(emptySegments) { append("░") }
            append("]")
        }

        TuiText(text = barText)
    }
}

@Composable
fun TuiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    Box(
        modifier = modifier
            .border(1.dp, TuiTheme.colors.primary.copy(alpha = 0.5f))
            .background(TuiTheme.colors.background)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (value.isEmpty()) {
            TuiText(text = "> $placeholder", color = TuiTheme.colors.primary.copy(alpha = 0.3f))
        }
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TuiTheme.typography.copy(color = TuiTheme.colors.primary),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(TuiTheme.colors.primary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TuiText(text = "> ")
                    innerTextField()
                }
            }
        )
    }
}

@Composable
fun TuiLoadingIndicator(modifier: Modifier = Modifier) {
    TuiText(text = " LOADING... ", modifier = modifier.padding(16.dp))
}

@Composable
fun TuiVisualizer(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    maxHeightLines: Int = 6
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val fontSize = TuiTheme.typography.fontSize
        // Estimated width for JetBrains Mono character
        val charWidth = with(density) { (fontSize.toPx() * 0.6f).toDp() }
        
        val barCount = (maxWidth / charWidth).toInt().coerceAtLeast(10)
        val totalSteps = maxHeightLines * 2

        val transition = androidx.compose.animation.core.rememberInfiniteTransition()
        val animatedValues = (0 until barCount).map { i ->
            if (isPlaying) {
                transition.animateFloat(
                    initialValue = 0.1f,
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(
                            durationMillis = (400 + (Math.random() * 600)).toInt(),
                            easing = androidx.compose.animation.core.FastOutSlowInEasing
                        ),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                    ),
                    label = "bar_$i"
                )
            } else {
                androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0.1f) }
            }
        }

        TuiText(
            text = buildString {
                for (l in maxHeightLines - 1 downTo 0) {
                    for (i in 0 until barCount) {
                        val h = (animatedValues[i].value * totalSteps).toInt()
                        if (h >= 2 * l + 2) {
                            append("█")
                        } else if (h == 2 * l + 1) {
                            append("▄")
                        } else {
                            append(" ")
                        }
                    }
                    if (l > 0) append("\n")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
