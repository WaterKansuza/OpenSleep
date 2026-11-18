#pragma once

#include <oboe/Oboe.h>
#include <atomic>
#include <array>
#include <cmath>
#include <cstdint>

struct IQBasebandSample {
    float iVal;
    float qVal;
    float carrierGoertzelMag;
    float noiseGoertzelMag;
};

struct SonarMetrics {
    float motionIntensity;
    float breathingRateBpm;
    float confidence;
    float gestureImpulse;
    float carrierSnrDb;
    float latestBreathWave;
};

// Bộ lọc IIR Biquad dùng double cho độ ổn định số ở tần số cực thấp (0.1 - 0.5 Hz)
class BiquadFilter {
public:
    void configureLowPass(double sampleRate, double cutoffHz);
    void configureHighPass(double sampleRate, double cutoffHz);
    void reset();
    double process(double input);

private:
    double b0 = 1.0, b1 = 0.0, b2 = 0.0;
    double a1 = 0.0, a2 = 0.0;
    double z1 = 0.0, z2 = 0.0;
};

class SonarEngine {
public:
    SonarEngine() = default;
    ~SonarEngine();

    bool start(double carrierFreqHz = 18750.0);
    void stop();
    SonarMetrics pollAndProcessMetrics();

private:
    class OutputCallback : public oboe::AudioStreamDataCallback {
    public:
        explicit OutputCallback(SonarEngine* parent) : engine(parent) {}
        oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* stream, void* audioData, int32_t numFrames) override;
    private:
        SonarEngine* engine;
    };

    class InputCallback : public oboe::AudioStreamDataCallback {
    public:
        explicit InputCallback(SonarEngine* parent) : engine(parent) {}
        oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* stream, void* audioData, int32_t numFrames) override;
    private:
        SonarEngine* engine;
    };

    static constexpr int32_t kSampleRate = 48000;
    static constexpr int32_t kDecimationFactor = 480; // 48000 Hz -> 100 Hz
    static constexpr double kBasebandRate = 100.0;
    static constexpr size_t kRingCapacity = 2048;
    static constexpr size_t kBreathWindowSize = 2000; // 20 giây @ 100Hz

    std::shared_ptr<oboe::AudioStream> outputStream;
    std::shared_ptr<oboe::AudioStream> inputStream;
    std::unique_ptr<OutputCallback> outCallback;
    std::unique_ptr<InputCallback> inCallback;

    std::atomic<bool> isRunning{false};
    double carrierHz = 18750.0;
    double txPhase = 0.0;
    double rxPhase = 0.0;
    double phaseStep = 0.0;

    // Tích lũy decimation trên audio input thread
    double decAccumI = 0.0;
    double decAccumQ = 0.0;
    int32_t decCounter = 0;

    // Goertzel cho Calibration nhanh (carrier vs noise bin)
    double gCoeffCarrier = 0.0;
    double gCoeffNoise = 0.0;
    double gCarrierS1 = 0.0, gCarrierS2 = 0.0;
    double gNoiseS1 = 0.0, gNoiseS2 = 0.0;
    int32_t gSampleCount = 0;
    float lastCarrierMag = 0.0f;
    float lastNoiseMag = 1e-6f;

    // Lock-free SPSC Ring Buffer
    std::array<IQBasebandSample, kRingCapacity> ringBuffer{};
    std::atomic<size_t> ringWriteIdx{0};
    std::atomic<size_t> ringReadIdx{0};

    // Trạng thái DSP ở 100 Hz (chỉ chạy trên worker thread pollAndProcessMetrics)
    double staticI = 0.0;
    double staticQ = 0.0;
    bool staticInitialized = false;
    double prevAngle = 0.0;
    double unwrappedPhase = 0.0;
    double prevUnwrappedPhase = 0.0;
    bool phaseInitialized = false;

    BiquadFilter breathHighPass;
    BiquadFilter breathLowPass;

    std::array<float, kBreathWindowSize> breathHistory{};
    size_t breathHistoryIdx = 0;
    size_t breathHistoryCount = 0;

    bool pushRingSample(const IQBasebandSample& sample);
    bool popRingSample(IQBasebandSample& out);
    float estimateBreathingBpm() const;
};
