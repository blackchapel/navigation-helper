package com.ridelink.app.voicechat

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

// Telephony-band voice quality (not 16kHz): halves the raw bitrate to
// 128kbps at exactly the point where Nearby Connections' real achievable
// throughput over Bluetooth at bike-riding range is a genuine unknown --
// the conservative choice until real-device testing shows there's headroom.
internal const val VOICE_SAMPLE_RATE = 8000
private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
private const val CHUNK_BYTES = 320 // 20ms at 8kHz/16-bit mono
private const val PIPE_BUFFER_BYTES = 16000 // ~1s of slack at 128kbps

/**
 * Captures mic audio on a dedicated real-time thread and exposes it as an
 * InputStream suitable for Payload.fromStream() -- Nearby Connections reads
 * from it on its own internal thread, so the capture thread and the pipe's
 * reader are always distinct (a hard PipedInputStream requirement).
 */
internal class AudioCapture {
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var captureThread: Thread? = null

    @Volatile
    private var muted = false

    /** Starts capturing and returns a live stream of the mic audio. Call [stop] to release everything. */
    fun start(): InputStream {
        val minBuffer = AudioRecord.getMinBufferSize(VOICE_SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            VOICE_SAMPLE_RATE,
            CHANNEL_IN,
            ENCODING,
            maxOf(minBuffer, CHUNK_BYTES * 4),
        )
        audioRecord = record

        // Hardware/OEM-dependent -- isAvailable() gates each defensively.
        // Matters concretely here: the rider is expected to use the phone's
        // loudspeaker hands-free, so without echo cancellation the mic would
        // pick the pillion's own voice back up off the speaker.
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true }
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true }
        }

        val pipedOutput = PipedOutputStream()
        val pipedInput = PipedInputStream(pipedOutput, PIPE_BUFFER_BYTES)

        record.startRecording()
        val thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buffer = ByteArray(CHUNK_BYTES)
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    // Muted frames are simply dropped rather than sent as
                    // zeroed bytes -- saves bandwidth too.
                    if (!muted) {
                        pipedOutput.write(buffer, 0, read)
                    }
                }
            } catch (e: IOException) {
                // Pipe closed by stop() -- expected, not an error.
            }
        }, "RideLink-AudioCapture")
        captureThread = thread
        thread.start()

        return pipedInput
    }

    fun setMuted(muted: Boolean) {
        this.muted = muted
    }

    fun stop() {
        captureThread?.interrupt()
        captureThread = null
        audioRecord?.let {
            try {
                it.stop()
            } catch (e: IllegalStateException) {
                // Already stopped -- fine.
            }
            it.release()
        }
        audioRecord = null
        echoCanceler?.release()
        echoCanceler = null
        noiseSuppressor?.release()
        noiseSuppressor = null
    }
}

/**
 * Plays back audio arriving from the peer's InputStream (a Nearby
 * Connections STREAM payload) on a dedicated real-time thread.
 */
internal class AudioPlayback {
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null

    fun start(inputStream: InputStream) {
        val minBuffer = AudioTrack.getMinBufferSize(VOICE_SAMPLE_RATE, CHANNEL_OUT, ENCODING)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(VOICE_SAMPLE_RATE)
                    .setChannelMask(CHANNEL_OUT)
                    .setEncoding(ENCODING)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuffer, CHUNK_BYTES * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        audioTrack = track
        track.play()

        val thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buffer = ByteArray(CHUNK_BYTES)
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val read = inputStream.read(buffer)
                    if (read < 0) break // peer ended the stream
                    if (read > 0) {
                        track.write(buffer, 0, read)
                    }
                }
            } catch (e: IOException) {
                // Stream closed/cancelled -- expected on hang-up.
            }
        }, "RideLink-AudioPlayback")
        playbackThread = thread
        thread.start()
    }

    fun stop() {
        playbackThread?.interrupt()
        playbackThread = null
        audioTrack?.let {
            try {
                it.stop()
            } catch (e: IllegalStateException) {
                // Already stopped -- fine.
            }
            it.release()
        }
        audioTrack = null
    }
}
