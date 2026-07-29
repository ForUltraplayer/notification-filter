package ru.krestnikov.pushfilter.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

class AppInfoProvider(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val labels = ConcurrentHashMap<String, String>()
    private val icons = ConcurrentHashMap<String, Bitmap>()

    fun label(packageName: String): String = labels.getOrPut(packageName) {
        runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0),
            ).toString()
        }.getOrDefault(packageName)
    }

    /** Returns null when the package is gone (uninstalled app still present in the log). */
    fun icon(packageName: String): Bitmap? {
        icons[packageName]?.let { return it }
        val bitmap = runCatching {
            packageManager.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX)
        }.getOrNull() ?: return null
        icons[packageName] = bitmap
        return bitmap
    }

    suspend fun installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        packageManager.getInstalledApplications(0)
            .map { info ->
                val label = runCatching { packageManager.getApplicationLabel(info).toString() }
                    .getOrDefault(info.packageName)
                labels[info.packageName] = label
                InstalledApp(
                    packageName = info.packageName,
                    label = label,
                    isSystem = info.isSystemApp(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun ApplicationInfo.isSystemApp(): Boolean =
        (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0

    private companion object {
        const val ICON_PX = 128
    }
}
