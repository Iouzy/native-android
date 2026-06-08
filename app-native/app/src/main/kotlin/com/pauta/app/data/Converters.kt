package com.pauta.app.data

import androidx.room.TypeConverter

/** Room type converters. The only non-primitive column is a habit's weekday
 *  schedule (List<Int>), stored as a compact comma-separated string. // PT:
 *  conversores Room — a lista de dias da semana fica como string separada por
 *  vírgulas. */
class Converters {
    @TypeConverter
    fun intListToString(value: List<Int>?): String =
        value?.joinToString(",") ?: ""

    @TypeConverter
    fun stringToIntList(value: String?): List<Int> =
        if (value.isNullOrBlank()) emptyList()
        else value.split(",").mapNotNull { it.trim().toIntOrNull() }
}
