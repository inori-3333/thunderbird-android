package net.thunderbird.core.preference.display.visualSettings.message.list

/**
 * Defines how messages of the message list are grouped for display.
 *
 * @property NONE Messages are displayed individually (default). Threading is not affected.
 * @property CONTACT Messages are grouped by the normalized email address of their sender.
 */
enum class MessageListAggregationMode {
    NONE,
    CONTACT,
}

val MESSAGE_LIST_SETTINGS_DEFAULT_AGGREGATION_MODE = MessageListAggregationMode.NONE
