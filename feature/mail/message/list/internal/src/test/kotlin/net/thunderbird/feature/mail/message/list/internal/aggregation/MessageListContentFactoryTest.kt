package net.thunderbird.feature.mail.message.list.internal.aggregation

import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import kotlin.test.Test
import net.thunderbird.core.preference.display.visualSettings.message.list.MessageListAggregationMode
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.mail.message.list.aggregation.model.ContactAggregationKey
import net.thunderbird.feature.mail.message.list.aggregation.model.ContactIdentity
import net.thunderbird.feature.mail.message.list.aggregation.model.createSenderIdentityOrNull
import net.thunderbird.feature.mail.message.list.internal.fakes.FakeStringsResourceManager
import net.thunderbird.feature.mail.message.list.ui.state.Account
import net.thunderbird.feature.mail.message.list.ui.state.ComposedAddressUi
import net.thunderbird.feature.mail.message.list.ui.state.MessageItemUi
import net.thunderbird.feature.mail.message.list.ui.state.MessageListContent
import net.thunderbird.feature.mail.message.list.R as MessageListApiR

class MessageListContentFactoryTest {
    private val stringsResourceManager = FakeStringsResourceManager()
        .withString(MessageListApiR.string.message_list_contact_group_unknown_sender, "Unknown sender")
        .withString(MessageListApiR.string.message_list_contact_group_unnamed_sender, "No name")
        .withString(MessageListApiR.string.message_list_contact_group_message_count, "%d messages")
        .withString(MessageListApiR.string.message_list_contact_group_unread_count, "%d unread")

    private val testSubject = MessageListContentFactory(
        contactMessageAggregator = ContactMessageAggregator(),
        stringsResourceManager = stringsResourceManager,
    )

    @Test
    fun `create should return the messages when aggregation is disabled`() {
        // Arrange
        val messages = listOf(createMessage(id = "1", address = "foo@example.com"))

        // Act
        val result = testSubject.create(aggregationMode = MessageListAggregationMode.NONE, messages = messages)

        // Assert
        assertThat(result).isInstanceOf(MessageListContent.Messages::class)
        assertThat((result as MessageListContent.Messages).items).hasSize(1)
    }

    @Test
    fun `create should return the sender groups when aggregation is enabled`() {
        // Arrange
        val messages = listOf(
            createMessage(id = "1", address = "foo@example.com", displayName = "Foo"),
            createMessage(id = "2", address = "foo@example.com", displayName = "Foo", isRead = true),
        )

        // Act
        val result = testSubject.create(aggregationMode = MessageListAggregationMode.CONTACT, messages = messages)

        // Assert
        assertThat(result).isInstanceOf(MessageListContent.ContactGroups::class)
        val groups = (result as MessageListContent.ContactGroups).items
        assertThat(groups).hasSize(1)
        assertThat(groups.first().title).isEqualTo("Foo")
        assertThat(groups.first().address).isEqualTo("foo@example.com")
        assertThat(groups.first().messageCount).isEqualTo(2)
        assertThat(groups.first().unreadCount).isEqualTo(1)
        assertThat(groups.first().messageCountLabel).isEqualTo("2 messages")
        assertThat(groups.first().unreadCountLabel).isEqualTo("1 unread")
    }

    @Test
    fun `create should fall back to the address when the sender has no display name`() {
        // Arrange
        val messages = listOf(createMessage(id = "1", address = "foo@example.com"))

        // Act
        val result = testSubject.create(aggregationMode = MessageListAggregationMode.CONTACT, messages = messages)

        // Assert
        val group = (result as MessageListContent.ContactGroups).items.first()
        assertThat(group.title).isEqualTo("foo@example.com")
    }

    @Test
    fun `create should not display an unread label for fully read groups`() {
        // Arrange
        val messages = listOf(createMessage(id = "1", address = "foo@example.com", isRead = true))

        // Act
        val result = testSubject.create(aggregationMode = MessageListAggregationMode.CONTACT, messages = messages)

        // Assert
        val group = (result as MessageListContent.ContactGroups).items.first()
        assertThat(group.unreadCount).isEqualTo(0)
        assertThat(group.unreadCountLabel).isNull()
    }

    @Test
    fun `create should use the contact name and picture when the sender was resolved`() {
        // Arrange
        val privateAddress = checkNotNull(
            createSenderIdentityOrNull(address = "zhangsan@qq.com", displayName = "Zhang San"),
        )
        val workAddress = checkNotNull(
            createSenderIdentityOrNull(address = "zhangsan@company.com", displayName = "Zhang San"),
        )
        val messages = listOf(
            createMessage(id = "1", address = "zhangsan@qq.com", displayName = "Zhang San"),
            createMessage(id = "2", address = "zhangsan@company.com", displayName = "Zhang San"),
        )
        val contact = ContactIdentity(
            key = ContactAggregationKey.AndroidContact(contactId = 42L),
            displayName = "张三",
            primaryAddress = "zhangsan@qq.com",
            addresses = setOf("zhangsan@qq.com", "zhangsan@company.com"),
            photoUri = "content://photo/42",
        )
        val contactIdentities = mapOf(privateAddress to contact, workAddress to contact)

        // Act
        val result = testSubject.create(
            aggregationMode = MessageListAggregationMode.CONTACT,
            messages = messages,
            contactIdentities = contactIdentities,
        )

        // Assert
        val group = (result as MessageListContent.ContactGroups).items.single()
        assertThat(group.key).isEqualTo(ContactAggregationKey.AndroidContact(contactId = 42L))
        assertThat(group.title).isEqualTo("张三")
        assertThat(group.address).isEqualTo("zhangsan@qq.com")
        assertThat(group.addresses).isEqualTo(setOf("zhangsan@qq.com", "zhangsan@company.com"))
        assertThat(group.messageCount).isEqualTo(2)
        assertThat(group.unreadCount).isEqualTo(2)
    }

    private fun createMessage(
        id: String,
        address: String?,
        displayName: String? = null,
        isRead: Boolean = false,
    ): MessageItemUi = MessageItemUi(
        state = if (isRead) MessageItemUi.State.Read else MessageItemUi.State.Unread,
        id = id,
        messageReference = "reference-$id",
        account = Account(id = AccountIdFactory.create(), color = Color.Unspecified),
        senders = ComposedAddressUi(displayName = address.orEmpty()),
        senderIdentity = createSenderIdentityOrNull(address = address, displayName = displayName),
        subject = "subject-$id",
        excerpt = "excerpt",
        formattedReceivedAt = "12:00",
        sortTimestamp = 0L,
        hasAttachments = false,
        starred = false,
        encrypted = false,
        answered = false,
        forwarded = false,
        selected = false,
    )
}
