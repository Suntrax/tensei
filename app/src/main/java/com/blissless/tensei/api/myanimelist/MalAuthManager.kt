package com.blissless.tensei.api.myanimelist

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

enum class LoginProvider {
    NONE, ANILIST, MAL
}

@Serializable
data class MalUserInfo(
    val id: Int = 0,
    val name: String = "",
    val picture: String? = null
)

@Serializable
data class MalAnimeListEntry(
    val node: MalAnimeNode = MalAnimeNode(),
    val list_status: MalListStatus? = null
)

@Serializable
data class MalAnimeNode(
    val id: Int = 0,
    val title: String = "",
    val main_picture: MalPicture? = null,
    val num_episodes: Int = 0,
    val alternative_titles: MalAlternativeTitles? = null
)

@Serializable
data class MalAlternativeTitles(
    val en: String? = null,
    val ja: String? = null
)

@Serializable
data class MalPicture(
    val medium: String? = null,
    val large: String? = null
)

@Serializable
data class MalListStatus(
    val status: String? = null,
    val score: Int = 0,
    val num_episodes_watched: Int = 0,
    val updated_at: String? = null
)

class MalAuthManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "mal_auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_TOKEN_TYPE = "token_type"
        private const val KEY_EXPIRES_IN = "expires_in"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_INFO = "user_info"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PICTURE = "user_picture"
        private const val KEY_CODE_VERIFIER = "code_verifier"
        private const val KEY_CODE_VERIFIER_TIME = "code_verifier_time"
        private const val CODE_VERIFIER_EXPIRY_MS = 10 * 60 * 1000L // 10 minutes

    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _accessToken = MutableStateFlow(prefs.getString(KEY_ACCESS_TOKEN, null))

    private val _userInfo = MutableStateFlow<MalUserInfo?>(null)
    val userInfo: StateFlow<MalUserInfo?> = _userInfo.asStateFlow()
    
    val isLoggedIn: Boolean get() = _accessToken.value != null
    
    init {
        loadUserInfo()
    }
    
    fun saveToken(token: String, tokenType: String = "Bearer", expiresIn: Int = 0, refreshToken: String? = null) {
        prefs.edit {
            putString(KEY_ACCESS_TOKEN, token)
            putString(KEY_TOKEN_TYPE, tokenType)
            putInt(KEY_EXPIRES_IN, expiresIn)
            refreshToken?.let { putString(KEY_REFRESH_TOKEN, it) }
        }
        _accessToken.value = token
    }
    
    fun clearToken() {
        prefs.edit {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_TOKEN_TYPE)
            remove(KEY_EXPIRES_IN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_USER_INFO)
            remove(KEY_USER_NAME)
            remove(KEY_USER_PICTURE)
        }
        _accessToken.value = null
        _userInfo.value = null
    }
    
    fun saveUserInfo(name: String, picture: String?) {
        prefs.edit {
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_PICTURE, picture)
        }
        _userInfo.value = MalUserInfo(name = name, picture = picture)
    }
    
    private fun loadUserInfo() {
        val name = prefs.getString(KEY_USER_NAME, null)
        val picture = prefs.getString(KEY_USER_PICTURE, null)
        if (name != null) {
            _userInfo.value = MalUserInfo(name = name, picture = picture)
        }
    }
    
    fun getAuthHeader(): String? {
        val token = _accessToken.value ?: return null
        val tokenType = prefs.getString(KEY_TOKEN_TYPE, "Bearer") ?: "Bearer"
        return "$tokenType $token"
    }
    
    fun saveCodeVerifier(verifier: String) {
        prefs.edit {
            putString(KEY_CODE_VERIFIER, verifier)
            putLong(KEY_CODE_VERIFIER_TIME, System.currentTimeMillis())
        }
    }
    
    fun getCodeVerifier(): String? {
        val verifier = prefs.getString(KEY_CODE_VERIFIER, null) ?: return null
        val timestamp = prefs.getLong(KEY_CODE_VERIFIER_TIME, 0)
        
        // Check if expired (10-minute window)
        if (System.currentTimeMillis() - timestamp > CODE_VERIFIER_EXPIRY_MS) {
            clearCodeVerifier()
            return null
        }
        
        return verifier
    }
    
    fun clearCodeVerifier() {
        prefs.edit {
            remove(KEY_CODE_VERIFIER)
            remove(KEY_CODE_VERIFIER_TIME)
        }
    }
}


