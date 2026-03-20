package com.example.kotlinmod4

import android.content.Context

object WeatherProgressStore {

    @Synchronized
    fun reset(context: Context, allCities: List<String>) {
        context.getSharedPreferences(WeatherKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(WeatherKeys.PREF_ALL_CITIES, allCities.toSet())
            .putStringSet(WeatherKeys.PREF_DONE_CITIES, emptySet())
            .apply()
    }

    @Synchronized
    fun markDone(context: Context, city: String): ProgressSnapshot {
        val prefs = context.getSharedPreferences(WeatherKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val allCities = prefs.getStringSet(WeatherKeys.PREF_ALL_CITIES, emptySet()).orEmpty().toList().sorted()
        val done = prefs.getStringSet(WeatherKeys.PREF_DONE_CITIES, emptySet()).orEmpty().toMutableSet()
        done += city
        prefs.edit().putStringSet(WeatherKeys.PREF_DONE_CITIES, done).apply()
        val doneList = done.toList().sorted()
        val pending = allCities.filterNot { it in done }
        return ProgressSnapshot(doneList, pending, allCities.size)
    }
}

data class ProgressSnapshot(
    val doneCities: List<String>,
    val pendingCities: List<String>,
    val totalCount: Int
)
