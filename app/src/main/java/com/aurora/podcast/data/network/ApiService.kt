package com.aurora.podcast.data.network

import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {

    @GET("api/feed")
    suspend fun getFeed(@Query("limit") limit: Int = 50): FeedResponse
}