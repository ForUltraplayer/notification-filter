package ru.krestnikov.pushfilter.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Applications the listener skips entirely: nothing is filtered and nothing is logged.
 * Checked before any other work, so a chatty messenger costs next to nothing.
 */
class IgnoreRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("push_filter_ignored", Context.MODE_PRIVATE)

    private val state = MutableStateFlow(read())

    val ignored: StateFlow<Set<String>> = state.asStateFlow()

    fun contains(packageName: String): Boolean = state.value.contains(packageName)

    fun add(packageName: String) = write(state.value + packageName)

    fun remove(packageName: String) = write(state.value - packageName)

    fun replaceAll(packages: Set<String>) = write(packages)

    private fun write(next: Set<String>) {
        prefs.edit { putStringSet(KEY, next) }
        state.value = next
    }

    // getStringSet returns an instance that must not be mutated or kept, hence the copy.
    private fun read(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet().orEmpty()

    private companion object {
        const val KEY = "ignored_packages"
    }
}
