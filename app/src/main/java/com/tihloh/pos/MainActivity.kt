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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pinStore = PinStore(this)
        themeSettings = ThemeSettings(this)
        themeMode = themeSettings.load()
        UpdateScheduler.schedule(applicationContext)

        setContent {
            PosTheme(mode = themeMode) {
                PosRoot(
                    pinStore = pinStore,
                    biometricAvailable = biometricAvailable(),
                    requestBiometric = ::authenticateBiometric,
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
