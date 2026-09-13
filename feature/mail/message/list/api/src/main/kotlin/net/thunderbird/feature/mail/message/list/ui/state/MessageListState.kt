package net.thunderbird.feature.mail.message.list.ui.state

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.core.preference.display.visualSettings.message.list.MessageListAggregationMode
import net.thunderbird.feature.mail.message.list.aggregation.model.ContactIdentity
import net.thunderbird.feature.mail.message.list.aggregation.model.SenderIdentity
import net.thunderbird.feature.mail.message.list.preferences.MessageListPreferences

/**
 * Represents the state of the message list screen.
 *
 * This sealed interface defines the different possible states, such as when messages are being loaded,
 * have been loaded, are being searched, or when the user is selecting messages for an action.
 *
 * @property metadata Contextual information about the message list.
 * @property preferences User-configurable preferences for the message list display.
 * @property messages The messages of the current folder/filter. This is the single source of truth for
 *  the data of the screen, independent of the way it is presented.
 * @property content The way the [messages] are presented. Either as regular message rows or - when contact
 *  aggregation is enabled - as one row per sender. Must always be derived from [messages].
 * @property contactIdentities The senders of [messages] that were resolved to an Android contact. Empty while
 *  contacts are not resolved (yet) or when the contacts permission is not granted.
 */
@Immutable
sealed interface MessageListState {
    val metadata: MessageListMetadata
    val preferences: MessageListPreferences?
    val messages: ImmutableList<MessageItemUi>
    val content: MessageListContent
    val contactIdentities: Map<SenderIdentity, ContactIdentity>

    /**
     * Creates a copy of the current state with updated [metadata]. All other properties are preserved.
     *
     * This ensures that the specific type of the state (e.g., [LoadedMessages], [SearchingMessages]) is
     * preserved.
     *
     * @param transform A lambda function that receives the current [MessageListMetadata] and returns a
     *  new, transformed instance.
     * @return A new [MessageListState] instance of the same type as the original, but with the updated
     *  metadata.
     */
    fun withMetadata(
        transform: MessageListMetadata.() -> MessageListMetadata,
    ): MessageListState = withUpdate(content = content, metadata = metadata.transform())

    /**
     * Creates a copy of the current state with updated [preferences]. All other properties are preserved.
     *
     * This ensures that the specific type of the state (e.g., [LoadedMessages], [SearchingMessages]) is
     * preserved.
     *
     * **Note:** Changing the aggregation mode in the preferences does not recompute [content]. The
     * aggregation mode is a projection of [messages] and has to be applied by the caller.
     *
     * @param transform A lambda function that receives the current [MessageListPreferences] and returns
     *  a new, transformed instance.
     * @return A new [MessageListState] instance of the same type as the original, but with the updated
     *  preferences.
     */
    fun withPreferences(
        transform: MessageListPreferences.() -> MessageListPreferences,
    ): MessageListState {
        val newPreferences = requireNotNull(preferences).transform()
        return withUpdate(content = content, preferences = newPreferences)
    }

    /**
     * Creates a copy of the current state with updated [messages] and [content].
     *
     * @param content Creates the new content for the transformed list of messages.
     * @param transform A lambda function that receives the current [ImmutableList] of [MessageItemUi]
     *  and returns a new, transformed list.
     * @return A new [MessageListState] instance of the same type as the original, containing the
     *  transformed messages.
     */
    fun mapMessages(
        content: (List<MessageItemUi>) -> MessageListContent,
        transform: (MessageItemUi) -> MessageItemUi,
    ): MessageListState {
        val newMessages = messages.map(transform).toImmutableList()
        return withUpdate(content = content(newMessages), messages = newMessages)
    }

    /**
     * Creates a copy of the current state with updated [contactIdentities] and the [content] that results from
     * them.
     *
     * @param content Creates the new content for the given identities.
     * @param contactIdentities The resolved senders.
     * @return A new [MessageListState] instance of the same type as the original.
     */
    fun withContactIdentities(
        contactIdentities: Map<SenderIdentity, ContactIdentity>,
        content: (Map<SenderIdentity, ContactIdentity>) -> MessageListContent,
    ): MessageListState = withUpdate(
        content = content(contactIdentities),
        contactIdentities = contactIdentities,
    )

    /**
     * Creates a copy of the current state with updated [content] for the currently loaded messages.
     *
     * @param content Creates the new content for the currently loaded messages.
     * @return A new [MessageListState] instance of the same type as the original, containing the given
     *  content.
     */
    fun withContent(content: (List<MessageItemUi>) -> MessageListContent): MessageListState =
        withUpdate(content = content(messages))

    /**
     * Creates a copy of the current state with the given changes applied.
     *
     * @param content The [content] of the new state.
     * @param messages The [messages] of the new state. Defaults to the messages of the current state.
     * @param preferences The [preferences] of the new state. Defaults to the preferences of the current state.
     * @param metadata The [metadata] of the new state. Defaults to the metadata of the current state.
     * @param contactIdentities The [contactIdentities] of the new state. Defaults to the identities of the
     *  current state.
     */
    @Suppress("LongParameterList")
    fun withUpdate(
        content: MessageListContent,
        messages: ImmutableList<MessageItemUi> = this.messages,
        preferences: MessageListPreferences? = this.preferences,
        metadata: MessageListMetadata = this.metadata,
        contactIdentities: Map<SenderIdentity, ContactIdentity> = this.contactIdentities,
    ): MessageListState = when (this) {
        is LoadedMessages -> copy(
            metadata = metadata,
            preferences = requireNotNull(preferences),
            messages = messages,
            content = content,
            contactIdentities = contactIdentities,
        )

        is LoadingMessages -> copy(
            metadata = metadata,
            preferences = requireNotNull(preferences),
            messages = messages,
            content = content,
            contactIdentities = contactIdentities,
        )

        is SearchingMessages -> copy(
            metadata = metadata,
            preferences = requireNotNull(preferences),
            messages = messages,
            content = content,
            contactIdentities = contactIdentities,
        )

        is SelectingMessages -> copy(
            metadata = metadata,
            preferences = requireNotNull(preferences),
            messages = messages,
            content = content,
            contactIdentities = contactIdentities,
        )

        is WarmingUp -> copy(
            metadata = metadata,
            preferences = preferences,
            messages = messages,
            content = content,
            contactIdentities = contactIdentities,
        )
    }

    /**
     * Whether the messages of the list are aggregated by sender.
     *
     * This is a convenience for hosts that display the current mode, such as a checkable menu item.
     */
    val aggregationEnabled: Boolean
        get() = preferences?.aggregationMode == MessageListAggregationMode.CONTACT

    /**
     * Represents the initial state of the message list screen before any messages are loaded.
     *
     * This state is used during the initial setup or "warm-up" phase, where the UI is being
     * prepared but no data fetching has been initiated yet. It provides default values for
     * the UI to display a consistent initial view.
     */
    data class WarmingUp(
        override val metadata: MessageListMetadata = MessageListMetadata(
            folder = null,
            swipeActions = persistentMapOf(),
            sortCriteriaPerAccount = persistentMapOf(),
            activeMessage = null,
            isActive = false,
        ),
        override val preferences: MessageListPreferences? = null,
        override val messages: ImmutableList<MessageItemUi> = persistentListOf(),
        override val content: MessageListContent = messages.toMessagesContent(),
        override val contactIdentities: Map<SenderIdentity, ContactIdentity> = persistentMapOf(),
    ) : MessageListState {
        /**
         * Indicates whether the warming-up state has completed and is ready to transition to an active state.
         *
         * @return `true` when both the metadata is ready and user preferences have been loaded, signaling
         * that the message list screen has completed its initialization phase and can proceed to display content.
         */
        val isReady: Boolean
            get() = metadata.isReady && preferences != null
    }

    /**
     * Represents the state where messages for a folder have been successfully loaded and are ready to be displayed.
     *
     * This is the primary "idle" or "ready" state for the message list.
     */
    data class LoadedMessages(
        override val metadata: MessageListMetadata,
        override val preferences: MessageListPreferences,
        override val messages: ImmutableList<MessageItemUi>,
        override val content: MessageListContent = messages.toMessagesContent(),
        override val contactIdentities: Map<SenderIdentity, ContactIdentity> = persistentMapOf(),
    ) : MessageListState

    /**
     * Represents the state where messages are being loaded.
     *
     * This state is active when the app is fetching new messages from a local or remote source.
     *
     * @param isPullToRefresh `true` if loading was triggered by a pull-to-refresh gesture, `false` otherwise.
     * @param isRemoteLoading `true` if messages are being fetched from the remote server, `false` for local loading.
     * @param progress A value between 0.0 and 1.0 indicating the loading progress.
     */
    data class LoadingMessages(
        val progress: Float,
        val isPullToRefresh: Boolean = false,
        val isRemoteLoading: Boolean = false,
        override val metadata: MessageListMetadata,
        override val preferences: MessageListPreferences,
        override val messages: ImmutableList<MessageItemUi> = persistentListOf(),
        override val content: MessageListContent = messages.toMessagesContent(),
        override val contactIdentities: Map<SenderIdentity, ContactIdentity> = persistentMapOf(),
    ) : MessageListState

    /**
     * Represents the state when the user is actively searching for messages.
     *
     * This state is triggered when the user enters a query in the search bar. The message list will display
     * results matching the query, either from a local database or by performing a search on the server.
     *
     * @param searchQuery The text query entered by the user.
     * @param isServerSearch `true` if the search is being performed on the mail server; `false` if it's a local search.
     */
    data class SearchingMessages(
        val searchQuery: String,
        val isServerSearch: Boolean,
        override val metadata: MessageListMetadata,
        override val preferences: MessageListPreferences,
        override val messages: ImmutableList<MessageItemUi>,
        override val content: MessageListContent = messages.toMessagesContent(),
        override val contactIdentities: Map<SenderIdentity, ContactIdentity> = persistentMapOf(),
    ) : MessageListState

    /**
     * Represents the state where the user is actively selecting one or more messages to perform a bulk action
     * (e.g., delete, archive, mark as read).
     *
     * This state is typically entered when a user long-presses a message or taps the selection checkbox,
     * enabling a multi-select mode in the UI.
     */
    data class SelectingMessages(
        override val metadata: MessageListMetadata,
        override val preferences: MessageListPreferences,
        override val messages: ImmutableList<MessageItemUi>,
        override val content: MessageListContent = messages.toMessagesContent(),
        override val contactIdentities: Map<SenderIdentity, ContactIdentity> = persistentMapOf(),
    ) : MessageListState {
        val selectedCount: Int = messages.count { it.selected }
    }
}
