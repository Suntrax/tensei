#include <jni.h>
#include <string>
#include <memory>
#include <android/log.h>

#define DISCORDPP_IMPLEMENTATION
#include <discordpp.h>

#define TAG "DiscordNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::shared_ptr<discordpp::Client> g_client;
static JavaVM *g_jvm = nullptr;

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    g_jvm = vm;
    LOGI("JNI_OnLoad: Discord JNI initialized");
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
Java_com_blissless_tensei_discord_DiscordNative_nativeInitialize(
        JNIEnv *env, jobject /* this */, jstring appId) {
    const char *app_id_str = env->GetStringUTFChars(appId, nullptr);
    LOGI("nativeInitialize: appId=%s", app_id_str);

    uint64_t app_id = std::stoull(std::string(app_id_str));
    g_client = std::make_shared<discordpp::Client>();

    g_client->AddLogCallback([](const std::string& message, discordpp::LoggingSeverity severity) {
        LOGI("SDK: [%d] %s", static_cast<int>(severity), message.c_str());
    }, discordpp::LoggingSeverity::Info);

    g_client->SetApplicationId(app_id);

    LOGI("nativeInitialize: client created, appId=%s", app_id_str);
    env->ReleaseStringUTFChars(appId, app_id_str);
}

JNIEXPORT void JNICALL
Java_com_blissless_tensei_discord_DiscordNative_nativeSetPresence(
        JNIEnv *env, jobject /* this */,
        jstring details, jstring state,
        jint type, jlong startTimestamp, jlong endTimestamp,
        jstring largeImage, jstring largeText,
        jstring smallImage, jstring smallText,
        jstring largeUrl) {

    if (!g_client) {
        LOGE("nativeSetPresence: client not initialized");
        return;
    }

    const char *c_details = details ? env->GetStringUTFChars(details, nullptr) : nullptr;
    const char *c_state = state ? env->GetStringUTFChars(state, nullptr) : nullptr;
    const char *c_largeImage = largeImage ? env->GetStringUTFChars(largeImage, nullptr) : nullptr;
    const char *c_largeText = largeText ? env->GetStringUTFChars(largeText, nullptr) : nullptr;
    const char *c_smallImage = smallImage ? env->GetStringUTFChars(smallImage, nullptr) : nullptr;
    const char *c_smallText = smallText ? env->GetStringUTFChars(smallText, nullptr) : nullptr;
    const char *c_largeUrl = largeUrl ? env->GetStringUTFChars(largeUrl, nullptr) : nullptr;

    LOGI("nativeSetPresence: details=%s state=%s type=%d startTs=%lld endTs=%lld",
         c_details ?: "", c_state ?: "", type, (long long)startTimestamp, (long long)endTimestamp);

    discordpp::Activity activity;

    switch (type) {
        case 0: activity.SetType(discordpp::ActivityTypes::Playing); break;
        case 1: activity.SetType(discordpp::ActivityTypes::Streaming); break;
        case 2: activity.SetType(discordpp::ActivityTypes::Listening); break;
        case 3: activity.SetType(discordpp::ActivityTypes::Watching); break;
        default: activity.SetType(discordpp::ActivityTypes::Playing); break;
    }

    if (c_details) activity.SetDetails(c_details);
    if (c_state) activity.SetState(c_state);

    if (startTimestamp > 0 || endTimestamp > 0) {
        discordpp::ActivityTimestamps timestamps;
        if (startTimestamp > 0) timestamps.SetStart(static_cast<uint64_t>(startTimestamp));
        if (endTimestamp > 0) timestamps.SetEnd(static_cast<uint64_t>(endTimestamp));
        activity.SetTimestamps(timestamps);
    }

    discordpp::ActivityAssets assets;
    if (c_largeImage) assets.SetLargeImage(c_largeImage);
    if (c_largeText) assets.SetLargeText(c_largeText);
    if (c_smallImage) assets.SetSmallImage(c_smallImage);
    if (c_smallText) assets.SetSmallText(c_smallText);
    if (c_largeUrl) assets.SetLargeUrl(c_largeUrl);
    activity.SetAssets(assets);

    g_client->UpdateRichPresence(activity, [](discordpp::ClientResult result) {
        if (result.Successful()) {
            LOGI("UpdateRichPresence: success");
        } else {
            LOGE("UpdateRichPresence: failed - type=%d error='%s' code=%d retryable=%d",
                 static_cast<int>(result.Type()), result.Error().c_str(), result.ErrorCode(), result.Retryable());
        }
    });

    if (c_details) env->ReleaseStringUTFChars(details, c_details);
    if (c_state) env->ReleaseStringUTFChars(state, c_state);
    if (c_largeImage) env->ReleaseStringUTFChars(largeImage, c_largeImage);
    if (c_largeText) env->ReleaseStringUTFChars(largeText, c_largeText);
    if (c_smallImage) env->ReleaseStringUTFChars(smallImage, c_smallImage);
    if (c_smallText) env->ReleaseStringUTFChars(smallText, c_smallText);
    if (c_largeUrl) env->ReleaseStringUTFChars(largeUrl, c_largeUrl);
}

JNIEXPORT void JNICALL
Java_com_blissless_tensei_discord_DiscordNative_nativeClearPresence(
        JNIEnv *env, jobject /* this */) {
    if (!g_client) {
        LOGE("nativeClearPresence: client not initialized");
        return;
    }
    LOGI("nativeClearPresence");
    discordpp::Activity activity;
    g_client->UpdateRichPresence(activity, [](discordpp::ClientResult result) {
        if (result.Successful()) {
            LOGI("ClearPresence: success");
        } else {
            LOGE("ClearPresence: failed - %s", result.Error().c_str());
        }
    });
}

JNIEXPORT void JNICALL
Java_com_blissless_tensei_discord_DiscordNative_nativeRunCallbacks(
        JNIEnv * /* env */, jobject /* this */) {
    discordpp::RunCallbacks();
}

JNIEXPORT void JNICALL
Java_com_blissless_tensei_discord_DiscordNative_nativeDestroy(
        JNIEnv *env, jobject /* this */) {
    LOGI("nativeDestroy");
    g_client = nullptr;
}

}
