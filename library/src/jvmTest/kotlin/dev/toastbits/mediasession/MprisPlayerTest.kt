package dev.toastbits.mediasession

import dev.toastbits.mediasession.mpris.DBusVariant
import dev.toastbits.mediasession.mpris.MprisConstants
import dev.toastbits.mediasession.mpris.MprisMediaSession
import dev.toastbits.mediasession.mpris.MprisProperty
import dev.toastbits.mediasession.mpris.fromMprisPlayerMetadata
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusProperty
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MprisPlayerTest {
    private val connection: DBusConnection = Mockito.mock(DBusConnection::class.java)
    private val session: TestSession = TestSession(connection)
    private val player: SessionInterface = session.properties
    private val track_id: DBusPath = DBusPath("/track/current")

    private fun setTrack(length_ms: Long? = 60000L) {
        session.setMetadata(MediaSessionMetadata(track_id = track_id.path, length_ms = length_ms))
    }

    private fun canSeek(): Boolean = player.getProperty(MprisProperty.CanSeek)!!.value as Boolean

    @Test
    fun seekConvertsMicrosecondsToMilliseconds() {
        val offsets: MutableList<Long> = mutableListOf()
        session.onSeek = { offsets.add(it) }

        player.Seek(10000000L)
        player.Seek(-5000000L)
        player.Seek(999L)

        assertEquals(listOf(10000L, -5000L, 0L), offsets)
    }

    @Test
    fun setPositionConvertsMicrosecondsToMilliseconds() {
        setTrack()
        var position: Long? = null
        session.onSeek = {}
        session.onSetPosition = { position = it }

        player.SetPosition(track_id, 10000000L)

        assertEquals(10000L, position)
    }

    @Test
    fun setPositionIgnoresStaleTrackAndInvalidPositions() {
        setTrack()
        val positions: MutableList<Long> = mutableListOf()
        session.onSeek = { error("Invalid SetPosition must not seek") }
        session.onSetPosition = { positions.add(it) }

        player.SetPosition(DBusPath("/track/previous"), 10000000L)
        player.SetPosition(track_id, -1L)

        assertTrue(positions.isEmpty())
    }

    @Test
    fun setPositionForwardsRequestsBeyondReportedTrackLength() {
        setTrack()
        val positions: MutableList<Long> = mutableListOf()
        var next_calls: Int = 0
        session.onSetPosition = { positions.add(it) }
        session.onNext = { next_calls++ }

        player.SetPosition(track_id, 60000001L)
        player.SetPosition(track_id, 100000000L)
        player.SetPosition(track_id, Long.MAX_VALUE)

        assertEquals(listOf(60000L, 100000L, Long.MAX_VALUE / 1000L), positions)
        assertEquals(0, next_calls)
    }

    @Test
    fun setPositionAcceptsTrackBoundaries() {
        setTrack()
        val positions: MutableList<Long> = mutableListOf()
        session.onSeek = {}
        session.onSetPosition = { positions.add(it) }

        player.SetPosition(track_id, 0L)
        player.SetPosition(track_id, 60000000L)

        assertEquals(listOf(0L, 60000L), positions)
    }

    @Test
    fun setPositionAcceptsUnknownLength() {
        setTrack(null)
        var position: Long? = null
        session.onSetPosition = { position = it }

        player.SetPosition(track_id, 10000000L)

        assertEquals(10000L, position)
    }

    @Test
    fun setPositionIgnoresMissingTrackAndNoTrack() {
        val positions: MutableList<Long> = mutableListOf()
        session.onSetPosition = { positions.add(it) }
        player.SetPosition(track_id, 1000000L)

        val no_track: DBusPath = DBusPath("/org/mpris/MediaPlayer2/TrackList/NoTrack")
        session.setMetadata(MediaSessionMetadata(track_id = no_track.path))
        player.SetPosition(no_track, 1000000L)

        assertTrue(positions.isEmpty())
    }

    @Test
    fun canSeekReflectsEitherCallback() {
        assertFalse(canSeek())
        session.onSetPosition = {}
        assertTrue(canSeek())
        session.onSetPosition = null
        assertFalse(canSeek())
        session.onSeek = {}
        assertTrue(canSeek())
        session.onSeek = null
        assertFalse(canSeek())
    }

    @Test
    fun capabilityChangesEmitPropertiesChangedOnlyWhenValueChanges() {
        session.onSeek = {}
        session.onSetPosition = {}
        session.onSeek = null
        session.onSetPosition = null

        val signals = Mockito.mockingDetails(connection).invocations
            .filter { it.method.name == "sendMessage" }
            .map { it.arguments[0] }
        assertEquals(2, signals.size)
        signals.forEach { assertIs<Properties.PropertiesChanged>(it) }
    }

    @Test
    fun missingSeekCallbackIgnoresRequestsWithoutChangingTrack() {
        setTrack()
        session.position_ms = 55000L
        val positions: MutableList<Long> = mutableListOf()
        var next_calls: Int = 0
        session.onSetPosition = { positions.add(it) }
        session.onNext = { next_calls++ }

        player.Seek(1000000L)
        player.Seek(-60000000L)
        player.Seek(10000000L)
        player.Seek(Long.MAX_VALUE)

        assertTrue(positions.isEmpty())
        assertEquals(0, next_calls)
    }

    @Test
    fun missingSetPositionCallbackIgnoresRequestsWithoutRelativeSeek() {
        setTrack()
        session.position_ms = 20000L
        val offsets: MutableList<Long> = mutableListOf()
        session.onSeek = { offsets.add(it) }

        player.SetPosition(track_id, 10000000L)
        player.SetPosition(track_id, 0L)
        player.SetPosition(track_id, 60000000L)

        assertTrue(offsets.isEmpty())
    }

    @Test
    fun seekAndSetPositionInvokeOnlyTheirOwnCallbacks() {
        setTrack()
        session.position_ms = 20000L
        val offsets: MutableList<Long> = mutableListOf()
        val positions: MutableList<Long> = mutableListOf()
        var next_calls: Int = 0
        session.onSeek = { offsets.add(it) }
        session.onSetPosition = { positions.add(it) }
        session.onNext = { next_calls++ }

        player.Seek(10000000L)
        player.Seek(100000000L)
        player.SetPosition(track_id, 5000000L)

        assertEquals(listOf(10000L, 100000L), offsets)
        assertEquals(listOf(5000L), positions)
        assertEquals(0, next_calls)
    }

    @Test
    fun seekingWithNoCallbacksDoesNothing() {
        setTrack()
        var next_calls: Int = 0
        session.onNext = { next_calls++ }

        player.Seek(Long.MAX_VALUE)
        player.SetPosition(track_id, 1000000L)

        assertFalse(canSeek())
        assertEquals(0, next_calls)
    }

    @Test
    fun metadataTrackIdIsAnObjectPath() {
        setTrack()
        @Suppress("UNCHECKED_CAST")
        val metadata = player.getProperty(MprisProperty.Metadata)!!.value as Map<String, DBusVariant<*>>
        val track = metadata.getValue("mpris:trackid")

        assertEquals("o", track.sig)
        assertIs<DBusPath>(track.value)
        assertEquals(track_id.path, metadata.fromMprisPlayerMetadata(session.identity).track_id)
    }

    @Test
    fun getAllReturnsOnlyPropertiesOfRequestedInterface() {
        val general = player.GetAll(MprisConstants.Interface.GENERAL.iface)
        val properties = player.GetAll(MprisConstants.Interface.PLAYER.iface)

        assertTrue("Identity" in general)
        assertFalse("CanSeek" in general)
        assertFalse("Identity" in properties)
        assertEquals(false, properties.getValue("CanSeek").value)
        assertTrue(player.GetAll("invalid.interface").isEmpty())
    }

    @Test
    fun getChecksPropertyInterface() {
        val can_seek = player.Get<Variant<Boolean>>(MprisConstants.Interface.PLAYER.iface, "CanSeek")

        assertEquals(false, can_seek!!.value)
        assertEquals(null, player.Get<Variant<Boolean>>(MprisConstants.Interface.GENERAL.iface, "CanSeek"))
    }

    @Test
    fun positionUsesInt64OnTheWireAndInIntrospection() {
        session.position_ms = 3000000L
        val position = player.getProperty(MprisProperty.Position)!!
        val annotation = PlayerInterface::class.java.getAnnotationsByType(DBusProperty::class.java)
            .first { it.name == "Position" }

        assertEquals(3000000000L, position.value)
        assertEquals("x", position.sig)
        assertEquals(Long::class.java, annotation.type.java)
    }

    private class TestSession(connection: DBusConnection): MprisMediaSession(), MediaSession {
        override val properties: SessionInterface = SessionInterface(this, connection)
        override val enabled: Boolean = true
        override fun setEnabled(enabled: Boolean) {}
        var position_ms: Long = 0L
        override fun getPositionMs(): Long = position_ms
        override fun onPositionChanged() {}

        override var onRaise: (() -> Unit)? = null
        override var onQuit: (() -> Unit)? = null
        override var onNext: (() -> Unit)? = null
        override var onPrevious: (() -> Unit)? = null
        override var onPause: (() -> Unit)? = null
        override var onPlayPause: (() -> Unit)? = null
        override var onStop: (() -> Unit)? = null
        override var onPlay: (() -> Unit)? = null
        override var onOpenUri: ((uri: String) -> Unit)? = null
        override var onSetRate: ((rate: Float) -> Unit)? = null
        override var onSetLoop: ((loop_mode: MediaSessionLoopMode) -> Unit)? = null
        override var onSetShuffle: ((shuffle_mode: Boolean) -> Unit)? = null
    }
}