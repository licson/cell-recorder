package com.cellrecorder.app.ui.shared

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PermissionHelperTest {

    @Nested
    @DisplayName("PermissionHelper.decidePermissionState")
    inner class DecidePermissionState {

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

    @Nested
    @DisplayName("PermissionHelper permission arrays")
    inner class PermissionArrays {

        /**
         * Default JVM test stubs report `Build.VERSION.SDK_INT = 0`, so the TIRAMISU branch
         * (`SDK_INT >= 33`) does not add `POST_NOTIFICATIONS`. Testing that branch requires
         * Robolectric or reflection on the static final field (blocked on JDK 17+); defer to a
         * future Robolectric-enabled change. The default case is characterized here.
         */
        @Test
        fun `foregroundPermissions excludes POST_NOTIFICATIONS when SDK_INT is below TIRAMISU`() {
            val perms = PermissionHelper.foregroundPermissions().toList()
            assertTrue(perms.contains(Manifest.permission.ACCESS_FINE_LOCATION))
            assertTrue(perms.contains(Manifest.permission.READ_PHONE_STATE))
            assertFalse(perms.contains(Manifest.permission.POST_NOTIFICATIONS),
                "POST_NOTIFICATIONS not added when SDK_INT < TIRAMISU (default JVM stub is 0)")
        }

        @Test
        fun `backgroundPermissions always returns ACCESS_BACKGROUND_LOCATION`() {
            val perms = PermissionHelper.backgroundPermissions().toList()
            assertEquals(1, perms.size)
            assertEquals(Manifest.permission.ACCESS_BACKGROUND_LOCATION, perms[0])
        }

        @Test
        fun `indoorPermissions always returns ACTIVITY_RECOGNITION`() {
            val perms = PermissionHelper.indoorPermissions().toList()
            assertEquals(1, perms.size)
            assertEquals(Manifest.permission.ACTIVITY_RECOGNITION, perms[0])
        }

        @Test
        fun `requiredPermissions is the union of foreground and background permissions`() {
            val perms = PermissionHelper.requiredPermissions().toSet()
            val expected = (PermissionHelper.foregroundPermissions() + PermissionHelper.backgroundPermissions()).toSet()
            assertEquals(expected, perms)
            assertTrue(perms.contains(Manifest.permission.ACCESS_FINE_LOCATION))
            assertTrue(perms.contains(Manifest.permission.READ_PHONE_STATE))
            assertTrue(perms.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        }
    }

    @Nested
    @DisplayName("PermissionHelper Context-based queries")
    inner class ContextBasedQueries {

        private val context = mockkContext()

        @BeforeEach
        fun setUp() {
            mockkStatic(ContextCompat::class)
        }

        @AfterEach
        fun tearDown() {
            unmockkStatic(ContextCompat::class)
        }

        private fun stubAllGranted() {
            every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        }

        private fun stubDenied(vararg perms: String) {
            every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
            perms.forEach { p ->
                every { ContextCompat.checkSelfPermission(any(), eq(p)) } returns PackageManager.PERMISSION_DENIED
            }
        }

        @Test
        fun `allGranted returns true when all required permissions are granted`() {
            stubAllGranted()
            assertTrue(PermissionHelper.allGranted(context))
        }

        @Test
        fun `allGranted returns false when any required permission is denied`() {
            stubDenied(Manifest.permission.ACCESS_FINE_LOCATION)
            assertFalse(PermissionHelper.allGranted(context))
        }

        @Test
        fun `allForegroundGranted returns true when all foreground permissions are granted`() {
            stubAllGranted()
            assertTrue(PermissionHelper.allForegroundGranted(context))
        }

        @Test
        fun `allForegroundGranted returns false when any foreground permission is denied`() {
            stubDenied(Manifest.permission.READ_PHONE_STATE)
            assertFalse(PermissionHelper.allForegroundGranted(context))
        }

        @Test
        fun `allBackgroundGranted returns true when ACCESS_BACKGROUND_LOCATION is granted`() {
            stubAllGranted()
            assertTrue(PermissionHelper.allBackgroundGranted(context))
        }

        @Test
        fun `allBackgroundGranted returns false when ACCESS_BACKGROUND_LOCATION is denied`() {
            stubDenied(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            assertFalse(PermissionHelper.allBackgroundGranted(context))
        }

        @Test
        fun `allIndoorGranted returns true when ACTIVITY_RECOGNITION is granted`() {
            stubAllGranted()
            assertTrue(PermissionHelper.allIndoorGranted(context))
        }

        @Test
        fun `allIndoorGranted returns false when ACTIVITY_RECOGNITION is denied`() {
            stubDenied(Manifest.permission.ACTIVITY_RECOGNITION)
            assertFalse(PermissionHelper.allIndoorGranted(context))
        }

        @Test
        fun `allGrantedForMode OUTDOOR returns true when all foreground and background are granted`() {
            stubAllGranted()
            assertTrue(PermissionHelper.allGrantedForMode("OUTDOOR", context))
        }

        @Test
        fun `allGrantedForMode TUNNEL returns true when all foreground and background are granted`() {
            stubAllGranted()
            assertTrue(PermissionHelper.allGrantedForMode("TUNNEL", context))
        }

        @Test
        fun `allGrantedForMode TUNNEL returns true even if indoor permission is missing`() {
            stubAllGranted()
            stubDenied(Manifest.permission.ACTIVITY_RECOGNITION)
            assertTrue(PermissionHelper.allGrantedForMode("TUNNEL", context))
        }

        @Test
        fun `allGrantedForMode TUNNEL returns true when background permission is missing (tunnel excludes background)`() {
            stubAllGranted()
            stubDenied(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            assertTrue(PermissionHelper.allGrantedForMode("TUNNEL", context),
                "Tunnel mode excludes ACCESS_BACKGROUND_LOCATION per tunnel/spec.md")
        }

        @Test
        fun `allGrantedForMode INDOOR returns false when indoor permission is missing`() {
            stubDenied(Manifest.permission.ACTIVITY_RECOGNITION)
            assertFalse(PermissionHelper.allGrantedForMode("INDOOR", context))
        }

        @Test
        fun `allGrantedForMode OUTDOOR returns false when background permission is missing`() {
            stubDenied(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            assertFalse(PermissionHelper.allGrantedForMode("OUTDOOR", context))
        }

        @Test
        fun `missingForegroundPermissions returns empty array when all granted`() {
            stubAllGranted()
            assertEquals(0, PermissionHelper.missingForegroundPermissions(context).size)
        }

        @Test
        fun `missingForegroundPermissions returns denied permissions`() {
            stubDenied(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE)
            val missing = PermissionHelper.missingForegroundPermissions(context).toSet()
            assertTrue(missing.contains(Manifest.permission.ACCESS_FINE_LOCATION))
            assertTrue(missing.contains(Manifest.permission.READ_PHONE_STATE))
        }

        @Test
        fun `missingIndoorPermissions returns empty array when granted`() {
            stubAllGranted()
            assertEquals(0, PermissionHelper.missingIndoorPermissions(context).size)
        }

        @Test
        fun `missingIndoorPermissions returns ACTIVITY_RECOGNITION when denied`() {
            stubDenied(Manifest.permission.ACTIVITY_RECOGNITION)
            val missing = PermissionHelper.missingIndoorPermissions(context).toList()
            assertEquals(1, missing.size)
            assertEquals(Manifest.permission.ACTIVITY_RECOGNITION, missing[0])
        }

        @Test
        fun `missingBackgroundPermissions returns empty array when granted`() {
            stubAllGranted()
            assertEquals(0, PermissionHelper.missingBackgroundPermissions(context).size)
        }

        @Test
        fun `missingBackgroundPermissions returns ACCESS_BACKGROUND_LOCATION when denied`() {
            stubDenied(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            val missing = PermissionHelper.missingBackgroundPermissions(context).toList()
            assertEquals(1, missing.size)
            assertEquals(Manifest.permission.ACCESS_BACKGROUND_LOCATION, missing[0])
        }

        @Test
        fun `missingPermissionsForMode returns foreground and indoor missing for INDOOR mode`() {
            stubDenied(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACTIVITY_RECOGNITION)
            val missing = PermissionHelper.missingPermissionsForMode("INDOOR", context).toSet()
            assertTrue(missing.contains(Manifest.permission.ACCESS_FINE_LOCATION))
            assertTrue(missing.contains(Manifest.permission.ACTIVITY_RECOGNITION))
        }

        @Test
        fun `missingPermissionsForMode returns foreground missing for OUTDOOR mode`() {
            stubDenied(Manifest.permission.ACCESS_FINE_LOCATION)
            val missing = PermissionHelper.missingPermissionsForMode("OUTDOOR", context).toSet()
            assertTrue(missing.contains(Manifest.permission.ACCESS_FINE_LOCATION))
            assertFalse(missing.contains(Manifest.permission.ACTIVITY_RECOGNITION),
                "Indoor permissions excluded from OUTDOOR mode")
        }

        @Test
        fun `missingPermissionsForMode returns foreground missing for TUNNEL mode (excludes FINE_LOCATION and ACTIVITY_RECOGNITION)`() {
            stubDenied(Manifest.permission.READ_PHONE_STATE)
            val missing = PermissionHelper.missingPermissionsForMode("TUNNEL", context).toSet()
            assertTrue(missing.contains(Manifest.permission.READ_PHONE_STATE),
                "Tunnel mode requires READ_PHONE_STATE")
            assertFalse(missing.contains(Manifest.permission.ACCESS_FINE_LOCATION),
                "Tunnel mode excludes ACCESS_FINE_LOCATION per tunnel/spec.md")
            assertFalse(missing.contains(Manifest.permission.ACTIVITY_RECOGNITION),
                "Indoor permissions excluded from TUNNEL mode")
        }

        @Test
        fun `missingAllForMode INDOOR includes foreground indoor and background missing`() {
            stubDenied(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACTIVITY_RECOGNITION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
            val missing = PermissionHelper.missingAllForMode("INDOOR", context).toSet()
            assertTrue(missing.contains(Manifest.permission.ACCESS_FINE_LOCATION))
            assertTrue(missing.contains(Manifest.permission.ACTIVITY_RECOGNITION))
            assertTrue(missing.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        }

        @Test
        fun `missingAllForMode OUTDOOR does not include indoor missing permissions`() {
            stubDenied(Manifest.permission.ACTIVITY_RECOGNITION)
            val missing = PermissionHelper.missingAllForMode("OUTDOOR", context).toSet()
            assertFalse(missing.contains(Manifest.permission.ACTIVITY_RECOGNITION),
                "Indoor permissions excluded from OUTDOOR mode")
        }

        @Test
        fun `missingAllForMode OUTDOOR includes foreground and background missing`() {
            stubDenied(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            val missing = PermissionHelper.missingAllForMode("OUTDOOR", context).toSet()
            assertTrue(missing.contains(Manifest.permission.READ_PHONE_STATE))
            assertTrue(missing.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        }

        @Test
        fun `missingAllForMode TUNNEL does not include indoor missing permissions`() {
            stubDenied(Manifest.permission.ACTIVITY_RECOGNITION)
            val missing = PermissionHelper.missingAllForMode("TUNNEL", context).toSet()
            assertFalse(missing.contains(Manifest.permission.ACTIVITY_RECOGNITION),
                "Indoor permissions excluded from TUNNEL mode")
        }

        @Test
        fun `missingAllForMode TUNNEL includes foreground missing but excludes background and location (tunnel spec)`() {
            stubDenied(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            val missing = PermissionHelper.missingAllForMode("TUNNEL", context).toSet()
            assertTrue(missing.contains(Manifest.permission.READ_PHONE_STATE),
                "Tunnel mode requires READ_PHONE_STATE")
            assertFalse(missing.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                "Tunnel mode excludes ACCESS_BACKGROUND_LOCATION per tunnel/spec.md")
            assertFalse(missing.contains(Manifest.permission.ACCESS_FINE_LOCATION),
                "Tunnel mode excludes ACCESS_FINE_LOCATION per tunnel/spec.md")
        }

        @Test
        fun `missingAllForMode returns empty array when all permissions granted`() {
            stubAllGranted()
            assertEquals(0, PermissionHelper.missingAllForMode("OUTDOOR", context).size)
            assertEquals(0, PermissionHelper.missingAllForMode("INDOOR", context).size)
            assertEquals(0, PermissionHelper.missingAllForMode("TUNNEL", context).size)
        }
    }
}

private fun mockkContext(): Context {
    return io.mockk.mockk(relaxed = true)
}
