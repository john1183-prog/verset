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
import androidx.glance.unit.ColorProvider
import com.johndev.verset.MainActivity
import com.johndev.verset.data.AppDatabase
import com.johndev.verset.data.VerseTagEntry
import kotlinx.coroutines.flow.firstOrNull
import kotlin.random.Random

class VerseWidget : GlanceAppWidget() {

    private val bgColor = ColorProvider(Color(0xFF1B2A4A))
    private val textColor = ColorProvider(Color.White)
    private val accentColor = ColorProvider(Color(0xFFC9A24B))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.get(context)
        val entries: List<VerseTagEntry> = db.entryDao().allEntriesFlow().firstOrNull() ?: emptyList()
        val todayKey = System.currentTimeMillis() / 86_400_000L
        val picked: VerseTagEntry? = if (entries.isEmpty()) null
                                     else entries[Random(todayKey).nextInt(entries.size)]

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(16.dp)
                    .clickable(actionStartActivity<MainActivity>())
            ) {
                if (picked == null) {
                    Text(
                        text = "Open Verset and tag a verse to see it here.",
                        style = TextStyle(color = textColor, fontSize = 13.sp)
                    )
                } else {
                    Text(
                        text = picked.verseText,
                        style = TextStyle(
                            color = textColor,
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic
                        ),
                        maxLines = 4
                    )
                    Spacer(GlanceModifier.height(8.dp))
                    Text(
                        text = "— ${picked.book} ${picked.chapter}:${picked.verse}",
                        style = TextStyle(
                            color = accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

class VerseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VerseWidget()
}
