package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.SelectedItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class Converters {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, SelectedItem::class.java)
    private val adapter = moshi.adapter<List<SelectedItem>>(listType)

    @TypeConverter
    fun stringToSelectedItems(value: String?): List<SelectedItem>? {
        return value?.let { adapter.fromJson(it) }
    }

    @TypeConverter
    fun selectedItemsToString(list: List<SelectedItem>?): String? {
        return list?.let { adapter.toJson(it) }
    }
}
