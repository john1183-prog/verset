package com.johndev.verset.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class OnboardingPage(val icon: ImageVector, val title: String, val body: String)

private val pages = listOf(
    OnboardingPage(
        Icons.Filled.MenuBook,
        "Read, search, navigate",
        "Tap any book name in the top bar to jump to a different chapter. Use the search icon to find verses by text or by reference — type \"John 3:16\" to go straight there. Previous and Next buttons flow naturally across book boundaries."
    ),
    OnboardingPage(
        Icons.Filled.Bookmark,
        "Build your own library",
        "Tap any verse and choose Tag / classify… to add it to a category you create — like \"Promise\", \"Comfort\", or anything that fits how you think. A verse can have multiple tags, each with its own note. Find everything you've saved in the My Verses tab."
    ),
    OnboardingPage(
        Icons.Filled.Share,
        "Share without friction",
        "Tap any verse and choose Share as text to send it immediately. Your verse gets automatically added to a \"Shared\" tag so you always have a record of what you've shared. Export a full tagged list as an image card or PDF from My Verses."
    )
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    val current = pages[page]

    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(40.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    current.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(current.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(current.body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Page dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == page) 10.dp else 8.dp)
                            .background(
                                if (i == page) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape
                            )
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            if (page < pages.size - 1) {
                Button(onClick = { page++ }, modifier = Modifier.fillMaxWidth()) { Text("Next") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
            } else {
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
            }
        }
    }
}
