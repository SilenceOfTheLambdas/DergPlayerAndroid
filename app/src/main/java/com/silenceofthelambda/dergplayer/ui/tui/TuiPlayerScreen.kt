package com.silenceofthelambda.dergplayer.ui.tui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silenceofthelambda.dergplayer.model.ShuffleMode
import com.silenceofthelambda.dergplayer.ui.PlayerViewModel
import androidx.media3.common.Player

@Composable
fun TuiPlayerScreen(
    currentTitle: String = "Unknown Title",
    currentArtist: String = "Unknown Artist",
    progress: Float = 0.5f,
    currentTime: String = "00:00",
    totalTime: String = "00:00",
    isPlaying: Boolean = false,
    isLiked: Boolean = false,
    shuffleMode: ShuffleMode = ShuffleMode.OFF,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    currentScheme: String = "Dynamic",
    asciiArt: String = "",
    nextTitle: String = "None",
    volume: Float = 1.0f,
    systemStatus: String = "",
    onPrevious: () -> Unit = {},
    onTogglePlay: () -> Unit = {},
    onNext: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onSetScheme: (String) -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onVolumeChange: (Float) -> Unit = {},
    onBack: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize().background(TuiTheme.colors.background)) {
        ScanlineOverlay()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
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
                    TuiButton(text = "LIBRARY", onClick = onBack)
                }
            }
            
            // Artwork Placeholder
            TuiBorderBox(
                modifier = Modifier.fillMaxWidth().weight(1f),
                title = "ARTWORK"
            ) {
                var offsetX by remember { mutableStateOf(0f) }
                val haptic = LocalHapticFeedback.current

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(onNext, onPrevious) {
                            val threshold = 80.dp.toPx()
                            detectHorizontalDragGestures(
                                onDragStart = { offsetX = 0f },
                                onDragEnd = {
                                    if (offsetX < -threshold) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onNext()
                                    } else if (offsetX > threshold) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onPrevious()
                                    }
                                    offsetX = 0f
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    offsetX += dragAmount
                                    change.consume()
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (asciiArt.isNotEmpty()) {
                        TuiText(
                            text = asciiArt,
                            style = TuiTheme.typography.copy(lineHeight = 6.sp, fontSize = 6.sp),
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    } else {
                        TuiText(
                            text = """
                                     .-----------------------.
                                     |   /~~~~~~~~~~~~~~~\   |
                                     |  /                 \  |
                                     | |   (O)       (O)   | |
                                     | |          V        | |
                                     |  \       _____     /  |
                                     |   \     \_____/   /   |
                                     |    \_____________/    |
                                     |                       |
                                     |     - NO ARTWORK -    |
                                     '-----------------------'
                            """.trimIndent(),
                            style = TuiTheme.typography.copy(lineHeight = 12.sp, fontSize = 10.sp)
                        )
                    }
                }
            }
            
            // Visualizer
            TuiBorderBox(modifier = Modifier.fillMaxWidth().weight(0.5f), title = "VISUALIZER") {
                TuiVisualizer(
                    modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
                    maxHeightLines = 5,
                    isPlaying = isPlaying
                )
            }
            
            // Playback Info
            TuiBorderBox(modifier = Modifier.fillMaxWidth(), title = "PLAYBACK") {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            TuiText("TITLE : $currentTitle", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TuiText("ARTIST: $currentArtist", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TuiText(
                            text = if (isPlaying) " [RUNNING] " else " [PAUSED] ",
                            color = if (isPlaying) TuiTheme.colors.primary else TuiTheme.colors.primary.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TuiText("NEXT  : $nextTitle", style = TuiTheme.typography.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // Progress Bar
            TuiBorderBox(modifier = Modifier.fillMaxWidth(), title = "PROGRESS") {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    TuiProgressBar(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                        onProgressChange = onSeek
                    )
                    
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

            // Volume Control
            TuiBorderBox(modifier = Modifier.fillMaxWidth(), title = "VOLUME") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TuiText("VOL: ", style = TuiTheme.typography.copy(fontSize = 11.sp))
                    TuiProgressBar(
                        progress = volume,
                        modifier = Modifier.weight(1f),
                        onProgressChange = onVolumeChange
                    )
                }
            }
            
            // Controls & Scheme
            TuiBorderBox(modifier = Modifier.fillMaxWidth(), title = "CONTROL PANEL") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TuiButton(
                            text = "PREV",
                            onClick = onPrevious,
                            modifier = Modifier.weight(1f)
                        )
                        TuiButton(
                            text = if (isPlaying) "PAUSE" else "PLAY",
                            onClick = onTogglePlay,
                            modifier = Modifier.weight(1f)
                        )
                        TuiButton(
                            text = "NEXT",
                            onClick = onNext,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TuiButton(
                            text = when(shuffleMode) {
                                ShuffleMode.ON -> "SHUF"
                                ShuffleMode.SMART -> "SMRT"
                                else -> "SHUF"
                            },
                            onClick = onToggleShuffle,
                            active = shuffleMode != ShuffleMode.OFF,
                            modifier = Modifier.weight(1f)
                        )
                        TuiButton(
                            text = when(repeatMode) {
                                Player.REPEAT_MODE_ONE -> "REP1"
                                Player.REPEAT_MODE_ALL -> "REPA"
                                else -> "REPO"
                            },
                            onClick = onToggleRepeat,
                            active = repeatMode != Player.REPEAT_MODE_OFF,
                            modifier = Modifier.weight(1f)
                        )
                        TuiButton(
                            text = "LIKE",
                            onClick = onToggleLike,
                            active = isLiked,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Styled Scheme Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TuiText(
                            text = "SELECT SCHEME: ",
                            style = TuiTheme.typography.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                        
                        val schemes = listOf("Dynamic" to "DYN", "Matrix" to "MTX", "Amber" to "AMB", "Cyberpunk" to "CYB")
                        schemes.forEachIndexed { index, pair ->
                            val (scheme, label) = pair
                            TuiText(
                                text = if (currentScheme == scheme) "[$label]" else " $label ",
                                modifier = Modifier
                                    .clickable { onSetScheme(scheme) }
                                    .padding(horizontal = 4.dp),
                                style = TuiTheme.typography.copy(fontSize = 11.sp),
                                color = if (currentScheme == scheme) TuiTheme.colors.primary else TuiTheme.colors.primary.copy(alpha = 0.5f)
                            )
                            if (index < schemes.size - 1) {
                                TuiText(
                                    text = "·",
                                    color = TuiTheme.colors.primary.copy(alpha = 0.3f),
                                    style = TuiTheme.typography.copy(fontSize = 11.sp)
                                )
                            }
                        }
                    }
                }
            }

            // System Status Bar
            if (systemStatus.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TuiText(
                        text = ">>> $systemStatus <<<",
                        fontWeight = FontWeight.Bold,
                        style = TuiTheme.typography.copy(fontSize = 12.sp)
                    )
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
            isLiked = true,
            shuffleMode = ShuffleMode.ON,
            repeatMode = Player.REPEAT_MODE_ALL
        )
    }
}
