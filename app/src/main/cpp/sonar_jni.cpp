#include <jni.h>
#include "sonar_engine.h"

static SonarEngine* gEngine = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_org_opensleep_app_service_tracking_SonarNativeBridge_nativeStart(
    JNIEnv*, jobject, jdouble carrierFreqHz) {
    if (gEngine == nullptr) {
        gEngine = new SonarEngine();
    }
    return gEngine->start(carrierFreqHz) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_org_opensleep_app_service_tracking_SonarNativeBridge_nativeStop(JNIEnv*, jobject) {
    if (gEngine != nullptr) {
        gEngine->stop();
        delete gEngine;
        gEngine = nullptr;
    }
}

JNIEXPORT jfloatArray JNICALL
Java_org_opensleep_app_service_tracking_SonarNativeBridge_nativePollMetrics(JNIEnv* env, jobject) {
    jfloatArray result = env->NewFloatArray(6);
    if (result == nullptr) return nullptr;

    float data[6] = {0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
    if (gEngine != nullptr) {
        SonarMetrics m = gEngine->pollAndProcessMetrics();
        data[0] = m.motionIntensity;
        data[1] = m.breathingRateBpm;
        data[2] = m.confidence;
        data[3] = m.gestureImpulse;
        data[4] = m.carrierSnrDb;
        data[5] = m.latestBreathWave;
    }
    env->SetFloatArrayRegion(result, 0, 6, data);
    return result;
}

}
