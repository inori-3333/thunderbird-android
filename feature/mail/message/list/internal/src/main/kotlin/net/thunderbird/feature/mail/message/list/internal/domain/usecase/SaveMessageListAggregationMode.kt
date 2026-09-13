package net.thunderbird.feature.mail.message.list.internal.domain.usecase

import net.thunderbird.core.logging.Logger
import net.thunderbird.core.preference.display.DisplaySettings
import net.thunderbird.core.preference.display.DisplaySettingsPreferenceManager
import net.thunderbird.core.preference.display.visualSettings.message.list.MessageListAggregationMode
import net.thunderbird.core.preference.update
import net.thunderbird.feature.mail.message.list.domain.DomainContract

private const val TAG = "SaveMessageListAggregationMode"

/**
 * Persists the aggregation mode selected by the user in the message list.
 */
class SaveMessageListAggregationMode(
    private val logger: Logger,
    private val displayPreferenceManager: DisplaySettingsPreferenceManager,
) : DomainContract.UseCase.SetMessageListAggregationMode {
    override suspend fun invoke(mode: MessageListAggregationMode) {
        logger.debug(TAG) { "invoke() called with: mode = $mode" }
        displayPreferenceManager.update { displaySettings ->
            displaySettings.withAggregationMode(mode)
        }
    }
}

private fun DisplaySettings.withAggregationMode(mode: MessageListAggregationMode): DisplaySettings = copy(
    visualSettings = visualSettings.copy(
        messageListSettings = visualSettings.messageListSettings.copy(aggregationMode = mode),
    ),
)
