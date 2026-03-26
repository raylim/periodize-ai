package com.periodizeai.app.repositories

import com.periodizeai.app.database.PeriodizeAIDatabase
import com.periodizeai.app.models.LiftCategory
import com.periodizeai.app.models.StickingPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class UserStickingPointData(
    val id: String,
    val userProfileId: String,
    val liftCategory: LiftCategory,
    val stickingPoint: StickingPoint,
)

class StickingPointRepository(private val db: PeriodizeAIDatabase) {

    suspend fun getForProfile(profileId: String): List<UserStickingPointData> = withContext(Dispatchers.IO) {
        db.userStickingPointQueries.selectByProfile(profileId).executeAsList().mapNotNull { row ->
            val lift = LiftCategory.from(row.liftCategoryRaw) ?: return@mapNotNull null
            val sp   = StickingPoint.from(row.stickingPointRaw) ?: return@mapNotNull null
            UserStickingPointData(row.id, row.userProfileId, lift, sp)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun upsert(profileId: String, lift: LiftCategory, sp: StickingPoint) = withContext(Dispatchers.IO) {
        db.userStickingPointQueries.upsert(
            id              = Uuid.random().toString(),
            userProfileId   = profileId,
            liftCategoryRaw = lift.raw,
            stickingPointRaw = sp.raw,
        )
    }

    suspend fun deleteForLift(profileId: String, lift: LiftCategory) = withContext(Dispatchers.IO) {
        db.userStickingPointQueries.deleteByLift(profileId, lift.raw)
    }

    suspend fun deleteAll(profileId: String) = withContext(Dispatchers.IO) {
        db.userStickingPointQueries.deleteAll(profileId)
    }
}
