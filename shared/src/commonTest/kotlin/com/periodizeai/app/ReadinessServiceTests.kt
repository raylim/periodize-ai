package com.periodizeai.app

import com.periodizeai.app.models.*
import com.periodizeai.app.repositories.ExerciseData
import com.periodizeai.app.services.ReadinessInput
import com.periodizeai.app.services.ReadinessService
import kotlin.test.*

/**
 * Tests for ReadinessService — composite readiness scores per lift, RPE adjustments,
 * and readiness label classification.
 *
 * All rating inputs use a 1–5 scale where:
 *   1 = worst (poor sleep, max soreness)
 *   5 = best (excellent sleep, no soreness)
 */
class ReadinessServiceTests {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun perfectInput() = ReadinessInput(
        sleep = 5,
        nutrition = 5,
        stress = 5,
        energy = 5,
        sorenessPecs = 1,
        sorenessLats = 1,
        sorenessLowerBack = 1,
        sorenessGlutesHams = 1,
        sorenessQuads = 1,
    )

    private fun worstInput() = ReadinessInput(
        sleep = 1,
        nutrition = 1,
        stress = 1,
        energy = 1,
        sorenessPecs = 5,
        sorenessLats = 5,
        sorenessLowerBack = 5,
        sorenessGlutesHams = 5,
        sorenessQuads = 5,
    )

    private fun exerciseWithPattern(
        id: String,
        name: String,
        pattern: MovementPattern,
    ) = ExerciseData(id = id, name = name, movementPattern = pattern)

    // ── Composite readiness score bounds ──────────────────────────────────────

    @Test
    fun testPerfectReadinessScores100() {
        val result = ReadinessService.calculateReadiness(perfectInput())
        assertEquals(100, result.squat,
            "Perfect inputs should yield squat readiness of 100")
        assertEquals(100, result.bench,
            "Perfect inputs should yield bench readiness of 100")
        assertEquals(100, result.deadlift,
            "Perfect inputs should yield deadlift readiness of 100")
    }

    @Test
    fun testWorstReadinessScoresLow() {
        val result = ReadinessService.calculateReadiness(worstInput())
        assertEquals(0, result.squat,
            "Worst inputs should yield squat readiness of 0")
        assertEquals(0, result.bench,
            "Worst inputs should yield bench readiness of 0")
        assertEquals(0, result.deadlift,
            "Worst inputs should yield deadlift readiness of 0")
    }

    @Test
    fun testReadinessScoresInRange() {
        // Arbitrary mid-range inputs should produce scores in [0, 100]
        val input = ReadinessInput(
            sleep = 3, nutrition = 3, stress = 3, energy = 3,
            sorenessPecs = 3, sorenessLats = 3, sorenessLowerBack = 3,
            sorenessGlutesHams = 3, sorenessQuads = 3,
        )
        val result = ReadinessService.calculateReadiness(input)
        assertTrue(result.squat in 0..100)
        assertTrue(result.bench in 0..100)
        assertTrue(result.deadlift in 0..100)
    }

    // ── Lift-specific soreness isolation ─────────────────────────────────────

    @Test
    fun testHighSquadSorenessLowersSquatReadinessOnly() {
        // Isolated high quad + glute/ham soreness should primarily tank squat
        val input = perfectInput().copy(sorenessQuads = 5, sorenessGlutesHams = 5)
        val result = ReadinessService.calculateReadiness(input)
        // Squat should be most impacted
        assertTrue(result.squat < result.bench,
            "High squat soreness should lower squat readiness more than bench")
    }

    @Test
    fun testHighPecSorenessLowersBenchReadinessOnly() {
        // High pec soreness should primarily tank bench
        val input = perfectInput().copy(sorenessPecs = 5)
        val result = ReadinessService.calculateReadiness(input)
        assertTrue(result.bench < result.squat,
            "High pec soreness should lower bench more than squat")
    }

    @Test
    fun testHighLowerBackSorenessLowersDeadliftReadiness() {
        // High lower-back soreness should primarily tank deadlift
        val input = perfectInput().copy(sorenessLowerBack = 5)
        val result = ReadinessService.calculateReadiness(input)
        assertTrue(result.deadlift < result.bench,
            "High lower-back soreness should lower deadlift more than bench")
    }

    // ── liftCategory ─────────────────────────────────────────────────────────

    @Test
    fun testLiftCategorySquatMovement() {
        val exercise = exerciseWithPattern("sq", "Low Bar Squat", MovementPattern.SQUAT)
        assertEquals(LiftCategory.SQUAT, ReadinessService.liftCategory(exercise))
    }

    @Test
    fun testLiftCategoryHingeIsDeadlift() {
        val exercise = exerciseWithPattern("dl", "Conventional Deadlift", MovementPattern.HINGE)
        assertEquals(LiftCategory.DEADLIFT, ReadinessService.liftCategory(exercise))
    }

    @Test
    fun testLiftCategoryHorizontalPushIsBench() {
        val exercise = exerciseWithPattern("bp", "Bench Press", MovementPattern.HORIZONTAL_PUSH)
        assertEquals(LiftCategory.BENCH, ReadinessService.liftCategory(exercise))
    }

    // ── rpeAdjustment ────────────────────────────────────────────────────────

    @Test
    fun testRpeAdjustmentHighReadiness() {
        // Readiness ≥ 80 → no RPE adjustment needed
        assertEquals(0.0, ReadinessService.rpeAdjustment(90), 0.001)
    }

    @Test
    fun testRpeAdjustmentPerfectReadiness() {
        assertEquals(0.0, ReadinessService.rpeAdjustment(100), 0.001)
    }

    @Test
    fun testRpeAdjustmentLowReadiness() {
        // Readiness of 30 → -1.5 RPE (reduce intensity)
        assertEquals(-1.5, ReadinessService.rpeAdjustment(30), 0.001)
    }

    @Test
    fun testRpeAdjustmentIsNonPositive() {
        // Adjustment should never increase RPE above prescribed
        for (score in 0..100 step 10) {
            assertTrue(ReadinessService.rpeAdjustment(score) <= 0.0,
                "RPE adjustment for score $score should be ≤ 0")
        }
    }

    @Test
    fun testRpeAdjustmentMonotonicallyIncreases() {
        // Higher readiness → less negative (closer to 0) adjustment
        val adj30 = ReadinessService.rpeAdjustment(30)
        val adj60 = ReadinessService.rpeAdjustment(60)
        val adj90 = ReadinessService.rpeAdjustment(90)
        assertTrue(adj30 <= adj60, "Score 30 adj ($adj30) should be ≤ score 60 adj ($adj60)")
        assertTrue(adj60 <= adj90, "Score 60 adj ($adj60) should be ≤ score 90 adj ($adj90)")
    }

    // ── readinessLabel ────────────────────────────────────────────────────────

    @Test
    fun testReadinessLabelHigh() {
        assertEquals("High", ReadinessService.readinessLabel(90))
    }

    @Test
    fun testReadinessLabelCritical() {
        assertEquals("Critical", ReadinessService.readinessLabel(10))
    }

    @Test
    fun testReadinessLabel100() {
        assertEquals("High", ReadinessService.readinessLabel(100))
    }

    @Test
    fun testReadinessLabel0() {
        assertEquals("Critical", ReadinessService.readinessLabel(0))
    }

    @Test
    fun testReadinessLabelIsNonEmpty() {
        for (score in 0..100 step 5) {
            val label = ReadinessService.readinessLabel(score)
            assertTrue(label.isNotBlank(), "Label for score $score should be non-blank")
        }
    }
}
