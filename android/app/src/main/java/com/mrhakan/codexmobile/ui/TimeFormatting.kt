package com.mrhakan.codexmobile.ui

import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/** "12m ago" style stamps, falling back to a date once it stops being useful. */
fun relativeTime(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val delta = nowMillis - epochMillis
    if (delta < 0) return "just now"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
    }
}
