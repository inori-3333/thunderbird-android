package net.thunderbird.feature.mail.message.list.internal.fakes

import net.thunderbird.core.common.resources.StringsResourceManager

/**
 * A [StringsResourceManager] that returns the configured value for a resource instead of a localized
 * string. Unknown resources are represented by `string_<id>`.
 *
 * Format arguments are applied like `Resources.getString(id, vararg)` does.
 */
class FakeStringsResourceManager : StringsResourceManager {
    private val strings = mutableMapOf<Int, String>()

    fun withString(resourceId: Int, value: String): FakeStringsResourceManager = apply {
        strings[resourceId] = value
    }

    override fun stringResource(resourceId: Int): String = strings[resourceId] ?: "string_$resourceId"

    override fun stringResource(resourceId: Int, vararg formatArgs: Any?): String {
        val value = strings[resourceId] ?: "string_$resourceId"
        return if (formatArgs.isEmpty()) value else String.format(value, *formatArgs)
    }
}
