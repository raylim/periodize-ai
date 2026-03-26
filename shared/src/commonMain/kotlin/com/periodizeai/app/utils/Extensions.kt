package com.periodizeai.app.utils

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// ── Double extensions ─────────────────────────────────────────────────────

fun Double.roundedToNearest(value: Double): Double =
    (this / value).let { kotlin.math.round(it) * value }

val Double.formattedWeight: String
    get() = if (this == kotlin.math.floor(this) && !isInfinite()) {
        toLong().toString()
    } else {
        val tenths = kotlin.math.round(this * 10.0).toLong()
        val intPart = tenths / 10
        val decPart = tenths % 10
        if (decPart == 0L) "$intPart" else "$intPart.$decPart"
    }

// ── List extensions ───────────────────────────────────────────────────────

/** Safe subscript — returns null instead of throwing on out-of-bounds. */
fun <T> List<T>.getOrNullSafe(index: Int): T? = if (index in indices) this[index] else null

fun <T> List<T>.chunked(size: Int): List<List<T>> =
    (indices step size).map { subList(it, minOf(it + size, this.size)) }

// ── Long (epoch ms) date helpers ──────────────────────────────────────────

fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)

fun Long.toLocalDate(tz: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(tz).date

fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

// ── Duration formatting ───────────────────────────────────────────────────

fun Long.formattedDuration(): String {
    val totalSeconds = this / 1000
    val hours   = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return buildString {
        if (hours > 0) {
            append(hours); append(':')
            append(minutes.toString().padStart(2, '0')); append(':')
        } else {
            append(minutes); append(':')
        }
        append(seconds.toString().padStart(2, '0'))
    }
}

// ── Plate rounding ────────────────────────────────────────────────────────

fun Double.roundToPlates(barWeight: Double, availablePlates: List<Double>): Double {
    val oneSide = (this - barWeight) / 2.0
    if (oneSide <= 0) return barWeight
    val sorted = availablePlates.sortedDescending()
    var remaining = oneSide
    var achieved = 0.0
    for (plate in sorted) {
        while (remaining >= plate) { achieved += plate; remaining -= plate }
    }
    val smallest = sorted.lastOrNull() ?: 2.5
    val roundedRemainder = kotlin.math.round(remaining / smallest) * smallest
    return barWeight + (achieved + roundedRemainder) * 2.0
}

// ── Delimited string helpers for SQLDelight TEXT columns ─────────────────

fun List<String>.toDelimitedString(): String = joinToString(",")
fun String.toStringList(): List<String> = if (isBlank()) emptyList() else split(",").map { it.trim() }

fun List<Double>.toDelimitedString(): String = joinToString(",") { it.toString() }
fun String.toDoubleList(): List<Double> = if (isBlank()) emptyList()
    else split(",").mapNotNull { it.trim().toDoubleOrNull() }

fun List<Int>.toDelimitedString(): String = joinToString(",") { it.toString() }
fun String.toIntList(): List<Int> = if (isBlank()) emptyList()
    else split(",").mapNotNull { it.trim().toIntOrNull() }
