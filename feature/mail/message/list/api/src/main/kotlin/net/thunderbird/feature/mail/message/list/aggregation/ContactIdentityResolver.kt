package net.thunderbird.feature.mail.message.list.aggregation

import net.thunderbird.feature.mail.message.list.aggregation.model.ContactIdentity
import net.thunderbird.feature.mail.message.list.aggregation.model.SenderIdentity

/**
 * Resolves the senders of a message list to the persons they belong to.
 *
 * Implementations must be safe to call from a coroutine and must never throw for a single failing lookup.
 * When the contacts permission is not granted, or the contacts provider fails, every sender falls back to
 * its email address.
 */
interface ContactIdentityResolver {
    /**
     * Resolves the given senders.
     *
     * @param senders The distinct senders of the currently loaded messages.
     * @return The identity for every sender of [senders]. Implementations must return an entry for every
     *  sender so that the caller doesn't have to handle missing entries.
     */
    suspend fun resolve(senders: Set<SenderIdentity>): Map<SenderIdentity, ContactIdentity>
}

/**
 * Resolves the senders of a message list to the persons they belong to.
 */
typealias ContactIdentityResolverFunction = suspend (Set<SenderIdentity>) -> Map<SenderIdentity, ContactIdentity>
