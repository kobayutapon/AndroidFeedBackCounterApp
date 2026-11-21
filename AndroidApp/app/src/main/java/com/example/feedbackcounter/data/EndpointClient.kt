package com.example.feedbackcounter.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class EndpointClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true) // Apps Script ContentService redirects to script.googleusercontent.com
        .build()

    @Throws(IOException::class)
    fun postCounts(endpoint: String, payload: String, authToken: String): String {
        if (!endpoint.startsWith("https://")) throw IOException("EndpointはHTTPSを指定してください")
        if (authToken.isBlank()) throw IOException("認証トークンを設定してください")
        val requestJson = JSONObject(payload)
        // GAS web apps don't reliably expose arbitrary request headers in doPost.
        // Put token in HTTPS JSON body; never log it or echo it back in GAS responses.
        requestJson.put("authToken", authToken)
        val requestId = requestJson.getString("requestId")
        val req = Request.Builder()
            .url(endpoint)
            .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(req).execute().use { response ->
            val body = response.body?.string()?.take(8192) ?: ""
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} (送信失敗)")
            val reply = try { JSONObject(body) } catch (e: Exception) {
                throw IOException("サーバー応答がJSONではありません (HTTP ${response.code})", e)
            }
            if (reply.optString("status") != "ok" || !reply.optBoolean("saved", false)) {
                throw IOException("サーバー保存失敗: ${reply.optString("message", "Unknown error")}")
            }
            if (reply.optString("requestId") != requestId) {
                throw IOException("応答のrequestIdが一致しません")
            }
            return if (reply.optBoolean("duplicate", false)) "OK (重複確認済)" else "OK (保存完了)"
        }
    }
}
