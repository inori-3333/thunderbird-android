package net.thunderbird.feature.mail.message.list.ui.state

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import net.thunderbird.core.preference.display.visualSettings.message.list.MessageListAggregationMode
import net.thunderbird.core.preference.display.visualSettings.message.list.MessageListDateTimeFormat
import net.thunderbird.core.preference.display.visualSettings.message.list.UiDensity
import net.thunderbird.feature.mail.message.list.preferences.MessageListPreferences

class MessageListStateTest {

    @Test
    fun `aggregationEnabled should be true when the preferences aggregate by sender`() {
        // Act
        val state = createState(aggregationMode = MessageListAggregationMode.CONTACT)

        // Assert
        assertThat(state.aggregationEnabled).isTrue()
    }

    @Test
    fun `aggregationEnabled should be false when the preferences show the normal list`() {
        // Act
        val state = createState(aggregationMode = MessageListAggregationMode.NONE)

        // Assert
        assertThat(state.aggregationEnabled).isFalse()
    }

    @Test
    fun `aggregationEnabled should be false while the preferences are not loaded`() {
        // Act
        val state = MessageListState.WarmingUp()

        // Assert
        assertThat(state.aggregationEnabled).isFalse()
    }

    private fun createState(aggregationMode: MessageListAggregationMode) = MessageListState.LoadedMessages(
        metadata = MessageListMetadata(
            folder = null,
            swipeActions = persistentMapOf(),
            sortCriteriaPerAccount = persistentMapOf(),
            isActive = false,
        ),
        preferences = MessageListPreferences(
            density = UiDensity.Default,
            groupConversations = false,
            showCorrespondentNames = false,
            showMessageAvatar = false,
            showFavouriteButton = false,
            senderAboveSubject = false,
            excerptLines = 1,
            dateTimeFormat = MessageListDateTimeFormat.Contextual,
            aggregationMode = aggregationMode,
        ),
        messages = persistentListOf(),
    )
}
