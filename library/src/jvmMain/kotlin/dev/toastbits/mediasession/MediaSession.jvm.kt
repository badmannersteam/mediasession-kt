package dev.toastbits.mediasession

import dev.toastbits.mediasession.linux.LinuxMediaSession
import dev.toastbits.mediasession.macos.MacOSMediaSession
import dev.toastbits.mediasession.smtc.SMTCMediaSession
import dev.toastbits.mediasession.smtc.JniSMTCAdapter

actual fun createMediaSession(getPositionMs: (() -> Long)?, initWinRtApartment: Boolean): MediaSession? {
    val os: String = System.getProperty("os.name").lowercase()

    if (os.startsWith("windows")) {
        return object : SMTCMediaSession(JniSMTCAdapter(), initWinRtApartment) {
            override fun getPositionMs(): Long = getPositionMs?.invoke() ?: super.getPositionMs()
        }
    }

    if (os.contains("linux")) {
        return object : LinuxMediaSession() {
            override fun getPositionMs(): Long = getPositionMs?.invoke() ?: super.getPositionMs()
        }
    }

    if (os.contains("mac") || os.contains("darwin")) {
        return MacOSMediaSession(getPositionMs)
    }

    return null
}
