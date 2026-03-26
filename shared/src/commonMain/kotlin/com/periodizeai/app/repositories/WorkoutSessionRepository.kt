package com.periodizeai.app.repositories

import com.periodizeai.app.database.CompletedSet
import com.periodizeai.app.database.PeriodizeAIDatabase
import com.periodizeai.app.database.WorkoutSession
import com.periodizeai.app.models.BandType
import com.periodizeai.app.utils.nowEpochMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

data class WorkoutSessionData(
    val id: String,
    val date: Long,
    val durationMs: Long? = null,
    val notes: String? = null,
    val isImported: Boolean = false,
    val isCompleted: Boolean = false,
    val linkedPlannedWorkoutId: String? = null,
    val completedSets: List<CompletedSetData> = emptyList(),
    val readinessSquatScore: Int? = null,
    val readinessBenchScore: Int? = null,
    val readinessDeadliftScore: Int? = null,
    val readinessSleep: Int? = null,
    val readinessNutrition: Int? = null,
    val readinessStress: Int? = null,
    val readinessEnergy: Int? = null,
    val readinessSorenessPecs: Int? = null,
    val readinessSorenessLats: Int? = null,
    val readinessSorenessLowerBack: Int? = null,
    val readinessSorenessGlutesHams: Int? = null,
    val readinessSorenessQuads: Int? = null,
) {
    val totalVolume get() = completedSets.filter { !it.isWarmup }.sumOf { it.weight * it.reps }
    val exerciseCount get() = completedSets.mapNotNull { it.exerciseId }.toSet().size
    val workingSets get() = completedSets.filter { !it.isWarmup }
}

data class CompletedSetData(
    val id: String,
    val sessionId: String,
    val exerciseId: String?,
    val exercise: ExerciseData? = null,
    val setNumber: Int,
    val order: Int,
    val weight: Double,
    val reps: Int,
    val rpe: Double? = null,
    val rir: Int? = null,
    val isWarmup: Boolean = false,
    val isAMRAP: Boolean = false,
    val completedAt: Long? = null,
    val usesBodyWeight: Boolean = false,
    val bandType: BandType? = null,
) {
    val effectiveWeight get() = weight + (bandType?.resistance ?: 0.0)
}

fun WorkoutSession.toDomain(sets: List<CompletedSetData> = emptyList()) = WorkoutSessionData(
    id = id, date = date, durationMs = durationMs, notes = notes,
    isImported = isImported == 1L, isCompleted = isCompleted == 1L,
    linkedPlannedWorkoutId = linkedPlannedWorkoutId, completedSets = sets,
    readinessSquatScore = readinessSquatScore?.toInt(),
    readinessBenchScore = readinessBenchScore?.toInt(),
    readinessDeadliftScore = readinessDeadliftScore?.toInt(),
    readinessSleep = readinessSleep?.toInt(),
    readinessNutrition = readinessNutrition?.toInt(),
    readinessStress = readinessStress?.toInt(),
    readinessEnergy = readinessEnergy?.toInt(),
    readinessSorenessPecs = readinessSorenessPecs?.toInt(),
    readinessSorenessLats = readinessSorenessLats?.toInt(),
    readinessSorenessLowerBack = readinessSorenessLowerBack?.toInt(),
    readinessSorenessGlutesHams = readinessSorenessGlutesHams?.toInt(),
    readinessSorenessQuads = readinessSorenessQuads?.toInt(),
)

fun CompletedSet.toDomain(exercise: ExerciseData? = null) = CompletedSetData(
    id = id, sessionId = sessionId, exerciseId = exerciseId, exercise = exercise,
    setNumber = setNumber.toInt(), order = setOrder.toInt(), weight = weight, reps = reps.toInt(),
    rpe = rpe, rir = rir?.toInt(), isWarmup = isWarmup == 1L, isAMRAP = isAMRAP == 1L,
    completedAt = completedAt, usesBodyWeight = usesBodyWeight == 1L,
    bandType = bandTypeRaw?.let { BandType.from(it) },
)

class WorkoutSessionRepository(
    private val db: PeriodizeAIDatabase,
    private val exerciseRepo: ExerciseRepository,
) {
    suspend fun getAll(): List<WorkoutSessionData> = withContext(Dispatchers.IO) {
        db.workoutSessionQueries.selectAll().executeAsList()
            .map { it.toDomain(setsForSession(it.id)) }
    }

    suspend fun getRecent(limit: Long = 20): List<WorkoutSessionData> = withContext(Dispatchers.IO) {
        db.workoutSessionQueries.selectRecent(limit).executeAsList()
            .map { it.toDomain(setsForSession(it.id)) }
    }

    suspend fun getById(id: String): WorkoutSessionData? = withContext(Dispatchers.IO) {
        db.workoutSessionQueries.selectById(id).executeAsOneOrNull()
            ?.toDomain(setsForSession(id))
    }

    suspend fun getByDateRange(startMs: Long, endMs: Long): List<WorkoutSessionData> = withContext(Dispatchers.IO) {
        db.workoutSessionQueries.selectByDateRange(startMs, endMs).executeAsList()
            .map { it.toDomain(setsForSession(it.id)) }
    }

    suspend fun saveSession(session: WorkoutSessionData) = withContext(Dispatchers.IO) {
        db.workoutSessionQueries.insert(
            session.id, session.date, session.durationMs, session.notes,
            if (session.isImported) 1L else 0L, if (session.isCompleted) 1L else 0L,
            session.linkedPlannedWorkoutId,
            session.readinessSquatScore?.toLong(), session.readinessBenchScore?.toLong(),
            session.readinessDeadliftScore?.toLong(), session.readinessSleep?.toLong(),
            session.readinessNutrition?.toLong(), session.readinessStress?.toLong(),
            session.readinessEnergy?.toLong(), session.readinessSorenessPecs?.toLong(),
            session.readinessSorenessLats?.toLong(), session.readinessSorenessLowerBack?.toLong(),
            session.readinessSorenessGlutesHams?.toLong(), session.readinessSorenessQuads?.toLong(),
        )
    }

    suspend fun saveSet(set: CompletedSetData) = withContext(Dispatchers.IO) {
        db.completedSetQueries.insert(
            set.id, set.sessionId, set.exerciseId, set.setNumber.toLong(), set.order.toLong(),
            set.weight, set.reps.toLong(), set.rpe, set.rir?.toLong(),
            if (set.isWarmup) 1L else 0L, if (set.isAMRAP) 1L else 0L,
            set.completedAt ?: nowEpochMs(), if (set.usesBodyWeight) 1L else 0L, set.bandType?.raw,
        )
    }

    suspend fun markCompleted(sessionId: String, durationMs: Long) = withContext(Dispatchers.IO) {
        db.workoutSessionQueries.markCompleted(durationMs, sessionId)
    }

    suspend fun delete(sessionId: String) = withContext(Dispatchers.IO) {
        db.workoutSessionQueries.delete(sessionId)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.workoutSessionQueries.deleteAll()
    }

    private fun setsForSession(sessionId: String): List<CompletedSetData> =
        db.completedSetQueries.selectBySession(sessionId).executeAsList().map { set ->
            set.toDomain(set.exerciseId?.let { exerciseRepo.getByIdSync(it) })
        }
}
