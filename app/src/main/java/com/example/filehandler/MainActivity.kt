package com.example.filehandler

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.edit

/**
 * [MainActivity] serves as the primary entry point for the Android TV File Manager application.
 *
 * Configured as a LEANBACK_LAUNCHER activity in AndroidManifest.xml, it initializes the MVVM
 * [FileManagerViewModel], handles launcher icon cache purges on version upgrades, and renders
 * the main Jetpack Compose TV UI tree ([TvFileManagerApp]).
 */
class MainActivity : ComponentActivity() {

    // ViewModel instance scoped to Activity lifecycle
    private val viewModel: FileManagerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force Android TV Home Launcher to refresh icon/banner cache if app was recently upgraded
        forceRefreshLauncherIconIfNeeded(this)

        // Render main Jetpack Compose for TV UI Tree
        setContent {
            TvFileManagerApp(viewModel = viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check storage permissions and refresh current folder whenever activity resumes
        viewModel.checkPermissionAndLoad()
    }

    /**
     * Programmatically forces the Android TV Home Launcher to purge its cached thumbnail for
     * the app's banner and icon whenever a new versionCode build is launched for the first time.
     *
     * @param context Application context used for SharedPreferences and PackageManager access.
     */
    private fun forceRefreshLauncherIconIfNeeded(context: Context) {
        try {
            val prefs = context.getSharedPreferences("app_version_prefs", MODE_PRIVATE)
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersionCode = pInfo.longVersionCode.toInt()
            val lastVersionCode = prefs.getInt("last_version_code", -1)

            // If versionCode has been incremented since last run
            if (currentVersionCode != lastVersionCode) {
                val componentName = ComponentName(context, MainActivity::class.java)
                val pm = context.packageManager

                // Briefly toggle activity component state with DONT_KILL_APP
                // This triggers ACTION_PACKAGE_CHANGED broadcast, forcing the TV launcher to invalidate old cached thumbnails
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

                // Persist current version code to preferences
                prefs.edit {
                    putInt("last_version_code", currentVersionCode)
                }
            }
        } catch (_: Exception) {
            // Silently ignore if preference write or package info query fails
        }
    }
}
