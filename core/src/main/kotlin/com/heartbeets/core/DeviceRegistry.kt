package com.heartbeets.core

/**
 * App-wide registry of [HrDriverFactory] instances.
 *
 * Call [register] once per factory at app startup (in [Application.onCreate]).
 * [ScanCoordinator] and the live-HR screen query [bestMatchFor] to pick the right
 * driver for a discovered device.
 */
object DeviceRegistry {

    private val _factories = mutableListOf<HrDriverFactory>()

    /** Ordered list of all registered factories (insertion order). */
    val factories: List<HrDriverFactory> get() = _factories.toList()

    /** Register a driver factory. Call once per family at app startup. */
    fun register(factory: HrDriverFactory) {
        _factories.add(factory)
    }

    /**
     * Find the factory with the highest [Match] confidence for [scan].
     * Returns null if no factory claims the device.
     */
    fun bestMatchFor(scan: BleScanResult): Pair<HrDriverFactory, Match>? {
        var best: Pair<HrDriverFactory, Match>? = null
        for (factory in _factories) {
            val m = factory.matches(scan)
            if (m == Match.NO) continue
            // Lower ordinal = higher confidence (EXACT=0, LIKELY=1)
            if (best == null || m.ordinal < best.second.ordinal) {
                best = Pair(factory, m)
            }
        }
        return best
    }

    /** Look up a factory by its stable [HrDriverFactory.id]. */
    fun findById(id: String): HrDriverFactory? = _factories.find { it.id == id }
}
