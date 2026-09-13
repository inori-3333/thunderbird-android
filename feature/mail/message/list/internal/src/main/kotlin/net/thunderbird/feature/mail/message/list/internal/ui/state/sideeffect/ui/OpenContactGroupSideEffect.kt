package net.thunderbird.feature.mail.message.list.internal.ui.state.sideeffect.ui

import kotlinx.coroutines.CoroutineScope
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.mail.message.list.aggregation.model.ContactAggregationKey
import net.thunderbird.feature.mail.message.list.ui.effect.MessageListEffect
import net.thunderbird.feature.mail.message.list.ui.event.MessageListEvent
import net.thunderbird.feature.mail.message.list.ui.state.MessageListContent
import net.thunderbird.feature.mail.message.list.ui.state.MessageListState
import net.thunderbird.feature.mail.message.list.ui.state.sideeffect.MessageListStateSideEffectHandler
import net.thunderbird.feature.mail.message.list.ui.state.sideeffect.MessageListStateSideEffectHandlerFactory

private const val TAG = "OpenContactGroupSideEffect"

/**
 * Requests the navigation to the message list of a single sender.
 *
 * A group that was resolved to an Android contact is opened for all of its addresses, so that the message list
 * of the contact is complete. Groups without a usable address are ignored.
 */
internal class OpenContactGroupSideEffect(
    private val logger: Logger,
    dispatch: suspend (MessageListEvent) -> Unit,
    dispatchUiEffect: suspend (MessageListEffect) -> Unit,
) : MessageListStateSideEffectHandler(logger, dispatch, dispatchUiEffect) {
    override fun accept(event: MessageListEvent, oldState: MessageListState, newState: MessageListState): Boolean =
        event is MessageListEvent.OpenContactGroup

    override suspend fun consume(
        event: MessageListEvent,
        oldState: MessageListState,
        newState: MessageListState,
    ): ConsumeResult = when (event) {
        is MessageListEvent.OpenContactGroup -> openGroup(event.key, newState)
        else -> ConsumeResult.Ignored
    }

    private suspend fun openGroup(key: ContactAggregationKey, state: MessageListState): ConsumeResult {
        val senderAddresses = when (key) {
            is ContactAggregationKey.EmailAddress -> listOf(key.normalizedAddress)
            is ContactAggregationKey.AndroidContact -> addressesOf(key, state)
            is ContactAggregationKey.UnknownSender -> emptyList()
        }
        if (senderAddresses.isEmpty()) return ConsumeResult.Ignored

        logger.verbose(TAG) { "$TAG.handle() called with a sender group of ${senderAddresses.size} addresses" }
        dispatchUiEffect(MessageListEffect.OpenContactGroup(senderAddresses = senderAddresses))
        return ConsumeResult.Consumed
    }

    private fun addressesOf(key: ContactAggregationKey, state: MessageListState): List<String> {
        val groups = (state.content as? MessageListContent.ContactGroups)?.items.orEmpty()
        return groups
            .firstOrNull { group -> group.key == key }
            ?.addresses
            ?.toList()
            .orEmpty()
    }

    class Factory(
        private val logger: Logger,
    ) : MessageListStateSideEffectHandlerFactory {
        override fun create(
            scope: CoroutineScope,
            dispatch: suspend (MessageListEvent) -> Unit,
            dispatchUiEffect: suspend (MessageListEffect) -> Unit,
        ): MessageListStateSideEffectHandler = OpenContactGroupSideEffect(
            logger = logger,
            dispatch = dispatch,
            dispatchUiEffect = dispatchUiEffect,
        )
    }
}
