package co.interaction.pokephone.network

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

data class UploadResult(
    val ok: Boolean,
    val statusCode: Int,
    val body: String
)

class PokePhoneBackend(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.MINUTES)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    suspend fun health(baseUrl: String, uploadToken: String): UploadResult {
        val endpoint = baseUrl.normalizedBaseUrl() + "/v1/health"
        val request = Request.Builder()
            .url(endpoint)
            .applyAuth(uploadToken)
            .get()
            .build()

        return execute(request)
    }

    suspend fun uploadVoiceNote(
        baseUrl: String,
        uploadToken: String,
        audioFile: File,
        durationMs: Long,
        source: String
    ): UploadResult {
        val endpoint = baseUrl.normalizedBaseUrl() + "/v1/voice-note"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "audio",
                audioFile.name,
                audioFile.asRequestBody("audio/mp4".toMediaType())
            )
            .addFormDataPart("duration_ms", durationMs.toString())
            .addFormDataPart("source", source)
            .addFormDataPart("device", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            .addFormDataPart("sdk", Build.VERSION.SDK_INT.toString())
            .build()

        val request = Request.Builder()
            .url(endpoint)
            .applyAuth(uploadToken)
            .post(body)
            .build()

        return execute(request)
    }

    suspend fun sendTest(baseUrl: String, uploadToken: String): UploadResult {
        val endpoint = baseUrl.normalizedBaseUrl() + "/v1/test-message"
        val request = Request.Builder()
            .url(endpoint)
            .applyAuth(uploadToken)
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        return execute(request)
    }

    private suspend fun execute(request: Request): UploadResult = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(request).execute().use { response ->
                UploadResult(
                    ok = response.isSuccessful,
                    statusCode = response.code,
                    body = response.body?.string().orEmpty()
                )
            }
        }.getOrElse { error ->
            UploadResult(ok = false, statusCode = 0, body = error.message.orEmpty())
        }
    }

    private fun Request.Builder.applyAuth(uploadToken: String): Request.Builder {
        if (uploadToken.isNotBlank()) {
            header("Authorization", "Bearer ${uploadToken.trim()}")
        }
        return this
    }

    private fun String.normalizedBaseUrl(): String {
        val trimmed = trim().trimEnd('/')
        require(trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            "Backend URL must start with https:// or http://"
        }
        return trimmed
    }
}
