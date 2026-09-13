package net.thunderbird.feature.mail.message.list.internal.ui.state.sideeffect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.thunderbird.core.logging.Logger
import net.thunderbird.core.preference.display.visualSettings.message.list.MessageListAggregationMode
import net.thunderbird.feature.mail.message.list.aggregation.ContactIdentityResolver
import net.thunderbird.feature.mail.message.list.aggregation.model.SenderIdentity
import net.thunderbird.feature.mail.message.list.ui.effect.MessageListEffect
import net.thunderbird.feature.mail.message.list.ui.event.MessageListEvent
import net.thunderbird.feature.mail.message.list.ui.state.MessageListState
import net.thunderbird.feature.mail.message.list.ui.state.sideeffect.MessageListStateSideEffectHandler
import net.thunderbird.feature.mail.message.list.ui.state.sideeffect.MessageListStateSideEffectHandlerFactory

private const val TAG = "ResolveContactIdentities"

/**
 * Resolves the senders of the loaded messages to the Android contacts they belong to.
 *
 * The messages are displayed with their sender addresses first. This side effect enhances the list
 * asynchronously, so a slow contacts provider can never delay the first rendering of the message list.
 *
 * Resolution only runs while contact aggregation is active. The resolved identities are kept when the user
 * switches back to the normal message list, so switching back does not query the contacts provider again.
 */
internal class ResolveContactIdentitiesSideEffect(
    private val scope: CoroutineScope,
    private val logger: Logger,
    private val contactIdentityResolver: ContactIdentityResolver,
    dispatch: suspend (MessageListEvent) -> Unit,
) : MessageListStateSideEffectHandler(logger, dispatch) {
    private var running: Job? = null

    /**
     * The senders of the last resolution. Used to skip the contacts provider when the sender set didn't
     * change.
     */
    private var resolvedSenders: Set<SenderIdentity>? = null

    override fun accept(event: MessageListEvent, oldState: MessageListState, newState: MessageListState): Boolean {
        if (newState.preferences?.aggregationMode != MessageListAggregationMode.CONTACT) return false
        val senders = sendersOf(newState)
        return senders.isNotEmpty() && senders != resolvedSenders
    }

    override suspend fun consume(
        event: MessageListEvent,
        oldState: MessageListState,
        newState: MessageListState,
    ): ConsumeResult {
        val senders = sendersOf(newState)
        if (senders.isEmpty()) return ConsumeResult.Ignored

        // Mark the senders as handled *before* resolving so that the state updates caused by the contacts
        // being resolved do not start another lookup.
        resolvedSenders = senders
        running?.cancel()
        running = scope.launch {
            val contactIdentities = contactIdentityResolver.resolve(senders)
            logger.verbose(TAG) { "Resolved ${contactIdentities.size} senders" }
            dispatch(MessageListEvent.ContactIdentitiesResolved(contactIdentities))
        }
        return ConsumeResult.Consumed
    }

    private fun sendersOf(state: MessageListState): Set<SenderIdentity> = state.messages
        .mapNotNull { message -> message.senderIdentity?.takeIf { it.normalizedAddress.isNotBlank() } }
        .toSet()

    class Factory(
        private val logger: Logger,
        private val contactIdentityResolver: ContactIdentityResolver,
    ) : MessageListStateSideEffectHandlerFactory {
        override fun create(
            scope: CoroutineScope,
            dispatch: suspend (MessageListEvent) -> Unit,
            dispatchUiEffect: suspend (MessageListEffect) -> Unit,
        ): MessageListStateSideEffectHandler = ResolveContactIdentitiesSideEffect(
            scope = scope,
            logger = logger,
            contactIdentityResolver = contactIdentityResolver,
            dispatch = dispatch,
        )
    }
}
