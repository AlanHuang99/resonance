package com.resonance.music.tabata

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.*

@OptIn(UnstableApi::class)
class TabataAudioProcessor : BaseAudioProcessor() {

    @Volatile var mode = EffectMode.FLANGER
    @Volatile var intensity = 0
    @Volatile var calm = false
    @Volatile var rampMs = 400

    private class Bq(
        var b0: Double = 1.0,
        var b1: Double = 0.0,
        var b2: Double = 0.0,
        var a1: Double = 0.0,
        var a2: Double = 0.0
    ) {
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0

        fun process(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }

        fun reset() {
            x1 = 0.0
            x2 = 0.0
            y1 = 0.0
            y2 = 0.0
        }
    }

    private val low = Array(2) { Bq() }
    private val peak = Array(2) { Bq() }
    private val high = Array(2) { Bq() }

    private val delay = Array(2) { DoubleArray(8192) }
    private var writeIndex = 0
    private var lfo = 0.0

    private var mix = 0.0
    private var sampleRate = 44100
    private var channels = 2
    private var configKey = ""

    override fun onConfigure(format: AudioFormat): AudioFormat {
        if (format.encoding != C.ENCODING_PCM_16BIT) {
            throw UnhandledAudioFormatException(format)
        }
        sampleRate = format.sampleRate
        channels = format.channelCount.coerceAtMost(2)
        return format
    }

    private fun lowShelf(filter: Bq, frequency: Double, db: Double) {
        val a = 10.0.pow(db / 40.0)
        val w = 2.0 * PI * frequency / sampleRate
        val cosine = cos(w)
        val alpha = sin(w) / 2.0 * sqrt(2.0)
        val slope = 2.0 * sqrt(a) * alpha

        val b0 = a * ((a + 1.0) - (a - 1.0) * cosine + slope)
        val b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosine)
        val b2 = a * ((a + 1.0) - (a - 1.0) * cosine - slope)
        val a0 = (a + 1.0) + (a - 1.0) * cosine + slope
        val a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosine)
        val a2 = (a + 1.0) + (a - 1.0) * cosine - slope

        filter.b0 = b0 / a0
        filter.b1 = b1 / a0
        filter.b2 = b2 / a0
        filter.a1 = a1 / a0
        filter.a2 = a2 / a0
        filter.reset()
    }

    private fun highShelf(filter: Bq, frequency: Double, db: Double) {
        val a = 10.0.pow(db / 40.0)
        val w = 2.0 * PI * frequency / sampleRate
        val cosine = cos(w)
        val alpha = sin(w) / 2.0 * sqrt(2.0)
        val slope = 2.0 * sqrt(a) * alpha

        val b0 = a * ((a + 1.0) + (a - 1.0) * cosine + slope)
        val b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosine)
        val b2 = a * ((a + 1.0) + (a - 1.0) * cosine - slope)
        val a0 = (a + 1.0) - (a - 1.0) * cosine + slope
        val a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosine)
        val a2 = (a + 1.0) - (a - 1.0) * cosine - slope

        filter.b0 = b0 / a0
        filter.b1 = b1 / a0
        filter.b2 = b2 / a0
        filter.a1 = a1 / a0
        filter.a2 = a2 / a0
        filter.reset()
    }

    private fun peaking(filter: Bq, frequency: Double, db: Double, q: Double) {
        val a = 10.0.pow(db / 40.0)
        val w = 2.0 * PI * frequency / sampleRate
        val cosine = cos(w)
        val alpha = sin(w) / (2.0 * q)

        val b0 = 1.0 + alpha * a
        val b1 = -2.0 * cosine
        val b2 = 1.0 - alpha * a
        val a0 = 1.0 + alpha / a
        val a1 = -2.0 * cosine
        val a2 = 1.0 - alpha / a

        filter.b0 = b0 / a0
        filter.b1 = b1 / a0
        filter.b2 = b2 / a0
        filter.a1 = a1 / a0
        filter.a2 = a2 / a0
        filter.reset()
    }

    private fun configure() {
        val level = intensity.coerceIn(0, 10) / 10.0
        if (mode == EffectMode.VOICE) {
            lowShelf(low[0], 220.0, -(5.0 + 9.0 * level))
            lowShelf(low[1], 220.0, -(5.0 + 9.0 * level))
            peaking(peak[0], 2500.0, 2.0 + 6.0 * level, 0.9)
            peaking(peak[1], 2500.0, 2.0 + 6.0 * level, 0.9)
            highShelf(high[0], 5000.0, -(3.0 + 8.0 * level))
            highShelf(high[1], 5000.0, -(3.0 + 8.0 * level))
        }
    }

    override fun queueInput(input: ByteBuffer) {
        val level = intensity.coerceIn(0, 10) / 10.0
        val output = replaceOutputBuffer(input.remaining())

        if (intensity <= 0) {
            output.put(input)
            output.flip()
            return
        }

        val key = "$mode|$intensity|$calm|$sampleRate"
        if (key != configKey) {
            configKey = key
            configure()
        }

        val frames = input.remaining() / (2 * channels)
        val target = when (mode) {
            EffectMode.FLANGER -> if (calm) 0.85 * level else 0.22 * level
            EffectMode.VOICE -> if (calm) 1.0 else 0.45
        }
        val ramp = 1.0 / (rampMs / 1000.0 * sampleRate)
        mix = if (mix < target) min(target, mix + ramp) else max(target, mix - ramp)

        when (mode) {
            EffectMode.FLANGER -> {
                val depth = (0.8 + 4.2 * level) * sampleRate / 1000.0
                val rate = 0.15 + 0.35 * level
                val feedback = 0.25 + 0.45 * level

                repeat(frames) {
                    lfo += 2.0 * PI * rate / sampleRate
                    if (lfo > 2.0 * PI) lfo -= 2.0 * PI
                    val delaySamples = 1.0 + depth * (0.5 + 0.5 * sin(lfo))

                    for (channel in 0 until channels) {
                        val channelIndex = min(channel, 1)
                        val buffer = delay[channelIndex]
                        val readPosition = writeIndex - delaySamples
                        val wrapped = ((readPosition % buffer.size) + buffer.size) % buffer.size
                        val index0 = wrapped.toInt()
                        val index1 = (index0 + 1) % buffer.size
                        val fraction = wrapped - index0
                        val delayed = buffer[index0] * (1.0 - fraction) + buffer[index1] * fraction

                        val dry = input.short / 32768.0
                        buffer[writeIndex] = dry + delayed * feedback
                        val wet = delayed
                        val result = dry * (1.0 - mix) + wet * mix
                        output.putShort((result.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort())
                    }
                    writeIndex = (writeIndex + 1) % delay[0].size
                }
            }

            EffectMode.VOICE -> {
                repeat(frames) {
                    for (channel in 0 until channels) {
                        val channelIndex = min(channel, 1)
                        val dry = input.short / 32768.0
                        val filtered = high[channelIndex]
                            .process(peak[channelIndex].process(low[channelIndex].process(dry)))
                        val result = dry * (1.0 - mix) + filtered * mix
                        output.putShort((result.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort())
                    }
                }
            }
        }

        output.flip()
    }
}
