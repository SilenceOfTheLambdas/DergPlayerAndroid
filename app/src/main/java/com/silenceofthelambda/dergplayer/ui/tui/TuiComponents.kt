package com.silenceofthelambda.dergplayer.ui.tui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
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
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style.copy(fontWeight = fontWeight),
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap
    )
}

@Composable
fun TuiBorderBox(
    modifier: Modifier = Modifier,
    title: String? = null,
    active: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundColor = if (active) TuiTheme.colors.primary else TuiTheme.colors.background
    val foregroundColor = if (active) TuiTheme.colors.background else TuiTheme.colors.primary

    Box(
        modifier = modifier
            .background(backgroundColor)
            .border(1.dp, foregroundColor.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        // Corners decoration
        TuiText(text = "┌", color = foregroundColor, modifier = Modifier.align(Alignment.TopStart).offset(x = (-8).dp, y = (-8).dp))
        TuiText(text = "┐", color = foregroundColor, modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp))
        TuiText(text = "└", color = foregroundColor, modifier = Modifier.align(Alignment.BottomStart).offset(x = (-8).dp, y = 8.dp))
        TuiText(text = "┘", color = foregroundColor, modifier = Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = 8.dp))

        if (title != null) {
            TuiText(
                text = " $title ",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-20).dp)
                    .background(backgroundColor)
                    .padding(horizontal = 4.dp),
                color = foregroundColor,
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
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .background(if (active) TuiTheme.colors.primary else TuiTheme.colors.background)
            .border(1.dp, TuiTheme.colors.primary)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        TuiText(
            text = "[ $text ]",
            color = if (active) TuiTheme.colors.background else TuiTheme.colors.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TuiProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    onProgressChange: ((Float) -> Unit)? = null
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.then(
            if (onProgressChange != null) {
                Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            onProgressChange((change.position.x / size.width).coerceIn(0f, 1f))
                            change.consume()
                        },
                        onDragStart = { offset ->
                            onProgressChange((offset.x / size.width).coerceIn(0f, 1f))
                        }
                    )
                }
            } else Modifier
        )
    ) {
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

        TuiText(
            text = barText,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
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
    magnitudes: List<Float>,
    modifier: Modifier = Modifier,
    maxHeightLines: Int = 6
) {
    val primaryColor = TuiTheme.colors.primary
    
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val totalWidth = size.width
            val totalHeight = size.height
            
            val displayMagnitudes = if (magnitudes.isEmpty()) {
                List(20) { 0f }
            } else {
                magnitudes
            }
            
            val barCount = displayMagnitudes.size
            val barW = totalWidth / barCount
            val blockH = totalHeight / maxHeightLines
            
            for (i in 0 until barCount) {
                val value = displayMagnitudes[i]
                
                // Draw a very faint baseline for all bars
                drawRect(
                    color = primaryColor.copy(alpha = 0.1f),
                    topLeft = Offset(i * barW + barW * 0.1f, totalHeight - 2.dp.toPx()),
                    size = Size(barW * 0.8f, 2.dp.toPx())
                )

                val blocksToDraw = (value * maxHeightLines).toInt().coerceIn(0, maxHeightLines)
                
                for (j in 0 until blocksToDraw) {
                    drawRect(
                        color = primaryColor,
                        topLeft = Offset(i * barW + barW * 0.1f, totalHeight - (j + 1) * blockH + blockH * 0.1f),
                        size = Size(barW * 0.8f, blockH * 0.8f)
                    )
                }
                
                // Partial block for smoother look
                val remainder = (value * maxHeightLines) - blocksToDraw
                if (remainder > 0.2f && blocksToDraw < maxHeightLines) {
                    val partialH = if (remainder > 0.6f) blockH * 0.8f else blockH * 0.4f
                    val yOffset = if (remainder > 0.6f) blockH * 0.1f else blockH * 0.5f
                    
                    drawRect(
                        color = primaryColor,
                        topLeft = Offset(i * barW + barW * 0.1f, totalHeight - (blocksToDraw + 1) * blockH + yOffset),
                        size = Size(barW * 0.8f, partialH)
                    )
                }
            }
        }
    }
}
