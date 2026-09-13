package net.thunderbird.feature.mail.message.list.ui.state

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.mail.message.list.aggregation.model.ContactAggregationKey

/**
 * Represents a single sender group row of the message list when contact aggregation is enabled.
 *
 * All counters only cover the messages that are currently loaded by the message list data source.
 *
 * @property key The identity of the group. Also used when opening the group.
 * @property title The text to display as the main title of the row.
 * @property address The sender address to display as a subtitle.
 * @property addresses All addresses of the group. Contains more than one entry when the group was resolved
 *  to an Android contact with several addresses.
 * @property avatar The avatar of the sender.
 * @property color An optional color associated with the sender, used for theming the avatar.
 * @property latestMessage The most recent message of the group. Its subject and timestamp are displayed.
 * @property messageCount The number of currently loaded messages of the group.
 * @property messageCountLabel The localized label for [messageCount].
 * @property unreadCount The number of currently loaded unread messages of the group.
 * @property unreadCountLabel The localized label for [unreadCount]. `null` when the group has no unread message.
 * @property hasStarredMessage Whether at least one message of the group is starred.
 * @property accountIds The accounts the messages of this group belong to.
 */
@Immutable
data class ContactGroupUiModel(
    val key: ContactAggregationKey,
    val title: String,
    val address: String,
    val addresses: Set<String>,
    val avatar: Avatar,
    val color: Color?,
    val latestMessage: MessageItemUi,
    val messageCount: Int,
    val messageCountLabel: String,
    val unreadCount: Int,
    val unreadCountLabel: String?,
    val hasStarredMessage: Boolean,
    val accountIds: Set<AccountId>,
)
