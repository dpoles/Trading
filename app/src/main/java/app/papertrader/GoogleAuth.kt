package app.papertrader

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

/**
 * Ottiene un access token OAuth (sola lettura su Sheets) con Google Identity Services.
 * Se serve il consenso dell'utente chiama [launch] con la richiesta da mostrare.
 */
object GoogleAuth {
    private fun request() = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SHEETS_SCOPE)))
        .build()

    fun authorize(
        context: Context,
        launch: (IntentSenderRequest) -> Unit,
        onToken: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        Identity.getAuthorizationClient(context)
            .authorize(request())
            .addOnSuccessListener { result ->
                val token = result.accessToken
                val pending = result.pendingIntent
                when {
                    result.hasResolution() && pending != null ->
                        launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    token != null -> onToken(token)
                    else -> onError("Autorizzazione Google non riuscita")
                }
            }
            .addOnFailureListener { onError(it.message ?: "Autorizzazione Google non riuscita") }
    }

    fun tokenFromResult(context: Context, resultCode: Int, data: Intent?): String? {
        if (resultCode != Activity.RESULT_OK || data == null) return null
        return runCatching {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken
        }.getOrNull()
    }
}
