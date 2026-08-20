package com.liftpath.helpers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Gets a Drive access token through Play Services' Authorization API rather than a classic
 * OAuth flow — there is no client secret or server to hold one, and no Play review beyond the
 * standard consent-screen verification every Drive-scoped app needs. The OAuth client itself is
 * an "Android" type client in Google Cloud Console, tied to LiftPath's package name and signing
 * certificate rather than to any string in this file (see GOOGLE_DRIVE_SETUP.md).
 *
 * Scoped to `drive.file`: LiftPath can only see and manage files it created itself, never the
 * rest of the user's Drive.
 */
object DriveAuthHelper {

    private val SCOPE = Scope("https://www.googleapis.com/auth/drive.file")

    sealed class AuthOutcome {
        data class Token(val accessToken: String) : AuthOutcome()
        data class NeedsConsent(val pendingIntent: PendingIntent) : AuthOutcome()
        data class Failure(val error: Exception) : AuthOutcome()
    }

    /**
     * Tries for a token silently first. Once the user has granted the scope once, this succeeds
     * with no UI — which is what lets the background worker back up without a prompt.
     */
    suspend fun authorize(context: Context): AuthOutcome = try {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(SCOPE))
            .build()
        outcomeFrom(requestAuthorization(context, request))
    } catch (e: Exception) {
        AuthOutcome.Failure(e)
    }

    /** Extracts the token from the consent Activity's result. */
    fun tokenFromConsentResult(context: Context, data: Intent?): AuthOutcome = try {
        outcomeFrom(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
    } catch (e: Exception) {
        AuthOutcome.Failure(e)
    }

    /**
     * Clears LiftPath's local record of the grant. The scope itself is Google's to revoke (from
     * the user's account permissions page) — there is nothing else to undo on-device.
     */
    fun disconnect(context: Context) {
        BackupSettingsManager(context).apply {
            driveEnabled = false
            lastDriveError = null
            driveFolderId = null
        }
    }

    private suspend fun requestAuthorization(
        context: Context,
        request: AuthorizationRequest
    ): AuthorizationResult = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context)
            .authorize(request)
            .addOnSuccessListener { cont.resumeWith(Result.success(it)) }
            .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
    }

    private fun outcomeFrom(result: AuthorizationResult): AuthOutcome {
        val pendingIntent = result.pendingIntent
        return when {
            result.hasResolution() && pendingIntent != null -> AuthOutcome.NeedsConsent(pendingIntent)
            result.accessToken != null -> AuthOutcome.Token(result.accessToken!!)
            else -> AuthOutcome.Failure(IllegalStateException("Authorization returned no token"))
        }
    }
}
