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
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingViewModelTest {

    companion object {
        private val testDispatcher = UnconfinedTestDispatcher()

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
}
