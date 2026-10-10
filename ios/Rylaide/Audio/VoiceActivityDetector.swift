/// On-device voice activity detection, no internet needed. Filters wind
/// and engine noise out of the "unmuted" audio stream so only actual
/// speech gets sent - saves bandwidth/battery and keeps the group's audio
/// clean. Start with a simple energy threshold, swap in a real model later
/// without touching anything else.
final class VoiceActivityDetector {

    func isSpeech(audioChunk: [Int16]) -> Bool {
        // TODO: replace with a real VAD algorithm/model
        return true
    }
}
