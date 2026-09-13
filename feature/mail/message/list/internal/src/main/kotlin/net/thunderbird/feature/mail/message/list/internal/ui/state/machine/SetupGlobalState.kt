package net.thunderbird.feature.mail.message.list.internal.ui.state.machine

import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import net.thunderbird.core.common.state.builder.BaseStateBuilder
import net.thunderbird.core.common.state.builder.StateMachineBuilder
import net.thunderbird.feature.account.UnifiedAccountId
import net.thunderbird.feature.mail.message.list.ui.event.FolderEvent
import net.thunderbird.feature.mail.message.list.ui.event.MessageItemEvent
import net.thunderbird.feature.mail.message.list.ui.event.MessageListEvent
import net.thunderbird.feature.mail.message.list.ui.state.MessageListState

/**
 * Sets up global state transitions that can occur from any state.
 *
 * This includes transitions for events that are not specific to a single state,
 * such as updating user preferences. By defining these transitions on the parent
 * [MessageListState], we avoid duplicating the logic in every single sub-state.
 */
internal fun StateMachineBuilder<MessageListState, MessageListEvent>.globalState(
    contentFactory: ContentFactory,
) {
    state<MessageListState> {
        preferencesTransitions(contentFactory)
        folderTransitions()
        selectionTransitions()
        focusTransitions()
        legacyTransitions()
    }
}

/**
 * Transitions for the user preferences and the message list metadata that is derived from them.
 */
private fun BaseStateBuilder<MessageListState, MessageListState, MessageListEvent>.preferencesTransitions(
    contentFactory: ContentFactory,
) {
    transition<MessageListEvent.UpdatePreferences> { state, event ->
        state.withUpdate(
            content = contentFactory(
                state.metadata,
                event.preferences.aggregationMode,
                state.messages,
                state.contactIdentities,
            ),
            preferences = event.preferences,
        )
    }

    transition<MessageListEvent.ContactIdentitiesResolved> { state, event ->
        state.withContactIdentities(contactIdentities = event.contactIdentities) { contactIdentities ->
            contentFactory(
                state.metadata,
                requireNotNull(state.preferences).aggregationMode,
                state.messages,
                contactIdentities,
            )
        }
    }

    transition<MessageListEvent.ChangeSortCriteria> { state, (accountId, sortCriteria) ->
        val newSortCriteriaPerAccount = state.metadata.sortCriteriaPerAccount + (accountId to sortCriteria)
        state.withMetadata { copy(sortCriteriaPerAccount = newSortCriteriaPerAccount.toPersistentMap()) }
    }

    transition<MessageListEvent.SwipeActionsLoaded> { state, (swipeActions) ->
        state.withMetadata { copy(swipeActions = swipeActions.toImmutableMap()) }
    }
}

/**
 * Transitions for the folder that is currently displayed.
 */
private fun BaseStateBuilder<MessageListState, MessageListState, MessageListEvent>.folderTransitions() {
    transition<FolderEvent.FolderLoaded> { state, (folder) ->
        state.withMetadata {
            copy(
                folder = folder,
                showAccountIndicator = folder.account.id == UnifiedAccountId,
                contactAggregationAvailable = isContactAggregationAvailable(folderType = folder.type),
            )
        }
    }
}

/**
 * Transitions for the message selection.
 *
 * Selection is not available while contact aggregation is active. The content therefore still matches the
 * messages after toggling the selection.
 */
private fun BaseStateBuilder<MessageListState, MessageListState, MessageListEvent>.selectionTransitions() {
    transition<MessageItemEvent.SelectAll> { state, _ ->
        MessageListState.SelectingMessages(
            metadata = state.metadata,
            preferences = requireNotNull(state.preferences),
            messages = state.messages.map { it.copy(selected = true) }.toImmutableList(),
            content = state.content,
        )
    }

    transition<MessageItemEvent.DeselectAll> { state, _ ->
        MessageListState.LoadedMessages(
            metadata = state.metadata,
            preferences = requireNotNull(state.preferences),
            messages = state.messages.map { it.copy(selected = false) }.toImmutableList(),
            content = state.content,
        )
    }
}

/**
 * Transitions for the focus of the message list.
 */
private fun BaseStateBuilder<MessageListState, MessageListState, MessageListEvent>.focusTransitions() {
    transition<MessageItemEvent.OnFocusEnter> { currentState, event ->
        currentState.withMetadata { copy(focusedMessage = event.message) }
    }

    transition<MessageItemEvent.OnFocusExit> { currentState, _ ->
        currentState.withMetadata { copy(focusedMessage = null) }
    }
}

/**
 * Transitions that only exist to support the legacy message list.
 */
private fun BaseStateBuilder<MessageListState, MessageListState, MessageListEvent>.legacyTransitions() {
    transition<MessageListEvent.UpdateFooter> { state, event ->
        state.withMetadata {
            val showFooter = event.footer?.isNotBlank() == true
            copy(footer = footer.copy(showFooter = showFooter, text = event.footer.orEmpty()))
        }
    }
}
