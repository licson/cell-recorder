package com.cellrecorder.app.ui

import android.Manifest
import androidx.core.app.ActivityCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.rule.GrantPermissionRule
import com.cellrecorder.app.HiltTestActivity
import com.cellrecorder.app.ui.shared.PermissionHelper
import com.cellrecorder.app.ui.shared.PermissionUiState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@MediumTest
class RecordingScreenPermissionTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val grantForegroundPermissions = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
    )

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun existingUser_indoorStart_neverAskedActivityRecognition_showsRationaleNotSettings() {
        ActivityScenario.launch(HiltTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val missing = arrayOf(Manifest.permission.ACTIVITY_RECOGNITION)

                assertFalse(
                    "Pre-condition: never-asked permission should return false from shouldShowRequestPermissionRationale",
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACTIVITY_RECOGNITION)
                )

                val state = PermissionHelper.decidePermissionState(
                    hasAttemptedOnce = false,
                    missingPermissions = missing,
                    activity = activity
                )
                assertEquals(
                    "Never-asked permission should show rationale, not Settings (the reported bug fix)",
                    PermissionUiState.ShowRationale,
                    state
                )
            }
        }
    }

    @Test
    fun existingUser_indoorStart_missingAllForMode_includesActivityRecognition() {
        ActivityScenario.launch(HiltTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val missing = PermissionHelper.missingAllForMode("INDOOR", activity)
                assertTrue(
                    "missingAllForMode for indoor should include ACTIVITY_RECOGNITION",
                    missing.contains(Manifest.permission.ACTIVITY_RECOGNITION)
                )
            }
        }
    }
}
