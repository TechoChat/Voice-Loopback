#include <jni.h>
#include <string>
#include <oboe/Oboe.h>
#include <android/log.h>
#include <atomic> // Required for thread-safe volume control

#define TAG "OboeEngine"

using namespace oboe;

// Global atomic variable for volume.
// This allows the UI thread to update volume while the Audio thread reads it safely.
std::atomic<float> currentGain(1.0f);

class AudioEngine : public AudioStreamCallback {
public:
    std::shared_ptr<AudioStream> recordingStream;
    std::shared_ptr<AudioStream> playbackStream;

    // Modified start function to accept a specific Microphone Device ID
    bool start(int32_t inputDeviceId) {
        
        // 1. Configure Recording Stream (Input)
        AudioStreamBuilder builderInput;
        builderInput.setDirection(Direction::Input)
                ->setPerformanceMode(PerformanceMode::LowLatency)
                ->setSharingMode(SharingMode::Exclusive)
                ->setFormat(AudioFormat::I16)
                ->setChannelCount(Mono)
                ->setSampleRate(kUnspecified)
                ->setInputPreset(InputPreset::Unprocessed) 
                ->setCallback(this);

        // Force the specific microphone ID (e.g., Built-in Mic) if provided.
        // This ensures we don't accidentally record from the wired headset mic in your pocket.
        if (inputDeviceId != 0) {
            builderInput.setDeviceId(inputDeviceId);
        }

        Result result = builderInput.openStream(recordingStream);
        if (result != Result::OK) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to open input stream");
            return false;
        }

        // 2. Configure Playback Stream (Output)
        AudioStreamBuilder builderOutput;
        builderOutput.setDirection(Direction::Output)
                ->setPerformanceMode(PerformanceMode::LowLatency)
                ->setSharingMode(SharingMode::Exclusive)
                ->setFormat(AudioFormat::I16)
                ->setChannelCount(Mono)
                ->setSampleRate(recordingStream->getSampleRate()); // Must match input sample rate

        result = builderOutput.openStream(playbackStream);
        if (result != Result::OK) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to open output stream");
            return false;
        }

        // 3. Start Both Streams
        // We start playback first so it is ready to consume data immediately
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

    // This callback runs on the high-priority audio thread (thousands of times per second)
    DataCallbackResult onAudioReady(AudioStream *oboeStream, void *audioData, int32_t numFrames) override {
        if (playbackStream && playbackStream->getState() == StreamState::Started) {
            
            // Cast void buffer to int16 so we can process the audio samples
            int16_t *buffer = static_cast<int16_t *>(audioData);
            
            // Load the current volume level set by the slider
            float gain = currentGain.load(); 

            // Process every audio sample
            for (int i = 0; i < numFrames; ++i) {
                // 1. Promote to 32-bit integer to prevent overflow during multiplication
                int32_t sample = buffer[i];
                
                // 2. Apply Gain (Volume Boost)
                int32_t amplified = (int32_t)(sample * gain);

                // 3. Hard Limiter (Clamping)
                // Ensures we don't exceed the 16-bit limits (-32768 to 32767)
                // preventing "integer wraparound" which sounds like static noise.
                if (amplified > 32767) {
                    amplified = 32767;
                } else if (amplified < -32768) {
                    amplified = -32768;
                }

                // 4. Write back to the buffer
                buffer[i] = (int16_t)amplified;
            }

            // Write the processed buffer to the Output Stream (Speaker/Headset)
            playbackStream->write(buffer, numFrames, 0);
        }
        return DataCallbackResult::Continue;
    }
};

static AudioEngine engine;

extern "C" {

// Start Oboe with a specific Microphone ID
JNIEXPORT jboolean JNICALL
Java_com_example_voice_1loopback_AudioLoopbackService_startOboe(JNIEnv *env, jobject thiz, jint inputDeviceId) {
    return engine.start(inputDeviceId);
}

// Stop Oboe
JNIEXPORT void JNICALL
Java_com_example_voice_1loopback_AudioLoopbackService_stopOboe(JNIEnv *env, jobject thiz) {
    engine.stop();
}

// Update Volume dynamically
JNIEXPORT void JNICALL
Java_com_example_voice_1loopback_AudioLoopbackService_setNativeVolume(JNIEnv *env, jobject thiz, jfloat volume) {
    currentGain.store(volume);
}

}