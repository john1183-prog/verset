package com.johndev.verset.widget

import android.content.Context
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.*
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
import androidx.glance.text.*
import com.johndev.verset.MainActivity
import com.johndev.verset.data.AppDatabase
import com.johndev.verset.data.VerseTagEntry
import kotlinx.coroutines.flow.firstOrNull
import kotlin.random.Random

/**
 * Home-screen widget showing the verse of the day from the user's saved verses.
 * Tapping it opens the app. Updates once per day via the system's widget update
 * mechanism (defined in widget_info.xml).
 */
class VerseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.get(context)
        val entries: List<VerseTagEntry> = db.entryDao().allEntriesFlow().firstOrNull() ?: emptyList()
        val todayKey = System.currentTimeMillis() / 86_400_000L
        val picked = if (entries.isEmpty()) null
                     else entries[Random(todayKey).nextInt(entries.size)]

        provideContent {
            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.primaryContainer)
                        .padding(16.dp)
                        .clickable(actionStartActivity<MainActivity>())
                ) {
                    if (picked == null) {
                        Text(
                            "Open Verset and tag a verse to see it here.",
                            style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer)
                        )
                    } else {
                        Column {
                            Text(
                                picked.verseText,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onPrimaryContainer,
                                    fontSize = 14.sp,
                                    fontStyle = FontStyle.Italic
                                ),
                                maxLines = 4
                            )
                            Spacer(GlanceModifier.height(8.dp))
                            Text(
                                "— ${picked.book} ${picked.chapter}:${picked.verse}",
                                style = TextStyle(
                                    color = GlanceTheme.colors.secondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

class VerseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VerseWidget()
}
