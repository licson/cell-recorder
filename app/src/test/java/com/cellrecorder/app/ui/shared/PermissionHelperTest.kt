package com.cellrecorder.app.ui.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PermissionHelper.decidePermissionState")
class PermissionHelperTest {

    private val anyPermission = arrayOf("android.permission.ACTIVITY_RECOGNITION")

    @Nested
    @DisplayName("when all permissions are granted (missing is empty)")
    inner class AllGranted {
        @Test
        fun `returns AllGranted regardless of hasAttemptedOnce or rationale`() {
            assertEquals(
                PermissionUiState.AllGranted,
                PermissionHelper.decidePermissionState(
                    hasAttemptedOnce = false,
                    missingPermissions = emptyArray(),
                    rationaleProvider = { false }
                )
            )
            assertEquals(
                PermissionUiState.AllGranted,
                PermissionHelper.decidePermissionState(
                    hasAttemptedOnce = true,
                    missingPermissions = emptyArray(),
                    rationaleProvider = { true }
                )
            )
        }
    }

    @Nested
    @DisplayName("when permission is missing and never asked before (hasAttemptedOnce=false, anyRationale=false)")
    inner class NeverAsked {
        @Test
        fun `returns ShowRationale so the in-app dialog primes the user before the system request`() {
            assertEquals(
                PermissionUiState.ShowRationale,
                PermissionHelper.decidePermissionState(
                    hasAttemptedOnce = false,
                    missingPermissions = anyPermission,
                    rationaleProvider = { false }
                )
            )
        }
    }

    @Nested
    @DisplayName("when permission is missing and denied once (anyRationale=true)")
    inner class DeniedOnce {
        @Test
        fun `returns ShowRationale so the user can retry`() {
            assertEquals(
                PermissionUiState.ShowRationale,
                PermissionHelper.decidePermissionState(
                    hasAttemptedOnce = false,
                    missingPermissions = anyPermission,
                    rationaleProvider = { true }
                )
            )
        }

        @Test
        fun `returns ShowRationale even after a previous attempt in this session`() {
            assertEquals(
                PermissionUiState.ShowRationale,
                PermissionHelper.decidePermissionState(
                    hasAttemptedOnce = true,
                    missingPermissions = anyPermission,
                    rationaleProvider = { true }
                )
            )
        }
    }

    @Nested
    @DisplayName("when permission is permanently denied (hasAttemptedOnce=true, anyRationale=false)")
    inner class PermanentlyDenied {
        @Test
        fun `returns ShowSettings`() {
            assertEquals(
                PermissionUiState.ShowSettings,
                PermissionHelper.decidePermissionState(
                    hasAttemptedOnce = true,
                    missingPermissions = anyPermission,
                    rationaleProvider = { false }
                )
            )
        }
    }

    @Nested
    @DisplayName("with multiple missing permissions")
    inner class MultiplePermissions {
        private val twoPermissions = arrayOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACTIVITY_RECOGNITION"
        )

        @Test
        fun `returns ShowRationale if any permission has rationale`() {
            assertEquals(
                PermissionUiState.ShowRationale,
                PermissionHelper.decidePermissionState(
                    hasAttemptedOnce = true,
                    missingPermissions = twoPermissions,
                    rationaleProvider = { perm -> perm == "android.permission.ACCESS_FINE_LOCATION" }
                )
            )
        }

        @Test
        fun `returns ShowSettings if no permission has rationale and hasAttemptedOnce`() {
            assertEquals(
                PermissionUiState.ShowSettings,
                PermissionHelper.decidePermissionState(
                    hasAttemptedOnce = true,
                    missingPermissions = twoPermissions,
                    rationaleProvider = { false }
                )
            )
        }

        @Test
        fun `returns ShowRationale if no permission has rationale but never attempted`() {
            assertEquals(
                PermissionUiState.ShowRationale,
                PermissionHelper.decidePermissionState(
                    hasAttemptedOnce = false,
                    missingPermissions = twoPermissions,
                    rationaleProvider = { false }
                )
            )
        }
    }
}
