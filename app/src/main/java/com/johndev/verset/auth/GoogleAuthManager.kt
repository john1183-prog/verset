package com.johndev.verset.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Google Sign-In via Credential Manager -> Firebase Auth.
 *
 * WEB_CLIENT_ID must be replaced with the "Web client" OAuth client ID from your
 * Firebase project (Firebase console -> Authentication -> Sign-in method -> Google
 * -> Web SDK configuration). This is NOT the Android client ID.
 */
object GoogleAuthManager {

    private const val WEB_CLIENT_ID = "REPLACE_WITH_YOUR_FIREBASE_WEB_CLIENT_ID"

    suspend fun signIn(context: Context): Result<Unit> {
        return try {
            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
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

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
    }

    fun currentUserId(): String? = FirebaseAuth.getInstance().currentUser?.uid
    fun isSignedIn(): Boolean = FirebaseAuth.getInstance().currentUser != null
}
