package net.thunderbird.feature.mail.message.list.internal.ui.state.sideeffect

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.common.state.sideeffect.StateSideEffectHandler
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.core.preference.display.visualSettings.message.list.MessageListAggregationMode
import net.thunderbird.feature.mail.message.list.domain.DomainContract
import net.thunderbird.feature.mail.message.list.internal.fakes.RecordingSuspendFunction
import net.thunderbird.feature.mail.message.list.ui.event.MessageListEvent

class SetAggregationModeSideEffectTest : BaseSideEffectHandlerTest() {

    @Test
    fun `handle() should return Consumed when the aggregation mode changes`() = runTest {
        // Arrange
        val testSubject = createTestSubject()

        // Act
        val result = testSubject.handle(
            event = MessageListEvent.SetAggregationMode(MessageListAggregationMode.CONTACT),
            oldState = createLoadedMessagesState(),
            newState = createLoadedMessagesState(),
        )

        // Assert
        assertThat(result).isEqualTo(StateSideEffectHandler.ConsumeResult.Consumed)
    }

    @Test
    fun `handle() should save the selected aggregation mode`() = runTest {
        // Arrange
        val setAggregationMode = RecordingSuspendFunction<MessageListAggregationMode>()
        val testSubject = createTestSubject(setAggregationMode = setAggregationMode.function)

        // Act
        testSubject.handle(
            event = MessageListEvent.SetAggregationMode(MessageListAggregationMode.CONTACT),
            oldState = createLoadedMessagesState(),
            newState = createLoadedMessagesState(),
        )

        // Assert
        assertThat(setAggregationMode.calls).containsExactly(MessageListAggregationMode.CONTACT)
    }

    @Test
    fun `handle() should return Ignored when the event is not SetAggregationMode`() = runTest {
        // Arrange
        val setAggregationMode = RecordingSuspendFunction<MessageListAggregationMode>()
        val testSubject = createTestSubject(setAggregationMode = setAggregationMode.function)

        // Act
        val result = testSubject.handle(
            event = MessageListEvent.EnterSelectionMode,
            oldState = createLoadedMessagesState(),
            newState = createLoadedMessagesState(),
        )

        // Assert
        assertThat(result).isEqualTo(StateSideEffectHandler.ConsumeResult.Ignored)
        assertThat(setAggregationMode.calls).isEmpty()
    }

    @Test
    fun `Factory should create a SetAggregationModeSideEffect instance`() = runTest {
        // Arrange
        val factory = SetAggregationModeSideEffect.Factory(
            logger = TestLogger(),
            setMessageListAggregationMode = DomainContract.UseCase.SetMessageListAggregationMode { },
        )

        // Act
        val result = factory.create(scope = this, dispatch = {}, dispatchUiEffect = {})

        // Assert
        assertThat(result).isInstanceOf(SetAggregationModeSideEffect::class)
    }

    private fun createTestSubject(
        setAggregationMode: suspend (MessageListAggregationMode) -> Unit = {},
    ) = SetAggregationModeSideEffect(
        logger = TestLogger(),
        setMessageListAggregationMode = DomainContract.UseCase.SetMessageListAggregationMode(setAggregationMode),
        dispatch = {},
    )
}
