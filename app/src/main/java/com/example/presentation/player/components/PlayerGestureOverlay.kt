package com.example.presentation.player.components

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CinemaCyan
import com.example.ui.theme.CinemaPurple
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerGestureOverlay(
    isLocked: Boolean,
    onSingleTap: () -> Unit,
    onDoubleTapSeek: (offsetMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val activity = context as? Activity

    // Double-tap visual indicators
    var showLeftDoubleTap by remember { mutableStateOf(false) }
    var showRightDoubleTap by remember { mutableStateOf(false) }
    var doubleTapTimerJob by remember { mutableStateOf<Job?>(null) }

    // Volume & Brightness HUD
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var currentVolumeFraction by remember { mutableFloatStateOf(0.5f) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var currentBrightnessFraction by remember { mutableFloatStateOf(0.5f) }
    var gestureHudJob by remember { mutableStateOf<Job?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isLocked) {
                if (isLocked) {
                    detectTapGestures(onTap = { onSingleTap() })
                } else {
                    detectTapGestures(
                        onTap = { onSingleTap() },
                        onDoubleTap = { offset ->
                            val isLeft = offset.x < size.width / 2
                            if (isLeft) {
                                onDoubleTapSeek(-10_000L)
                                showLeftDoubleTap = true
                                doubleTapTimerJob?.cancel()
                                doubleTapTimerJob = scope.launch {
                                    delay(750L)
                                    showLeftDoubleTap = false
                                }
                            } else {
                                onDoubleTapSeek(10_000L)
                                showRightDoubleTap = true
                                doubleTapTimerJob?.cancel()
                                doubleTapTimerJob = scope.launch {
                                    delay(750L)
                                    showRightDoubleTap = false
                                }
                            }
                        }
                    )
                }
            }
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectDragGestures(
                        onDragEnd = {
                            gestureHudJob?.cancel()
                            gestureHudJob = scope.launch {
                                delay(1200L)
                                showVolumeIndicator = false
                                showBrightnessIndicator = false
                            }
                        },
                        onDrag = { change, dragAmount ->
                            val isLeftHalf = change.position.x < size.width / 2
                            val deltaFraction = -dragAmount.y / (size.height * 0.75f)

                            if (isLeftHalf) {
                                // Brightness adjustment
                                activity?.let { act ->
                                    val lp = act.window.attributes
                                    val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                    val newBrightness = (cur + deltaFraction).coerceIn(0.05f, 1.0f)
                                    lp.screenBrightness = newBrightness
                                    act.window.attributes = lp
                                    currentBrightnessFraction = newBrightness
                                    showBrightnessIndicator = true
                                    showVolumeIndicator = false
                                }
                            } else {
                                // Volume adjustment
                                audioManager?.let { am ->
                                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val curFrac = cur.toFloat() / max.toFloat()
                                    val newFrac = (curFrac + deltaFraction).coerceIn(0f, 1f)
                                    val targetVol = (newFrac * max).toInt()
                                    am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                    currentVolumeFraction = newFrac
                                    showVolumeIndicator = true
                                    showBrightnessIndicator = false
                                }
                            }
                        }
                    )
                }
            }
    ) {
        // Left Double Tap Feedback
        AnimatedVisibility(
            visible = showLeftDoubleTap,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(CinemaCyan.copy(alpha = 0.25f), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "-10 sec",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Right Double Tap Feedback
        AnimatedVisibility(
            visible = showRightDoubleTap,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, CinemaPurple.copy(alpha = 0.25f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "+10 sec",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Center Volume Indicator Overlay
        AnimatedVisibility(
            visible = showVolumeIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = CinemaCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    LinearProgressIndicator(
                        progress = { currentVolumeFraction },
                        color = CinemaCyan,
                        trackColor = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier
                            .width(100.dp)
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${(currentVolumeFraction * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Center Brightness Indicator Overlay
        AnimatedVisibility(
            visible = showBrightnessIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BrightnessMedium,
                        contentDescription = null,
                        tint = CinemaCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    LinearProgressIndicator(
                        progress = { currentBrightnessFraction },
                        color = CinemaCyan,
                        trackColor = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier
                            .width(100.dp)
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${(currentBrightnessFraction * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
