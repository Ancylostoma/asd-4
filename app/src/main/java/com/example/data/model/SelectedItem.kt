package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SelectedItem(
    val id: Int,
    val name: String,
    val price: Double,
    val quantity: Int
)

