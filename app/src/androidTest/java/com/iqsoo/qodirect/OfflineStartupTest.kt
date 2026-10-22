package com.iqsoo.qodirect

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Does not write charging settings. Can run on a plain API36 emulator or unprivileged physical
 * phone.
 */
@RunWith(AndroidJUnit4::class)
class OfflineStartupTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShowsRealControlsWithoutShizuku() {
        ui.onNodeWithText("让供电，顺应使用").assertIsDisplayed()
        ui.onNodeWithText("智能管理").assertIsDisplayed()
    }

    @Test
    fun ruleEditorIsInteractive() {
        ui.onNodeWithText("规则").performClick()
        ui.onNodeWithText("创建规则").performClick()
        ui.onNodeWithText("编辑智能规则").assertIsDisplayed()
        ui.onNodeWithText("取消").performClick()
    }

    @Test
    fun targetSdkAndNoNetworkOrSensorPermissions() {
        val c = ui.activity
        assertEquals(36, c.applicationInfo.targetSdkVersion)
        val permissions =
            c.packageManager
                .getPackageInfo(c.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                ?.toSet()
                .orEmpty()
        assertFalse(Manifest.permission.INTERNET in permissions)
        assertFalse(Manifest.permission.CAMERA in permissions)
        assertFalse(Manifest.permission.RECORD_AUDIO in permissions)
        assertFalse(Manifest.permission.ACCESS_FINE_LOCATION in permissions)
    }
}
