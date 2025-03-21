package eu.kanade.tachiyomi.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import eu.kanade.tachiyomi.ui.player.cast.CastMediaBuilder
import eu.kanade.tachiyomi.ui.player.cast.CastSessionListener
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.tail.TLMR

class CastManager(
    private val context: Context,
    private val activity: PlayerActivity,
) {

    private val viewModel by activity.viewModels<PlayerViewModel> { PlayerViewModelProviderFactory(activity) }
    private val player by lazy { activity.player }
    private val playerPreferences: PlayerPreferences by lazy { viewModel.playerPreferences }
    private val autoplayEnabled = playerPreferences.autoplayEnabled().get()

    private val _castState = MutableStateFlow(CastState.DISCONNECTED)
    val castState: StateFlow<CastState> = _castState.asStateFlow()

    var castContext: CastContext? = null
        private set
    private var castSession: CastSession? = null
    private var sessionListener: CastSessionListener? = null
    private val mediaBuilder = CastMediaBuilder(viewModel, activity)

    private val isCastApiAvailable: Boolean
        get() = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    init {
        initializeCast()
    }

    private fun initializeCast() {
        if (!isCastApiAvailable) return
        try {
            castContext = CastContext.getSharedInstance(context.applicationContext)
            sessionListener = CastSessionListener(this)
            registerSessionListener()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    fun registerSessionListener() {
        sessionListener?.let { listener ->
            castContext?.sessionManager?.addSessionManagerListener(listener, CastSession::class.java)
        }
    }

    fun unregisterSessionListener() {
        sessionListener?.let { listener ->
            castContext?.sessionManager?.removeSessionManagerListener(listener, CastSession::class.java)
        }
    }

    fun refreshCastContext() {
        castSession = castContext?.sessionManager?.currentCastSession
        castSession?.takeIf { it.isConnected }?.let {
            updateCastState(CastState.CONNECTED)
        }
    }

    private fun endSession() {
        castContext?.sessionManager?.endCurrentSession(true)
        updateCastState(CastState.DISCONNECTED)
    }

    fun cleanup() {
        unregisterSessionListener()
        castSession = null
    }

    fun onSessionConnected(session: CastSession) {
        castSession = session
        updateCastState(CastState.CONNECTED)
    }

    fun onSessionEnded() {
        castSession = null
        updateCastState(CastState.DISCONNECTED)
    }

    fun updateCastState(state: CastState) {
        _castState.value = state
        when (state) {
            CastState.CONNECTED -> {
                player.paused = true
                activity.invalidateOptionsMenu()
            }
            CastState.DISCONNECTED -> {
                activity.invalidateOptionsMenu()
            }
            CastState.CONNECTING -> { }
        }
    }

    fun maintainCastSessionBackground() {
        castSession?.remoteMediaClient?.takeIf { it.isPlaying }?.pause()
    }

    fun handleQualitySelection() {
        viewModel.videoList
            .filter { it.isNotEmpty() }
            .onEach { videos ->
                if (videos.size > 1) {
                    activity.runOnUiThread { showQualitySelectionDialog() }
                } else {
                    loadRemoteMedia()
                }
            }
            .launchIn(viewModel.viewModelScope)
    }

    private fun showQualitySelectionDialog() {
        activity.runOnUiThread {
            AlertDialog.Builder(context)
                .setTitle(context.stringResource(TLMR.strings.title_cast_quality))
                .setSingleChoiceItems(
                    viewModel.videoList.value.map { it.quality }.toTypedArray(),
                    viewModel.selectedVideoIndex.value,
                ) { dialog, which ->
                    viewModel.setVideoIndex(which)
                    dialog.dismiss()
                    loadRemoteMediaWithState()
                }
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun loadRemoteMediaWithState() {
        _castState.value = CastState.CONNECTING
        loadRemoteMedia()
    }

    @SuppressLint("SuspiciousIndentation")
    private fun loadRemoteMedia() {
        if (!isCastApiAvailable) return
        val remoteMediaClient = castSession?.remoteMediaClient ?: return
        activity.lifecycleScope.launch {
            try {
                val selectedIndex = viewModel.selectedVideoIndex.value
                val mediaInfo = mediaBuilder.buildMediaInfo(selectedIndex)
                // If there is already a video placed then add to the tail
                if (remoteMediaClient.mediaQueue.itemCount > 0) {
                    remoteMediaClient.queueAppendItem(
                        MediaQueueItem.Builder(mediaInfo).build(),
                        null,
                    )

                    activity.runOnUiThread {
                        Toast.makeText(
                            context,
                            context.stringResource(TLMR.strings.cast_video_added_to_queue),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                } else {
                    // Start new reproduction
                    remoteMediaClient.load(
                        MediaLoadRequestData.Builder()
                            .setMediaInfo(mediaInfo)
                            .setAutoplay(autoplayEnabled)
                            .setCurrentTime((player.timePos ?: 0).toLong() * 1000)
                            .build(),
                    )
                    _castState.value = CastState.CONNECTED
                }
            } catch (e: Exception) {
                // _castState.value = CastState.DISCONNECTED
                logcat(LogPriority.ERROR, e)
                activity.runOnUiThread {
                    Toast.makeText(
                        context,
                        context.stringResource(TLMR.strings.cast_error_loading),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    enum class CastState {
        CONNECTED,
        DISCONNECTED,
        CONNECTING,
    }
}
