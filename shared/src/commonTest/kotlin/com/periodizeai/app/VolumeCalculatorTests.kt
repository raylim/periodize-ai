package com.periodizeai.app

import com.periodizeai.app.models.*
import com.periodizeai.app.repositories.UserProfileData
import com.periodizeai.app.services.LiftVolumeBounds
import com.periodizeai.app.services.VolumeCalculator
import kotlin.test.*

/**
 * Tests for VolumeCalculator — MEV/MRV bounds, weekly set progression,
 * deload set prescription, and mesocycle length calculation.
 */
class VolumeCalculatorTests {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun defaultProfile() = UserProfileData(
        id = "test",
        name = "Test Athlete",
        weightUnit = WeightUnit.LB,
        goal = TrainingGoal.POWERLIFTING,
        hasCompletedOnboarding = true,
        hasImportedCSV = false,
        userBodyWeight = 185.0,
        barbellWeight = 45.0,
        availablePlatesLb = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5),
        availablePlatesKg = listOf(20.0, 15.0, 10.0, 5.0, 2.5, 1.25),
        restTimerMainLift = 180L,
        restTimerCompound = 120L,
        restTimerIsolation = 90L,
        healthKitEnabled = false,
        syncBodyWeightFromHealth = false,
        deadliftStance = DeadliftStance.CONVENTIONAL,
        meetDateMs = null,
        sex = Sex.MALE,
        userHeight = 70.0,
        userAge = 28L,
        dateOfBirthMs = null,
        trainingAge = 3L,
        strengthLevel = StrengthLevel.INTERMEDIATE,
        dietStatus = DietStatus.MAINTENANCE,
        sleepQuality = RecoveryRating.AVERAGE,
        stressLevel = RecoveryRating.AVERAGE,
        trainingDaysPerWeek = 4L,
        createdAt = 0L,
    )

    // ── adjustedVolume — coverage of all three lift categories ────────────────

    @Test
    fun testAdjustedVolumeHypertrophyHasThreeLifts() {
        val bounds = VolumeCalculator.adjustedVolume(BlockPhase.HYPERTROPHY, defaultProfile())
        assertEquals(3, bounds.size, "Expected bounds for all three lifts")
        assertTrue(bounds.any { it.lift == LiftCategory.SQUAT }, "Missing SQUAT bounds")
        assertTrue(bounds.any { it.lift == LiftCategory.BENCH }, "Missing BENCH bounds")
        assertTrue(bounds.any { it.lift == LiftCategory.DEADLIFT }, "Missing DEADLIFT bounds")
    }

    @Test
    fun testAdjustedVolumeStrengthHasThreeLifts() {
        val bounds = VolumeCalculator.adjustedVolume(BlockPhase.STRENGTH, defaultProfile())
        assertEquals(3, bounds.size)
        assertTrue(bounds.any { it.lift == LiftCategory.SQUAT })
        assertTrue(bounds.any { it.lift == LiftCategory.BENCH })
        assertTrue(bounds.any { it.lift == LiftCategory.DEADLIFT })
    }

    @Test
    fun testAdjustedVolumePeakingHasThreeLifts() {
        val bounds = VolumeCalculator.adjustedVolume(BlockPhase.PEAKING, defaultProfile())
        assertEquals(3, bounds.size)
    }

    // ── MEV < MRV invariant across all training phases ────────────────────────

    @Test
    fun testMEVAlwaysLessThanMRV() {
        for (phase in listOf(BlockPhase.HYPERTROPHY, BlockPhase.STRENGTH, BlockPhase.PEAKING)) {
            val bounds = VolumeCalculator.adjustedVolume(phase, defaultProfile())
            bounds.forEach { b ->
                assertTrue(b.mev < b.mrv,
                    "MEV (${b.mev}) must be strictly less than MRV (${b.mrv}) for $phase ${b.lift}")
            }
        }
    }

    @Test
    fun testMEVIsPositive() {
        for (phase in listOf(BlockPhase.HYPERTROPHY, BlockPhase.STRENGTH, BlockPhase.PEAKING)) {
            val bounds = VolumeCalculator.adjustedVolume(phase, defaultProfile())
            bounds.forEach { b ->
                assertTrue(b.mev > 0.0, "MEV must be positive for $phase ${b.lift}")
            }
        }
    }

    // ── Diet status adjustments ───────────────────────────────────────────────

    @Test
    fun testSurplusDietIncreasesVolume() {
        val maintenance = defaultProfile()
        val surplus = maintenance.copy(dietStatus = DietStatus.SURPLUS)
        val mBounds = VolumeCalculator.adjustedVolume(BlockPhase.HYPERTROPHY, maintenance)
        val sBounds = VolumeCalculator.adjustedVolume(BlockPhase.HYPERTROPHY, surplus)
        // Surplus should produce ≥ MEV/MRV compared to maintenance for at least one lift
        val mTotal = mBounds.sumOf { it.mrv }
        val sTotal = sBounds.sumOf { it.mrv }
        assertTrue(sTotal >= mTotal,
            "Surplus diet MRV ($sTotal) should be ≥ maintenance MRV ($mTotal)")
    }

    @Test
    fun testDeficitDietDecreasesVolume() {
        val maintenance = defaultProfile()
        val deficit = maintenance.copy(dietStatus = DietStatus.DEFICIT)
        val mBounds = VolumeCalculator.adjustedVolume(BlockPhase.HYPERTROPHY, maintenance)
        val dBounds = VolumeCalculator.adjustedVolume(BlockPhase.HYPERTROPHY, deficit)
        val mTotal = mBounds.sumOf { it.mrv }
        val dTotal = dBounds.sumOf { it.mrv }
        assertTrue(dTotal <= mTotal,
            "Deficit diet MRV ($dTotal) should be ≤ maintenance MRV ($mTotal)")
    }

    // ── setsForWeek — linear ramp from MEV to MRV ────────────────────────────

    @Test
    fun testSetsForWeekLinearRamp() {
        // Week 0 of 4 → MEV (8), Week 3 of 4 → MRV (16)
        val sets0 = VolumeCalculator.setsForWeek(
            weekIndex = 0, trainingWeeks = 4, mev = 8.0, mrv = 16.0,
        )
        val sets3 = VolumeCalculator.setsForWeek(
            weekIndex = 3, trainingWeeks = 4, mev = 8.0, mrv = 16.0,
        )
        assertEquals(8, sets0, "First week should start at MEV")
        assertEquals(16, sets3, "Last week should reach MRV")
    }

    @Test
    fun testSetsForWeekIsNonDecreasing() {
        // Sets must ramp up across a mesocycle
        val weeks = (0..3).map { week ->
            VolumeCalculator.setsForWeek(weekIndex = week, trainingWeeks = 4, mev = 8.0, mrv = 16.0)
        }
        for (i in 1 until weeks.size) {
            assertTrue(weeks[i] >= weeks[i - 1],
                "Sets should be non-decreasing (week $i: ${weeks[i]} < week ${i-1}: ${weeks[i-1]})")
        }
    }

    @Test
    fun testSetsForWeekClampedToMRV() {
        // Even if weekIndex exceeds expected range, should not exceed MRV
        val sets = VolumeCalculator.setsForWeek(
            weekIndex = 99, trainingWeeks = 4, mev = 8.0, mrv = 16.0,
        )
        assertTrue(sets <= 16, "Sets should not exceed MRV")
    }

    // ── deloadSets — half MEV ─────────────────────────────────────────────────

    @Test
    fun testDeloadSetsHalfMEV() {
        val deload = VolumeCalculator.deloadSets(mev = 8.0)
        assertEquals(4, deload, "Deload sets should be half of MEV (8 / 2 = 4)")
    }

    @Test
    fun testDeloadSetsOddMEV() {
        // Odd MEV rounds down (floor)
        val deload = VolumeCalculator.deloadSets(mev = 9.0)
        assertTrue(deload == 4 || deload == 5,
            "Deload for MEV=9 should be 4 or 5 (floor or round of 9/2)")
    }

    // ── mesocycleLength — gap-based calculation ───────────────────────────────

    @Test
    fun testMesocycleLengthWideBounds() {
        // Wide gap (8 sets): expected length = max(4, gap + 1) = 9
        val bounds = listOf(
            LiftVolumeBounds(lift = LiftCategory.SQUAT, mev = 4.0, mrv = 12.0), // gap = 8
        )
        assertEquals(9, VolumeCalculator.mesocycleLength(bounds))
    }

    @Test
    fun testMesocycleLengthNarrowBounds() {
        // Narrow gap (2 sets): expected length = max(4, gap + 1) = max(4, 3) = 4
        val bounds = listOf(
            LiftVolumeBounds(lift = LiftCategory.BENCH, mev = 7.0, mrv = 9.0), // gap = 2
        )
        assertEquals(4, VolumeCalculator.mesocycleLength(bounds))
    }

    @Test
    fun testMesocycleLengthMinimumFourWeeks() {
        // Even a minimal gap should not produce a mesocycle shorter than 4 weeks
        val bounds = listOf(
            LiftVolumeBounds(lift = LiftCategory.DEADLIFT, mev = 6.0, mrv = 7.0), // gap = 1
        )
        assertTrue(VolumeCalculator.mesocycleLength(bounds) >= 4,
            "Mesocycle must be at least 4 weeks")
    }

    @Test
    fun testMesocycleLengthMultipleLiftsUsesMaxGap() {
        // With multiple lifts, length is driven by the largest gap
        val bounds = listOf(
            LiftVolumeBounds(lift = LiftCategory.SQUAT, mev = 4.0, mrv = 12.0),  // gap = 8
            LiftVolumeBounds(lift = LiftCategory.BENCH, mev = 7.0, mrv = 9.0),   // gap = 2
            LiftVolumeBounds(lift = LiftCategory.DEADLIFT, mev = 5.0, mrv = 10.0), // gap = 5
        )
        // Driven by the squat gap of 8 → expected 9
        assertEquals(9, VolumeCalculator.mesocycleLength(bounds))
    }
}
