package com.heartbeets.core

/**
 * Plug-in contract for device families.
 *
 * Each supported device family (VeePoo, Standard HRS, Polar, …) provides exactly
 * one factory. The factory is registered at app startup via [DeviceRegistry.register].
 *
 * Factories are created with any Android [android.content.Context] they need stored
 * in their constructor so that [create] and [matches] remain Android-agnostic here
 * in the [core] module.
 */
interface HrDriverFactory {
    /** Stable identifier, e.g. "veepoo", "standard-hrs". */
    val id: String

    /** Human-readable name shown in the scan UI. */
    val displayName: String

    /** Manufacturer / brand name hint, e.g. "VeePoo", "Generic". */
    val manufacturerHint: String

    /**
     * Evaluate how well this factory matches a discovered BLE device.
     *
     * [Match.EXACT]  — the device name or advertised service UUID conclusively
     *                  identifies this family.
     * [Match.LIKELY] — partial evidence (NUS service present, generic name pattern).
     * [Match.NO]     — definitely not this family.
     *
     * [ScanCoordinator] picks the highest-confidence factory for each device.
     */
    fun matches(scan: BleScanResult): Match

    /** Create a new driver instance for the given address. Not yet connected. */
    fun create(address: String, name: String?): HrDriver
}

/** Confidence that a [BleScanResult] belongs to a particular driver family. */
enum class Match {
    /** Conclusive match (lower ordinal = higher confidence). */
    EXACT,
    /** Plausible match but not certain. */
    LIKELY,
    /** Definitely not this driver. */
    NO,
}
