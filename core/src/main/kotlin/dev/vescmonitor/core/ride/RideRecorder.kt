package dev.vescmonitor.core.ride

import dev.vescmonitor.core.session.LiveValues
import dev.vescmonitor.core.store.Chunk
import dev.vescmonitor.core.store.ChunkBuilder
import dev.vescmonitor.core.store.SampleStore
import dev.vescmonitor.core.store.StreamInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Records every sample of every controller while logging is on. Samples arrive on the
 * session thread; chunks are cut per second per controller and handed to a single
 * writer coroutine on [writerScope], one store call per second. The queue is bounded:
 * an overflow drops that second and is counted in [droppedChunks], never silently.
 */
class RideRecorder(
    private val store: SampleStore,
    private val writerScope: CoroutineScope,
) {
    private sealed interface Command {
        class Open(
            val startMs: Long,
            val streams: List<StreamInfo>,
        ) : Command

        class Write(
            val chunks: List<Chunk>,
        ) : Command

        class Close(
            val done: CompletableDeferred<Unit>,
        ) : Command

        class Recover(
            val done: CompletableDeferred<Unit>,
        ) : Command
    }

    private val queue = Channel<Command>(QUEUE_CAPACITY)
    private var builders: List<ChunkBuilder> = emptyList()
    private val pending = ArrayList<Chunk>()

    /** Session-thread state: a ride is open (samples are being recorded). */
    var recording = false
        private set
    var rideStartMs: Long? = null
        private set

    @Volatile var droppedChunks = 0L
        private set

    @Volatile var captureId: Long? = null
        private set

    /** Store calls that threw (e.g. disk full); the recorder keeps going. */
    @Volatile var storeErrors = 0L
        private set

    init {
        writerScope.launch {
            for (c in queue) {
                try {
                    handle(c)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    storeErrors++
                }
            }
        }
    }

    /** Opens a ride for these controllers (stream = index in [streams]). */
    fun open(
        nowMs: Long,
        streams: List<StreamInfo>,
    ) {
        if (recording) return
        builders = streams.map { ChunkBuilder(it.stream) }
        pending.clear()
        recording = true
        rideStartMs = nowMs
        send(Command.Open(nowMs, streams))
    }

    fun onSample(
        stream: Int,
        tMs: Long,
        values: LiveValues,
        speedMps: Double = Double.NaN,
        alertCode: Int = 0,
    ) {
        if (!recording) return
        val done = builders.getOrNull(stream)?.add(tMs, values, speedMps, alertCode) ?: return
        if (pending.isNotEmpty() && pending[0].t0Ms != done.t0Ms) flushPending()
        pending += done
    }

    /** Flushes the open second and closes the ride; waits until the summary is stored. */
    suspend fun close() {
        if (!recording) return
        recording = false
        rideStartMs = null
        builders.mapNotNullTo(pending) { it.flush() }
        flushPending()
        val done = CompletableDeferred<Unit>()
        withContext(NonCancellable) {
            queue.send(Command.Close(done))
            done.await()
        }
    }

    /**
     * Closes rides a kill left open, as recovered; empty ones are deleted. Queued like
     * every other store call, so it runs before any ride this recorder opens later.
     */
    suspend fun recover() {
        val done = CompletableDeferred<Unit>()
        queue.send(Command.Recover(done))
        done.await()
    }

    private fun flushPending() {
        if (pending.isEmpty()) return
        send(Command.Write(pending.toList()))
        pending.clear()
    }

    private fun send(c: Command) {
        if (queue.trySend(c).isFailure) droppedChunks += (c as? Command.Write)?.chunks?.size ?: 1
    }

    private suspend fun handle(c: Command) {
        when (c) {
            is Command.Open -> {
                captureId = store.openCapture(c.startMs, c.streams)
            }

            is Command.Write -> {
                captureId?.let { store.writeChunks(it, c.chunks) }
            }

            is Command.Recover -> {
                try {
                    for (id in store.openCaptures()) {
                        val summary = RideSummary.of(store.chunks(id))
                        if (summary == null) store.deleteCapture(id) else store.closeCapture(id, summary, recovered = true)
                    }
                } finally {
                    c.done.complete(Unit)
                }
            }

            is Command.Close -> {
                try {
                    captureId?.let { id ->
                        val summary = RideSummary.of(store.chunks(id))
                        if (summary == null) store.deleteCapture(id) else store.closeCapture(id, summary, recovered = false)
                    }
                } finally {
                    captureId = null
                    c.done.complete(Unit)
                }
            }
        }
    }

    companion object {
        /** About a minute of seconds for 1..N controllers before anything is dropped. */
        const val QUEUE_CAPACITY = 64
    }
}
