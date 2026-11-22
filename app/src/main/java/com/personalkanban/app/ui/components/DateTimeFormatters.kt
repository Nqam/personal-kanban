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

    private val headerFmt = ThreadLocal.withInitial { SimpleDateFormat("EEEE, d MMMM", ruLocale) }
    private val shortDateFmt = ThreadLocal.withInitial { SimpleDateFormat("d MMM", ruLocale) }
    private val fullDateFmt = ThreadLocal.withInitial { SimpleDateFormat("d MMM yyyy", ruLocale) }
    private val timeFmt = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", ruLocale) }
    private val timestampFmt = ThreadLocal.withInitial { SimpleDateFormat("dd.MM.yyyy HH:mm", ruLocale) }

    fun formatHeaderDate(millis: Long = System.currentTimeMillis()): String {
        val raw = headerFmt.get()!!.format(Date(millis))
        return raw.replaceFirstChar { it.uppercase(ruLocale) }
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
                val fmt = if (dueDate.year == today.year) shortDateFmt.get()!! else fullDateFmt.get()!!
                fmt.format(Date(dueAt))
            }
        }

        return if (hasTime) {
            "$datePart, ${timeFmt.get()!!.format(Date(dueAt))}"
        } else {
            datePart
        }
    }

    fun formatTimestamp(millis: Long): String = timestampFmt.get()!!.format(Date(millis))

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
