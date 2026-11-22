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

    @Volatile var calm = false
    @Volatile var bassDb = -9f
    @Volatile var trebleDb = -6f
    @Volatile var gainDb = -3f
    @Volatile var rampMs = 400

    private class Bq(var b0: Double = 1.0, var b1: Double = 0.0, var b2: Double = 0.0, var a1: Double = 0.0, var a2: Double = 0.0) {
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        fun p(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y; return y
        }
    }

    private var low = Array(2) { Bq() }
    private var high = Array(2) { Bq() }
    private var mix = 0.0
    private var sr = 44100
    private var ch = 2
    private var cfgKey = ""

    override fun onConfigure(f: AudioFormat): AudioFormat {
        if (f.encoding != C.ENCODING_PCM_16BIT) throw UnhandledAudioFormatException(f)
        sr = f.sampleRate
        ch = f.channelCount.coerceAtMost(2)
        return f
    }

    private fun shelf(b: Bq, hi: Boolean, f0: Double, db: Double) {
        val A = 10.0.pow(db / 40)
        val w = 2 * PI * f0 / sr
        val c = cos(w)
        val al = sin(w) / 2 * sqrt(2.0)
        val s = 2 * sqrt(A) * al
        val b0: Double; val b1: Double; val b2: Double; val a0: Double; val a1: Double; val a2: Double
        if (!hi) {
            b0 = A * ((A + 1) - (A - 1) * c + s); b1 = 2 * A * ((A - 1) - (A + 1) * c); b2 = A * ((A + 1) - (A - 1) * c - s)
            a0 = (A + 1) + (A - 1) * c + s; a1 = -2 * ((A - 1) + (A + 1) * c); a2 = (A + 1) + (A - 1) * c - s
        } else {
            b0 = A * ((A + 1) + (A - 1) * c + s); b1 = -2 * A * ((A - 1) + (A + 1) * c); b2 = A * ((A + 1) + (A - 1) * c - s)
            a0 = (A + 1) - (A - 1) * c + s; a1 = 2 * ((A - 1) - (A + 1) * c); a2 = (A + 1) - (A - 1) * c - s
        }
        b.b0 = b0 / a0; b.b1 = b1 / a0; b.b2 = b2 / a0; b.a1 = a1 / a0; b.a2 = a2 / a0
    }

    override fun queueInput(input: ByteBuffer) {
        val key = "$bassDb|$trebleDb|$sr"
        if (key != cfgKey) {
            cfgKey = key
            for (i in 0..1) {
                shelf(low[i], false, 150.0, bassDb.toDouble())
                shelf(high[i], true, 3500.0, trebleDb.toDouble())
            }
        }
        val out = replaceOutputBuffer(input.remaining())
        val g = 10.0.pow(gainDb / 20.0)
        val step = 1.0 / (rampMs / 1000.0 * sr)
        val target = if (calm) 1.0 else 0.0
        val frames = input.remaining() / (2 * channelCountIn())
        repeat(frames) {
            mix = if (mix < target) min(target, mix + step) else max(target, mix - step)
            for (c in 0 until channelCountIn()) {
                val x = input.short / 32768.0
                val ci = min(c, 1)
                val wet = high[ci].p(low[ci].p(x)) * g
                val y = x * (1 - mix) + wet * mix
                out.putShort((y.coerceIn(-1.0, 1.0) * 32767).toInt().toShort())
            }
        }
        out.flip()
    }

    private fun channelCountIn() = ch
}
