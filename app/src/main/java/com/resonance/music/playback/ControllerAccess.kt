package com.resonance.music.playback

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import androidx.media3.session.MediaSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ControllerAccess @Inject constructor(@ApplicationContext private val context: Context) {
    fun allowed(controller: MediaSession.ControllerInfo): Boolean {
        if (controller.uid == Process.myUid() || controller.isTrusted) return true
        val certificates = CAR_CERTIFICATES[controller.packageName] ?: return false
        val packages = context.packageManager.getPackagesForUid(controller.uid).orEmpty()
        if (controller.packageName !in packages) return false
        return certificates.any { certificate ->
            context.packageManager.hasSigningCertificate(controller.packageName,
                certificate.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),
                PackageManager.CERT_INPUT_SHA256)
        }
    }

    companion object {
        // Production signing certificates published by Android's Universal Android Music Player sample.
        // https://github.com/android/uamp/blob/main/common/src/main/res/xml/allowed_media_browser_callers.xml
        private val CAR_CERTIFICATES = mapOf(
            "com.google.android.projection.gearhead" to listOf(
                "fdb00c43dbde8b51cb312aa81d3b5fa17713adb94b28f598d77f8eb89daceedf",
                "1ca8dcc0bed3cbd872d2cb791200c0292ca9975768a82d676b8b424fb65b5295"
            ),
            "com.google.android.googlequicksearchbox" to listOf(
                "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83"
            )
        )
    }
}
