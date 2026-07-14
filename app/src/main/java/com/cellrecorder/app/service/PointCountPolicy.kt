package com.cellrecorder.app.service

/**
 * Pure-logic policy that decides the session `pointCount` increment for a single
 * recording trigger. The increment equals the number of rows actually inserted
 * (across all SIMs) for that trigger. An empty batch (all snapshots failed to
 * build or transient-failed at the DB layer) produces zero increment, preventing
 * drift between the displayed `pointCount` and the actual row count in `cell_records`.
 */
object PointCountPolicy {

    fun incrementFor(insertedCount: Int): Int = insertedCount.coerceAtLeast(0)
}
