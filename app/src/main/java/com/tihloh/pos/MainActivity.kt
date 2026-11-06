package com.tihloh.pos

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.tihloh.pos.security.PinStore
import com.tihloh.pos.ui.PosRoot
import com.tihloh.pos.ui.theme.PosTheme
import com.tihloh.pos.ui.theme.ThemeMode
import com.tihloh.pos.ui.theme.ThemeSettings
import com.tihloh.pos.update.UpdateScheduler

class MainActivity : FragmentActivity() {
    private lateinit var pinStore: PinStore
    private lateinit var themeSettings: ThemeSettings
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var faceUnlockEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pinStore = PinStore(this)
        faceUnlockEnabled = pinStore.isFaceUnlockEnabled()
        themeSettings = ThemeSettings(this)
        themeMode = themeSettings.load()
        UpdateScheduler.schedule(applicationContext)

        setContent {
            PosTheme(mode = themeMode) {
                PosRoot(
                    pinStore = pinStore,
                    biometricAvailable = biometricAvailable(),
                    faceBiometricAvailable = faceBiometricAvailable(),
                    faceUnlockEnabled = faceUnlockEnabled,
                    requestBiometric = ::authenticateBiometric,
                    requestFaceBiometric = ::authenticateFaceBiometric,
                    onFaceUnlockEnabledChange = { enabled ->
                        pinStore.setFaceUnlockEnabled(enabled)
                        faceUnlockEnabled = enabled
                    },
                    themeMode = themeMode,
                    onThemeModeChange = {
                        themeMode = it
                        themeSettings.save(it)
                    }
                )
            }
        }
    }

    private fun biometricAvailable(): Boolean {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return BiometricManager.from(this).canAuthenticate(authenticators) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    // Weak includes strong biometrics and allows compatible Android face unlock.
    // Android selects the enrolled modality; apps cannot require face specifically.
    private fun faceBiometricAvailable(): Boolean =
        BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS

    private fun authenticateFaceBiometric(onSuccess: () -> Unit) {
        if (!faceUnlockEnabled || !faceBiometricAvailable()) return

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock POS with face or fingerprint")
            .setSubtitle("Use your enrolled Android biometric")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText("Use POS PIN")
            .build()

        prompt.authenticate(info)
    }

    private fun authenticateBiometric(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock POS")
            .setSubtitle("Authenticate to continue")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(info)
    }
}
