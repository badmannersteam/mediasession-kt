package dev.toastbits.mediasession.macos

import dev.toastbits.mediasession.MediaSession
import dev.toastbits.mediasession.MediaSessionLoopMode
import dev.toastbits.mediasession.MediaSessionMetadata
import dev.toastbits.mediasession.MediaSessionPlaybackStatus
import dev.toastbits.mediasession.MediaSessionProperties

class MacOSMediaSession(private val getPositionMs: (() -> Long)?) : MediaSession, MediaSessionProperties {
    private val adapter = JniNowPlayingAdapter()

    private var _enabled = false
    private var _metadata: MediaSessionMetadata = MediaSessionMetadata()
    private var _status: MediaSessionPlaybackStatus = MediaSessionPlaybackStatus.STOPPED
    private var _loopMode: MediaSessionLoopMode = MediaSessionLoopMode.NONE
    private var _shuffle: Boolean = false
    private var _rate: Float = 1f

    init {
        adapter.registerCallbacks(
            onPlay     = { onPlay?.invoke() },
            onPause    = { onPause?.invoke() },
            onStop     = { onStop?.invoke() },
            onNext     = { onNext?.invoke() },
            onPrevious = { onPrevious?.invoke() },
            onSeek     = { posSecs -> onSetPosition?.invoke((posSecs * 1000.0).toLong()) },
            onRate     = { rate -> onSetRate?.invoke(rate.toFloat()) }
        )
    }

    override val enabled: Boolean get() = _enabled

    override fun setEnabled(enabled: Boolean) {
        if (enabled == _enabled) return
        _enabled = enabled
        if (!enabled) {
            adapter.clear()
        } else {
            pushCurrentInfo()
            adapter.setPlaybackState(_status.toNativeState())
            _metadata.art_url?.let { adapter.setArtworkUrl(it) }
        }
    }

    override var onRaise: (() -> Unit)? = null
    override var onQuit: (() -> Unit)? = null
    override var onNext: (() -> Unit)? = null
    override var onPrevious: (() -> Unit)? = null
    override var onPause: (() -> Unit)? = null
    override var onPlayPause: (() -> Unit)? = null
    override var onStop: (() -> Unit)? = null
    override var onPlay: (() -> Unit)? = null
    override var onSeek: ((by_ms: Long) -> Unit)? = null
    override var onSetPosition: ((to_ms: Long) -> Unit)? = null
    override var onOpenUri: ((uri: String) -> Unit)? = null
    override var onSetRate: ((rate: Float) -> Unit)? = null
    override var onSetLoop: ((loop_mode: MediaSessionLoopMode) -> Unit)? = null
    override var onSetShuffle: ((shuffle_mode: Boolean) -> Unit)? = null

    override fun getPositionMs(): Long = getPositionMs?.invoke() ?: 0

    override fun onPositionChanged() {
        if (_enabled) pushCurrentInfo()
    }

    override val identity: String get() = ""
    override val desktop_entry: String? get() = null
    override val supported_uri_schemes: List<String> get() = emptyList()
    override val supported_mime_types: List<String> get() = emptyList()
    override val loop_mode: MediaSessionLoopMode get() = _loopMode
    override val shuffle: Boolean get() = _shuffle
    override val volume: Float get() = 0f
    override val rate: Float get() = _rate
    override val metadata: MediaSessionMetadata get() = _metadata
    override val playback_status: MediaSessionPlaybackStatus get() = _status
    override val maximum_rate: Float get() = 1f
    override val minimum_rate: Float get() = 0f

    override fun setIdentity(identity: String) {}
    override fun setDesktopEntry(desktop_entry: String?) {}
    override fun setSupportedUriSchemes(supported_uri_schemes: List<String>) {}
    override fun setSupportedMimeTypes(supported_mime_types: List<String>) {}

    override fun setLoopMode(loop_mode: MediaSessionLoopMode) {
        _loopMode = loop_mode
        if (_enabled) pushCurrentInfo()
    }

    override fun setShuffle(shuffle: Boolean) {
        _shuffle = shuffle
    }

    override fun setVolume(volume: Float) {}

    override fun setRate(rate: Float) {
        _rate = rate
        if (_enabled) pushCurrentInfo()
    }

    override fun setMaximumRate(maximum_rate: Float) {}
    override fun setMinimumRate(minimum_rate: Float) {}

    override fun setPlaybackStatus(status: MediaSessionPlaybackStatus) {
        _status = status
        if (_enabled) {
            adapter.setPlaybackState(status.toNativeState())
        }
    }

    override fun setMetadata(metadata: MediaSessionMetadata) {
        val artUrlChanged = metadata.art_url != _metadata.art_url
        _metadata = metadata
        if (_enabled) {
            pushCurrentInfo()
            if (artUrlChanged) {
                adapter.setArtworkUrl(metadata.art_url ?: "")
            }
        }
    }

    private fun pushCurrentInfo() {
        adapter.pushInfo(
            title       = _metadata.title ?: "",
            artist      = _metadata.artist ?: "",
            album       = _metadata.album ?: "",
            albumArtist = _metadata.album_artists?.firstOrNull() ?: "",
            genre       = _metadata.genres?.firstOrNull() ?: "",
            elapsed     = getPositionMs() / 1000.0,
            total       = (_metadata.length_ms ?: 0L) / 1000.0,
            rate        = _rate.toDouble(),
            trackNumber = _metadata.track_number ?: 0
        )
    }
}

// MPNowPlayingPlaybackState: 1=playing, 2=paused, 3=stopped
private fun MediaSessionPlaybackStatus.toNativeState(): Int =
    when (this) {
        MediaSessionPlaybackStatus.PLAYING -> 1
        MediaSessionPlaybackStatus.PAUSED  -> 2
        MediaSessionPlaybackStatus.STOPPED -> 3
    }
