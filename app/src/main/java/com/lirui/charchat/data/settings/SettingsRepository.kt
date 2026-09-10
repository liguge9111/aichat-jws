package com.lirui.charchat.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lirui.charchat.data.remote.ApiKey
import com.lirui.charchat.domain.model.PlayerProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * 用 AndroidX Security 的 EncryptedSharedPreferences 持久化 BYOK Key 与开关。
 * Key 仅驻留本机加密存储；无后端即无服务端泄露面（root 设备可被提取，个人把玩可接受）。
 */
class SettingsRepository(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "charchat_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _config = MutableStateFlow(load())
    val config: StateFlow<ApiConfig> = _config.asStateFlow()

    /** 全局默认玩家背景：新建/导入角色卡时用作默认值，群聊与单聊显示名也优先取它。 */
    private val _player = MutableStateFlow(loadPlayer())
    val player: StateFlow<PlayerProfile> = _player.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    private fun load(): ApiConfig = ApiConfig(
        chatBaseUrl = prefs.getString(KEY_CHAT_URL, ApiConfig().chatBaseUrl) ?: ApiConfig().chatBaseUrl,
        chatApiKey = prefs.getString(KEY_CHAT_KEY, "") ?: "",
        chatModel = prefs.getString(KEY_CHAT_MODEL, ApiConfig().chatModel) ?: ApiConfig().chatModel,
        imageBaseUrl = prefs.getString(KEY_IMG_URL, ApiConfig().imageBaseUrl) ?: ApiConfig().imageBaseUrl,
        imageApiKey = prefs.getString(KEY_IMG_KEY, "") ?: "",
        imageModel = prefs.getString(KEY_IMG_MODEL, ApiConfig().imageModel) ?: ApiConfig().imageModel,
        ttsBaseUrl = prefs.getString(KEY_TTS_URL, "") ?: "",
        ttsApiKey = prefs.getString(KEY_TTS_KEY, "") ?: "",
        ttsModel = prefs.getString(KEY_TTS_MODEL, "") ?: "",
        ttsVoice = prefs.getString(KEY_TTS_VOICE, "") ?: "",
        asrBaseUrl = prefs.getString(KEY_ASR_URL, "") ?: "",
        asrApiKey = prefs.getString(KEY_ASR_KEY, "") ?: "",
        asrModel = prefs.getString(KEY_ASR_MODEL, "") ?: "",
        nsfwFilterEnabled = prefs.getBoolean(KEY_NSFW, false),
        ageVerified = prefs.getBoolean(KEY_AGE, false)
    )

    fun save(cfg: ApiConfig) {
        // 入库前清洗：URL 去空白，Key 去掉误粘的 Bearer 前缀/引号/换行
        val clean = cfg.copy(
            chatBaseUrl = cfg.chatBaseUrl.trim(),
            chatApiKey = ApiKey.normalize(cfg.chatApiKey),
            chatModel = cfg.chatModel.trim(),
            imageBaseUrl = cfg.imageBaseUrl.trim(),
            imageApiKey = ApiKey.normalize(cfg.imageApiKey),
            imageModel = cfg.imageModel.trim(),
            ttsBaseUrl = cfg.ttsBaseUrl.trim(),
            ttsApiKey = ApiKey.normalize(cfg.ttsApiKey),
            ttsModel = cfg.ttsModel.trim(),
            ttsVoice = cfg.ttsVoice.trim(),
            asrBaseUrl = cfg.asrBaseUrl.trim(),
            asrApiKey = ApiKey.normalize(cfg.asrApiKey),
            asrModel = cfg.asrModel.trim()
        )
        prefs.edit().apply {
            putString(KEY_CHAT_URL, clean.chatBaseUrl)
            putString(KEY_CHAT_KEY, clean.chatApiKey)
            putString(KEY_CHAT_MODEL, clean.chatModel)
            putString(KEY_IMG_URL, clean.imageBaseUrl)
            putString(KEY_IMG_KEY, clean.imageApiKey)
            putString(KEY_IMG_MODEL, clean.imageModel)
            putString(KEY_TTS_URL, clean.ttsBaseUrl)
            putString(KEY_TTS_KEY, clean.ttsApiKey)
            putString(KEY_TTS_MODEL, clean.ttsModel)
            putString(KEY_TTS_VOICE, clean.ttsVoice)
            putString(KEY_ASR_URL, clean.asrBaseUrl)
            putString(KEY_ASR_KEY, clean.asrApiKey)
            putString(KEY_ASR_MODEL, clean.asrModel)
            putBoolean(KEY_NSFW, clean.nsfwFilterEnabled)
            putBoolean(KEY_AGE, clean.ageVerified)
        }.apply()
        _config.value = clean
    }

    fun setAgeVerified() {
        save(_config.value.copy(ageVerified = true))
    }

    /** 玩家显示名：全局设置优先，回落卡内背景。 */
    fun playerNameOr(fallback: String): String =
        _player.value.name.takeIf { it.isNotBlank() && it != PlayerProfile().name } ?: fallback

    private fun loadPlayer(): PlayerProfile = runCatching {
        val raw = prefs.getString(KEY_PLAYER, null) ?: return PlayerProfile()
        json.decodeFromString(PlayerProfile.serializer(), raw)
    }.getOrDefault(PlayerProfile())

    fun savePlayer(p: PlayerProfile) {
        prefs.edit().putString(KEY_PLAYER, json.encodeToString(PlayerProfile.serializer(), p)).apply()
        _player.value = p
    }

    companion object {
        private const val KEY_CHAT_URL = "chat_base_url"
        private const val KEY_CHAT_KEY = "chat_api_key"
        private const val KEY_CHAT_MODEL = "chat_model"
        private const val KEY_IMG_URL = "image_base_url"
        private const val KEY_IMG_KEY = "image_api_key"
        private const val KEY_IMG_MODEL = "image_model"
        private const val KEY_TTS_URL = "tts_base_url"
        private const val KEY_TTS_KEY = "tts_api_key"
        private const val KEY_TTS_MODEL = "tts_model"
        private const val KEY_TTS_VOICE = "tts_voice"
        private const val KEY_ASR_URL = "asr_base_url"
        private const val KEY_ASR_KEY = "asr_api_key"
        private const val KEY_ASR_MODEL = "asr_model"
        private const val KEY_NSFW = "nsfw_filter"
        private const val KEY_AGE = "age_verified"
        private const val KEY_PLAYER = "default_player_json"
    }
}
