package com.wegood.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiError(val status: Int, message: String) : Exception(message)

/** REST API（SSE 见 Repo 内独立 client） */
object Api {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    val sseClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // 长连接不设读超时
        .retryOnConnectionFailure(true)
        .build()

    private val restClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun request(method: String, path: String, bodyJson: String? = null): Request {
        val b = Request.Builder().url(Prefs.serverUrl + path)
            .header("x-device-id", Prefs.deviceId)
            .header("x-device-secret", Prefs.secret)
        when (method) {
            "POST" -> b.post((bodyJson ?: "{}").toRequestBody(JSON_TYPE))
            "PUT" -> b.put((bodyJson ?: "{}").toRequestBody(JSON_TYPE))
            "DELETE" -> b.delete()
        }
        return b.build()
    }

    private suspend fun call(method: String, path: String, bodyJson: String? = null): String =
        withContext(Dispatchers.IO) {
            restClient.newCall(request(method, path, bodyJson)).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching { json.decodeFromString<ErrResp>(text).error }.getOrNull() ?: "请求失败(${resp.code})"
                    throw ApiError(resp.code, msg)
                }
                text
            }
        }

    @kotlinx.serialization.Serializable
    private data class ErrResp(val error: String? = null)

    private inline fun <reified T> decode(text: String): T = json.decodeFromString(text)

    suspend fun register(name: String): RegisterResponse = decode(call("POST", "/api/register", json.encodeToString(NameReq(name))))
    suspend fun me(): MeResponse = decode(call("GET", "/api/me"))
    suspend fun updateName(name: String): OkResponse = decode(call("POST", "/api/me", json.encodeToString(NameReq(name))))
    suspend fun pair(code: String): OkResponse = decode(call("POST", "/api/pair", json.encodeToString(PairReq(code))))
    suspend fun unpair(): OkResponse = decode(call("POST", "/api/unpair"))
    suspend fun sendHeart(kind: String): OkResponse = decode(call("POST", "/api/hearts", json.encodeToString(HeartReq(kind))))
    suspend fun addAnniversary(input: AnniversaryInput): OkResponse = decode(call("POST", "/api/anniversaries", json.encodeToString(input)))
    suspend fun updateAnniversary(id: String, input: AnniversaryInput): OkResponse = decode(call("PUT", "/api/anniversaries/$id", json.encodeToString(input)))
    suspend fun deleteAnniversary(id: String): OkResponse = decode(call("DELETE", "/api/anniversaries/$id"))
    suspend fun tttMove(index: Int): TttResponse = decode(call("POST", "/api/games/ttt/move", json.encodeToString(MoveReq(index))))
    suspend fun tttReset(): TttResponse = decode(call("POST", "/api/games/ttt/reset"))
    suspend fun dailyAnswer(text: String): DailyResponse = decode(call("POST", "/api/games/daily/answer", json.encodeToString(AnswerReq(text))))

    @kotlinx.serialization.Serializable private data class NameReq(val name: String)
    @kotlinx.serialization.Serializable private data class PairReq(val code: String)
    @kotlinx.serialization.Serializable private data class HeartReq(val kind: String)
    @kotlinx.serialization.Serializable private data class MoveReq(val index: Int)
    @kotlinx.serialization.Serializable private data class AnswerReq(val text: String)
}
