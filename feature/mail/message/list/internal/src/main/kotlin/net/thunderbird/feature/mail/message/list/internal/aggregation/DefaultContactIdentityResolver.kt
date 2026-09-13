package net.thunderbird.feature.mail.message.list.internal.aggregation

import app.k9mail.core.android.common.contact.ContactPermissionResolver
import app.k9mail.core.android.common.contact.ContactRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.thunderbird.core.common.mail.toEmailAddressOrNull
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.mail.message.list.aggregation.ContactIdentityResolver
import net.thunderbird.feature.mail.message.list.aggregation.model.ContactAggregationKey
import net.thunderbird.feature.mail.message.list.aggregation.model.ContactIdentity
import net.thunderbird.feature.mail.message.list.aggregation.model.SenderIdentity

private const val TAG = "DefaultContactIdentityResolver"

/**
 * Resolves senders through the Android contacts provider.
 *
 * The contacts provider is only queried when the contacts permission is granted. Every lookup is cached by
 * [ContactRepository], so the current message list only queries an address once.
 *
 * Senders that belong to the same Android contact are mapped to the same [ContactIdentity], so that all of
 * their addresses end up in a single group.
 */
internal class DefaultContactIdentityResolver(
    private val contactRepository: ContactRepository,
    private val contactPermissionResolver: ContactPermissionResolver,
    private val logger: Logger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ContactIdentityResolver {

    override suspend fun resolve(senders: Set<SenderIdentity>): Map<SenderIdentity, ContactIdentity> = when {
        senders.isEmpty() -> emptyMap()

        !contactPermissionResolver.hasContactPermission() -> {
            logger.debug(TAG) { "resolve() called without contacts permission, grouping by email address" }
            senders.associateWith { sender -> sender.toEmailIdentity() }
        }

        else -> withContext(ioDispatcher) { resolveWithContacts(senders) }
    }

    private fun resolveWithContacts(senders: Set<SenderIdentity>): Map<SenderIdentity, ContactIdentity> {
        val contactsById = LinkedHashMap<Long, ResolvedContact>()
        val contactIdBySender = LinkedHashMap<SenderIdentity, Long>()

        for (sender in senders) {
            val contact = lookupContact(sender) ?: continue
            contactsById[contact.id] = contact
            contactIdBySender[sender] = contact.id
        }

        val addressesByContactId = contactIdBySender.entries
            .groupBy({ (_, contactId) -> contactId }, { (sender, _) -> sender.normalizedAddress })
            .mapValues { (_, addresses) -> addresses.toSet() }

        return senders.associateWith { sender ->
            val contactId = contactIdBySender[sender]
            sender.toIdentityOrFallback(
                contact = contactId?.let(contactsById::get),
                addresses = contactId?.let(addressesByContactId::get).orEmpty(),
            )
        }
    }

    /**
     * Returns the Android contact for a sender address, or `null` when the address is not known.
     *
     * A failing contacts provider must never break the message list, so exceptions are logged and treated as
     * "no contact".
     */
    private fun lookupContact(sender: SenderIdentity): ResolvedContact? {
        val emailAddress = sender.rawAddress.toEmailAddressOrNull() ?: return null
        return try {
            contactRepository.getContactFor(emailAddress)?.let { contact ->
                ResolvedContact(
                    id = contact.id,
                    name = contact.name,
                    photoUri = contact.photoUri?.toString(),
                )
            }
        } catch (@Suppress("TooGenericExceptionCaught") exception: Exception) {
            logger.error(TAG) { "Contact lookup failed: $exception" }
            null
        }
    }

    private fun SenderIdentity.toIdentityOrFallback(
        contact: ResolvedContact?,
        addresses: Set<String>,
    ): ContactIdentity = if (contact == null) {
        toEmailIdentity()
    } else {
        ContactIdentity(
            key = ContactAggregationKey.AndroidContact(contactId = contact.id),
            displayName = contact.name?.takeIf { it.isNotBlank() },
            primaryAddress = rawAddress,
            addresses = addresses.ifEmpty { setOf(normalizedAddress) },
            photoUri = contact.photoUri,
        )
    }

    private fun SenderIdentity.toEmailIdentity(): ContactIdentity = ContactIdentity(
        key = ContactAggregationKey.EmailAddress(normalizedAddress = normalizedAddress),
        displayName = displayName,
        primaryAddress = rawAddress,
        addresses = setOf(normalizedAddress),
    )

    private data class ResolvedContact(
        val id: Long,
        val name: String?,
        val photoUri: String?,
    )
}
