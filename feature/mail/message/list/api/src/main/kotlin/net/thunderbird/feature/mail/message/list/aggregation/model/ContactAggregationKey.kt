package net.thunderbird.feature.mail.message.list.aggregation.model

import androidx.compose.runtime.Immutable

/**
 * Uniquely identifies the "person" a group of messages is aggregated by.
 *
 * The display name of a sender must never be used as identity. Two senders with the same display name
 * but different email addresses are two different people.
 */
@Immutable
sealed interface ContactAggregationKey {
    /**
     * A sender that is identified by the normalized email address of the message sender.
     *
     * @property normalizedAddress The normalized email address. See `normalizeEmailAddress`.
     */
    data class EmailAddress(
        val normalizedAddress: String,
    ) : ContactAggregationKey

    /**
     * A message that doesn't provide a usable sender address.
     *
     * Every such message gets its own group. Falling back to a single shared "unknown" group would
     * incorrectly merge unrelated messages.
     *
     * @property messageId The identifier of the message the group was created for.
     */
    data class UnknownSender(
        val messageId: String,
    ) : ContactAggregationKey

    /**
     * A sender that could be mapped to an Android contact. Resolved in phase 2.
     *
     * @property contactId The identifier of the Android contact.
     */
    data class AndroidContact(
        val contactId: Long,
    ) : ContactAggregationKey
}
