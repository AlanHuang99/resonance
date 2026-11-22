package com.resonance.music.tabata

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

enum class Phase { WARMUP, WORK, REST, COOLDOWN, DONE }

data class TabataConfig(
    val warmup: Int = 60,
    val work: Int = 20,
    val rest: Int = 10,
    val rounds: Int = 8,
    val cooldown: Int = 60
)

data class TabataState(val phase: Phase, val round: Int, val secLeft: Int)

class TabataEngine(
    private val fx: TabataAudioProcessor,
    private val scope: CoroutineScope
) {
    val state = MutableStateFlow(TabataState(Phase.DONE, 0, 0))
    private var job: Job? = null

    fun start(c: TabataConfig, onBeep: (Phase) -> Unit = {}) {
        job?.cancel()
        job = scope.launch {
            suspend fun run(p: Phase, s: Int, r: Int, calm: Boolean) {
                fx.calm = calm
                onBeep(p)
                for (t in s downTo 1) {
                    state.value = TabataState(p, r, t)
                    delay(1000)
                }
            }
            if (c.warmup > 0) run(Phase.WARMUP, c.warmup, 0, true)
            for (r in 1..c.rounds) {
                run(Phase.WORK, c.work, r, false)
                if (r < c.rounds || c.cooldown > 0) run(Phase.REST, c.rest, r, true)
            }
            if (c.cooldown > 0) run(Phase.COOLDOWN, c.cooldown, c.rounds, true)
            fx.calm = false
            state.value = TabataState(Phase.DONE, 0, 0)
        }
    }

    fun stop() {
        job?.cancel()
        fx.calm = false
        state.value = TabataState(Phase.DONE, 0, 0)
    }
}
