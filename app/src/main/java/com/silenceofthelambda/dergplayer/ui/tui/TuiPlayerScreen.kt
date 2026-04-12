package com.silenceofthelambda.dergplayer.ui.tui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silenceofthelambda.dergplayer.ui.PlayerViewModel

@Composable
fun TuiPlayerScreen(
    currentTitle: String = "Unknown Title",
    currentArtist: String = "Unknown Artist",
    progress: Float = 0.5f,
    currentTime: String = "00:00",
    totalTime: String = "00:00",
    isPlaying: Boolean = false,
    isLiked: Boolean = false,
    currentScheme: String = "Matrix",
    onPrevious: () -> Unit = {},
    onTogglePlay: () -> Unit = {},
    onNext: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    onSetScheme: (String) -> Unit = {},
    onToggleTui: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize().background(TuiTheme.colors.background)) {
        ScanlineOverlay()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            TuiBorderBox(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TuiText(
                        text = "DERG PLAYER V1.1.0-TUI",
                        fontWeight = FontWeight.Bold
                    )
                    TuiButton(text = "EXIT TUI", onClick = onToggleTui)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Artwork Placeholder
            TuiBorderBox(
                modifier = Modifier.fillMaxWidth().weight(1f),
                title = "VISUALIZER"
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TuiText(
                        text = """
                            .------------------------.
                            |                        |
                            |       /^^^^^^^\        |
                            |      |  (o)(o)  |      |
                            |      |    ^^    |      |
                            |       \_______/        |
                            |                        |
                            '------------------------'
                        """.trimIndent(),
                        style = TuiTheme.typography.copy(lineHeight = 12.sp, fontSize = 10.sp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Info
            TuiBorderBox(modifier = Modifier.fillMaxWidth(), title = "NOW PLAYING") {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    TuiText("TITLE : $currentTitle", fontWeight = FontWeight.Bold)
                    TuiText("ARTIST: $currentArtist")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress
            TuiBorderBox(modifier = Modifier.fillMaxWidth(), title = "PROGRESS") {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    TuiProgressBar(progress = progress, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TuiText(currentTime)
                        TuiText(totalTime)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Controls
            TuiBorderBox(modifier = Modifier.fillMaxWidth(), title = "CONTROLS") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TuiButton(text = "< PREV", onClick = onPrevious)
                        TuiButton(text = if (isPlaying) "|| PAUSE" else "> PLAY", onClick = onTogglePlay)
                        TuiButton(text = "NEXT >", onClick = onNext)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TuiButton(
                        text = if (isLiked) "<3 LIKED" else "LIKE?",
                        onClick = onToggleLike
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scheme Selector
            TuiBorderBox(modifier = Modifier.fillMaxWidth(), title = "SCHEME") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("Matrix", "Amber", "Cyberpunk", "Dynamic").forEach { scheme ->
                        TuiText(
                            text = if (currentScheme == scheme) "[$scheme]" else " $scheme ",
                            modifier = Modifier.clickable { onSetScheme(scheme) },
                            fontWeight = if (currentScheme == scheme) FontWeight.Bold else FontWeight.Normal,
                            color = if (currentScheme == scheme) TuiTheme.colors.primary else TuiTheme.colors.primary.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TuiPlayerScreenPreview() {
    TuiTheme {
        TuiPlayerScreen(
            currentTitle = "Song of the Lambda",
            currentArtist = "Derg feat. Silence",
            progress = 0.42f,
            currentTime = "01:45",
            totalTime = "04:10",
            isPlaying = true,
            isLiked = true
        )
    }
}
