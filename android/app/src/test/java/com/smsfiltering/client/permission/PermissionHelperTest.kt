package com.smsfiltering.client.permission

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class PermissionHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testPermissionConstants() {
        assertEquals("android.permission.RECEIVE_SMS", PermissionHelper.PERMISSION_RECEIVE_SMS)
        assertEquals("android.permission.POST_NOTIFICATIONS", PermissionHelper.PERMISSION_POST_NOTIFICATIONS)
    }

    @Test
    @Config(sdk = [33])
    fun testRequiredPermissions_Api33_IncludesBothSmsAndNotification() {
        val required = PermissionHelper.getRequiredPermissions()
        assertEquals(2, required.size)
        assertTrue(required.contains(Manifest.permission.RECEIVE_SMS))
        assertTrue(required.contains(Manifest.permission.POST_NOTIFICATIONS))
    }

    @Test
    @Config(sdk = [30])
    fun testRequiredPermissions_Api30_IncludesOnlySms() {
        val required = PermissionHelper.getRequiredPermissions()
        assertEquals(1, required.size)
        assertTrue(required.contains(Manifest.permission.RECEIVE_SMS))
        assertFalse(required.contains(Manifest.permission.POST_NOTIFICATIONS))
    }

    @Test
    @Config(sdk = [33])
    fun testHasReceiveSmsPermission_WhenDeniedAndGranted() {
        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())

        // Başlangıçta izin verilmemiş
        shadowApp.denyPermissions(Manifest.permission.RECEIVE_SMS)
        assertFalse(PermissionHelper.hasReceiveSmsPermission(context))

        // İzin verildiğinde
        shadowApp.grantPermissions(Manifest.permission.RECEIVE_SMS)
        assertTrue(PermissionHelper.hasReceiveSmsPermission(context))
    }

    @Test
    @Config(sdk = [33])
    fun testHasNotificationPermission_Api33_WhenDeniedAndGranted() {
        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())

        // Başlangıçta izin yok
        shadowApp.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(PermissionHelper.hasNotificationPermission(context))

        // İzin verildikten sonra
        shadowApp.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(PermissionHelper.hasNotificationPermission(context))
    }

    @Test
    @Config(sdk = [30])
    fun testHasNotificationPermission_BelowApi33_AlwaysTrue() {
        // Android 13 öncesi için runtime bildirim izni gereksizdir ve true dönmelidir
        assertTrue(PermissionHelper.hasNotificationPermission(context))
    }

    @Test
    @Config(sdk = [33])
    fun testAreAllPermissionsGranted_Api33() {
        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())

        shadowApp.denyPermissions(Manifest.permission.RECEIVE_SMS, Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(PermissionHelper.areAllPermissionsGranted(context))

        shadowApp.grantPermissions(Manifest.permission.RECEIVE_SMS)
        assertFalse(PermissionHelper.areAllPermissionsGranted(context))

        shadowApp.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(PermissionHelper.areAllPermissionsGranted(context))
    }

    @Test
    @Config(sdk = [33])
    fun testGetMissingPermissions_Api33() {
        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())

        shadowApp.denyPermissions(Manifest.permission.RECEIVE_SMS, Manifest.permission.POST_NOTIFICATIONS)
        val missingAll = PermissionHelper.getMissingPermissions(context)
        assertEquals(2, missingAll.size)
        assertTrue(missingAll.contains(Manifest.permission.RECEIVE_SMS))
        assertTrue(missingAll.contains(Manifest.permission.POST_NOTIFICATIONS))

        shadowApp.grantPermissions(Manifest.permission.RECEIVE_SMS)
        val missingNotificationOnly = PermissionHelper.getMissingPermissions(context)
        assertEquals(1, missingNotificationOnly.size)
        assertEquals(Manifest.permission.POST_NOTIFICATIONS, missingNotificationOnly[0])

        shadowApp.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val missingNone = PermissionHelper.getMissingPermissions(context)
        assertTrue(missingNone.isEmpty())
    }
}
