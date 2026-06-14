package dev.toastbits.mediasession.macos

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

internal class JniNowPlayingAdapter {
    private val lib: NowPlayingLibrary = Native.load("nowplaying", NowPlayingLibrary::class.java)

    // Held as fields so JNA callbacks are not garbage-collected while in use
    private var onPlayCb: SimpleCallback? = null
    private var onPauseCb: SimpleCallback? = null
    private var onStopCb: SimpleCallback? = null
    private var onNextCb: SimpleCallback? = null
    private var onPreviousCb: SimpleCallback? = null
    private var onSeekCb: SeekCallback? = null
    private var onRateCb: RateCallback? = null

    val center: Pointer = lib.nowplaying_default_center()

    fun registerCallbacks(
        onPlay: (() -> Unit)?,
        onPause: (() -> Unit)?,
        onStop: (() -> Unit)?,
        onNext: (() -> Unit)?,
        onPrevious: (() -> Unit)?,
        onSeek: ((Double) -> Unit)?,
        onRate: ((Double) -> Unit)?
    ) {
        onPlayCb     = onPlay?.let { SimpleCallback(it) }
        onPauseCb    = onPause?.let { SimpleCallback(it) }
        onStopCb     = onStop?.let { SimpleCallback(it) }
        onNextCb     = onNext?.let { SimpleCallback(it) }
        onPreviousCb = onPrevious?.let { SimpleCallback(it) }
        onSeekCb     = onSeek?.let { SeekCallback(it) }
        onRateCb     = onRate?.let { RateCallback(it) }

        lib.nowplaying_register_commands_with_callbacks(
            onPlayCb, onPauseCb, onStopCb,
            onNextCb, onPreviousCb,
            onSeekCb, onRateCb
        )
    }

    fun setPlaybackState(state: Int) =
        lib.nowplaying_set_playback_state(center, state)

    fun pushInfo(
        title: String,
        artist: String,
        album: String,
        albumArtist: String,
        genre: String,
        elapsed: Double,
        total: Double,
        rate: Double,
        trackNumber: Int
    ) = lib.nowplaying_push_info(
        center, title, artist, album, albumArtist, genre,
        elapsed, total, rate, trackNumber
    )

    fun setArtworkUrl(url: String) = lib.nowplaying_set_artwork_url(url)

    fun clear() = lib.nowplaying_clear(center)

    fun release() = lib.nowplaying_release_center(center)
}

private interface NowPlayingLibrary : Library {
    fun nowplaying_default_center(): Pointer
    fun nowplaying_register_commands_with_callbacks(
        onPlay: SimpleCallback?,
        onPause: SimpleCallback?,
        onStop: SimpleCallback?,
        onNext: SimpleCallback?,
        onPrevious: SimpleCallback?,
        onSeek: SeekCallback?,
        onRate: RateCallback?
    )
    fun nowplaying_set_playback_state(center: Pointer, state: Int)
    fun nowplaying_push_info(
        center: Pointer,
        title: String, artist: String, album: String, albumArtist: String, genre: String,
        elapsed: Double, total: Double, rate: Double,
        trackNumber: Int
    )
    fun nowplaying_set_artwork_url(url: String)
    fun nowplaying_clear(center: Pointer)
    fun nowplaying_release_center(center: Pointer)
}

private class SimpleCallback(private val fn: () -> Unit) : Callback {
    fun invoke() = fn()
}

private class SeekCallback(private val fn: (Double) -> Unit) : Callback {
    fun invoke(positionSeconds: Double) = fn(positionSeconds)
}

private class RateCallback(private val fn: (Double) -> Unit) : Callback {
    fun invoke(rate: Double) = fn(rate)
}
