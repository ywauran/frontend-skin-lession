package com.blessynta.skinlession.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiConfig {
    fun getPredictApiService(): ApiService {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://192.168.1.4:5000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(ApiService::class.java)
    }
}