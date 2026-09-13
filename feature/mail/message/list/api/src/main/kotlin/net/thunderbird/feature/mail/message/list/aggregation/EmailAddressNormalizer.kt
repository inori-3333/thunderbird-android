package net.thunderbird.feature.mail.message.list.aggregation

/**
 * Normalizes an email address so that addresses that only differ in casing or surrounding whitespace
 * are treated as the same sender.
 *
 * The result is locale independent and does not depend on the current default locale.
 *
 * **Note:** This intentionally does not remove provider specific aliases (`+tag` suffixes or dots in the
 * local part), because doing so would merge addresses of different providers incorrectly.
 *
 * @param address The address to normalize.
 * @return The normalized address. An empty string when [address] does not contain anything but whitespace.
 */
fun normalizeEmailAddress(address: String): String = address.trim().lowercase()
