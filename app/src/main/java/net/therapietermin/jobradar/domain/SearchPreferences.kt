package net.therapietermin.jobradar.domain

import android.content.Context

object SearchPreferences {
    private const val PREFS = "jobradar_search"
    private const val KEY_RADIUS = "radius_km"

    fun getRadius(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_RADIUS, 50)
            .takeIf { it in listOf(25, 50, 75, 100) }
            ?: 50

    fun setRadius(context: Context, radius: Int) {
        if (radius !in listOf(25, 50, 75, 100)) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_RADIUS, radius)
            .apply()
    }
}
