package net.thunderbird.feature.mail.message.list.internal.aggregation

import android.net.Uri
import app.k9mail.core.android.common.contact.Contact
import app.k9mail.core.android.common.contact.ContactPermissionResolver
import app.k9mail.core.android.common.contact.ContactRepository
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.common.mail.EmailAddress
import net.thunderbird.core.common.mail.toEmailAddressOrThrow
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.feature.mail.message.list.aggregation.model.ContactAggregationKey
import net.thunderbird.feature.mail.message.list.aggregation.model.SenderIdentity
import net.thunderbird.feature.mail.message.list.aggregation.model.createSenderIdentityOrNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DefaultContactIdentityResolverTest {

    @Test
    fun `resolve should fall back to email identities when the contacts permission is missing`() = runTest {
        // Arrange
        val contactRepository = FakeContactRepository(contacts = mapOf("zhangsan@qq.com" to contact(id = 42L)))
        val testSubject = createTestSubject(
            contactRepository = contactRepository,
            hasContactPermission = false,
        )

        // Act
        val result = testSubject.resolve(setOf(sender("zhangsan@qq.com")))

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result.values.single().key)
            .isEqualTo(ContactAggregationKey.EmailAddress("zhangsan@qq.com"))
        assertThat(contactRepository.lookups).hasSize(0)
    }

    @Test
    fun `resolve should use the android contact when the address is known`() = runTest {
        // Arrange
        val testSubject = createTestSubject(
            contactRepository = FakeContactRepository(
                contacts = mapOf("zhangsan@qq.com" to contact(id = 42L, name = "张三")),
            ),
        )

        // Act
        val result = testSubject.resolve(setOf(sender("zhangsan@qq.com", displayName = "Zhang San")))

        // Assert
        val identity = result.values.single()
        assertThat(identity.key).isEqualTo(ContactAggregationKey.AndroidContact(contactId = 42L))
        assertThat(identity.displayName).isEqualTo("张三")
        assertThat(identity.primaryAddress).isEqualTo("zhangsan@qq.com")
        assertThat(identity.photoUri).isEqualTo("content://photo/42")
    }

    @Test
    fun `resolve should map several addresses of the same contact to one identity`() = runTest {
        // Arrange
        val testSubject = createTestSubject(
            contactRepository = FakeContactRepository(
                contacts = mapOf(
                    "zhangsan@qq.com" to contact(id = 42L, name = "张三"),
                    "zhangsan@company.com" to contact(id = 42L, name = "张三"),
                ),
            ),
        )
        val senders = setOf(
            sender("zhangsan@qq.com"),
            sender("zhangsan@company.com"),
        )

        // Act
        val result = testSubject.resolve(senders)

        // Assert
        assertThat(result).hasSize(2)
        assertThat(result.values.map { it.key }.toSet())
            .isEqualTo(setOf(ContactAggregationKey.AndroidContact(contactId = 42L)))
        assertThat(result.values.first().addresses)
            .isEqualTo(setOf("zhangsan@qq.com", "zhangsan@company.com"))
    }

    @Test
    fun `resolve should fall back to the email identity when the address is unknown`() = runTest {
        // Arrange
        val testSubject = createTestSubject(contactRepository = FakeContactRepository(contacts = emptyMap()))

        // Act
        val result = testSubject.resolve(setOf(sender("nobody@example.com")))

        // Assert
        assertThat(result.values.single().key)
            .isEqualTo(ContactAggregationKey.EmailAddress("nobody@example.com"))
        assertThat(result.values.single().displayName).isNull()
    }

    @Test
    fun `resolve should fall back to the email identity when the contact lookup fails`() = runTest {
        // Arrange
        val testSubject = createTestSubject(
            contactRepository = FakeContactRepository(contacts = emptyMap(), failWith = IllegalStateException("boom")),
        )

        // Act
        val result = testSubject.resolve(setOf(sender("broken@example.com")))

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result.values.single().key)
            .isEqualTo(ContactAggregationKey.EmailAddress("broken@example.com"))
    }

    @Test
    fun `resolve should query an address only once`() = runTest {
        // Arrange
        val contactRepository = FakeContactRepository(contacts = mapOf("foo@example.com" to contact(id = 1L)))
        val testSubject = createTestSubject(contactRepository = contactRepository)

        // Act
        testSubject.resolve(setOf(sender("foo@example.com")))
        testSubject.resolve(setOf(sender("foo@example.com")))

        // Assert
        assertThat(contactRepository.lookups).isEqualTo(listOf("foo@example.com"))
    }

    @Test
    fun `resolve should return an empty result for no senders`() = runTest {
        // Arrange
        val contactRepository = FakeContactRepository(contacts = emptyMap())
        val testSubject = createTestSubject(contactRepository = contactRepository)

        // Act
        val result = testSubject.resolve(emptySet())

        // Assert
        assertThat(result).hasSize(0)
        assertThat(contactRepository.lookups).hasSize(0)
    }

    private fun createTestSubject(
        contactRepository: ContactRepository,
        hasContactPermission: Boolean = true,
    ) = DefaultContactIdentityResolver(
        contactRepository = contactRepository,
        contactPermissionResolver = FakeContactPermissionResolver(hasContactPermission),
        logger = TestLogger(),
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    private fun sender(address: String, displayName: String? = null): SenderIdentity =
        checkNotNull(createSenderIdentityOrNull(address = address, displayName = displayName))

    private fun contact(id: Long, name: String? = null) = Contact(
        id = id,
        name = name,
        emailAddress = "unused@example.com".toEmailAddressOrThrow(),
        uri = Uri.EMPTY,
        photoUri = Uri.parse("content://photo/$id"),
    )
}

private class FakeContactPermissionResolver(
    private val hasContactPermission: Boolean,
) : ContactPermissionResolver {
    override fun hasContactPermission(): Boolean = hasContactPermission
}

/**
 * A [ContactRepository] that only returns the contacts of [contacts] and records every lookup.
 *
 * The results are cached like the production `CachingContactRepository` does, so that tests can verify that an
 * address is not looked up twice.
 */
private class FakeContactRepository(
    private val contacts: Map<String, Contact>,
    private val failWith: Exception? = null,
) : ContactRepository {
    val lookups = mutableListOf<String>()
    private val cache = mutableMapOf<String, Contact?>()

    override fun getContactFor(emailAddress: EmailAddress): Contact? {
        val address = emailAddress.address
        if (cache.containsKey(address)) return cache[address]
        lookups += address
        failWith?.let { throw it }
        return contacts[address].also { cache[address] = it }
    }

    override fun hasContactFor(emailAddress: EmailAddress): Boolean = getContactFor(emailAddress) != null

    override fun hasAnyContactFor(emailAddresses: List<EmailAddress>): Boolean =
        emailAddresses.any { hasContactFor(it) }

    override fun getPhotoUri(emailAddress: String): Uri? = getContactFor(emailAddress.toEmailAddressOrThrow())
        ?.photoUri
}
