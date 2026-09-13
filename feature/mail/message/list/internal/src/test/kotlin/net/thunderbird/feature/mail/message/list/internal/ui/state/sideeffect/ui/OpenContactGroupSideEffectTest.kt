package net.thunderbird.feature.mail.message.list.internal.ui.state.sideeffect.ui

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.common.state.sideeffect.StateSideEffectHandler
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.feature.mail.message.list.aggregation.model.ContactAggregationKey
import net.thunderbird.feature.mail.message.list.internal.fakes.RecordingSuspendFunction
import net.thunderbird.feature.mail.message.list.internal.ui.state.sideeffect.BaseSideEffectHandlerTest
import net.thunderbird.feature.mail.message.list.ui.effect.MessageListEffect
import net.thunderbird.feature.mail.message.list.ui.event.MessageListEvent

class OpenContactGroupSideEffectTest : BaseSideEffectHandlerTest() {

    @Test
    fun `handle() should return Consumed when event opens a sender that is identified by an address`() = runTest {
        // Arrange
        val testSubject = createTestSubject()

        // Act
        val result = testSubject.handle(
            event = MessageListEvent.OpenContactGroup(ContactAggregationKey.EmailAddress("foo@example.com")),
            oldState = createLoadedMessagesState(),
            newState = createLoadedMessagesState(),
        )

        // Assert
        assertThat(result).isEqualTo(StateSideEffectHandler.ConsumeResult.Consumed)
    }

    @Test
    fun `handle() should dispatch OpenContactGroup effect with the normalized address`() = runTest {
        // Arrange
        val dispatchUiEffect = RecordingSuspendFunction<MessageListEffect>()
        val testSubject = createTestSubject(dispatchUiEffect = dispatchUiEffect.function)

        // Act
        testSubject.handle(
            event = MessageListEvent.OpenContactGroup(ContactAggregationKey.EmailAddress("foo@example.com")),
            oldState = createLoadedMessagesState(),
            newState = createLoadedMessagesState(),
        )

        // Assert
        assertThat(dispatchUiEffect.calls)
            .containsExactly(MessageListEffect.OpenContactGroup(senderAddresses = listOf("foo@example.com")))
    }

    @Test
    fun `handle() should return Ignored for a sender without an address`() = runTest {
        // Arrange
        val dispatchUiEffect = RecordingSuspendFunction<MessageListEffect>()
        val testSubject = createTestSubject(dispatchUiEffect = dispatchUiEffect.function)

        // Act
        val result = testSubject.handle(
            event = MessageListEvent.OpenContactGroup(ContactAggregationKey.UnknownSender(messageId = "1")),
            oldState = createLoadedMessagesState(),
            newState = createLoadedMessagesState(),
        )

        // Assert
        assertThat(result).isEqualTo(StateSideEffectHandler.ConsumeResult.Ignored)
        assertThat(dispatchUiEffect.calls).isEmpty()
    }

    @Test
    fun `handle() should return Ignored when the event is not OpenContactGroup`() = runTest {
        // Arrange
        val testSubject = createTestSubject()

        // Act
        val result = testSubject.handle(
            event = MessageListEvent.EnterSelectionMode,
            oldState = createLoadedMessagesState(),
            newState = createLoadedMessagesState(),
        )

        // Assert
        assertThat(result).isEqualTo(StateSideEffectHandler.ConsumeResult.Ignored)
    }

    @Test
    fun `Factory should create an OpenContactGroupSideEffect instance`() = runTest {
        // Arrange
        val factory = OpenContactGroupSideEffect.Factory(logger = TestLogger())

        // Act
        val result = factory.create(scope = this, dispatch = {}, dispatchUiEffect = {})

        // Assert
        assertThat(result).isInstanceOf(OpenContactGroupSideEffect::class)
    }

    private fun createTestSubject(
        dispatchUiEffect: suspend (MessageListEffect) -> Unit = {},
    ) = OpenContactGroupSideEffect(
        logger = TestLogger(),
        dispatch = {},
        dispatchUiEffect = dispatchUiEffect,
    )
}
