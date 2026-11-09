package com.resonance.music

import androidx.test.platform.app.InstrumentationRegistry

internal val fixtureUsername: String
    get() = InstrumentationRegistry.getArguments().getString("fixtureUsername", "device-test")
