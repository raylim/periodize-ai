package com.periodizeai.app

import com.periodizeai.app.models.MovementPattern
import com.periodizeai.app.repositories.ExerciseData
import com.periodizeai.app.services.OneRepMaxCalculator
import kotlin.test.*

/**
 * Tests for OneRepMaxCalculator — Epley formula, weight suggestions, smoothing,
 * warmup ramp generation, and plate math.
 */
class OneRepMaxCalculatorTests {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun minimalExercise(
        name: String = "Test Exercise",
        movementPattern: MovementPattern = MovementPattern.HORIZONTAL_PUSH,
        isBarbell: Boolean = true,
        estimatedOneRepMax: Double? = null,
        workingMax: Double? = null,
    ) = ExerciseData(
        id = "test_id",
        name = name,
        movementPattern = movementPattern,
        isBarbell = isBarbell,
        estimatedOneRepMax = estimatedOneRepMax,
        workingMax = workingMax,
    )

    // ── Epley formula ─────────────────────────────────────────────────────────

    @Test
    fun testEpleyFormulaBasic() {
        // 100 kg × 5 reps (no RIR) → effectiveReps = 5
        // 1RM = 100 × (1 + 5/30) ≈ 116.67
        val result = OneRepMaxCalculator.calculate(weight = 100.0, reps = 5)
        assertNotNull(result)
        assertEquals(100.0 * (1.0 + 5.0 / 30.0), result!!, 0.01)
    }

    @Test
    fun testEpleyWithRIR() {
        // 5 reps @ RIR 2 → effectiveReps = 5 + 2 = 7
        // 1RM = 100 × (1 + 7/30) ≈ 123.33
        val result = OneRepMaxCalculator.calculate(weight = 100.0, reps = 5, rir = 2)
        assertNotNull(result)
        assertEquals(100.0 * (1.0 + 7.0 / 30.0), result!!, 0.01)
    }

    @Test
    fun testSingleRepReturnsWeight() {
        // 1 rep, no RIR → effectiveReps = 1; formula = weight × (1 + 1/30) ≈ weight
        val result = OneRepMaxCalculator.calculate(weight = 200.0, reps = 1)
        assertNotNull(result)
        // Result must be at least the lifted weight
        assertTrue(result!! >= 200.0)
    }

    @Test
    fun testZeroWeightReturnsNull() {
        assertNull(OneRepMaxCalculator.calculate(weight = 0.0, reps = 5))
    }

    @Test
    fun testNegativeWeightReturnsNull() {
        assertNull(OneRepMaxCalculator.calculate(weight = -10.0, reps = 5))
    }

    // ── Weight suggestions ────────────────────────────────────────────────────

    @Test
    fun testSuggestWeightInverseEpley() {
        // e1RM = 100, 5 reps @ RIR 2 → effectiveReps = 7
        // percentage = 1 / (1 + 7/30) = 30/37 ≈ 0.8108 → ≈ 81.08 lb
        // Rounded to nearest 5 lb → 80.0
        val exercise = minimalExercise(estimatedOneRepMax = 100.0, workingMax = 90.0)
        val weight = OneRepMaxCalculator.suggestWeight(exercise, reps = 5, rir = 2)
        assertNotNull(weight)
        // Must be divisible by 5 (rounded to plate increments)
        assertEquals(0.0, weight!! % 5.0, 0.01)
        // Must be a reasonable percentage of e1RM
        assertTrue(weight > 0.0 && weight < 100.0)
    }

    @Test
    fun testSuggestWeightNullE1RMReturnsNull() {
        val exercise = minimalExercise(estimatedOneRepMax = null)
        assertNull(OneRepMaxCalculator.suggestWeight(exercise, reps = 5, rir = 2))
    }

    // ── e1RM update / exponential smoothing ──────────────────────────────────

    @Test
    fun testUpdateEstimateImprovement() {
        // PR: new estimate should pull current upward
        val updated = OneRepMaxCalculator.updateEstimate(current = 100.0, new = 110.0)
        assertTrue(updated > 100.0 && updated < 110.0,
            "Updated e1RM $updated should be between current (100) and new (110)")
    }

    @Test
    fun testUpdateEstimateDecline() {
        // Bad day: new estimate should lower current slightly but conservatively
        val updated = OneRepMaxCalculator.updateEstimate(current = 100.0, new = 90.0)
        assertTrue(updated < 100.0 && updated > 90.0,
            "Updated e1RM $updated should be between 90 and 100")
        // Conservative blend: result should stay closer to current than to new
        assertTrue(updated > 95.0,
            "Conservative blend should stay above 95 (closer to 100 than to 90)")
    }

    @Test
    fun testUpdateEstimateNullCurrentReturnsNew() {
        val updated = OneRepMaxCalculator.updateEstimate(current = null, new = 150.0)
        assertEquals(150.0, updated, 0.01)
    }

    // ── Warmup ramp ───────────────────────────────────────────────────────────

    @Test
    fun testWarmupRamp() {
        val ramp = OneRepMaxCalculator.warmupRamp(workingWeight = 100.0, barWeight = 45.0)
        assertTrue(ramp.isNotEmpty(), "Ramp must have at least one set")
        // First set is always the empty bar
        assertEquals(45.0, ramp.first().first, 0.01)
        // All warmup weights must be ≤ working weight
        assertTrue(ramp.all { it.first <= 100.0 },
            "All ramp weights must be ≤ working weight")
    }

    @Test
    fun testWarmupRampIsAscending() {
        val ramp = OneRepMaxCalculator.warmupRamp(workingWeight = 225.0, barWeight = 45.0)
        val weights = ramp.map { it.first }
        for (i in 1 until weights.size) {
            assertTrue(weights[i] >= weights[i - 1],
                "Ramp weights must be non-decreasing")
        }
    }

    // ── Plate math ────────────────────────────────────────────────────────────

    @Test
    fun testPlateMath() {
        // 225 lb total, 45 lb bar → 180 lb plates, 90 lb per side → [45, 45]
        val plates = OneRepMaxCalculator.plateMath(
            totalWeight = 225.0,
            barWeight = 45.0,
            availablePlates = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5),
        )
        assertNotNull(plates)
        assertEquals(listOf(45.0, 45.0), plates)
    }

    @Test
    fun testPlateMathOddWeight() {
        // 135 lb total, 45 lb bar → 90 lb plates, 45 lb per side → [45]
        val plates = OneRepMaxCalculator.plateMath(
            totalWeight = 135.0,
            barWeight = 45.0,
            availablePlates = listOf(45.0, 25.0, 10.0, 5.0, 2.5),
        )
        assertNotNull(plates)
        assertEquals(listOf(45.0), plates)
    }

    @Test
    fun testPlateMathBareBar() {
        // Bar only — no plates needed
        val plates = OneRepMaxCalculator.plateMath(
            totalWeight = 45.0,
            barWeight = 45.0,
            availablePlates = listOf(45.0, 25.0, 10.0, 5.0, 2.5),
        )
        assertNotNull(plates)
        assertTrue(plates!!.isEmpty())
    }
}
