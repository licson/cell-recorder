package com.cellrecorder.app.service

/**
 * Pure helper that recomputes the discontinuity index deque when the recorded path
 * deque overflows its max size. When the path's first element is removed, all
 * discontinuity indices must shift down by 1; a discontinuity at index 0 becomes
 * invalid (the element it pointed to is gone) and is dropped.
 *
 * Extracted from [PointRecorder] so the shift math is unit-testable in isolation
 * without instantiating the Hilt-injected recording machinery.
 */
object IndoorDiscontinuityShifter {

    /**
     * Returns a new deque containing the shifted discontinuity indices.
     * - If the input deque has a discontinuity at index 0, that discontinuity is dropped
     *   (the path element it pointed to has been removed).
     * - All remaining discontinuity indices are decremented by 1.
     *
     * The input deque is not mutated.
     */
    fun shift(discontinuities: ArrayDeque<Int>): ArrayDeque<Int> {
        val source = if (discontinuities.isNotEmpty() && discontinuities.first() == 0) {
            ArrayDeque<Int>(discontinuities).apply { removeFirst() }
        } else {
            discontinuities
        }
        val shifted = ArrayDeque<Int>(source.size)
        for (idx in source) {
            shifted.addLast(idx - 1)
        }
        return shifted
    }
}
