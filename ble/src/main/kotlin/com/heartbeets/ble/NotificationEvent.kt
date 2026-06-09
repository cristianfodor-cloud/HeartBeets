package com.heartbeets.ble

import java.util.UUID

/** A BLE GATT characteristic notification received from a connected device. */
data class NotificationEvent(
    val characteristicUuid: UUID,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NotificationEvent) return false
        return characteristicUuid == other.characteristicUuid &&
                java.util.Arrays.equals(data, other.data)
    }

    override fun hashCode(): Int {
        var result = characteristicUuid.hashCode()
        result = 31 * result + java.util.Arrays.hashCode(data)
        return result
    }
}
