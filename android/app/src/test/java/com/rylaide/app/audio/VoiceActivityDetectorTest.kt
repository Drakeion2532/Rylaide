package com.rylaide.app.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceActivityDetectorTest {

    /** A chunk whose loudness (RMS) is exactly [level]. */
    private fun chunk(level: Int) = ShortArray(IntercomAudioEngine.FRAME_SAMPLES) { i ->
        (if (i % 2 == 0) level else -level).toShort()
    }

    @Test
    fun quietRoomSoftSpeechPasses() {
        val vad = VoiceActivityDetector(hangoverFrames = 0)
        repeat(100) { assertFalse(vad.isSpeech(chunk(25))) }   // background hiss
        assertTrue(vad.isSpeech(chunk(400)))                      // soft voice
    }

    @Test
    fun steadyWindRaisesTheGate() {
        val vad = VoiceActivityDetector(hangoverFrames = 0)
        repeat(100) { vad.isSpeech(chunk(25)) }
        repeat(400) { vad.isSpeech(chunk(1200)) }                // sustained wind
        assertFalse(vad.isSpeech(chunk(1200)))                    // wind alone no longer opens it
        assertTrue(vad.isSpeech(chunk(6000)))                     // a loud voice still does
    }

    @Test
    fun manualModeUsesTheFixedLevel() {
        val vad = VoiceActivityDetector(threshold = 500.0, hangoverFrames = 0)
        vad.auto = false
        assertFalse(vad.isSpeech(chunk(400)))
        assertTrue(vad.isSpeech(chunk(600)))
    }

    @Test
    fun sensitiveModeOpensForQuieterVoiceThanAuto() {
        fun opens(sensitive: Boolean): Boolean {
            val vad = VoiceActivityDetector()
            vad.auto = true
            vad.sensitive = sensitive
            repeat(200) { vad.isSpeech(chunk(500)) }      // learn steady background of 500
            return vad.isSpeech(chunk(900))               // voice only 1.8x the background
        }
        assertFalse("auto should stay closed at 1.8x", opens(false))
        assertTrue("sensitive should open at 1.8x", opens(true))
    }

    @Test
    fun alwaysSendNeverClosesTheGate() {
        val vad = VoiceActivityDetector()
        vad.alwaysSend = true
        repeat(100) { assertTrue(vad.isSpeech(chunk(5))) }
    }
}
