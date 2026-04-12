package com.silenceofthelambda.dergplayer.api

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.youtube.YouTubeScopes
import androidx.core.content.edit

class AuthManager(private val context: Context) {
    private val SCOPES = listOf(YouTubeScopes.YOUTUBE)
    private val PREFS_NAME = "DergPlayerPrefs"
    private val KEY_ACCOUNT_NAME = "accountName"
    
    val credential = GoogleAccountCredential.usingOAuth2(context, SCOPES)

    init {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val accountName = prefs.getString(KEY_ACCOUNT_NAME, null)
        if (accountName != null) {
            credential.selectedAccountName = accountName
        }
    }

    fun setAccountName(accountName: String) {
        credential.selectedAccountName = accountName
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_ACCOUNT_NAME, accountName)
            }
    }

    fun isAuthorized(): Boolean {
        return credential.selectedAccountName != null
    }

    fun getPickAccountIntent(): Intent {
        return credential.newChooseAccountIntent()
    }
}
