package com.example.filehandler

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.edit

class MainActivity : ComponentActivity() {

    private val viewModel: FileManagerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        forceRefreshLauncherIconIfNeeded(this)

        setContent {
            TvFileManagerApp(viewModel = viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermissionAndLoad()
    }

    private fun forceRefreshLauncherIconIfNeeded(context: Context) {
        try {
            val prefs = context.getSharedPreferences("app_version_prefs", MODE_PRIVATE)
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersionCode = pInfo.longVersionCode.toInt()
            val lastVersionCode = prefs.getInt("last_version_code", -1)

            if (currentVersionCode != lastVersionCode) {
                val componentName = ComponentName(context, MainActivity::class.java)
                val pm = context.packageManager

                // Trigger ACTION_PACKAGE_CHANGED to force Android TV Home Launcher to purge icon cache
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )

                prefs.edit {
                    putInt("last_version_code", currentVersionCode)
                }
            }
        } catch (_: Exception) {
        }
    }
}
