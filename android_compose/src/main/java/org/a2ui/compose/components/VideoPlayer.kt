package org.a2ui.compose.components

import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = (totalSeconds / 60).toString().padStart(2, '0')
    val seconds = (totalSeconds % 60).toString().padStart(2, '0')
    return "$minutes:$seconds"
}

@Composable
fun VideoPlayer(videoUri: Uri,
                modifier: Modifier = Modifier,
                aspectRatio: Float = 16f / 9f,
                autoPlay: Boolean = true) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = autoPlay
        }
    }

    var isPlaying by remember { mutableStateOf(autoPlay) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var isFullScreen by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_STOP -> exoPlayer.stop()
                Lifecycle.Event.ON_DESTROY -> exoPlayer.release()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration
            delay(500)
        }
    }

    fun togglePlayPause() {
        if (isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
        isPlaying = !isPlaying
    }

    fun seekTo(position: Float) {
        val newPosition = (position * duration).toLong()
        exoPlayer.seekTo(newPosition)
        currentPosition = newPosition
    }

    fun toggleFullScreen() {
        val activity = context as? ComponentActivity
        activity?.requestedOrientation = if (isFullScreen) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        isFullScreen = !isFullScreen
    }

    Box(
        modifier = modifier
            .then(if (isFullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    useController = false
                }
            },
            modifier = if (isFullScreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .height((LocalContext.current.resources.displayMetrics.widthPixels / aspectRatio).dp)
            }
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(8.dp)
        ) {
            IconButton(
                onClick = ::togglePlayPause,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White
                )
            }

            Slider(
                value = if (duration == 0L) 0f else currentPosition.toFloat() / duration,
                onValueChange = { seekTo(it) },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 48.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.Gray
                )
            )

            Text(
                text = "${formatTime(currentPosition)}/${formatTime(duration)}",
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterEnd)
            )

            IconButton(
                onClick = ::toggleFullScreen,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(start = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = if (isFullScreen) "退出全屏" else "全屏",
                    tint = Color.White
                )
            }
        }
    }
}
