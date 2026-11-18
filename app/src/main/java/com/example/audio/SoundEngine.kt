package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SoundEngine {
    private val sampleRate = 22050
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    var isMuted: Boolean = false

    @Volatile
    private var engineActive: Boolean = false

    @Volatile
    private var speedRatio: Float = 0f

    private var engineJob: Job? = null
    private var engineTrack: AudioTrack? = null

    fun startEngineLoopIfNeeded() {
        if (engineJob?.isActive == true) return
        engineJob = scope.launch {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(1024)

            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull() ?: return@launch

            engineTrack = track
            runCatching { track.play() }

            val chunkSize = 512
            val buffer = ShortArray(chunkSize)
            var phase = 0.0
            var lowPass = 0.0

            while (isActive) {
                if (!engineActive || isMuted) {
                    delay(40)
                    continue
                }
                val freq = 48.0 + speedRatio.coerceIn(0f, 1.3f) * 115.0
                val amp = 0.045 + speedRatio.coerceIn(0f, 1.3f) * 0.055
                val phaseInc = freq / sampleRate

                for (i in 0 until chunkSize) {
                    phase += phaseInc
                    if (phase >= 1.0) phase -= 1.0
                    // Warm sawtooth + fundamental sub-harmonic, low-pass filtered
                    val saw = 2.0 * phase - 1.0
                    val sub = sin(2.0 * PI * phase)
                    val raw = 0.65 * saw + 0.35 * sub
                    lowPass += 0.14 * (raw - lowPass)
                    buffer[i] = (lowPass * amp * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                }
                runCatching {
                    track.write(buffer, 0, chunkSize)
                }
            }
        }
    }

    fun updateEngine(playing: Boolean, ratio: Float) {
        engineActive = playing
        speedRatio = ratio
    }

    fun playNvgToggle(activating: Boolean) {
        if (isMuted) return
        val start = if (activating) 620f else 1400f
        val end = if (activating) 2100f else 380f
        playSweepTone(start, end, durationSec = 0.16f, volume = 0.22f)
    }

    fun playBatteryPickup() {
        if (isMuted) return
        playSweepTone(580f, 940f, durationSec = 0.16f, volume = 0.24f)
    }

    fun playLevelComplete() {
        if (isMuted) return
        playSweepTone(523f, 1046f, durationSec = 0.28f, volume = 0.25f)
    }

    fun playCrash() {
        if (isMuted) return
        scope.launch {
            val count = (sampleRate * 0.38f).toInt()
            val samples = ShortArray(count)
            var lp = 0.0
            for (i in 0 until count) {
                val env = exp(-i / (sampleRate * 0.11))
                val noise = (Random.nextDouble() * 2.0 - 1.0) * env
                lp += 0.22 * (noise - lp)
                samples[i] = (lp * 0.55 * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            playStaticSamples(samples)
        }
    }

    private fun playSweepTone(freqStart: Float, freqEnd: Float, durationSec: Float, volume: Float) {
        scope.launch {
            val count = (sampleRate * durationSec).toInt().coerceAtLeast(1)
            val samples = ShortArray(count)
            var phase = 0.0
            for (i in 0 until count) {
                val t = i.toFloat() / count
                val freq = freqStart + (freqEnd - freqStart) * t
                val env = (1f - t) * (1f - t * 0.3f)
                phase += freq / sampleRate
                val s = sin(2.0 * PI * phase) * env * volume
                samples[i] = (s * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            playStaticSamples(samples)
        }
    }

    private fun playStaticSamples(samples: ShortArray) {
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(samples, 0, samples.size)
            track.play()
            scope.launch {
                delay((samples.size * 1000L / sampleRate) + 120L)
                runCatching {
                    track.stop()
                    track.release()
                }
            }
        }
    }

    fun release() {
        engineActive = false
        engineJob?.cancel()
        runCatching {
            engineTrack?.stop()
            engineTrack?.release()
        }
        engineTrack = null
        scope.cancel()
    }
}
