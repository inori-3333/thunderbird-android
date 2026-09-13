package net.thunderbird.feature.mail.message.list.internal.aggregation

import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.mail.message.list.aggregation.model.ContactAggregationKey
import net.thunderbird.feature.mail.message.list.aggregation.model.ContactIdentity
import net.thunderbird.feature.mail.message.list.aggregation.model.SenderIdentity
import net.thunderbird.feature.mail.message.list.aggregation.model.createSenderIdentityOrNull
import net.thunderbird.feature.mail.message.list.ui.state.Account
import net.thunderbird.feature.mail.message.list.ui.state.ComposedAddressUi
import net.thunderbird.feature.mail.message.list.ui.state.MessageItemUi

class ContactMessageAggregatorTest {
    private val testSubject = ContactMessageAggregator()

    @Test
    fun `aggregate should group messages of the same address`() {
        // Arrange
        val messages = listOf(
            createMessage(id = "1", address = "foo@example.com"),
            createMessage(id = "2", address = "foo@example.com"),
        )

        // Act
        val result = testSubject.aggregate(messages)

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result.first().messageCount).isEqualTo(2)
        assertThat(result.first().key).isEqualTo(ContactAggregationKey.EmailAddress("foo@example.com"))
    }

    @Test
    fun `aggregate should not group messages of different addresses with the same display name`() {
        // Arrange
        val messages = listOf(
            createMessage(id = "1", address = "a@github.com", displayName = "GitHub"),
            createMessage(id = "2", address = "b@github.com", displayName = "GitHub"),
        )

        // Act
        val result = testSubject.aggregate(messages)

        // Assert
        assertThat(result).hasSize(2)
    }

    @Test
    fun `aggregate should group addresses that only differ in casing`() {
        // Arrange
        val messages = listOf(
            createMessage(id = "1", address = "Foo@Example.com"),
            createMessage(id = "2", address = "foo@example.COM"),
        )

        // Act
        val result = testSubject.aggregate(messages)

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result.first().key).isEqualTo(ContactAggregationKey.EmailAddress("foo@example.com"))
    }

    @Test
    fun `aggregate should sort groups by the date of their latest message descending`() {
        // Arrange
        val messages = listOf(
            createMessage(id = "old", address = "old@example.com", sortTimestamp = 100L),
            createMessage(id = "newest", address = "newest@example.com", sortTimestamp = 300L),
            createMessage(id = "middle", address = "middle@example.com", sortTimestamp = 200L),
        )

        // Act
        val result = testSubject.aggregate(messages)

        // Assert
        val addresses = result.map { group -> (group.key as ContactAggregationKey.EmailAddress).normalizedAddress }
        assertThat(addresses).containsExactly("newest@example.com", "middle@example.com", "old@example.com")
    }

    @Test
    fun `aggregate should use the latest message of a group`() {
        // Arrange
        val messages = listOf(
            createMessage(id = "new", address = "foo@example.com", subject = "New", sortTimestamp = 200L),
            createMessage(id = "old", address = "foo@example.com", subject = "Old", sortTimestamp = 100L),
        )

        // Act
        val result = testSubject.aggregate(messages)

        // Assert
        assertThat(result.first().latestMessage.id).isEqualTo("new")
    }

    @Test
    fun `aggregate should count unread messages`() {
        // Arrange
        val messages = listOf(
            createMessage(id = "1", address = "foo@example.com", isRead = false),
            createMessage(id = "2", address = "foo@example.com", isRead = true),
            createMessage(id = "3", address = "foo@example.com", isRead = false),
        )

        // Act
        val result = testSubject.aggregate(messages)

        // Assert
        assertThat(result.first().unreadCount).isEqualTo(2)
        assertThat(result.first().messageCount).isEqualTo(3)
    }

    @Test
    fun `aggregate should set hasStarredMessage when at least one message is starred`() {
        // Arrange
        val messages = listOf(
            createMessage(id = "1", address = "foo@example.com", starred = false),
            createMessage(id = "2", address = "foo@example.com", starred = true),
        )

        // Act
        val result = testSubject.aggregate(messages)

        // Assert
        assertThat(result.first().hasStarredMessage).isEqualTo(true)
    }

    @Test
    fun `aggregate should merge messages that are loaded later into the existing group`() {
        // Arrange
        val firstPage = listOf(
            createMessage(id = "a", address = "a@example.com"),
            createMessage(id = "b", address = "b@example.com"),
        )
        val secondPage = firstPage + createMessage(id = "a2", address = "a@example.com")

        // Act
        val firstResult = testSubject.aggregate(firstPage)
        val secondResult = testSubject.aggregate(secondPage)

        // Assert
        assertThat(firstResult).hasSize(2)
        assertThat(firstResult.first { it.key == ContactAggregationKey.EmailAddress("a@example.com") }.messageCount)
            .isEqualTo(1)
        assertThat(secondResult).hasSize(2)
        assertThat(secondResult.first { it.key == ContactAggregationKey.EmailAddress("a@example.com") }.messageCount)
            .isEqualTo(2)
    }

    @Test
    fun `aggregate should group the same sender of different accounts`() {
        // Arrange
        val accountId = AccountIdFactory.create()
        val otherAccountId = AccountIdFactory.create()
        val messages = listOf(
            createMessage(id = "1", address = "foo@example.com", accountId = accountId),
            createMessage(id = "2", address = "foo@example.com", accountId = otherAccountId),
        )

        // Act
        val result = testSubject.aggregate(messages)

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result.first().accountIds).isEqualTo(setOf(accountId, otherAccountId))
    }

    @Test
    fun `aggregate should not merge messages without a sender address`() {
        // Arrange
        val messages = listOf(
            createMessage(id = "1", address = null),
            createMessage(id = "2", address = null),
        )

        // Act
        val result = testSubject.aggregate(messages)

        // Assert
        assertThat(result).hasSize(2)
        assertThat(result.map { group -> group.key })
            .containsExactly(
                ContactAggregationKey.UnknownSender("1"),
                ContactAggregationKey.UnknownSender("2"),
            )
        assertThat(result.first().sender).isNull()
    }

    @Test
    fun `aggregate should merge several addresses of the same contact into one group`() {
        // Arrange
        val work = sender("zhangsan@company.com")
        val private = sender("zhangsan@qq.com")
        val contactIdentities = mapOf(
            work to contactIdentity(
                contactId = 42L,
                primaryAddress = "zhangsan@qq.com",
                addresses = setOf("zhangsan@company.com", "zhangsan@qq.com"),
            ),
            private to contactIdentity(
                contactId = 42L,
                primaryAddress = "zhangsan@qq.com",
                addresses = setOf("zhangsan@company.com", "zhangsan@qq.com"),
            ),
        )
        val messages = listOf(
            createMessage(id = "1", address = "zhangsan@company.com", subject = "Work"),
            createMessage(id = "2", address = "zhangsan@qq.com", subject = "Private"),
        )

        // Act
        val result = testSubject.aggregate(messages, contactIdentities)

        // Assert
        assertThat(result).hasSize(1)
        val group = result.single()
        assertThat(group.key).isEqualTo(ContactAggregationKey.AndroidContact(contactId = 42L))
        assertThat(group.messageCount).isEqualTo(2)
        assertThat(group.addresses).isEqualTo(setOf("zhangsan@company.com", "zhangsan@qq.com"))
        assertThat(group.contact?.displayName).isEqualTo("张三")
    }

    @Test
    fun `aggregate should keep senders without a contact in their own group`() {
        // Arrange
        val known = sender("zhangsan@qq.com")
        val contactIdentities = mapOf(
            known to contactIdentity(
                contactId = 42L,
                primaryAddress = "zhangsan@qq.com",
                addresses = setOf("zhangsan@qq.com"),
            ),
        )
        val messages = listOf(
            createMessage(id = "1", address = "zhangsan@qq.com"),
            createMessage(id = "2", address = "other@example.com"),
        )

        // Act
        val result = testSubject.aggregate(messages, contactIdentities)

        // Assert
        assertThat(result).hasSize(2)
        assertThat(result.map { it.key }.toSet()).isEqualTo(
            setOf(
                ContactAggregationKey.AndroidContact(contactId = 42L),
                ContactAggregationKey.EmailAddress("other@example.com"),
            ),
        )
    }

    @Test
    fun `aggregate should fall back to the email address when the identities are empty`() {
        // Arrange
        val messages = listOf(
            createMessage(id = "1", address = "foo@example.com"),
            createMessage(id = "2", address = "foo@example.com"),
        )

        // Act
        val result = testSubject.aggregate(messages, emptyMap())

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result.single().key).isEqualTo(ContactAggregationKey.EmailAddress("foo@example.com"))
        assertThat(result.single().addresses).isEqualTo(setOf("foo@example.com"))
    }

    @Test
    fun `aggregate should return an empty list when there are no messages`() {
        assertThat(testSubject.aggregate(emptyList())).hasSize(0)
    }

    @Suppress("LongParameterList")
    private fun createMessage(
        id: String,
        address: String?,
        displayName: String? = null,
        subject: String? = null,
        sortTimestamp: Long = 0L,
        isRead: Boolean = false,
        starred: Boolean = false,
        accountId: AccountId = AccountIdFactory.create(),
    ): MessageItemUi {
        val senderIdentity: SenderIdentity? = createSenderIdentityOrNull(address = address, displayName = displayName)
        return MessageItemUi(
            state = if (isRead) MessageItemUi.State.Read else MessageItemUi.State.Unread,
            id = id,
            messageReference = "reference-$id",
            account = Account(id = accountId, color = Color.Unspecified),
            senders = ComposedAddressUi(displayName = address.orEmpty()),
            senderIdentity = senderIdentity,
            subject = subject ?: "subject-$id",
            excerpt = "excerpt",
            formattedReceivedAt = "12:00",
            sortTimestamp = sortTimestamp,
            hasAttachments = false,
            starred = starred,
            encrypted = false,
            answered = false,
            forwarded = false,
            selected = false,
        )
    }

    private fun sender(address: String, displayName: String? = null): SenderIdentity =
        checkNotNull(createSenderIdentityOrNull(address = address, displayName = displayName))

    private fun contactIdentity(
        contactId: Long,
        primaryAddress: String,
        addresses: Set<String>,
    ): ContactIdentity = ContactIdentity(
        key = ContactAggregationKey.AndroidContact(contactId = contactId),
        displayName = "张三",
        primaryAddress = primaryAddress,
        addresses = addresses,
    )
}
