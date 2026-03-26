package com.periodizeai.app.repositories

import com.periodizeai.app.database.Exercise
import com.periodizeai.app.database.PeriodizeAIDatabase
import com.periodizeai.app.models.*
import com.periodizeai.app.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

data class ExerciseData(
    val id: String,
    val name: String,
    val primaryMuscles: List<MuscleGroup> = emptyList(),
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val movementPattern: MovementPattern = MovementPattern.SQUAT,
    val classification: ExerciseClassification = ExerciseClassification.COMPOUND,
    val equipment: EquipmentType = EquipmentType.BARBELL,
    val coachingCues: List<String> = emptyList(),
    val estimatedOneRepMax: Double? = null,
    val workingMax: Double? = null,
    val lastUsedAt: Long? = null,
    val isCustom: Boolean = false,
    val isBarbell: Boolean = false,
    val minReps: Int = 6,
    val maxReps: Int = 12,
    val addressesStickingPoints: List<StickingPoint> = emptyList(),
) {
    val allMuscles get() = primaryMuscles + secondaryMuscles
    val isLowerBodyLift get() = movementPattern == MovementPattern.SQUAT || movementPattern == MovementPattern.HINGE
}

fun Exercise.toDomain() = ExerciseData(
    id                       = id,
    name                     = name,
    primaryMuscles           = primaryMusclesRaw.toStringList().mapNotNull { MuscleGroup.from(it) },
    secondaryMuscles         = secondaryMusclesRaw.toStringList().mapNotNull { MuscleGroup.from(it) },
    movementPattern          = MovementPattern.from(movementPatternRaw),
    classification           = ExerciseClassification.from(classificationRaw),
    equipment                = EquipmentType.from(equipmentRaw) ?: EquipmentType.BARBELL,
    coachingCues             = coachingCuesRaw.toStringList(),
    estimatedOneRepMax       = estimatedOneRepMax,
    workingMax               = workingMax,
    lastUsedAt               = lastUsedAt,
    isCustom                 = isCustom == 1L,
    isBarbell                = isBarbell == 1L,
    minReps                  = minReps.toInt(),
    maxReps                  = maxReps.toInt(),
    addressesStickingPoints  = addressesStickingPointsRaw.toStringList().mapNotNull { StickingPoint.from(it) },
)

class ExerciseRepository(private val db: PeriodizeAIDatabase) {

    private val queries get() = db.exerciseQueries

    suspend fun getAll(): List<ExerciseData> = withContext(Dispatchers.IO) {
        queries.selectAll().executeAsList().map { it.toDomain() }
    }

    suspend fun getById(id: String): ExerciseData? = withContext(Dispatchers.IO) {
        queries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    /** Synchronous version for use inside repository helpers called within withContext(IO). */
    fun getByIdSync(id: String): ExerciseData? =
        queries.selectById(id).executeAsOneOrNull()?.toDomain()

    suspend fun getByName(name: String): ExerciseData? = withContext(Dispatchers.IO) {
        queries.selectByName(name).executeAsOneOrNull()?.toDomain()
    }

    suspend fun getMainLifts(): List<ExerciseData> = withContext(Dispatchers.IO) {
        queries.selectMainLifts().executeAsList().map { it.toDomain() }
    }

    suspend fun save(e: ExerciseData) = withContext(Dispatchers.IO) {
        queries.insert(
            id                         = e.id,
            name                       = e.name,
            primaryMusclesRaw          = e.primaryMuscles.joinToString(",") { it.raw },
            secondaryMusclesRaw        = e.secondaryMuscles.joinToString(",") { it.raw },
            movementPatternRaw         = e.movementPattern.raw,
            classificationRaw          = e.classification.raw,
            equipmentRaw               = e.equipment.raw,
            coachingCuesRaw            = e.coachingCues.toDelimitedString(),
            estimatedOneRepMax         = e.estimatedOneRepMax,
            workingMax                 = e.workingMax,
            lastUsedAt                 = e.lastUsedAt,
            isCustom                   = if (e.isCustom) 1L else 0L,
            isBarbell                  = if (e.isBarbell) 1L else 0L,
            minReps                    = e.minReps.toLong(),
            maxReps                    = e.maxReps.toLong(),
            addressesStickingPointsRaw = e.addressesStickingPoints.joinToString(",") { it.raw },
        )
    }

    suspend fun updateE1RM(id: String, e1rm: Double, workingMax: Double) = withContext(Dispatchers.IO) {
        queries.updateE1RM(e1rm, workingMax, nowEpochMs(), id)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        queries.delete(id)
    }
}
