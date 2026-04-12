package com.silenceofthelambda.dergplayer.ui.tui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TuiText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TuiTheme.colors.primary,
    fontWeight: FontWeight = FontWeight.Normal,
    style: TextStyle = TuiTheme.typography
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style.copy(fontWeight = fontWeight)
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
    modifier: Modifier = Modifier,
    segments: Int = 20
) {
    val filledSegments = (progress * segments).toInt().coerceIn(0, segments)
    val emptySegments = segments - filledSegments
    
    val barText = buildString {
        append("[")
        repeat(filledSegments) { append("█") }
        repeat(emptySegments) { append("░") }
        append("]")
    }
    
    TuiText(text = barText, modifier = modifier)
}
