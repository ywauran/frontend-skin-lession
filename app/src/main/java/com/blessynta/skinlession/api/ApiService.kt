package com.blessynta.skinlession.api

import com.blessynta.skinlession.response.PredictResponse
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.*

interface ApiService {
    @Multipart
    @POST("predict")
    fun predict(@Part image: MultipartBody.Part): Call<PredictResponse>
}