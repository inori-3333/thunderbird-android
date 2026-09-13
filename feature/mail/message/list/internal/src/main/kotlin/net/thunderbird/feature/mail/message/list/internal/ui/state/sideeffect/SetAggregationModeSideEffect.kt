package net.thunderbird.feature.mail.message.list.internal.ui.state.sideeffect

import kotlinx.coroutines.CoroutineScope
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.mail.message.list.domain.DomainContract
import net.thunderbird.feature.mail.message.list.ui.effect.MessageListEffect
import net.thunderbird.feature.mail.message.list.ui.event.MessageListEvent
import net.thunderbird.feature.mail.message.list.ui.state.MessageListState
import net.thunderbird.feature.mail.message.list.ui.state.sideeffect.MessageListStateSideEffectHandler
import net.thunderbird.feature.mail.message.list.ui.state.sideeffect.MessageListStateSideEffectHandlerFactory

private const val TAG = "SetAggregationModeSideEffect"

/**
 * Persists the aggregation mode the user selected.
 *
 * The updated preferences are emitted by `LoadPreferencesSideEffect` and applied to the state via
 * [MessageListEvent.UpdatePreferences].
 */
internal class SetAggregationModeSideEffect(
    private val logger: Logger,
    private val setMessageListAggregationMode: DomainContract.UseCase.SetMessageListAggregationMode,
    dispatch: suspend (MessageListEvent) -> Unit,
) : MessageListStateSideEffectHandler(logger, dispatch) {
    override fun accept(event: MessageListEvent, oldState: MessageListState, newState: MessageListState): Boolean =
        event is MessageListEvent.SetAggregationMode

    override suspend fun consume(
        event: MessageListEvent,
        oldState: MessageListState,
        newState: MessageListState,
    ): ConsumeResult {
        if (event !is MessageListEvent.SetAggregationMode) return ConsumeResult.Ignored
        logger.verbose(TAG) { "$TAG.handle() called with: mode = ${event.mode}" }
        setMessageListAggregationMode(event.mode)
        return ConsumeResult.Consumed
    }

    class Factory(
        private val logger: Logger,
        private val setMessageListAggregationMode: DomainContract.UseCase.SetMessageListAggregationMode,
    ) : MessageListStateSideEffectHandlerFactory {
        override fun create(
            scope: CoroutineScope,
            dispatch: suspend (MessageListEvent) -> Unit,
            dispatchUiEffect: suspend (MessageListEffect) -> Unit,
        ): MessageListStateSideEffectHandler = SetAggregationModeSideEffect(
            logger = logger,
            setMessageListAggregationMode = setMessageListAggregationMode,
            dispatch = dispatch,
        )
    }
}
