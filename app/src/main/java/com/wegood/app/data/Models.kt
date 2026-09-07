package com.wegood.app.data

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val id: String,
    val name: String,
    val code: String,
    val online: Boolean = false,
    val createdAt: Long = 0,
)

@Serializable
data class CoupleInfo(val id: String, val createdAt: Long)

@Serializable
data class Anniversary(
    val id: String,
    val name: String,
    val date: String, // yyyy-MM-dd
    val repeatYearly: Boolean = false,
    val remindDaysBefore: Int? = null,
    val remindTime: String? = null,
    val createdBy: String? = null,
    val createdAt: Long = 0,
)

@Serializable
data class FeedEvent(
    val id: String,
    val ts: Long,
    val kind: String, // heart | sys
    val heartKind: String? = null,
    val fromName: String? = null,
    val text: String? = null,
)

@Serializable
data class TttGame(
    val board: List<String?> = emptyList(),
    val turn: String = "X",
    val winner: String? = null,
    val winLine: List<Int>? = null,
    val players: Map<String, String> = emptyMap(),
    val updatedAt: Long = 0,
)

@Serializable
data class DailyInfo(
    val date: String,
    val question: String,
    val myAnswer: String? = null,
    val partnerAnswer: String? = null,
    val partnerAnswered: Boolean = false,
)

@Serializable
data class MeResponse(
    val device: Device,
    val partner: Device? = null,
    val couple: CoupleInfo? = null,
    val anniversaries: List<Anniversary> = emptyList(),
    val events: List<FeedEvent> = emptyList(),
    val ttt: TttGame? = null,
    val daily: DailyInfo? = null,
)

@Serializable
data class RegisterResponse(val deviceId: String, val secret: String, val code: String, val name: String)

@Serializable
data class OkResponse(val ok: Boolean = true)

@Serializable
data class TttResponse(val ttt: TttGame)

@Serializable
data class DailyResponse(val daily: DailyInfo)

/** SSE 推送事件（字段按需携带） */
@Serializable
data class SseEvent(
    val type: String,
    val kind: String? = null,
    val fromName: String? = null,
    val ts: Long = 0,
    val online: Boolean? = null,
    val partnerName: String? = null,
    val name: String? = null,
    val text: String? = null,
    val days: Int? = null,
    val ttt: TttGame? = null,
)

/** 提交给服务端的纪念日表单 */
@Serializable
data class AnniversaryInput(
    val name: String,
    val date: String,
    val repeatYearly: Boolean,
    val remindDaysBefore: Int?,
    val remindTime: String?,
)

val HEART_EMOJI = mapOf("heart" to "❤️", "kiss" to "😘", "hug" to "🤗", "rose" to "🌹", "miss" to "💭")
