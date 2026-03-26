package com.periodizeai.app.services

import com.periodizeai.app.models.BandType
import com.periodizeai.app.repositories.CompletedSetData
import com.periodizeai.app.repositories.ExerciseData
import com.periodizeai.app.repositories.WorkoutSessionData
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class ImportResult(
    val sessionsImported: Int,
    val exercisesFound: Int,
    val setsImported: Int,
    val errors: List<String>,
)

object CsvImportService {

    private val skipExercises = setOf(
        "Box Jumps", "DB Jumps", "1H Heavy club inside/outside circles",
        "1H Heavy Club Shield Cast", "Heavy Club Pullover", "Kettlebell standing pullover",
    )

    fun importCsv(
        content: String,
        existingExercises: List<ExerciseData>,
    ): Pair<ImportResult, List<WorkoutSessionData>> {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.size <= 1) {
            return Pair(
                ImportResult(0, 0, 0, listOf("Empty CSV file")),
                emptyList(),
            )
        }

        val errors = mutableListOf<String>()
        val sessionsByDate = mutableMapOf<String, MutableList<Pair<Int, List<String>>>>()

        for ((idx, line) in lines.drop(1).withIndex()) {
            val lineNum = idx + 2
            val fields = parseCSVLine(line)
            if (fields.size < 6) {
                errors.add("Line $lineNum: insufficient fields")
                continue
            }
            val dateStr = fields[0].trim()
            sessionsByDate.getOrPut(dateStr) { mutableListOf() }.add(Pair(lineNum, fields))
        }

        val resultSessions = mutableListOf<WorkoutSessionData>()
        var sessionsImported = 0
        val exerciseNames = mutableSetOf<String>()

        for ((dateStr, rows) in sessionsByDate) {
            val epochMs = parseDate(dateStr)
            if (epochMs == null) {
                errors.add("Could not parse date: $dateStr")
                continue
            }
            val (session, sessionErrors) = buildSession(
                epochMs = epochMs,
                rows = rows,
                existingExercises = existingExercises,
                exerciseNames = exerciseNames,
            )
            errors.addAll(sessionErrors)
            if (session != null) {
                resultSessions.add(session)
                sessionsImported++
            }
        }

        val totalSets = resultSessions.sumOf { it.completedSets.size }

        return Pair(
            ImportResult(
                sessionsImported = sessionsImported,
                exercisesFound = exerciseNames.size,
                setsImported = totalSets,
                errors = errors,
            ),
            resultSessions,
        )
    }

    private fun buildSession(
        epochMs: Long,
        rows: List<Pair<Int, List<String>>>,
        existingExercises: List<ExerciseData>,
        exerciseNames: MutableSet<String>,
    ): Pair<WorkoutSessionData?, List<String>> {
        val errors = mutableListOf<String>()
        val completedSets = mutableListOf<CompletedSetData>()
        var exerciseOrder = 0
        var currentExerciseName: String? = null
        var setNumber = 0

        for ((lineNum, fields) in rows) {
            val exerciseName = fields.getOrNull(2)?.trim() ?: continue
            if (exerciseName == "undefined" || exerciseName.isEmpty()) continue
            if (skipExercises.contains(exerciseName)) continue

            if (exerciseName != currentExerciseName) {
                currentExerciseName = exerciseName
                exerciseOrder++
                setNumber = 0
            }
            setNumber++

            val exercise = ExerciseCatalogService.findExercise(exerciseName, existingExercises)
            if (exercise == null) {
                errors.add("Line $lineNum: unknown exercise '$exerciseName'")
                continue
            }

            exerciseNames.add(exerciseName)

            val weight = fields.getOrNull(3)?.trim()?.toDoubleOrNull() ?: 0.0
            val reps = fields.getOrNull(5)?.trim()?.toIntOrNull() ?: 0
            val rpeStr = fields.getOrNull(6)?.trim()
            val rpe = if (rpeStr != null && rpeStr != "N/A") rpeStr.toDoubleOrNull() else null
            val rirStr = fields.getOrNull(7)?.trim()
            val rir = if (rirStr != null && rirStr != "N/A") rirStr.toIntOrNull() else null
            val bodyweightStr = fields.getOrNull(8)?.trim()
            val isBodyweight = bodyweightStr == "true"
            val bandedStr = fields.getOrNull(9)?.trim()
            val bandType: BandType? = if (bandedStr != null && bandedStr != "N/A" && bandedStr != "false" && bandedStr.isNotEmpty()) {
                BandType.from(bandedStr)
            } else null

            val sessionId = "import_session_$epochMs"
            completedSets.add(
                CompletedSetData(
                    id = "import_${epochMs}_${exerciseOrder}_$setNumber",
                    sessionId = sessionId,
                    exerciseId = exercise.id,
                    exercise = exercise,
                    setNumber = setNumber,
                    order = exerciseOrder,
                    weight = if (isBodyweight) 0.0 else weight,
                    reps = reps,
                    rpe = rpe,
                    rir = rir,
                    usesBodyWeight = isBodyweight,
                    bandType = bandType,
                    completedAt = epochMs,
                    isWarmup = false,
                    isAMRAP = false,
                ),
            )
        }

        if (completedSets.isEmpty()) return Pair(null, errors)

        val session = WorkoutSessionData(
            id = "import_session_$epochMs",
            date = epochMs,
            isCompleted = true,
            isImported = true,
            completedSets = completedSets,
        )
        return Pair(session, errors)
    }

    // Attempts to extract an ISO date or epoch from various CSV date string formats.
    private fun parseDate(str: String): Long? {
        // Try ISO 8601 with time: "2023-11-15T08:30:00Z" or "2023-11-15T08:30:00+00:00"
        val isoRegex = Regex("""(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})""")
        val isoMatch = isoRegex.find(str)
        if (isoMatch != null) {
            return try {
                Instant.parse(isoMatch.value + "Z").toEpochMilliseconds()
            } catch (_: Exception) {
                null
            }
        }
        // Try plain ISO date: "2023-11-15"
        val dateRegex = Regex("""(\d{4}-\d{2}-\d{2})""")
        val dateMatch = dateRegex.find(str)
        if (dateMatch != null) {
            return try {
                val local = LocalDate.parse(dateMatch.value)
                Instant.fromEpochMilliseconds(
                    local.toEpochDays().toLong() * 86_400_000L,
                ).toEpochMilliseconds()
            } catch (_: Exception) {
                null
            }
        }
        // Try epoch millis (numeric string)
        return str.trim().toLongOrNull()
    }

    private fun parseCSVLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        val chars = line.toCharArray()

        while (i < chars.size) {
            val ch = chars[i]
            when {
                ch == '"' && inQuotes -> {
                    if (i + 1 < chars.size && chars[i + 1] == '"') {
                        current.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                    }
                }
                ch == '"' && current.toString().isBlank() -> inQuotes = true
                ch == '"' -> current.append(ch)
                ch == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }
}
