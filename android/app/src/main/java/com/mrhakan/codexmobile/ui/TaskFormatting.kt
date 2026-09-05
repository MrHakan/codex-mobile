package com.mrhakan.codexmobile.ui

import com.mrhakan.codexmobile.data.TaskStatus
import java.text.DateFormat
import java.util.Date

fun TaskStatus.label(): String = when (this) {
    TaskStatus.DISPATCHED -> "Dispatched"
    TaskStatus.QUEUED -> "Queued"
    TaskStatus.RUNNING -> "Running"
    TaskStatus.SUCCEEDED -> "Succeeded"
    TaskStatus.FAILED -> "Failed"
    TaskStatus.CANCELLED -> "Cancelled"
    TaskStatus.DISPATCH_FAILED -> "Not started"
}

fun formatTimestamp(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMillis))
