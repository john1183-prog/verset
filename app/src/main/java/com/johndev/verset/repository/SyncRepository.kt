package com.johndev.verset.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.johndev.verset.data.AppDatabase
import com.johndev.verset.data.Tag
import com.johndev.verset.data.VerseTagEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * Simple last-write-wins sync: pushes locally-dirty entries/tags to
 * users/{uid}/entries and users/{uid}/tags, then pulls the full remote set
 * back down. Good enough for a single-user, few-thousand-verse workload.
 */
class SyncRepository(private val db: AppDatabase) {

    private val firestore get() = FirebaseFirestore.getInstance()
    private fun uid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    suspend fun syncNow(): Result<Unit> {
        val userId = uid() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            pushTags(userId)
            pushEntries(userId)
            pullEntries(userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun pushTags(userId: String) {
        val tagsCol = firestore.collection("users").document(userId).collection("tags")
        val localTags = db.tagDao().allTags()
        val snapshot = localTags.first()
        for (tag in snapshot) {
            tagsCol.document(tag.id.toString()).set(tag).await()
        }
    }

    private suspend fun pushEntries(userId: String) {
        val dirty = db.entryDao().dirtyEntries()
        if (dirty.isEmpty()) return
        val entriesCol = firestore.collection("users").document(userId).collection("entries")
        for (entry in dirty) {
            val docId = entry.remoteId ?: entry.id.toString()
            entriesCol.document(docId).set(entry).await()
            db.entryDao().update(entry.copy(remoteId = docId, dirty = false))
        }
    }

    private suspend fun pullEntries(userId: String) {
        val snapshot = firestore.collection("users").document(userId).collection("entries").get().await()
        val local = db.entryDao().allEntriesOnce().associateBy { it.remoteId }
        for (doc in snapshot.documents) {
            val remote = doc.toObject(VerseTagEntry::class.java) ?: continue
            val existing = local[doc.id]
            if (existing == null) {
                db.entryDao().insert(remote.copy(id = 0, remoteId = doc.id, dirty = false))
            } else if (remote.updatedAt > existing.updatedAt) {
                db.entryDao().update(remote.copy(id = existing.id, remoteId = doc.id, dirty = false))
            }
        }
    }
}
