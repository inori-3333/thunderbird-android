package net.thunderbird.feature.mail.message.list.aggregation.model

import androidx.compose.runtime.Immutable
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.mail.message.list.ui.state.MessageItemUi

/**
 * Represents the messages of a single sender.
 *
 * All counters only cover the messages that are currently loaded by the message list data source.
 *
 * @property key The identity of the group.
 * @property sender The sender the group was created for. Taken from the latest message of the group.
 * @property contact The Android contact the group was resolved to, or `null` when the sender is not known.
 * @property addresses All addresses of the group. When the group was resolved to an Android contact, this
 *  contains the addresses of that contact that occur in the currently loaded messages.
 * @property latestMessage The most recent message of the group.
 * @property messageCount The number of currently loaded messages of the group.
 * @property unreadCount The number of currently loaded unread messages of the group.
 * @property hasStarredMessage Whether at least one message of the group is starred.
 * @property accountIds The accounts the messages of this group belong to.
 */
@Immutable
data class ContactGroup(
    val key: ContactAggregationKey,
    val sender: SenderIdentity?,
    val contact: ContactIdentity?,
    val addresses: Set<String>,
    val latestMessage: MessageItemUi,
    val messageCount: Int,
    val unreadCount: Int,
    val hasStarredMessage: Boolean,
    val accountIds: Set<AccountId>,
)
