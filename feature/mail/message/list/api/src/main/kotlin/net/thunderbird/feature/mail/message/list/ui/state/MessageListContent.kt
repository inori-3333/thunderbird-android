package net.thunderbird.feature.mail.message.list.ui.state

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList

/**
 * Represents the content that is displayed by the message list.
 *
 * The message list either shows the messages of the current folder/filter, or - when the user enabled
 * contact aggregation - one row per sender. Both are derived from the same [MessageListState.messages]
 * list, but only one of them is displayed at a time.
 *
 * The [MessageListState.messages] list always stays the single source of truth. This content only
 * describes how those messages are presented.
 */
@Immutable
sealed interface MessageListContent {
    /**
     * The regular message list.
     *
     * @property items The messages to display, in the order defined by the current sort criteria.
     */
    data class Messages(
        val items: ImmutableList<MessageItemUi>,
    ) : MessageListContent

    /**
     * The messages of the current folder/filter aggregated into one row per sender.
     *
     * @property items The sender groups, ordered by the date of their latest message (descending).
     */
    data class ContactGroups(
        val items: ImmutableList<ContactGroupUiModel>,
    ) : MessageListContent
}

/**
 * Creates [MessageListContent.Messages] content for this list of messages.
 */
fun List<MessageItemUi>.toMessagesContent(): MessageListContent.Messages =
    MessageListContent.Messages(items = toPersistentList())
