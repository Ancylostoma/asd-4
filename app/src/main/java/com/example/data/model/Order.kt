package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class Order(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerName: String,
    val customerPhone: String,
    val status: String, // Received, In Progress, Ready for Pickup, Delivered
    val orderDate: Long = System.currentTimeMillis(),
    val deliveredDate: Long? = null,
    val totalAmount: Double,
    val items: List<SelectedItem>
)
