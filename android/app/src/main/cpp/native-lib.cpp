#include <jni.h>
#include <string>
#include <oboe/Oboe.h>
#include <android/log.h>

// Log tag for debugging
#define TAG "OboeEngine"

using namespace oboe;

// The engine class handles the audio streams
class AudioEngine : public AudioStreamCallback {
public:
    std::shared_ptr<AudioStream> recordingStream;
    std::shared_ptr<AudioStream> playbackStream;

    bool start() {
        // 1. Create Recording Stream (Input)
        AudioStreamBuilder builderInput;
        builderInput.setDirection(Direction::Input)
                ->setPerformanceMode(PerformanceMode::LowLatency)
                ->setSharingMode(SharingMode::Exclusive) // Vital for lowest latency
                ->setFormat(AudioFormat::I16)
                ->setChannelCount(Mono)
                ->setSampleRate(48000)
                ->setInputPreset(InputPreset::Unprocessed)
                ->setCallback(this); // We handle data in onAudioReady below

        Result result = builderInput.openStream(recordingStream);
        if (result != Result::OK) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to open input stream");
            return false;
        }

        // 2. Create Playback Stream (Output)
        AudioStreamBuilder builderOutput;
        builderOutput.setDirection(Direction::Output)
                ->setPerformanceMode(PerformanceMode::LowLatency)
                ->setSharingMode(SharingMode::Exclusive)
                ->setFormat(AudioFormat::I16)
                ->setChannelCount(Mono)
                ->setSampleRate(48000); // Must match input

        result = builderOutput.openStream(playbackStream);
        if (result != Result::OK) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to open output stream");
            return false;
        }

        // 3. Start Both
        // Note: We start playback first so it's ready to receive data
        playbackStream->requestStart();
        recordingStream->requestStart();

        return true;
    }

    void stop() {
        if (recordingStream) {
            recordingStream->stop();
            recordingStream->close();
        }
        if (playbackStream) {
            playbackStream->stop();
            playbackStream->close();
        }
    }

    // This runs on the high-priority audio thread. NO HEAVY LOGIC HERE!
    DataCallbackResult onAudioReady(AudioStream *oboeStream, void *audioData, int32_t numFrames) override {
        if (playbackStream && playbackStream->getState() == StreamState::Started) {
            
            // --- OPTIONAL: SOFTWARE AMPLIFIER (GAIN) ---
            // Cast the void* buffer to int16 so we can do math on audio samples
            int16_t *buffer = static_cast<int16_t *>(audioData);
            
            // Gain factor: 1.0 is normal, 2.0 is double volume, 4.0 is loud.
            // Be careful! Too high causes distortion (clipping).
            float gain = 2.0f; 

            for (int i = 0; i < numFrames; ++i) {
                // Multiply sample by gain
                int32_t amplified = (int32_t)(buffer[i] * gain);

                // "Clamp" the values so they don't exceed the 16-bit limit (prevents horrible crackling)
                if (amplified > 32767) amplified = 32767;
                if (amplified < -32768) amplified = -32768;

                buffer[i] = (int16_t)amplified;
            }
            // -------------------------------------------

            // Write the amplified audio to output
            playbackStream->write(buffer, numFrames, 0);
        }
        return DataCallbackResult::Continue;
    }
};

// Global instance (simple approach for this use case)
static AudioEngine engine;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_voice_1loopback_AudioLoopbackService_startOboe(JNIEnv *env, jobject thiz) {
    return engine.start();
}

JNIEXPORT void JNICALL
Java_com_example_voice_1loopback_AudioLoopbackService_stopOboe(JNIEnv *env, jobject thiz) {
    engine.stop();
}

}