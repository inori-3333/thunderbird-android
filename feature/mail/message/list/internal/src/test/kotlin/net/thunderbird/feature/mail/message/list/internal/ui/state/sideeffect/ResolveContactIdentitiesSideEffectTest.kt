package net.thunderbird.feature.mail.message.list.internal.ui.state.sideeffect

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.common.state.sideeffect.StateSideEffectHandler
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.core.preference.display.visualSettings.message.list.MessageListAggregationMode
import net.thunderbird.feature.mail.message.list.aggregation.ContactIdentityResolver
import net.thunderbird.feature.mail.message.list.aggregation.model.ContactAggregationKey
import net.thunderbird.feature.mail.message.list.aggregation.model.ContactIdentity
import net.thunderbird.feature.mail.message.list.aggregation.model.SenderIdentity
import net.thunderbird.feature.mail.message.list.aggregation.model.createSenderIdentityOrNull
import net.thunderbird.feature.mail.message.list.internal.fakes.RecordingSuspendFunction
import net.thunderbird.feature.mail.message.list.ui.event.MessageListEvent
import net.thunderbird.feature.mail.message.list.ui.state.MessageItemUi
import net.thunderbird.feature.mail.message.list.ui.state.MessageListContent
import net.thunderbird.feature.mail.message.list.ui.state.MessageListState

@OptIn(ExperimentalCoroutinesApi::class)
class ResolveContactIdentitiesSideEffectTest : BaseSideEffectHandlerTest() {

    @Test
    fun `handle() should resolve the senders and dispatch ContactIdentitiesResolved`() = runTest {
        // Arrange
        val message = createMessageItemUi(id = "1").withSender("foo@example.com")
        val resolver = FakeContactIdentityResolver()
        val dispatch = RecordingSuspendFunction<MessageListEvent>()
        val testSubject = createTestSubject(contactIdentityResolver = resolver, dispatch = dispatch.function)

        // Act
        val result = testSubject.handle(
            event = MessageListEvent.UpdateLoadingProgress(progress = 1f, messages = listOf(message)),
            oldState = loadedState(messages = emptyList()),
            newState = contactAggregatedState(messages = listOf(message)),
        )
        advanceUntilIdle()

        // Assert
        assertThat(result).isEqualTo(StateSideEffectHandler.ConsumeResult.Consumed)
        assertThat(resolver.requests).hasSize(1)
        assertThat(resolver.requests.single().map { it.normalizedAddress }).containsExactly("foo@example.com")

        val dispatched = dispatch.calls.single()
        assertThat(dispatched).isInstanceOf<MessageListEvent.ContactIdentitiesResolved>()
        assertThat((dispatched as MessageListEvent.ContactIdentitiesResolved).contactIdentities.keys.single())
            .isEqualTo(resolver.requests.single().single())
    }

    @Test
    fun `handle() should return Ignored while contact aggregation is disabled`() = runTest {
        // Arrange
        val message = createMessageItemUi(id = "1").withSender("foo@example.com")
        val resolver = FakeContactIdentityResolver()
        val testSubject = createTestSubject(contactIdentityResolver = resolver)

        // Act
        val result = testSubject.handle(
            event = MessageListEvent.UpdateLoadingProgress(progress = 1f, messages = listOf(message)),
            oldState = loadedState(messages = emptyList()),
            newState = loadedState(messages = listOf(message)),
        )
        advanceUntilIdle()

        // Assert
        assertThat(result).isEqualTo(StateSideEffectHandler.ConsumeResult.Ignored)
        assertThat(resolver.requests).hasSize(0)
    }

    @Test
    fun `handle() should not resolve again when the senders did not change`() = runTest {
        // Arrange
        val message = createMessageItemUi(id = "1").withSender("foo@example.com")
        val resolver = FakeContactIdentityResolver()
        val testSubject = createTestSubject(contactIdentityResolver = resolver)

        // Act
        testSubject.handle(
            event = MessageListEvent.EnterSelectionMode,
            oldState = contactAggregatedState(messages = listOf(message)),
            newState = contactAggregatedState(messages = listOf(message)),
        )
        advanceUntilIdle()
        testSubject.handle(
            event = MessageListEvent.EnterSelectionMode,
            oldState = contactAggregatedState(messages = listOf(message)),
            newState = contactAggregatedState(messages = listOf(message.copy(selected = true))),
        )
        advanceUntilIdle()

        // Assert
        assertThat(resolver.requests).hasSize(1)
    }

    @Test
    fun `handle() should resolve the new senders when load more adds a sender`() = runTest {
        // Arrange
        val first = createMessageItemUi(id = "1").withSender("foo@example.com")
        val second = createMessageItemUi(id = "2").withSender("bar@example.com")
        val resolver = FakeContactIdentityResolver()
        val testSubject = createTestSubject(contactIdentityResolver = resolver)

        // Act
        testSubject.handle(
            event = MessageListEvent.EnterSelectionMode,
            oldState = contactAggregatedState(messages = emptyList()),
            newState = contactAggregatedState(messages = listOf(first)),
        )
        advanceUntilIdle()
        testSubject.handle(
            event = MessageListEvent.EnterSelectionMode,
            oldState = contactAggregatedState(messages = listOf(first)),
            newState = contactAggregatedState(messages = listOf(first, second)),
        )
        advanceUntilIdle()

        // Assert
        assertThat(resolver.requests).hasSize(2)
        assertThat(resolver.requests.last().map { it.normalizedAddress }.toSet())
            .isEqualTo(setOf("foo@example.com", "bar@example.com"))
    }

    @Test
    fun `handle() should return Ignored when there are no senders`() = runTest {
        // Arrange
        val resolver = FakeContactIdentityResolver()
        val testSubject = createTestSubject(contactIdentityResolver = resolver)

        // Act
        val result = testSubject.handle(
            event = MessageListEvent.UpdateLoadingProgress(progress = 1f),
            oldState = loadedState(messages = emptyList()),
            newState = contactAggregatedState(messages = emptyList()),
        )
        advanceUntilIdle()

        // Assert
        assertThat(result).isEqualTo(StateSideEffectHandler.ConsumeResult.Ignored)
        assertThat(resolver.requests).hasSize(0)
    }

    @Test
    fun `Factory should create a ResolveContactIdentitiesSideEffect instance`() = runTest {
        // Arrange
        val factory = ResolveContactIdentitiesSideEffect.Factory(
            logger = TestLogger(),
            contactIdentityResolver = FakeContactIdentityResolver(),
        )

        // Act
        val result = factory.create(scope = this, dispatch = {}, dispatchUiEffect = {})

        // Assert
        assertThat(result).isInstanceOf(ResolveContactIdentitiesSideEffect::class)
    }

    private fun TestScope.createTestSubject(
        contactIdentityResolver: ContactIdentityResolver,
        dispatch: suspend (MessageListEvent) -> Unit = {},
    ) = ResolveContactIdentitiesSideEffect(
        scope = this,
        logger = TestLogger(),
        contactIdentityResolver = contactIdentityResolver,
        dispatch = dispatch,
    )

    private fun loadedState(messages: List<MessageItemUi>): MessageListState = createLoadedMessagesState()
        .withUpdate(
            content = MessageListContent.Messages(items = messages.toPersistentList()),
            messages = messages.toPersistentList(),
        )

    private fun contactAggregatedState(messages: List<MessageItemUi>): MessageListState = loadedState(messages)
        .withUpdate(
            content = MessageListContent.Messages(items = messages.toPersistentList()),
            preferences = createMessageListPreferences(aggregationMode = MessageListAggregationMode.CONTACT),
        )

    private fun MessageItemUi.withSender(address: String): MessageItemUi = copy(
        senderIdentity = createSenderIdentityOrNull(address = address, displayName = null),
    )

    private class FakeContactIdentityResolver : ContactIdentityResolver {
        val requests = mutableListOf<Set<SenderIdentity>>()

        override suspend fun resolve(senders: Set<SenderIdentity>): Map<SenderIdentity, ContactIdentity> {
            requests += senders
            return senders.associateWith { sender ->
                ContactIdentity(
                    key = ContactAggregationKey.EmailAddress(sender.normalizedAddress),
                    displayName = sender.displayName,
                    primaryAddress = sender.rawAddress,
                    addresses = setOf(sender.normalizedAddress),
                )
            }
        }
    }
}
