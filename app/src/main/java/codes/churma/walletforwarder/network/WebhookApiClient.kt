package codes.churma.walletforwarder.network

import codes.churma.walletforwarder.model.NotificationPayload
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object WebhookApiClient {

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun sendNotification(
        payload: NotificationPayload,
        targetUrl: String,
        bearerToken: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        if (targetUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Webhook URL is empty"))
        }

        try {
            val jsonBody = gson.toJson(payload)
            val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)

            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .post(requestBody)

            if (!bearerToken.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer ${bearerToken.trim()}")
            }

            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Result.success(responseBody.ifBlank { "OK (${response.code})" })
                } else {
                    Result.failure(IOException("Server error ${response.code}: $responseBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
