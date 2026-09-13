package net.thunderbird.feature.mail.message.list.internal.aggregation

import kotlinx.collections.immutable.toPersistentList
import net.thunderbird.core.common.resources.StringsResourceManager
import net.thunderbird.core.preference.display.visualSettings.message.list.MessageListAggregationMode
import net.thunderbird.feature.mail.message.list.aggregation.model.ContactGroup
import net.thunderbird.feature.mail.message.list.aggregation.model.ContactIdentity
import net.thunderbird.feature.mail.message.list.aggregation.model.SenderIdentity
import net.thunderbird.feature.mail.message.list.ui.state.Avatar
import net.thunderbird.feature.mail.message.list.ui.state.ContactGroupUiModel
import net.thunderbird.feature.mail.message.list.ui.state.MessageItemUi
import net.thunderbird.feature.mail.message.list.ui.state.MessageListContent
import net.thunderbird.feature.mail.message.list.ui.state.toMessagesContent
import net.thunderbird.feature.mail.message.list.R as MessageListApiR

/**
 * Creates the [MessageListContent] that is displayed by the message list.
 *
 * The content is always derived from the complete list of currently loaded messages. Load more therefore
 * updates the counters of the already existing groups instead of creating a second group for the same sender.
 *
 * @param contactMessageAggregator Aggregates messages into sender groups.
 * @param stringsResourceManager Provides the texts that are displayed for sender groups.
 */
internal class MessageListContentFactory(
    private val contactMessageAggregator: ContactMessageAggregator,
    private val stringsResourceManager: StringsResourceManager,
) {
    private val unknownSenderText: String =
        stringsResourceManager.stringResource(MessageListApiR.string.message_list_contact_group_unknown_sender)
    private val unnamedSenderText: String =
        stringsResourceManager.stringResource(MessageListApiR.string.message_list_contact_group_unnamed_sender)

    /**
     * @param aggregationMode The aggregation mode to apply.
     * @param messages The complete list of currently loaded messages.
     * @param contactIdentities The senders of [messages] that were resolved to an Android contact.
     */
    fun create(
        aggregationMode: MessageListAggregationMode,
        messages: List<MessageItemUi>,
        contactIdentities: Map<SenderIdentity, ContactIdentity> = emptyMap(),
    ): MessageListContent = when (aggregationMode) {
        MessageListAggregationMode.NONE -> messages.toMessagesContent()

        MessageListAggregationMode.CONTACT -> MessageListContent.ContactGroups(
            items = contactMessageAggregator
                .aggregate(messages, contactIdentities)
                .map { group -> group.toUiModel() }
                .toPersistentList(),
        )
    }

    private fun ContactGroup.toUiModel(): ContactGroupUiModel {
        val sender = sender
        val address = contact?.primaryAddress ?: sender?.visibleAddress.orEmpty()
        val title = contact?.displayName?.takeIf { it.isNotBlank() }
            ?: sender.title()
        return ContactGroupUiModel(
            key = key,
            title = title,
            address = address,
            addresses = addresses,
            avatar = contact?.photoUri?.let { photoUri -> Avatar.Image(url = photoUri) }
                ?: latestMessage.senders.avatar
                ?: Avatar.Monogram(value = title),
            color = latestMessage.senders.color,
            latestMessage = latestMessage,
            messageCount = messageCount,
            messageCountLabel = stringsResourceManager.stringResource(
                MessageListApiR.string.message_list_contact_group_message_count,
                messageCount,
            ),
            unreadCount = unreadCount,
            unreadCountLabel = if (unreadCount == 0) {
                null
            } else {
                stringsResourceManager.stringResource(
                    MessageListApiR.string.message_list_contact_group_unread_count,
                    unreadCount,
                )
            },
            hasStarredMessage = hasStarredMessage,
            accountIds = accountIds,
        )
    }

    private fun SenderIdentity?.title(): String {
        if (this == null) return unknownSenderText
        val displayName = displayName?.takeIf { it.isNotBlank() && it != visibleAddress }
        return displayName ?: visibleAddress.ifBlank { unnamedSenderText }
    }
}
