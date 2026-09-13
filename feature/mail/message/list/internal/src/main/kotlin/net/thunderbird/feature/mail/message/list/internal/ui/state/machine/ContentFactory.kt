package net.thunderbird.feature.mail.message.list.internal.ui.state.machine

import net.thunderbird.core.preference.display.visualSettings.message.list.MessageListAggregationMode
import net.thunderbird.feature.mail.folder.api.FolderType
import net.thunderbird.feature.mail.message.list.aggregation.model.ContactIdentity
import net.thunderbird.feature.mail.message.list.aggregation.model.SenderIdentity
import net.thunderbird.feature.mail.message.list.internal.aggregation.MessageListContentFactory
import net.thunderbird.feature.mail.message.list.preferences.MessageListPreferences
import net.thunderbird.feature.mail.message.list.ui.state.MessageItemUi
import net.thunderbird.feature.mail.message.list.ui.state.MessageListContent
import net.thunderbird.feature.mail.message.list.ui.state.MessageListMetadata

/**
 * Creates the [MessageListContent] that is displayed for a list of messages.
 *
 * @param metadata The metadata of the message list. Used to determine whether contact aggregation is
 *  available for the current folder.
 * @param aggregationMode The aggregation mode that is selected by the user.
 * @param messages The new list of messages.
 * @param contactIdentities The senders of [messages] that were resolved to an Android contact.
 */
internal typealias ContentFactory = (
    metadata: MessageListMetadata,
    aggregationMode: MessageListAggregationMode,
    messages: List<MessageItemUi>,
    contactIdentities: Map<SenderIdentity, ContactIdentity>,
) -> MessageListContent

/**
 * Creates the [ContentFactory] that is used by the state machine to keep [MessageListContent] in sync with
 * the messages of the message list.
 *
 * @param contentFactory Creates the content for messages and the effective aggregation mode.
 */
internal fun createContentFactory(
    contentFactory: MessageListContentFactory,
): ContentFactory = { metadata, aggregationMode, messages, contactIdentities ->
    val folderType = metadata.folder?.type
    val effectiveMode = aggregationMode
        .takeIf { folderType == null || isContactAggregationAvailable(folderType) }
        ?: MessageListAggregationMode.NONE
    contentFactory.create(
        aggregationMode = effectiveMode,
        messages = messages,
        contactIdentities = contactIdentities,
    )
}

/**
 * Creates the function that produces the content for a new list of messages.
 *
 * @param metadata The metadata of the message list.
 * @param preferences The preferences that are applied to the messages.
 * @param contactIdentities The senders of the messages that were resolved to an Android contact.
 * @param contentFactory Creates the content.
 */
internal fun contentFor(
    metadata: MessageListMetadata,
    preferences: MessageListPreferences,
    contactIdentities: Map<SenderIdentity, ContactIdentity>,
    contentFactory: ContentFactory,
): (List<MessageItemUi>) -> MessageListContent = { messages ->
    contentFactory(metadata, preferences.aggregationMode, messages, contactIdentities)
}

/**
 * Whether contact aggregation can be used for a folder of the given [folderType].
 *
 * Contact aggregation only makes sense when the messages are not sent by the user themselves. For folders
 * that contain the user's own messages, all messages would end up in a single group.
 */
internal fun isContactAggregationAvailable(folderType: FolderType): Boolean =
    folderType in CONTACT_AGGREGATION_FOLDER_TYPES

private val CONTACT_AGGREGATION_FOLDER_TYPES = setOf(
    FolderType.INBOX,
    FolderType.REGULAR,
    FolderType.ARCHIVE,
    FolderType.SPAM,
)
