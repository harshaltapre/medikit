package com.smartmed.dispenser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.smartmed.dispenser.ui.DispenserAppRoot
import com.smartmed.dispenser.ui.DispenserViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: DispenserViewModel
    private var googleSignInClient: GoogleSignInClient? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (!idToken.isNullOrBlank()) {
                    viewModel.signInWithGoogleIdToken(idToken, account.displayName, account.email)
                } else {
                    viewModel.signInWithGoogleFallback(
                        accountName = account.displayName,
                        accountEmail = account.email,
                        reason = "Google session linked (ID token unavailable)"
                    )
                }
            } catch (e: ApiException) {
                when (e.statusCode) {
                    12501 -> viewModel.showError("Google Sign-In was cancelled.")
                    else -> viewModel.signInWithGoogleFallback(
                        accountName = null,
                        accountEmail = null,
                        reason = "OAuth setup pending (code ${e.statusCode}) — opened MediKit profile"
                    )
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[DispenserViewModel::class.java]
        requestNotificationPermissionIfNeeded()
        initGoogleSignIn()

        setContent {
            MaterialTheme {
                Surface {
                    val state by viewModel.uiState.collectAsState()
                    DispenserAppRoot(
                        state = state,
                        viewModel = viewModel,
                        onGoogleSignInClick = { launchGoogleSignIn() },
                        onFullSignOut = {
                            runCatching { googleSignInClient?.signOut() }
                            viewModel.signOut()
                        }
                    )
                }
            }
        }
    }

    private fun initGoogleSignIn() {
        runCatching {
            val resId = resources.getIdentifier("default_web_client_id", "string", packageName)
            val webClientId = if (resId != 0) {
                getString(resId)
            } else {
                "592613800841-ncffjljbfmvo8mgvsg9s5smhe7edvus6.apps.googleusercontent.com"
            }
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
        }
    }

    private fun launchGoogleSignIn() {
        val client = googleSignInClient
        if (client == null) {
            viewModel.signInWithGoogleFallback(null, null, "Signed in with MediKit account")
            return
        }
        runCatching {
            googleSignInLauncher.launch(client.signInIntent)
        }.onFailure {
            viewModel.signInWithGoogleFallback(null, null, "Play Services unavailable — opened MediKit profile")
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
