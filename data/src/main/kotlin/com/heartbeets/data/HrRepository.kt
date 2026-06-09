package com.heartbeets.data

import android.content.Context
import com.heartbeets.core.HrSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Single access point for all HR persistence.
 *
 * Call [openSession] when a driver connects, [closeSession] on disconnect.
 * Every [HrSample] is persisted via [insertSample] during a session.
 * [rememberPaired] persists the device address/name for quick reconnect.
 */
class HrRepository private constructor(private val db: HrDatabase) {

    private val sampleDao = db.hrSampleDao()
    private val sessionDao = db.hrSessionDao()
    private val pairedDao = db.pairedDeviceDao()

    // ──────────────── Session lifecycle ────────────────

    suspend fun openSession(deviceAddress: String, driverId: String): Long =
        withContext(Dispatchers.IO) {
            sessionDao.insert(
                HrSessionEntity(
                    deviceAddress = deviceAddress,
                    driverId = driverId,
                    startedAt = System.currentTimeMillis(),
                )
            )
        }

    suspend fun closeSession(sessionId: Long) =
        withContext(Dispatchers.IO) {
            sessionDao.close(sessionId, System.currentTimeMillis())
        }

    fun observeSessions(): Flow<List<HrSessionEntity>> = sessionDao.observeAll()

    // ──────────────── Sample persistence ────────────────

    suspend fun insertSample(sample: HrSample, sessionId: Long) =
        withContext(Dispatchers.IO) {
            sampleDao.insert(
                HrSampleEntity(
                    sessionId = sessionId,
                    deviceAddress = sample.source.deviceAddress,
                    driverId = sample.source.driverId,
                    bpm = sample.bpm,
                    timestamp = sample.timestamp,
                    rrIntervalsMs = sample.rrIntervalsMs?.joinToString(","),
                    contactDetected = sample.contactDetected,
                )
            )
        }

    fun observeSamplesForSession(sessionId: Long): Flow<List<HrSampleEntity>> =
        sampleDao.observeForSession(sessionId)

    fun observeRecentSamples(limit: Int = 200): Flow<List<HrSampleEntity>> =
        sampleDao.observeRecent(limit)

    // ──────────────── Paired device ────────────────

    suspend fun rememberPaired(address: String, name: String?, driverId: String) =
        withContext(Dispatchers.IO) {
            pairedDao.upsert(
                PairedDeviceEntity(
                    address = address,
                    name = name,
                    driverId = driverId,
                    lastConnectedAt = System.currentTimeMillis(),
                )
            )
        }

    suspend fun lastPaired(): PairedDeviceEntity? =
        withContext(Dispatchers.IO) { pairedDao.lastPaired() }

    fun observePairedDevices(): Flow<List<PairedDeviceEntity>> = pairedDao.observeAll()

    companion object {
        @Volatile private var instance: HrRepository? = null

        fun getInstance(context: Context): HrRepository =
            instance ?: synchronized(this) {
                instance ?: HrRepository(HrDatabase.getInstance(context)).also { instance = it }
            }
    }
}
