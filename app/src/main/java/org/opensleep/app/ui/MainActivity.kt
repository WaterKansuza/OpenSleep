package org.opensleep.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import org.opensleep.app.service.tracking.NightTimeService
import org.opensleep.app.ui.dashboard.DashboardViewModel
import org.opensleep.app.ui.dashboard.SleepDebtDashboardScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Permission results handled reactively */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRequiredPermissions()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()

                    SleepDebtDashboardScreen(
                        uiState = uiState,
                        liveWaveFlow = viewModel.liveWaveFlow,
                        onStartNightSleep = {
                            startTrackingService("NIGHT")
                        },
                        onStartPowerNap = {
                            startTrackingService("NAP")
                        },
                        onStopTracking = {
                            stopTrackingService()
                        },
                        onRunCalibration = {
                            viewModel.runSonarCalibration()
                        }
                    )
                }
            }
        }
    }

    private fun startTrackingService(sessionType: String) {
        val intent = Intent(this, NightTimeService::class.java).apply {
            action = NightTimeService.ACTION_START_TRACKING
            putExtra(NightTimeService.EXTRA_SESSION_TYPE, sessionType)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopTrackingService() {
        val intent = Intent(this, NightTimeService::class.java).apply {
            action = NightTimeService.ACTION_STOP_TRACKING
        }
        startService(intent)
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
