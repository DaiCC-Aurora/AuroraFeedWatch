package com.aurora.podcast.data.network

import com.google.gson.annotations.SerializedName

/**
 * 与云端 /api/feed 返回的 JSON 结构对应（snake_case）。
 */
data class FeedResponse(
    @SerializedName("channel") val channel: ChannelDto?,
    @SerializedName("items") val items: List<FeedItemDto>?
)

data class ChannelDto(
    @SerializedName("title") val title: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("language") val language: String?,
    @SerializedName("update_time") val updateTime: String?
)

data class FeedItemDto(
    @SerializedName("guid") val guid: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("pub_date") val pubDate: String?,
    @SerializedName("audio_url") val audioUrl: String?,
    @SerializedName("subtitle_url") val subtitleUrl: String?,
    @SerializedName("duration_seconds") val durationSeconds: Int?,
    @SerializedName("transcript") val transcript: String?
)