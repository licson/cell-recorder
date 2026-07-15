package com.cellrecorder.app.ui.recording

import android.content.Context
import android.telephony.SubscriptionManager
import androidx.lifecycle.viewModelScope
import com.cellrecorder.app.data.local.entity.AppConfigEntity
import com.cellrecorder.app.data.local.entity.RecentMarkerLabelEntity
import com.cellrecorder.app.data.local.entity.SessionEntity
import com.cellrecorder.app.data.local.entity.SessionMarkerEntity
import com.cellrecorder.app.data.repository.ConfigRepository
import com.cellrecorder.app.data.repository.RecentMarkerLabelRepository
import com.cellrecorder.app.data.repository.SessionMarkerRepository
import com.cellrecorder.app.data.repository.SessionRepository
import com.cellrecorder.app.domain.model.MarkerType
import com.cellrecorder.app.service.CellInfoCollector
import com.cellrecorder.app.service.IndoorPositionCollector
import com.cellrecorder.app.service.RecordingMutex
import com.cellrecorder.app.service.RecordingStateManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.regex.Pattern

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingViewModelTest {

    companion object {
        private val testDispatcher = UnconfinedTestDispatcher()
        val NOTE_AUTO_LABEL_PATTERN: Pattern = Pattern.compile("""^NOTE #\d+ \d{2}:\d{2}:\d{2}$""")

        @JvmStatic
        @BeforeAll
        fun setUpClass() {
            Dispatchers.setMain(testDispatcher)
        }

        @JvmStatic
        @AfterAll
        fun tearDownClass() {
            Dispatchers.resetMain()
        }
    }

    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val sessionMarkerRepository = mockk<SessionMarkerRepository>(relaxed = true)
    private val recentMarkerLabelRepository = mockk<RecentMarkerLabelRepository>(relaxed = true)
    private val recordingMutex = RecordingMutex()
    private val cellInfoCollector = mockk<CellInfoCollector>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>()
    private val stateManager = mockk<RecordingStateManager>(relaxed = true)
    private val indoorPositionCollector = mockk<IndoorPositionCollector>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val subscriptionManager = mockk<SubscriptionManager>(relaxed = true)

    private lateinit var viewModel: RecordingViewModel

    @BeforeEach
    fun setUp() {
        val config = AppConfigEntity(cellInfoRefreshIntervalSec = 1)
        every { configRepository.getConfig() } returns MutableStateFlow(config)
        every { context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) } returns subscriptionManager
        every { cellInfoCollector.snapshots(config) } returns emptyList()

        viewModel = RecordingViewModel(
            sessionRepository = sessionRepository,
            sessionMarkerRepository = sessionMarkerRepository,
            recentMarkerLabelRepository = recentMarkerLabelRepository,
            recordingMutex = recordingMutex,
            cellInfoCollector = cellInfoCollector,
            configRepository = configRepository,
            stateManager = stateManager,
            indoorPositionCollector = indoorPositionCollector,
            context = context
        )
    }

    @AfterEach
    fun tearDown() {
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `loadSession collects markers for session`() = runTest(testDispatcher) {
        val session = SessionEntity(id = 10, name = "Test", createdAt = 0)
        val markers = listOf(
            SessionMarkerEntity(id = 1, sessionId = 10, timestamp = 1000, seq = 1, type = "NOTE", label = "A")
        )
        every { sessionMarkerRepository.getMarkersForSession(10) } returns flowOf(markers)
        every { sessionRepository.getById(10) } returns flowOf(session)

        viewModel.loadSession(10)
        assertEquals(markers, viewModel.markers.value)
    }

    @Test
    fun `quickMark inserts note with auto label when session loaded`() = runTest(testDispatcher) {
        val session = SessionEntity(id = 10, name = "Test", createdAt = 0)
        coEvery { sessionMarkerRepository.insertMarkerWithAutoLabel(10, MarkerType.NOTE) } returns 1L
        every { sessionMarkerRepository.getMarkersForSession(10) } returns emptyFlow()
        every { sessionRepository.getById(10) } returns flowOf(session)
        viewModel.loadSession(10)

        viewModel.quickMark().join()

        coVerify { sessionMarkerRepository.insertMarkerWithAutoLabel(10, MarkerType.NOTE) }
    }

    @Test
    fun `createMarker inserts marker with type and label`() = runTest(testDispatcher) {
        val session = SessionEntity(id = 10, name = "Test", createdAt = 0)
        coEvery { sessionMarkerRepository.insertMarker(10, MarkerType.SEGMENT_START, "Tunnel A") } returns 2L
        every { sessionMarkerRepository.getMarkersForSession(10) } returns emptyFlow()
        every { sessionRepository.getById(10) } returns flowOf(session)
        viewModel.loadSession(10)

        viewModel.createMarker(MarkerType.SEGMENT_START, "Tunnel A").join()

        coVerify { sessionMarkerRepository.insertMarker(10, MarkerType.SEGMENT_START, "Tunnel A") }
    }

    @Test
    fun `createMarker does nothing when session is not loaded`() = runTest(testDispatcher) {
        viewModel.createMarker(MarkerType.SEGMENT_START, "Tunnel A").join()
        coVerify(exactly = 0) { sessionMarkerRepository.insertMarker(any(), any(), any()) }
    }

    @Test
    fun `editMarker updates marker`() = runTest(testDispatcher) {
        coEvery { sessionMarkerRepository.updateMarker(5, MarkerType.SEGMENT_END, "End") } just runs

        viewModel.editMarker(5, MarkerType.SEGMENT_END, "End").join()

        coVerify { sessionMarkerRepository.updateMarker(5, MarkerType.SEGMENT_END, "End") }
    }

    @Test
    fun `deleteMarker removes marker`() = runTest(testDispatcher) {
        coEvery { sessionMarkerRepository.deleteMarker(5) } just runs

        viewModel.deleteMarker(5).join()

        coVerify { sessionMarkerRepository.deleteMarker(5) }
    }

    @Test
    fun `getRecentLabels returns labels from repository`() = runTest(testDispatcher) {
        val labels = listOf(
            RecentMarkerLabelEntity(
                type = "NOTE",
                label = "A",
                useCount = 1,
                lastUsed = 1000
            )
        )
        coEvery { recentMarkerLabelRepository.getByTypeOrdered(MarkerType.NOTE) } returns labels

        val result = viewModel.getRecentLabels(MarkerType.NOTE)

        assertEquals(labels, result)
        coVerify { recentMarkerLabelRepository.getByTypeOrdered(MarkerType.NOTE) }
    }

    @Test
    fun `quickMark auto-label matches NOTE #N HH-MM-SS format`() = runTest(testDispatcher) {
        val session = SessionEntity(id = 10, name = "Test", createdAt = 0)
        val (repo, state) = newCountingMarkerRepo()
        val vm = buildViewModelWithMarkerRepo(repo)
        stubSessionLoad(session)

        vm.loadSession(10)
        vm.quickMark().join()

        assertEquals(1, state.inserted.size)
        val label = state.inserted[0].label
        assertNotNull(label)
        assertTrue(NOTE_AUTO_LABEL_PATTERN.matcher(label).matches(),
            "Expected auto-label 'NOTE #N HH:MM:SS', got '$label'")
    }

    @Test
    fun `createMarker inserts marker with WAYPOINT type and label`() = runTest(testDispatcher) {
        val session = SessionEntity(id = 10, name = "Test", createdAt = 0)
        val (repo, state) = newCountingMarkerRepo()
        val vm = buildViewModelWithMarkerRepo(repo)
        stubSessionLoad(session)

        vm.loadSession(10)
        vm.createMarker(MarkerType.WAYPOINT, "Central").join()

        assertEquals(1, state.inserted.size)
        assertEquals(MarkerType.WAYPOINT.toStorageString(), state.inserted[0].type)
        assertEquals("Central", state.inserted[0].label)
    }

    @Test
    fun `editMarker updates the row in place without changing seq`() = runTest(testDispatcher) {
        val (repo, state) = newCountingMarkerRepo()
        val vm = buildViewModelWithMarkerRepo(repo)
        val original = SessionMarkerEntity(id = 5, sessionId = 10, timestamp = 1000, seq = 3, type = "WAYPOINT", label = "old")
        state.rows[5] = original

        vm.editMarker(5, MarkerType.WAYPOINT, "King's Cross").join()

        val updated = state.rows[5]
        assertNotNull(updated)
        assertEquals("King's Cross", updated!!.label)
        assertEquals("WAYPOINT", updated.type)
        assertEquals(3, updated.seq, "seq must be unchanged after edit")
        assertEquals(1000L, updated.timestamp, "timestamp must be unchanged after edit")
        assertTrue(state.upsertedRecents.any { it.label == "King's Cross" },
            "editing a marker's label should upsert the new label into recent_marker_labels")
    }

    @Test
    fun `editMarker does not decrement the old label in recents`() = runTest(testDispatcher) {
        val (repo, state) = newCountingMarkerRepo()
        val vm = buildViewModelWithMarkerRepo(repo)
        state.rows[5] = SessionMarkerEntity(id = 5, sessionId = 10, timestamp = 1000, seq = 1, type = "WAYPOINT", label = "old")

        vm.editMarker(5, MarkerType.WAYPOINT, "new").join()

        assertTrue(state.decrementedRecents.isEmpty(),
            "editing a marker's label must NOT decrement the old recent label")
    }

    @Test
    fun `seq increments monotonically per session`() = runTest(testDispatcher) {
        val session = SessionEntity(id = 10, name = "Test", createdAt = 0)
        val (repo, state) = newCountingMarkerRepo()
        val vm = buildViewModelWithMarkerRepo(repo)
        stubSessionLoad(session)

        vm.loadSession(10)
        repeat(3) { vm.quickMark().join() }

        val seqs = state.inserted.map { it.seq }
        assertEquals(listOf(1, 2, 3), seqs, "seq must increment monotonically per session")
    }

    @Test
    fun `concurrent quickMark calls do not collide on seq`() = runTest(testDispatcher) {
        val session = SessionEntity(id = 10, name = "Test", createdAt = 0)
        val (repo, state) = newCountingMarkerRepo()
        val vm = buildViewModelWithMarkerRepo(repo)
        stubSessionLoad(session)

        vm.loadSession(10)
        val jobs = (1..50).map { async { vm.quickMark().join() } }
        jobs.awaitAll()

        val seqs = state.inserted.map { it.seq }.sorted()
        assertEquals((1..50).toList(), seqs, "All 50 concurrent quickMark calls must get unique, contiguous seq values")
    }

    @Test
    fun `seq is per-session (two sessions each start at seq=1)`() = runTest(testDispatcher) {
        val sessionA = SessionEntity(id = 10, name = "A", createdAt = 0)
        val sessionB = SessionEntity(id = 20, name = "B", createdAt = 0)
        val (repo, state) = newCountingMarkerRepo()
        val vm = buildViewModelWithMarkerRepo(repo)

        every { sessionRepository.getById(10) } returns flowOf(sessionA)
        every { sessionRepository.getById(20) } returns flowOf(sessionB)
        vm.loadSession(10)
        vm.quickMark().join()
        vm.quickMark().join()
        vm.loadSession(20)
        vm.quickMark().join()

        val seqsBySession = state.inserted.groupBy { it.sessionId }.mapValues { it.value.map { m -> m.seq } }
        assertEquals(listOf(1, 2), seqsBySession[10])
        assertEquals(listOf(1), seqsBySession[20])
    }

    private fun stubSessionLoad(session: SessionEntity) {
        every { sessionRepository.getById(session.id) } returns flowOf(session)
    }

    private fun buildViewModelWithMarkerRepo(markerRepo: SessionMarkerRepository): RecordingViewModel =
        RecordingViewModel(
            sessionRepository = sessionRepository,
            sessionMarkerRepository = markerRepo,
            recentMarkerLabelRepository = recentMarkerLabelRepository,
            recordingMutex = recordingMutex,
            cellInfoCollector = cellInfoCollector,
            configRepository = configRepository,
            stateManager = stateManager,
            indoorPositionCollector = indoorPositionCollector,
            context = context
        )
}

private class CountingRepoState {
    val inserted = mutableListOf<SessionMarkerEntity>()
    val upsertedRecents = mutableListOf<RecentMarkerLabelEntity>()
    val decrementedRecents = mutableListOf<String>()
    val rows = mutableMapOf<Long, SessionMarkerEntity>()
    val maxSeqBySession = mutableMapOf<Long, Int>()
    var nextId = 1L
}

private fun newCountingMarkerRepo(): Pair<SessionMarkerRepository, CountingRepoState> {
    val state = CountingRepoState()
    val repo = mockk<SessionMarkerRepository>(relaxed = true)

    coEvery { repo.insertMarker(any(), any(), any()) } answers {
        val sessionId = firstArg<Long>()
        val type = secondArg<MarkerType>()
        val label = thirdArg<String?>()
        synchronized(state) {
            val seq = (state.maxSeqBySession[sessionId] ?: 0) + 1
            state.maxSeqBySession[sessionId] = seq
            val id = state.nextId++
            val m = SessionMarkerEntity(id = id, sessionId = sessionId, timestamp = System.currentTimeMillis(), seq = seq, type = type.toStorageString(), label = label)
            state.rows[id] = m
            state.inserted += m
            if (!label.isNullOrBlank()) {
                state.upsertedRecents += RecentMarkerLabelEntity(type = type.toStorageString(), label = label, useCount = 1, lastUsed = System.currentTimeMillis())
            }
            id
        }
    }

    coEvery { repo.insertMarkerWithAutoLabel(any(), any()) } answers {
        val sessionId = firstArg<Long>()
        val type = secondArg<MarkerType>()
        synchronized(state) {
            val seq = (state.maxSeqBySession[sessionId] ?: 0) + 1
            state.maxSeqBySession[sessionId] = seq
            val id = state.nextId++
            val now = System.currentTimeMillis()
            val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(now))
            val label = "${type.toStorageString()} #$seq $timeStr"
            val m = SessionMarkerEntity(id = id, sessionId = sessionId, timestamp = now, seq = seq, type = type.toStorageString(), label = label)
            state.rows[id] = m
            state.inserted += m
            state.upsertedRecents += RecentMarkerLabelEntity(type = type.toStorageString(), label = label, useCount = 1, lastUsed = now)
            id
        }
    }

    coEvery { repo.updateMarker(any(), any(), any()) } answers {
        val id = firstArg<Long>()
        val type = secondArg<MarkerType>()
        val label = thirdArg<String?>()
        synchronized(state) {
            val existing = state.rows[id]
            if (existing != null) {
                state.rows[id] = existing.copy(type = type.toStorageString(), label = label)
                if (!label.isNullOrBlank()) {
                    state.upsertedRecents += RecentMarkerLabelEntity(type = type.toStorageString(), label = label, useCount = 1, lastUsed = System.currentTimeMillis())
                }
            }
        }
        Unit
    }

    coEvery { repo.deleteMarker(any()) } answers {
        val id = firstArg<Long>()
        synchronized(state) {
            state.rows.remove(id)?.let { m -> state.decrementedRecents += m.label ?: "" }
        }
        Unit
    }

    every { repo.getMarkersForSession(any()) } returns kotlinx.coroutines.flow.MutableStateFlow(emptyList())

    return repo to state
}
