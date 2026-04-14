package com.hrishipvt.scantopdf.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class QuoteResponse(
    val quote: String,
    val author: String
)

interface QuoteApiService {
    @GET("quotes/random")
    suspend fun getRandomQuote(): QuoteResponse

    companion object {
        private const val BASE_URL = "https://dummyjson.com/"

        fun create(): QuoteApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(QuoteApiService::class.java)
        }
    }
}
