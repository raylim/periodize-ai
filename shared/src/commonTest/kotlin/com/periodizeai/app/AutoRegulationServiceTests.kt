package com.periodizeai.app

import com.periodizeai.app.services.OneRepMaxCalculator
import kotlin.test.*

/**
 * Tests for the auto-regulation e1RM smoothing logic, accessed via
 * [OneRepMaxCalculator.updateEstimate].
 *
 * Blend formula (default readiness):
 *   updated = oldWeight × current + newWeight × new
 * where oldWeight + newWeight = 1 and oldWeight > 0.5 for conservative smoothing.
 *
 * When readiness is low, the new-estimate weight is further reduced so that a
 * poor performance day does not overly deflate the stored e1RM.
 */
class AutoRegulationServiceTests {

    // ── Basic smoothing ───────────────────────────────────────────────────────

    @Test
    fun testE1RMExponentialSmoothing() {
        // PR day (new > current): result should be blended upward from current
        val updated = OneRepMaxCalculator.updateEstimate(current = 100.0, new = 110.0)
        // Must land strictly between current and new
        assertTrue(updated > 100.0 && updated < 110.0,
            "Blended e1RM $updated should be between 100 and 110")
    }

    @Test
    fun testE1RMSmoothingIsConservative() {
        // Conservative blend: old value should have majority weight (oldWeight > newWeight)
        // So the result is always closer to current than to new
        val updatedUp = OneRepMaxCalculator.updateEstimate(current = 100.0, new = 110.0)
        val updatedDown = OneRepMaxCalculator.updateEstimate(current = 100.0, new = 90.0)
        // Distance from current should be less than distance from new
        assertTrue(kotlin.math.abs(updatedUp - 100.0) < kotlin.math.abs(updatedUp - 110.0),
            "Result $updatedUp should be closer to current (100) than to new (110)")
        assertTrue(kotlin.math.abs(updatedDown - 100.0) < kotlin.math.abs(updatedDown - 90.0),
            "Result $updatedDown should be closer to current (100) than to new (90)")
    }

    // ── Readiness-adjusted smoothing ──────────────────────────────────────────

    @Test
    fun testE1RMLowReadinessConservative() {
        // Low readiness (score = 30) reduces confidence in the new measurement.
        // confidence factor for readiness=30 (e.g. confidence = readiness/100 * scaleFactor ≈ 0.4)
        // effectiveNewWeight = baseNewWeight × confidence (e.g. 0.5 × 0.4 = 0.2)
        // blend = (1 - 0.2) × 100 + 0.2 × 110 = 80 + 22 = 102.0
        val updated = OneRepMaxCalculator.updateEstimate(
            current = 100.0,
            new = 110.0,
            readinessScore = 30,
        )
        // Low readiness → result should be much closer to current than normal
        val normalUpdated = OneRepMaxCalculator.updateEstimate(current = 100.0, new = 110.0)
        assertTrue(updated < normalUpdated,
            "Low-readiness update $updated should be less aggressive than normal update $normalUpdated")
        // Must still be above current (PR) — just less of a bump
        assertTrue(updated > 100.0,
            "Low-readiness update $updated should still move above current (100)")
    }

    @Test
    fun testE1RMHighReadinessFullWeight() {
        // High readiness (score = 100) → full confidence → same as default call
        val fullReadiness = OneRepMaxCalculator.updateEstimate(
            current = 100.0,
            new = 110.0,
            readinessScore = 100,
        )
        val defaultCall = OneRepMaxCalculator.updateEstimate(current = 100.0, new = 110.0)
        assertEquals(defaultCall, fullReadiness, 0.01)
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun testNullCurrentReturnsNew() {
        val updated = OneRepMaxCalculator.updateEstimate(current = null, new = 150.0)
        assertEquals(150.0, updated, 0.01)
    }

    @Test
    fun testSameCurrent() {
        // new == current → result should equal current
        val updated = OneRepMaxCalculator.updateEstimate(current = 100.0, new = 100.0)
        assertEquals(100.0, updated, 0.01)
    }
}
