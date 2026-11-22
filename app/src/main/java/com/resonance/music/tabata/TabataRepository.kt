package com.resonance.music.tabata

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TabataRepository @Inject constructor() {

    val audioProcessor: TabataAudioProcessor = TabataAudioProcessor()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val engine: TabataEngine = TabataEngine(audioProcessor, scope)

    fun start(config: TabataConfig) {
        engine.start(config)
    }

    fun stop() {
        engine.stop()
    }
}
