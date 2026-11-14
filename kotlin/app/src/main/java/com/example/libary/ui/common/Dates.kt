package com.example.libary.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatIsoDate(value: String?): String {
    val day = value?.take(10).orEmpty()
    val parts = day.split("-")
    if (parts.size != 3) return value.orEmpty()
    return "${parts[2]}.${parts[1]}.${parts[0]}"
}

fun isPastDue(value: String?): Boolean {
    val day = value?.take(10) ?: return false
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    return day < today
}
