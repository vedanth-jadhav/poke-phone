package co.interaction.pokephone.settings

import android.content.Context

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("poke_phone", Context.MODE_PRIVATE)

    var backendUrl: String
        get() = prefs.getString(KEY_BACKEND_URL, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_BACKEND_URL, value.trim()).apply()
        }

    var uploadToken: String
        get() = prefs.getString(KEY_UPLOAD_TOKEN, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_UPLOAD_TOKEN, value.trim()).apply()
        }

    companion object {
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_UPLOAD_TOKEN = "upload_token"
    }
}
