package net.thunderbird.feature.mail.message.list.aggregation.model

import androidx.compose.runtime.Immutable

/**
 * Represents the person a sender belongs to.
 *
 * Phase 1 only knows senders by their email address. When the user granted the contacts permission, several
 * addresses of the same Android contact can resolve to the same [ContactIdentity] and are then displayed as a
 * single group.
 *
 * @property key The identity used to aggregate messages.
 * @property displayName The name to display for the group.
 * @property primaryAddress The address to display as a subtitle.
 * @property addresses All known addresses of this identity. Only contains the addresses that were part of the
 *  resolution request.
 * @property photoUri The URI of the contact picture, or `null` when the identity has no picture.
 */
@Immutable
data class ContactIdentity(
    val key: ContactAggregationKey,
    val displayName: String?,
    val primaryAddress: String,
    val addresses: Set<String>,
    val photoUri: String? = null,
)
