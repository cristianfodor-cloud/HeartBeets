package com.heartbeets.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.heartbeets.core.ConnectionState
import com.heartbeets.core.DeviceRegistry
import com.heartbeets.core.HrDriver
import com.heartbeets.data.HrRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the active [HrDriver] and keeps the BLE connection
 * alive while the screen is off.
 *
 * Started via [HrServiceController]. The UI binds to it to observe live [HrDriver]
 * state (samples, battery, connection) without running BLE in the ViewModel itself.
 */
class HrService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): HrService = this@HrService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var driver: HrDriver? = null
    private var sessionId: Long = -1L
    private var persistJob: Job? = null

    private val _driverState = MutableStateFlow<HrDriver?>(null)
    /** Exposes the currently active driver (or null) to bound clients. */
    val driverState: StateFlow<HrDriver?> = _driverState.asStateFlow()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Searching…", null))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.getStringExtra(EXTRA_ADDRESS) ?: return START_NOT_STICKY
        val factoryId = intent.getStringExtra(EXTRA_FACTORY_ID)

        scope.launch { startStreaming(address, factoryId) }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.launch {
            driver?.disconnect()
            if (sessionId >= 0) {
                HrRepository.getInstance(applicationContext).closeSession(sessionId)
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    // ──────────────────────────── Internal ────────────────────────────

    private suspend fun startStreaming(address: String, factoryId: String?) {
        val factory = factoryId?.let { DeviceRegistry.findById(it) }
            ?: DeviceRegistry.factories.firstOrNull()
            ?: run {
                updateNotification("No driver available", null)
                return
            }

        val d = factory.create(address, null)
        driver = d
        _driverState.value = d

        // Open a session in the database.
        val repo = HrRepository.getInstance(applicationContext)
        sessionId = repo.openSession(address, factory.id)

        // Observe connection state and update notification.
        scope.launch {
            d.state.collect { state ->
                val label = when (state) {
                    ConnectionState.Connecting -> "Connecting…"
                    ConnectionState.Connected -> null // BPM will fill this
                    ConnectionState.Disconnected -> "Disconnected"
                    ConnectionState.Error -> "Connection error"
                }
                updateNotification(label ?: "Connected", null)
            }
        }

        // Persist every sample and update notification.
        persistJob = scope.launch {
            d.samples.collect { sample ->
                repo.insertSample(sample, sessionId)
                updateNotification(null, sample.bpm)
            }
        }

        // Connect (throws on failure).
        try {
            d.connect()
            repo.rememberPaired(address, d.displayName, factory.id)
        } catch (e: Exception) {
            updateNotification("Connection failed: ${e.message}", null)
        }
    }

    private fun updateNotification(label: String?, bpm: Int?) {
        val text = when {
            bpm != null -> "$bpm BPM"
            label != null -> label
            else -> "Running"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text, bpm))
    }

    private fun buildNotification(text: String, bpm: Int?): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("HeartBeets")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Heart Rate Monitoring",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Live heart-rate streaming" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_FACTORY_ID = "factoryId"
        private const val CHANNEL_ID = "hr_service"
        private const val NOTIFICATION_ID = 1001
    }
}
