package com.blessynta.skinlession.response

import com.google.gson.annotations.SerializedName

data class PredictResponse(

    @field:SerializedName("confidence_cnn")
    val confidenceCnn: String? = null,

    @field:SerializedName("prediction_cnn")
    val predictionCnn: String? = null
)