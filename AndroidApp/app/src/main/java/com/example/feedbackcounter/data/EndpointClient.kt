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
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class)
    fun postCounts(
        endpoint: String,
        good: Int,
        normal: Int,
        bad: Int,
        passL2R: Int,
        passR2L: Int
    ): String {
        if (endpoint.isBlank()) return "Endpoint not set"

        val json = JSONObject()
            .put("good", good)
            .put("normal", normal)
            .put("bad", bad)
            .put("passL2R", passL2R)
            .put("passR2L", passR2L)
            .put("ts", System.currentTimeMillis())

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(endpoint)
            .post(body)
            .build()

        client.newCall(req).execute().use { resp ->
            val code = resp.code
            val respBody = resp.body?.string()?.take(200) ?: ""
            if (!resp.isSuccessful) throw IOException("HTTP $code $respBody")
            return "OK (HTTP $code)"
        }
    }
}
