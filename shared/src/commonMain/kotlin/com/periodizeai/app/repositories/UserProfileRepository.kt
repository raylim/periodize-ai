package com.periodizeai.app.repositories

import com.periodizeai.app.database.PeriodizeAIDatabase
import com.periodizeai.app.database.UserProfile
import com.periodizeai.app.models.*
import com.periodizeai.app.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

data class UserProfileData(
    val id: String,
    val name: String,
    val weightUnit: WeightUnit,
    val goal: TrainingGoal,
    val hasCompletedOnboarding: Boolean,
    val hasImportedCSV: Boolean,
    val userBodyWeight: Double,
    val barbellWeight: Double,
    val availablePlatesLb: List<Double>,
    val availablePlatesKg: List<Double>,
    val restTimerMainLift: Long,
    val restTimerCompound: Long,
    val restTimerIsolation: Long,
    val healthKitEnabled: Boolean,
    val syncBodyWeightFromHealth: Boolean,
    val deadliftStance: DeadliftStance,
    val meetDateMs: Long?,
    val sex: UserSex,
    val userHeight: Double,
    val userAge: Long,
    val dateOfBirthMs: Long?,
    val trainingAge: Long,
    val strengthLevel: StrengthLevel,
    val dietStatus: DietStatus,
    val sleepQuality: SleepQuality,
    val stressLevel: StressLevel,
    val trainingDaysPerWeek: Long,
    val createdAt: Long,
)

private fun UserProfile.toDomain() = UserProfileData(
    id                       = id,
    name                     = name,
    weightUnit               = WeightUnit.from(weightUnitRaw),
    goal                     = TrainingGoal.from(goalRaw),
    hasCompletedOnboarding   = hasCompletedOnboarding == 1L,
    hasImportedCSV           = hasImportedCSV == 1L,
    userBodyWeight           = userBodyWeight,
    barbellWeight            = barbellWeight,
    availablePlatesLb        = availablePlatesLbRaw.toDoubleList(),
    availablePlatesKg        = availablePlatesKgRaw.toDoubleList(),
    restTimerMainLift        = restTimerMainLift,
    restTimerCompound        = restTimerCompound,
    restTimerIsolation       = restTimerIsolation,
    healthKitEnabled         = healthKitEnabled == 1L,
    syncBodyWeightFromHealth = syncBodyWeightFromHealth == 1L,
    deadliftStance           = DeadliftStance.from(deadliftStanceRaw),
    meetDateMs               = meetDateMs,
    sex                      = UserSex.from(sexRaw),
    userHeight               = userHeight,
    userAge                  = userAge,
    dateOfBirthMs            = dateOfBirthMs,
    trainingAge              = trainingAge,
    strengthLevel            = StrengthLevel.from(strengthLevelRaw),
    dietStatus               = DietStatus.from(dietStatusRaw),
    sleepQuality             = SleepQuality.from(sleepQualityRaw),
    stressLevel              = StressLevel.from(stressLevelRaw),
    trainingDaysPerWeek      = trainingDaysPerWeek,
    createdAt                = createdAt,
)

class UserProfileRepository(private val db: PeriodizeAIDatabase) {

    private val queries get() = db.userProfileQueries

    suspend fun getProfile(): UserProfileData? = withContext(Dispatchers.IO) {
        queries.selectFirst().executeAsOneOrNull()?.toDomain()
    }

    suspend fun save(profile: UserProfileData) = withContext(Dispatchers.IO) {
        queries.insert(
            id                       = profile.id,
            name                     = profile.name,
            weightUnitRaw            = profile.weightUnit.raw,
            goalRaw                  = profile.goal.raw,
            hasCompletedOnboarding   = if (profile.hasCompletedOnboarding) 1L else 0L,
            hasImportedCSV           = if (profile.hasImportedCSV) 1L else 0L,
            userBodyWeight           = profile.userBodyWeight,
            barbellWeight            = profile.barbellWeight,
            availablePlatesLbRaw     = profile.availablePlatesLb.toDelimitedString(),
            availablePlatesKgRaw     = profile.availablePlatesKg.toDelimitedString(),
            restTimerMainLift        = profile.restTimerMainLift,
            restTimerCompound        = profile.restTimerCompound,
            restTimerIsolation       = profile.restTimerIsolation,
            healthKitEnabled         = if (profile.healthKitEnabled) 1L else 0L,
            syncBodyWeightFromHealth = if (profile.syncBodyWeightFromHealth) 1L else 0L,
            deadliftStanceRaw        = profile.deadliftStance.raw,
            meetDateMs               = profile.meetDateMs,
            sexRaw                   = profile.sex.raw,
            userHeight               = profile.userHeight,
            userAge                  = profile.userAge,
            dateOfBirthMs            = profile.dateOfBirthMs,
            trainingAge              = profile.trainingAge,
            strengthLevelRaw         = profile.strengthLevel.raw,
            dietStatusRaw            = profile.dietStatus.raw,
            sleepQualityRaw          = profile.sleepQuality.raw,
            stressLevelRaw           = profile.stressLevel.raw,
            trainingDaysPerWeek      = profile.trainingDaysPerWeek,
            createdAt                = profile.createdAt,
        )
    }

    suspend fun markOnboardingComplete(id: String) = withContext(Dispatchers.IO) {
        queries.updateOnboardingComplete(id = id)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        queries.delete(id = id)
    }
}
