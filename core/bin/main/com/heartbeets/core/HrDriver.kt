package com.heartbeets.core

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract that every heart-rate device driver must satisfy.
 *
 * Drivers are coroutine-friendly. [connect] and [disconnect] are suspend functions;
 * [samples] and [state] are Flows. The caller (typically [HrService]) owns the
 * lifecycle and calls connect/disconnect at the right times.
 *
 * Drivers are NOT aware of Android services, persistence, or UI — they only produce
 * a stream of [HrSample]s and report connection state.
 */
interface HrDriver {
    /** MAC address of the connected device. */
    val deviceAddress: String

    /** Human-readable label shown in the UI (device name or driver name). */
    val displayName: String

    /** Current BLE connection lifecycle state. */
    val state: StateFlow<ConnectionState>

    /**
     * Live stream of heart-rate samples. Shared; multiple collectors are allowed.
     * Emits only while [state] == [ConnectionState.Connected].
     */
    val samples: SharedFlow<HrSample>

    /** Battery level 0–100, or null if the device doesn't report it. */
    val battery: StateFlow<Int?>

    /** Establish the BLE connection and start streaming. Throws on failure. */
    suspend fun connect()

    /** Stop streaming and close the BLE connection gracefully. */
    suspend fun disconnect()
}
