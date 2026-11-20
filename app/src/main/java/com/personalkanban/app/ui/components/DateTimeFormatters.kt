package com.personalkanban.app.ui.components

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale

object DateTimeFormatters {
    private val ruLocale = Locale("ru", "RU")

    fun formatHeaderDate(millis: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("EEEE, d MMMM", ruLocale)
        return fmt.format(Date(millis)).replaceFirstChar { it.uppercase(ruLocale) }
    }

    fun formatDueDate(dueAt: Long, hasTime: Boolean): String {
        val zone = ZoneId.systemDefault()
        val dueDate = Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)

        val datePart = when (dueDate) {
            today -> "Сегодня"
            today.plusDays(1) -> "Завтра"
            today.minusDays(1) -> "Вчера"
            else -> {
                val pattern = if (dueDate.year == today.year) "d MMM" else "d MMM yyyy"
                SimpleDateFormat(pattern, ruLocale).format(Date(dueAt))
            }
        }

        return if (hasTime) {
            val timePart = SimpleDateFormat("HH:mm", ruLocale).format(Date(dueAt))
            "$datePart, $timePart"
        } else {
            datePart
        }
    }

    fun formatTimestamp(millis: Long): String {
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", ruLocale)
        return fmt.format(Date(millis))
    }

    fun combineDateAndTime(dateMillis: Long, hour: Int, minute: Int): Long {
        val zone = ZoneId.systemDefault()
        val localDate = Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate()
        return localDate.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
    }

    fun endOfDayMillis(dateMillis: Long): Long {
        val zone = ZoneId.systemDefault()
        val localDate = Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate()
        return localDate.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
    }
}
