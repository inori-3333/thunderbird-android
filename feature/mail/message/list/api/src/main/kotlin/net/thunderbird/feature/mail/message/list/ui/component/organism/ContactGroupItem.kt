package net.thunderbird.feature.mail.message.list.ui.component.organism

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import net.thunderbird.components.ui.bolt.atom.ClickableSurface
import net.thunderbird.components.ui.bolt.atom.text.TextBodySmall
import net.thunderbird.components.ui.bolt.atom.text.TextLabelSmall
import net.thunderbird.components.ui.bolt.atom.text.TextTitleSmall
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.core.preference.display.visualSettings.message.list.UiDensity
import net.thunderbird.feature.mail.message.list.R
import net.thunderbird.feature.mail.message.list.preferences.MessageListPreferences
import net.thunderbird.feature.mail.message.list.ui.component.config.MessageItemAccountIndicator
import net.thunderbird.feature.mail.message.list.ui.component.molecule.AccountIndicatorIcon
import net.thunderbird.feature.mail.message.list.ui.component.molecule.MessageItemAvatarCircle
import net.thunderbird.feature.mail.message.list.ui.component.molecule.MessageItemAvatarCircleDefaults
import net.thunderbird.feature.mail.message.list.ui.state.ContactGroupUiModel

const val TEST_TAG_CONTACT_GROUP_ITEM_ROOT = "ContactGroupItem_Root"

/**
 * Represents one sender group in the message list while contact aggregation is active.
 *
 * ```
 * Contact group item structure:
 * ┌───────────┬──────────────────────┬──────────┐
 * │           │  Title               │  Date    │
 * │  Avatar   │  Address             ├──────────┤
 * │           │  Subject             │  Count   │
 * └───────────┴──────────────────────┴──────────┘
 * ```
 *
 * A sender group is not a message. It can therefore not be swiped, starred or selected. Clicking it
 * navigates to the messages of the sender.
 *
 * @param group The sender group to display.
 * @param preferences The message list preferences that control the appearance.
 * @param accountIndicator Optional visual indicator to identify which account the group belongs to,
 *  useful in the unified inbox. Pass `null` if no indicator should be shown.
 * @param onClick Callback invoked when the group is clicked.
 * @param modifier The modifier to be applied to the item.
 */
@Composable
fun ContactGroupItem(
    group: ContactGroupUiModel,
    preferences: MessageListPreferences,
    accountIndicator: MessageItemAccountIndicator?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUnread = group.unreadCount > 0
    val stateDescriptionText = stringResource(
        if (isUnread) {
            R.string.message_list_contact_group_state_unread_description
        } else {
            R.string.message_list_contact_group_state_read_description
        },
    )

    ClickableSurface(
        onClick = onClick,
        color = BoltTheme.colors.surfaceContainerLowest,
        modifier = modifier
            .fillMaxWidth()
            .testTag(TEST_TAG_CONTACT_GROUP_ITEM_ROOT)
            .semantics(mergeDescendants = true) {
                stateDescription = stateDescriptionText
            },
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding(preferences.density)),
        ) {
            MessageItemAvatarCircle(
                avatar = group.avatar,
                colors = MessageItemAvatarCircleDefaults.colorsFrom(group.color ?: BoltTheme.colors.primary),
                onClick = onClick,
                enabled = false,
            )
            Spacer(modifier = Modifier.width(BoltTheme.spacings.default))
            ContactGroupTexts(
                group = group,
                isUnread = isUnread,
                accountIndicator = accountIndicator,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(BoltTheme.spacings.half))
            ContactGroupCounters(group = group, isUnread = isUnread)
        }
    }
}

/**
 * Displays the title, address and subject of a sender group.
 */
@Composable
private fun ContactGroupTexts(
    group: ContactGroupUiModel,
    isUnread: Boolean,
    accountIndicator: MessageItemAccountIndicator?,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.quarter),
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextTitleSmall(
                text = group.title.buildTitle(isUnread),
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            accountIndicator?.let { indicator ->
                AccountIndicatorIcon(color = indicator.color)
            }
        }
        TextBodySmall(
            text = group.address,
            color = BoltTheme.colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextBodySmall(
            text = group.latestMessage.subject,
            color = BoltTheme.colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Displays the date of the latest message and the number of unread messages of a sender group.
 */
@Composable
private fun ContactGroupCounters(group: ContactGroupUiModel, isUnread: Boolean) {
    Column(horizontalAlignment = Alignment.End) {
        TextLabelSmall(
            text = group.latestMessage.formattedReceivedAt,
            color = BoltTheme.colors.onSurfaceVariant,
        )
        TextLabelSmall(
            text = group.unreadCountLabel ?: group.messageCountLabel,
            color = if (isUnread) BoltTheme.colors.primary else BoltTheme.colors.onSurfaceVariant,
        )
    }
}

/**
 * Renders the title of a group with a bold font weight while it contains unread messages.
 */
private fun String.buildTitle(isUnread: Boolean): AnnotatedString = if (isUnread) {
    buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(this@buildTitle)
        }
    }
} else {
    AnnotatedString(this)
}

@Composable
private fun contentPadding(density: UiDensity): PaddingValues = when (density) {
    UiDensity.Compact -> PaddingValues(
        vertical = BoltTheme.spacings.half,
        horizontal = BoltTheme.spacings.half,
    )

    UiDensity.Default -> PaddingValues(
        vertical = BoltTheme.spacings.default,
        horizontal = BoltTheme.spacings.default,
    )

    UiDensity.Relaxed -> PaddingValues(
        vertical = BoltTheme.spacings.double,
        horizontal = BoltTheme.spacings.double,
    )
}
