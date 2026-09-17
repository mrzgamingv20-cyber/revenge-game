#include <jni.h>
#include <memory>
#include "story_engine.h"

// Satu instance engine untuk seluruh proses aplikasi
static std::unique_ptr<StoryEngine> g_engine;

static StoryEngine& engine() {
    if (!g_engine) {
        g_engine = std::make_unique<StoryEngine>();
    }
    return *g_engine;
}

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_mrzgaming_revenge_MainActivity_nativeGetCurrentNode(JNIEnv* env, jobject /* this */) {
    std::string json = engine().getCurrentNodeJson();
    return env->NewStringUTF(json.c_str());
}

JNIEXPORT void JNICALL
Java_com_mrzgaming_revenge_MainActivity_nativeChooseOption(JNIEnv* env, jobject /* this */, jint index) {
    engine().choose(index);
}

JNIEXPORT void JNICALL
Java_com_mrzgaming_revenge_MainActivity_nativeReset(JNIEnv* env, jobject /* this */) {
    engine().reset();
}

} // extern "C"
