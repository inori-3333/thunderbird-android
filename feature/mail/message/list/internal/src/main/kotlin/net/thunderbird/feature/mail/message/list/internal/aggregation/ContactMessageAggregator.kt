package net.thunderbird.feature.mail.message.list.internal.aggregation

import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.mail.message.list.aggregation.model.ContactAggregationKey
import net.thunderbird.feature.mail.message.list.aggregation.model.ContactGroup
import net.thunderbird.feature.mail.message.list.aggregation.model.ContactIdentity
import net.thunderbird.feature.mail.message.list.aggregation.model.SenderIdentity
import net.thunderbird.feature.mail.message.list.ui.state.MessageItemUi

/**
 * Aggregates message list items into one group per sender.
 *
 * Messages are grouped by the normalized email address of their sender. When a sender was resolved to an
 * Android contact, all addresses of that contact are grouped together instead. Messages without a usable
 * sender address get a group of their own so that they are never merged with unrelated messages.
 *
 * Aggregating is a single pass over [messages] and therefore runs in O(n).
 */
internal class ContactMessageAggregator {
    /**
     * @param messages The messages to aggregate. Must be the complete list of currently loaded messages,
     *  not a single page, so that groups of already loaded messages are updated instead of duplicated.
     * @return The sender groups, ordered by the date of their latest message (descending).
     */
    fun aggregate(messages: List<MessageItemUi>): List<ContactGroup> = aggregate(messages, emptyMap())

    /**
     * @param messages The messages to aggregate. Must be the complete list of currently loaded messages,
     *  not a single page, so that groups of already loaded messages are updated instead of duplicated.
     * @param contactIdentities The resolved identity for the senders of [messages]. Empty while contacts are
     *  not resolved (yet), in which case messages are grouped by their sender address.
     * @return The sender groups, ordered by the date of their latest message (descending).
     */
    fun aggregate(
        messages: List<MessageItemUi>,
        contactIdentities: Map<SenderIdentity, ContactIdentity>,
    ): List<ContactGroup> {
        val groups = LinkedHashMap<ContactAggregationKey, MutableContactGroup>()
        for (message in messages) {
            val sender = message.senderIdentity?.takeIf { it.normalizedAddress.isNotBlank() }
            if (sender == null) {
                // A message without a usable sender address is a group of its own.
                groups.getOrPut(ContactAggregationKey.UnknownSender(messageId = message.id)) {
                    MutableContactGroup(sender = null, contact = null)
                }.add(message)
            } else {
                val contact = contactIdentities[sender]
                val key = contact?.key ?: ContactAggregationKey.EmailAddress(sender.normalizedAddress)
                groups.getOrPut(key) { MutableContactGroup(sender = sender, contact = contact) }
                    .add(message, sender.normalizedAddress)
            }
        }

        return groups
            .map { (key, group) -> group.toContactGroup(key) }
            .sortedByDescending { group -> group.latestMessage.sortTimestamp }
    }

    private class MutableContactGroup(
        private val sender: SenderIdentity?,
        private val contact: ContactIdentity?,
    ) {
        private var latestMessage: MessageItemUi? = null
        private var messageCount: Int = 0
        private var unreadCount: Int = 0
        private var hasStarredMessage: Boolean = false
        private val accountIds: MutableSet<AccountId> = mutableSetOf()
        private val addresses: MutableSet<String> = mutableSetOf()

        fun add(message: MessageItemUi, normalizedAddress: String? = null) {
            val currentLatestMessage = latestMessage
            if (currentLatestMessage == null || message.sortTimestamp > currentLatestMessage.sortTimestamp) {
                latestMessage = message
            }
            messageCount += 1
            if (message.state != MessageItemUi.State.Read) {
                unreadCount += 1
            }
            if (message.starred) {
                hasStarredMessage = true
            }
            accountIds.add(message.account.id)
            if (normalizedAddress != null) {
                addresses += normalizedAddress
            }
        }

        fun toContactGroup(key: ContactAggregationKey): ContactGroup {
            val latestMessage = requireNotNull(latestMessage) {
                "A contact group must contain at least one message"
            }
            val effectiveSender = sender ?: latestMessage.senderIdentity
            val effectiveAddresses = when {
                contact != null -> addresses.ifEmpty { setOf(effectiveSender?.normalizedAddress.orEmpty()) }
                effectiveSender != null -> setOf(effectiveSender.normalizedAddress)
                else -> emptySet()
            }
            return ContactGroup(
                key = key,
                sender = effectiveSender?.takeIf { it.normalizedAddress.isNotBlank() },
                contact = contact,
                addresses = effectiveAddresses,
                latestMessage = latestMessage,
                messageCount = messageCount,
                unreadCount = unreadCount,
                hasStarredMessage = hasStarredMessage,
                accountIds = accountIds.toSet(),
            )
        }
    }
}
