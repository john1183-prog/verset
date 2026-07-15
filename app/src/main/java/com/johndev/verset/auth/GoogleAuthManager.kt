package com.johndev.verset.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.johndev.verset.data.Prefs
import kotlinx.coroutines.tasks.await

object GoogleAuthManager {

    /**
     * Returns true if the bundled google-services.json is still the placeholder.
     * Detected by checking the Firebase project ID at runtime — lets the UI show
     * a clear error instead of a cryptic sign-in failure.
     */
    fun isPlaceholderConfig(): Boolean {
        return try {
            FirebaseApp.getInstance().options.projectId == "verset-placeholder"
        } catch (e: Exception) {
            true
        }
    }

    suspend fun signIn(context: Context, webClientId: String): Result<Unit> {
        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(context, request)
            val googleIdCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdCredential.idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
            Result.success(Unit)
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() = FirebaseAuth.getInstance().signOut()
    fun currentUserId(): String? = FirebaseAuth.getInstance().currentUser?.uid
    fun isSignedIn(): Boolean = FirebaseAuth.getInstance().currentUser != null
}
