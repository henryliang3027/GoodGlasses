package com.example.goodglasses.util

import android.media.MediaCodec
import com.htc.viveglass.sdk.StreamingEvent
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class StreamingPlayer(
    private val videoDecoder: H264Decoder,
    private val audioDecoder: AudioDecoder,
    private val callbacks: StreamingPlayerCallback
) {

    private val log = Logger.instance
    private val logTag: String = "StreamingPlayer"

    private val running = AtomicBoolean(false)
    private var queuedVideoFrames = 0L
    private var isVideoPlaying = false
    private val fps = 24
    private val bufferSeconds = 0.2
    private val targetBufferedFrames = fps * bufferSeconds
    fun interface ClockUs { fun nowUs(): Long }
    val audioClock: ClockUs = ClockUs { audioDecoder.getClockUs() }

    data class EncodedFrame(val data: ByteArray, val ptsUs: Long, val flags: Int)
    sealed class PlaybackEvent {
        object Started : PlaybackEvent()
        object EndOfStream : PlaybackEvent()
        object Stopped : PlaybackEvent()
        data class Error(val reason: Throwable) : PlaybackEvent()
    }

    interface StreamingPlayerCallback {
        fun onPlaybackEvent(event: PlaybackEvent)
    }

    fun startAudioDecoder() {
        audioDecoder.setPlaybackEnabled(false)
        audioDecoder.start()
    }

    fun stopAudioDecoder() {
        audioDecoder.stop()
    }

    fun startVideoDecode() {
        if (!running.compareAndSet(false, true)) return

        videoDecoder.setClock(clock = audioClock)
        videoDecoder.setPlaybackEnabled(false)

        videoDecoder.start()
        log.e(logTag, "startDecode()")
    }

    fun stopVideoDecode() {
        if (!running.compareAndSet(true, false)) return

        videoDecoder.stop()

        queuedVideoFrames = 0L
        isVideoPlaying = false

        log.e(logTag, "stopDecode()")
    }

    fun release() {
        audioDecoder.releaseForever()
        videoDecoder.releaseForever()
    }

    fun onStreamEvent(streamingEvent: StreamingEvent) {
        when (streamingEvent) {
            StreamingEvent.STARTED -> {
                log.w(logTag, "onStreamEvent : start")
                startAudioDecoder()
                startVideoDecode()
            }
            StreamingEvent.STOPPED -> {
                log.w(logTag, "onStreamEvent : end")

                callbacks.onPlaybackEvent(PlaybackEvent.EndOfStream)
                stopVideoDecode()
                stopAudioDecoder()
            }

            else -> {
                log.w(logTag, "onStreamEvent : $streamingEvent")
            }
        }
    }

    fun onReceivedAudioBuffer(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        audioDecoder.onReceivedAudioBuffer(byteBuffer, bufferInfo)
    }

    fun onReceivedVideoBuffer(byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!running.get()) return
        if (bufferInfo.size <= 0) return

        val copy = ByteArray(bufferInfo.size)
        val dup = byteBuffer.duplicate().apply {
            position(bufferInfo.offset)
            limit(bufferInfo.offset + bufferInfo.size)
        }
        dup.get(copy)

        if (videoDecoder.isCodecConfigured()) {
            callbacks.onPlaybackEvent(PlaybackEvent.Started)
        }

        val frame = EncodedFrame(
            data = copy,
            ptsUs = bufferInfo.presentationTimeUs,
            flags = bufferInfo.flags
        )

        videoDecoder.submit(frame)
        queuedVideoFrames++

        if (!isVideoPlaying &&
            queuedVideoFrames >= targetBufferedFrames
        ) {
            isVideoPlaying = true
            audioDecoder.setPlaybackEnabled(true)
            videoDecoder.setPlaybackEnabled(true)
        }
    }

    fun getFrameRatio(): Float {
        return videoDecoder.getFrameRatio()
    }
}
