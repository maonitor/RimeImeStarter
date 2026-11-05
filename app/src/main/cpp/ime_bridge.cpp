// JNI 桥：把 librime 官方 C API (rime_api.h) 暴露给 Kotlin。
//
// librime 不是编译期依赖，而是运行时 dlopen("librime.so") + dlsym("rime_get_api")。
// rime_get_api() 返回自描述版本的 RimeApi 函数表，用 RIME_PROVIDED() 逐字段判断
// 某个函数在当前 .so 里是否存在，因此可以兼容 1.10 ~ 1.17 的 librime。
#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>

#include <cstring>
#include <string>
#include <vector>

#include "third_party/rime_api.h"

namespace {

constexpr const char* kTag = "RimeBridge";
using GetApiFn = RimeApi* (*)();

void* gLibrary = nullptr;
RimeApi* gApi = nullptr;
bool gLoadAttempted = false;
bool gInitialized = false;
RimeTraits gTraits{};

// traits 里的 const char* 必须在 librime 整个生命周期内有效，所以用静态 string 持有。
std::string gSharedDir;
std::string gUserDir;
std::string gAppName;

bool EnsureApi() {
  if (gApi != nullptr) return true;
  if (gLoadAttempted) return false;
  gLoadAttempted = true;

  gLibrary = dlopen("librime.so", RTLD_NOW | RTLD_LOCAL);
  if (gLibrary == nullptr) {
    __android_log_print(ANDROID_LOG_WARN, kTag, "librime.so 不可用: %s", dlerror());
    return false;
  }
  auto getApi = reinterpret_cast<GetApiFn>(dlsym(gLibrary, "rime_get_api"));
  if (getApi == nullptr) {
    __android_log_print(ANDROID_LOG_WARN, kTag, "librime.so 未导出 rime_get_api");
    return false;
  }
  RimeApi* api = getApi();
  if (api == nullptr || !RIME_PROVIDED(api, setup) ||
      !RIME_PROVIDED(api, initialize) || !RIME_PROVIDED(api, create_session) ||
      !RIME_PROVIDED(api, process_key) || !RIME_PROVIDED(api, get_context)) {
    __android_log_print(ANDROID_LOG_WARN, kTag, "librime.so 的 RimeApi 不完整");
    return false;
  }
  gApi = api;
  return true;
}

jstring ToJavaString(JNIEnv* env, const char* text) {
  return env->NewStringUTF(text != nullptr ? text : "");
}

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_rimeime_engine_RimeBridge_nativeIsEnginePresent(JNIEnv*, jobject) {
  return EnsureApi() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_rimeime_engine_RimeBridge_nativeInit(
    JNIEnv* env, jobject, jstring sharedDir, jstring userDir, jstring appName) {
  if (!EnsureApi()) return JNI_FALSE;

  const char* s = env->GetStringUTFChars(sharedDir, nullptr);
  const char* u = env->GetStringUTFChars(userDir, nullptr);
  const char* a = env->GetStringUTFChars(appName, nullptr);
  gSharedDir = s != nullptr ? s : "";
  gUserDir = u != nullptr ? u : "";
  gAppName = a != nullptr ? a : "rime.ime";
  if (s) env->ReleaseStringUTFChars(sharedDir, s);
  if (u) env->ReleaseStringUTFChars(userDir, u);
  if (a) env->ReleaseStringUTFChars(appName, a);

  gTraits = RimeTraits{};
  gTraits.data_size = sizeof(RimeTraits) - sizeof(gTraits.data_size);
  gTraits.shared_data_dir = gSharedDir.c_str();
  gTraits.user_data_dir = gUserDir.c_str();
  gTraits.distribution_name = "Rime";
  gTraits.distribution_code_name = "RimeImeStarter";
  gTraits.distribution_version = "1.12";
  gTraits.app_name = gAppName.c_str();
  gTraits.min_log_level = 2;

  gApi->setup(&gTraits);
  return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_rimeime_engine_RimeBridge_nativeDeploy(JNIEnv*, jobject) {
  if (!EnsureApi() || gTraits.shared_data_dir == nullptr) return JNI_FALSE;
  // deploy 会编译词典，可能耗时数秒，Kotlin 侧必须放在后台线程调用。
  if (RIME_PROVIDED(gApi, deployer_initialize)) {
    gApi->deployer_initialize(&gTraits);
  }
  if (RIME_PROVIDED(gApi, prebuild)) {
    gApi->prebuild();
  }
  if (RIME_PROVIDED(gApi, deploy)) {
    gApi->deploy();
  }
  gApi->initialize(&gTraits);
  gInitialized = true;
  return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_example_rimeime_engine_RimeBridge_nativeCreateSession(JNIEnv*, jobject) {
  if (!EnsureApi() || !gInitialized) return 0;
  return static_cast<jlong>(gApi->create_session());
}

JNIEXPORT void JNICALL
Java_com_example_rimeime_engine_RimeBridge_nativeDestroySession(JNIEnv*, jobject,
                                                               jlong session) {
  if (gApi != nullptr && session != 0 && RIME_PROVIDED(gApi, destroy_session)) {
    gApi->destroy_session(static_cast<RimeSessionId>(session));
  }
}

JNIEXPORT void JNICALL
Java_com_example_rimeime_engine_RimeBridge_nativeProcessKey(
    JNIEnv*, jobject, jlong session, jint keycode, jint mask) {
  if (gApi == nullptr || session == 0) return;
  gApi->process_key(static_cast<RimeSessionId>(session), keycode, mask);
}

JNIEXPORT jstring JNICALL
Java_com_example_rimeime_engine_RimeBridge_nativeTakeCommit(JNIEnv* env, jobject,
                                                           jlong session) {
  if (gApi == nullptr || session == 0 || !RIME_PROVIDED(gApi, get_commit)) {
    return nullptr;
  }
  RimeCommit commit{};
  RIME_STRUCT_INIT(RimeCommit, commit);
  if (!gApi->get_commit(static_cast<RimeSessionId>(session), &commit)) {
    return nullptr;
  }
  jstring result = nullptr;
  if (commit.text != nullptr && commit.text[0] != '\0') {
    result = env->NewStringUTF(commit.text);
  }
  if (RIME_PROVIDED(gApi, free_commit)) {
    gApi->free_commit(&commit);
  }
  return result;
}

JNIEXPORT jstring JNICALL
Java_com_example_rimeime_engine_RimeBridge_nativeGetComposing(JNIEnv* env, jobject,
                                                             jlong session) {
  if (gApi == nullptr || session == 0) return env->NewStringUTF("");
  // 原始拼音串更适合放进 composing 区域（preedit 带分隔符和光标标记）。
  if (RIME_PROVIDED(gApi, get_input)) {
    const char* input = gApi->get_input(static_cast<RimeSessionId>(session));
    if (input != nullptr) return env->NewStringUTF(input);
  }
  if (!RIME_PROVIDED(gApi, get_context)) return env->NewStringUTF("");
  RimeContext ctx{};
  RIME_STRUCT_INIT(RimeContext, ctx);
  jstring result = env->NewStringUTF("");
  if (gApi->get_context(static_cast<RimeSessionId>(session), &ctx)) {
    result = ToJavaString(env, ctx.composition.preedit);
    if (RIME_PROVIDED(gApi, free_context)) gApi->free_context(&ctx);
  }
  return result;
}

JNIEXPORT jobjectArray JNICALL
Java_com_example_rimeime_engine_RimeBridge_nativeGetCandidates(JNIEnv* env, jobject,
                                                              jlong session) {
  jclass stringClass = env->FindClass("java/lang/String");
  if (gApi == nullptr || session == 0 || !RIME_PROVIDED(gApi, get_context)) {
    return env->NewObjectArray(0, stringClass, env->NewStringUTF(""));
  }
  RimeContext ctx{};
  RIME_STRUCT_INIT(RimeContext, ctx);
  std::vector<std::string> words;
  if (gApi->get_context(static_cast<RimeSessionId>(session), &ctx)) {
    for (int i = 0; i < ctx.menu.num_candidates && ctx.menu.candidates != nullptr; ++i) {
      const char* text = ctx.menu.candidates[i].text;
      words.emplace_back(text != nullptr ? text : "");
    }
    if (RIME_PROVIDED(gApi, free_context)) gApi->free_context(&ctx);
  }
  jobjectArray result = env->NewObjectArray(static_cast<jsize>(words.size()),
                                            stringClass, env->NewStringUTF(""));
  for (jsize i = 0; i < static_cast<jsize>(words.size()); ++i) {
    env->SetObjectArrayElement(result, i, env->NewStringUTF(words[i].c_str()));
  }
  return result;
}

JNIEXPORT void JNICALL
Java_com_example_rimeime_engine_RimeBridge_nativeSelectCandidate(
    JNIEnv*, jobject, jlong session, jint index) {
  if (gApi == nullptr || session == 0) return;
  const auto id = static_cast<RimeSessionId>(session);
  // 候选栏传的是「当前页内下标」，必须优先用 on_current_page 版本。
  if (RIME_PROVIDED(gApi, select_candidate_on_current_page)) {
    gApi->select_candidate_on_current_page(id, static_cast<size_t>(index));
  } else if (RIME_PROVIDED(gApi, select_candidate)) {
    gApi->select_candidate(id, static_cast<size_t>(index));
  }
}

JNIEXPORT void JNICALL
Java_com_example_rimeime_engine_RimeBridge_nativeCommitComposition(
    JNIEnv*, jobject, jlong session) {
  if (gApi != nullptr && session != 0 && RIME_PROVIDED(gApi, commit_composition)) {
    gApi->commit_composition(static_cast<RimeSessionId>(session));
  }
}

JNIEXPORT void JNICALL
Java_com_example_rimeime_engine_RimeBridge_nativeClearComposition(
    JNIEnv*, jobject, jlong session) {
  if (gApi != nullptr && session != 0 && RIME_PROVIDED(gApi, clear_composition)) {
    gApi->clear_composition(static_cast<RimeSessionId>(session));
  }
}

JNIEXPORT jstring JNICALL
Java_com_example_rimeime_engine_RimeBridge_nativeVersion(JNIEnv* env, jobject) {
  if (EnsureApi() && RIME_PROVIDED(gApi, get_version)) {
    const char* version = gApi->get_version();
    if (version != nullptr) return env->NewStringUTF(version);
  }
  return env->NewStringUTF("unavailable");
}

}  // extern "C"
