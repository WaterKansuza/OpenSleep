#include "sonar_engine.h"
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "OpenSleepSonar"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static constexpr double kTwoPi = 6.28318530717958647692;
static constexpr double kPi = 3.14159265358979323846;

void BiquadFilter::configureLowPass(double sampleRate, double cutoffHz) {
    double K = std::tan(kPi * cutoffHz / sampleRate);
    double K2 = K * K;
    double sqrt2 = 1.4142135623730951;
    double norm = 1.0 / (1.0 + sqrt2 * K + K2);
    b0 = K2 * norm;
    b1 = 2.0 * b0;
    b2 = b0;
    a1 = 2.0 * (K2 - 1.0) * norm;
    a2 = (1.0 - sqrt2 * K + K2) * norm;
    reset();
}

void BiquadFilter::configureHighPass(double sampleRate, double cutoffHz) {
    double K = std::tan(kPi * cutoffHz / sampleRate);
    double K2 = K * K;
    double sqrt2 = 1.4142135623730951;
    double norm = 1.0 / (1.0 + sqrt2 * K + K2);
    b0 = 1.0 * norm;
    b1 = -2.0 * b0;
    b2 = b0;
    a1 = 2.0 * (K2 - 1.0) * norm;
    a2 = (1.0 - sqrt2 * K + K2) * norm;
    reset();
}

void BiquadFilter::reset() {
    z1 = 0.0;
    z2 = 0.0;
}

double BiquadFilter::process(double input) {
    double out = input * b0 + z1;
    z1 = input * b1 + z2 - a1 * out;
    z2 = input * b2 - a2 * out;
    return out;
}

SonarEngine::~SonarEngine() {
    stop();
}

bool SonarEngine::start(double carrierFreqHz) {
    if (isRunning.load()) return true;

    // Căn chỉnh sóng mang vào chính tâm bin FFT 4096 để triệt tiêu rò rỉ phổ
    double df = static_cast<double>(kSampleRate) / 4096.0;
    double bin = std::round(carrierFreqHz / df);
    carrierHz = bin * df;
    phaseStep = kTwoPi * carrierHz / static_cast<double>(kSampleRate);

    txPhase = 0.0;
    rxPhase = 0.0;
    decAccumI = 0.0;
    decAccumQ = 0.0;
    decCounter = 0;

    // Goertzel coefficients cho sóng mang và vùng nhiễu lân cận (17500 Hz)
    gCoeffCarrier = 2.0 * std::cos(kTwoPi * carrierHz / static_cast<double>(kSampleRate));
    gCoeffNoise = 2.0 * std::cos(kTwoPi * 17500.0 / static_cast<double>(kSampleRate));
    gCarrierS1 = gCarrierS2 = gNoiseS1 = gNoiseS2 = 0.0;
    gSampleCount = 0;

    ringWriteIdx.store(0);
    ringReadIdx.store(0);
    staticInitialized = false;
    phaseInitialized = false;
    unwrappedPhase = 0.0;
    prevUnwrappedPhase = 0.0;
    breathHistoryIdx = 0;
    breathHistoryCount = 0;

    // Bộ lọc dải thở người: 0.10 Hz (6 bpm) đến 0.55 Hz (33 bpm)
    breathHighPass.configureHighPass(kBasebandRate, 0.10);
    breathLowPass.configureLowPass(kBasebandRate, 0.55);

    outCallback = std::make_unique<OutputCallback>(this);
    inCallback = std::make_unique<InputCallback>(this);

    // 1. Mở luồng phát sóng siêu âm CW
    oboe::AudioStreamBuilder outBuilder;
    outBuilder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Shared)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Mono)
        ->setSampleRate(kSampleRate)
        ->setUsage(oboe::Usage::Media)
        ->setContentType(oboe::ContentType::Sonification)
        ->setDataCallback(outCallback.get());

    if (outBuilder.openStream(outputStream) != oboe::Result::OK) {
        LOGE("Failed to open Oboe Output stream");
        return false;
    }

    // 2. Mở luồng thu Mic thô (Unprocessed để né AGC/Noise Suppression phần cứng)
    oboe::AudioStreamBuilder inBuilder;
    inBuilder.setDirection(oboe::Direction::Input)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Shared)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Mono)
        ->setSampleRate(kSampleRate)
        ->setInputPreset(oboe::InputPreset::Unprocessed)
        ->setDataCallback(inCallback.get());

    if (inBuilder.openStream(inputStream) != oboe::Result::OK) {
        LOGI("Unprocessed input unavailable, falling back to VoiceRecognition");
        inBuilder.setInputPreset(oboe::InputPreset::VoiceRecognition);
        if (inBuilder.openStream(inputStream) != oboe::Result::OK) {
            LOGE("Failed to open Oboe Input stream");
            outputStream->close();
            outputStream.reset();
            return false;
        }
    }

    isRunning.store(true);
    if (inputStream->requestStart() != oboe::Result::OK ||
        outputStream->requestStart() != oboe::Result::OK) {
        stop();
        return false;
    }

    LOGI("SonarEngine started at %.2f Hz", carrierHz);
    return true;
}

void SonarEngine::stop() {
    isRunning.store(false);
    if (outputStream) {
        outputStream->requestStop();
        outputStream->close();
        outputStream.reset();
    }
    if (inputStream) {
        inputStream->requestStop();
        inputStream->close();
        inputStream.reset();
    }
    outCallback.reset();
    inCallback.reset();
}

oboe::DataCallbackResult SonarEngine::OutputCallback::onAudioReady(
    oboe::AudioStream*, void* audioData, int32_t numFrames) {
    auto* out = static_cast<float*>(audioData);
    if (!engine->isRunning.load(std::memory_order_relaxed)) {
        std::fill(out, out + numFrames, 0.0f);
        return oboe::DataCallbackResult::Continue;
    }

    constexpr float kAmplitude = 0.25f; // Đủ mạnh để thu phản xạ nhưng an toàn cho loa
    double phase = engine->txPhase;
    const double step = engine->phaseStep;

    for (int32_t i = 0; i < numFrames; ++i) {
        out[i] = kAmplitude * static_cast<float>(std::sin(phase));
        phase += step;
        if (phase >= kTwoPi) phase -= kTwoPi;
    }
    engine->txPhase = phase;
    return oboe::DataCallbackResult::Continue;
}

oboe::DataCallbackResult SonarEngine::InputCallback::onAudioReady(
    oboe::AudioStream*, void* audioData, int32_t numFrames) {
    if (!engine->isRunning.load(std::memory_order_relaxed)) {
        return oboe::DataCallbackResult::Continue;
    }

    const auto* in = static_cast<const float*>(audioData);
    double phase = engine->rxPhase;
    const double step = engine->phaseStep;

    for (int32_t i = 0; i < numFrames; ++i) {
        const double sample = static_cast<double>(in[i]);

        // 1. Coherent I/Q Demodulation
        engine->decAccumI += sample * std::cos(phase);
        engine->decAccumQ += sample * (-std::sin(phase));
        engine->decCounter++;

        phase += step;
        if (phase >= kTwoPi) phase -= kTwoPi;

        // 2. Goertzel song song để đo SNR Calibration
        double cS0 = sample + engine->gCoeffCarrier * engine->gCarrierS1 - engine->gCarrierS2;
        engine->gCarrierS2 = engine->gCarrierS1;
        engine->gCarrierS1 = cS0;

        double nS0 = sample + engine->gCoeffNoise * engine->gNoiseS1 - engine->gNoiseS2;
        engine->gNoiseS2 = engine->gNoiseS1;
        engine->gNoiseS1 = nS0;
        engine->gSampleCount++;

        if (engine->gSampleCount >= 4096) {
            double cPower = engine->gCarrierS1 * engine->gCarrierS1 + engine->gCarrierS2 * engine->gCarrierS2
                          - engine->gCoeffCarrier * engine->gCarrierS1 * engine->gCarrierS2;
            double nPower = engine->gNoiseS1 * engine->gNoiseS1 + engine->gNoiseS2 * engine->gNoiseS2
                          - engine->gCoeffNoise * engine->gNoiseS1 * engine->gNoiseS2;
            engine->lastCarrierMag = static_cast<float>(std::max(1e-12, cPower));
            engine->lastNoiseMag = static_cast<float>(std::max(1e-12, nPower));
            engine->gCarrierS1 = engine->gCarrierS2 = engine->gNoiseS1 = engine->gNoiseS2 = 0.0;
            engine->gSampleCount = 0;
        }

        // 3. Decimate 480 mẫu -> 1 mẫu 100 Hz đẩy vào Lock-free Ring Buffer
        if (engine->decCounter >= kDecimationFactor) {
            IQBasebandSample iq{
                static_cast<float>(engine->decAccumI / kDecimationFactor),
                static_cast<float>(engine->decAccumQ / kDecimationFactor),
                engine->lastCarrierMag,
                engine->lastNoiseMag
            };
            engine->pushRingSample(iq);
            engine->decAccumI = 0.0;
            engine->decAccumQ = 0.0;
            engine->decCounter = 0;
        }
    }
    engine->rxPhase = phase;
    return oboe::DataCallbackResult::Continue;
}

bool SonarEngine::pushRingSample(const IQBasebandSample& sample) {
    size_t w = ringWriteIdx.load(std::memory_order_relaxed);
    size_t nextW = (w + 1) % kRingCapacity;
    if (nextW == ringReadIdx.load(std::memory_order_acquire)) {
        return false; // Ring full
    }
    ringBuffer[w] = sample;
    ringWriteIdx.store(nextW, std::memory_order_release);
    return true;
}

bool SonarEngine::popRingSample(IQBasebandSample& out) {
    size_t r = ringReadIdx.load(std::memory_order_relaxed);
    if (r == ringWriteIdx.load(std::memory_order_acquire)) {
        return false;
    }
    out = ringBuffer[r];
    ringReadIdx.store((r + 1) % kRingCapacity, std::memory_order_release);
    return true;
}

SonarMetrics SonarEngine::pollAndProcessMetrics() {
    IQBasebandSample sample{};
    double maxMotionImpulse = 0.0;
    double motionEnergySum = 0.0;
    int processedCount = 0;
    float snrDb = 0.0f;
    float latestWave = 0.0f;

    while (popRingSample(sample)) {
        // Triệt tiêu thành phần tĩnh (Direct Leakage + Tường cố định)
        if (!staticInitialized) {
            staticI = sample.iVal;
            staticQ = sample.qVal;
            staticInitialized = true;
        } else {
            constexpr double kStaticAlpha = 0.004; // Cửa sổ thích nghi ~2.5 giây
            staticI = (1.0 - kStaticAlpha) * staticI + kStaticAlpha * sample.iVal;
            staticQ = (1.0 - kStaticAlpha) * staticQ + kStaticAlpha * sample.qVal;
        }

        double iDyn = sample.iVal - staticI;
        double qDyn = sample.qVal - staticQ;

        double angle = std::atan2(qDyn, iDyn);
        if (!phaseInitialized) {
            prevAngle = angle;
            prevUnwrappedPhase = 0.0;
            phaseInitialized = true;
        } else {
            double delta = angle - prevAngle;
            if (delta > kPi) delta -= kTwoPi;
            else if (delta < -kPi) delta += kTwoPi;
            prevAngle = angle;
            unwrappedPhase += delta;
        }

        double phaseSpeed = std::abs(unwrappedPhase - prevUnwrappedPhase);
        prevUnwrappedPhase = unwrappedPhase;

        // Tốc độ dịch pha cao đột ngột -> Chuyển động cơ thể hoặc vẫy tay Gesture
        if (phaseSpeed > maxMotionImpulse) maxMotionImpulse = phaseSpeed;
        motionEnergySum += phaseSpeed;

        // Lọc thông dải 0.10 - 0.55 Hz lấy sóng hô hấp
        double breathSignal = breathLowPass.process(breathHighPass.process(unwrappedPhase));
        latestWave = static_cast<float>(breathSignal);

        breathHistory[breathHistoryIdx] = latestWave;
        breathHistoryIdx = (breathHistoryIdx + 1) % kBreathWindowSize;
        if (breathHistoryCount < kBreathWindowSize) breathHistoryCount++;

        snrDb = 10.0f * std::log10(sample.carrierGoertzelMag / std::max(1e-12f, sample.noiseGoertzelMag));
        processedCount++;
    }

    float avgMotion = processedCount > 0 ? static_cast<float>((motionEnergySum / processedCount) * 8.0) : 0.0f;
    float bpm = estimateBreathingBpm();
    float confidence = (snrDb >= 12.0f) ? 0.92f : (snrDb >= 6.0f ? 0.70f : 0.35f);

    return SonarMetrics{
        std::min(1.5f, avgMotion),
        bpm,
        confidence,
        static_cast<float>(maxMotionImpulse),
        snrDb,
        latestWave
    };
}

float SonarEngine::estimateBreathingBpm() const {
    // Cần tối thiểu 10 giây dữ liệu (1000 mẫu @ 100 Hz)
    if (breathHistoryCount < 1000) return 0.0f;

    // Đếm số lần cắt điểm 0 theo chiều dương (Zero-crossing) với ngưỡng chống nhiễu
    int zeroCrossings = 0;
    bool wasBelow = true;
    constexpr float kHysteresis = 0.02f;

    size_t startIdx = (breathHistoryCount == kBreathWindowSize) ? breathHistoryIdx : 0;
    for (size_t i = 0; i < breathHistoryCount; ++i) {
        size_t idx = (startIdx + i) % kBreathWindowSize;
        float v = breathHistory[idx];
        if (wasBelow && v > kHysteresis) {
            zeroCrossings++;
            wasBelow = false;
        } else if (!wasBelow && v < -kHysteresis) {
            wasBelow = true;
        }
    }

    float durationSeconds = static_cast<float>(breathHistoryCount) / static_cast<float>(kBasebandRate);
    float bpm = (static_cast<float>(zeroCrossings) / durationSeconds) * 60.0f;
    return (bpm >= 6.0f && bpm <= 30.0f) ? bpm : 0.0f;
}
