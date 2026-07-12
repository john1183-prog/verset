package com.johndev.verset.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.*
import androidx.glance.layout.*
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
 *
 * Uses hardcoded Verset theme colors rather than GlanceTheme.colors to avoid
 * Glance Material3 API surface differences across library versions.
 */
class VerseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.get(context)
        val entries: List<VerseTagEntry> = db.entryDao().allEntriesFlow().firstOrNull() ?: emptyList()
        val todayKey = System.currentTimeMillis() / 86_400_000L
        val picked: VerseTagEntry? = if (entries.isEmpty()) null
                                     else entries[Random(todayKey).nextInt(entries.size)]

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(androidx.glance.color.ColorProvider(Color(0xFF1B2A4A)))
                    .padding(16.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.TopStart
            ) {
                if (picked == null) {
                    Text(
                        text = "Open Verset and tag a verse to see it here.",
                        style = TextStyle(
                            color = androidx.glance.color.ColorProvider(Color.White),
                            fontSize = 13.sp
                        )
                    )
                } else {
                    Column {
                        Text(
                            text = picked.verseText,
                            style = TextStyle(
                                color = androidx.glance.color.ColorProvider(Color.White),
                                fontSize = 14.sp,
                                fontStyle = FontStyle.Italic
                            ),
                            maxLines = 4
                        )
                        Spacer(GlanceModifier.height(8.dp))
                        Text(
                            text = "— ${picked.book} ${picked.chapter}:${picked.verse}",
                            style = TextStyle(
                                color = androidx.glance.color.ColorProvider(Color(0xFFC9A24B)),
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

class VerseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VerseWidget()
}
