package com.example.tagone.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

private const val BASE_URL = "https://danbooru.donmai.us"

private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

private val retrofit = Retrofit.Builder()
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .baseUrl(BASE_URL)
    .build()

interface DanbooruApiService {
    @GET("/posts.json")
    suspend fun getPosts(
        @Query("tags") tags: String,
        @Query("limit") limit: Int,
        @Query("page") page: Int
    ): List<DanbooruPostNet>

    @GET("/tags.json")
    suspend fun getTags(
        @Query("limit") limit: Int,
        @Query("page") page: Int,
        @Query("search[name_matches]") tag: String,
        @Query("search[order]") order: String,
        @Query("search[hide_empty]") excludeEmpty: Boolean
    ): List<DanbooruTagNet>
}

object DanbooruApi {
    val retrofitService: DanbooruApiService by lazy {
        retrofit.create(DanbooruApiService::class.java)
    }
}
