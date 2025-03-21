package eu.kanade.tachiyomi.ui.player.cast.components
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.ui.player.CastManager
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.tail.TLMR
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun CastPlayerDialog(
    castManager: CastManager,
    onDismiss: () -> Unit,
) {
    val mediaInfo = castManager.currentMedia.collectAsState()
    val isPlaying = castManager.isPlaying.collectAsState()
    val volume = castManager.volume.collectAsState()
    val orientation = LocalConfiguration.current.orientation

    TachiyomiTheme {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(mediaInfo.value?.title ?: "") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                        if (!mediaInfo.value?.thumbnail.isNullOrEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                            ) {
                                AsyncImage(
                                    model = mediaInfo.value?.thumbnail,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }

                    Text(
                        text = mediaInfo.value?.subtitle ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        IconButton(onClick = { castManager.previousVideo() }) {
                            Icon(Icons.Default.SkipPrevious, stringResource(TLMR.strings.cast_previous_video))
                        }
                        IconButton(onClick = { castManager.seekRelative(-30) }) {
                            Icon(Icons.Default.FastRewind, stringResource(TLMR.strings.cast_rewind_30s))
                        }
                        IconButton(onClick = { castManager.playPause() }) {
                            Icon(
                                if (isPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (isPlaying.value) {
                                    stringResource(
                                        TLMR.strings.cast_pause,
                                    )
                                } else {
                                    stringResource(TLMR.strings.cast_play)
                                },
                            )
                        }
                        IconButton(onClick = { castManager.seekRelative(30) }) {
                            Icon(Icons.Default.FastForward, stringResource(TLMR.strings.cast_forward_30s))
                        }
                        IconButton(onClick = { castManager.nextVideo() }) {
                            Icon(Icons.Default.SkipNext, stringResource(TLMR.strings.cast_next_video))
                        }
                    }

                    Column {
                        Text(
                            text = "${stringResource(TLMR.strings.cast_volume)}: ${(volume.value * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Slider(
                            value = volume.value,
                            onValueChange = { castManager.setVolume(it) },
                            valueRange = 0f..1f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = LocalContext.current.stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        castManager.endSession()
                        onDismiss()
                    },
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = stringResource(TLMR.strings.cast_end_session),
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(text = stringResource(TLMR.strings.cast_end_session))
                    }
                }
            },
        )
    }
}
