package net.thunderbird.feature.mail.message.list.aggregation.model

import androidx.compose.runtime.Immutable
import net.thunderbird.feature.mail.message.list.aggregation.normalizeEmailAddress

/**
 * Represents the sender of a message as used for contact aggregation.
 *
 * @property normalizedAddress The address used as aggregation key. It is locale independent lower cased
 *  and trimmed. See [normalizeEmailAddress].
 * @property rawAddress The address as it is stored in the data source.
 * @property displayName The display name that should be shown to the user. May be the name of an Android
 *  contact, the "friendly" name of the sender or the raw address as a fallback.
 */
@Immutable
data class SenderIdentity(
    val normalizedAddress: String,
    val rawAddress: String,
    val displayName: String?,
) {
    /**
     * The address that should be shown to the user. Falls back to the normalized address when the raw
     * address is not available.
     */
    val visibleAddress: String
        get() = rawAddress.ifBlank { normalizedAddress }
}

/**
 * Creates a [SenderIdentity] from the raw address and the display name of a sender.
 *
 * @param address The raw email address of the sender.
 * @param displayName The name that should be shown to the user, if any.
 * @return The identity, or `null` when [address] does not contain a usable address.
 */
fun createSenderIdentityOrNull(
    address: String?,
    displayName: String?,
): SenderIdentity? {
    val rawAddress = address?.trim().orEmpty()
    val normalizedAddress = normalizeEmailAddress(rawAddress)
    return if (normalizedAddress.isEmpty()) {
        null
    } else {
        SenderIdentity(
            normalizedAddress = normalizedAddress,
            rawAddress = rawAddress,
            displayName = displayName?.takeIf { it.isNotBlank() },
        )
    }
}
