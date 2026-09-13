package net.thunderbird.feature.mail.message.list.aggregation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import kotlin.test.Test

class EmailAddressNormalizerTest {

    @Test
    fun `normalizeEmailAddress should trim and lower case the address`() {
        // Act
        val result = normalizeEmailAddress(" Foo@Example.COM ")

        // Assert
        assertThat(result).isEqualTo("foo@example.com")
    }

    @Test
    fun `normalizeEmailAddress should return an empty string for input without content`() {
        assertThat(normalizeEmailAddress("")).isEqualTo("")
        assertThat(normalizeEmailAddress("   ")).isEqualTo("")
    }

    @Test
    fun `normalizeEmailAddress should not remove provider specific aliases`() {
        // `+tag` suffixes and dots in the local part must not be removed: doing so would merge
        // addresses of unrelated providers.
        assertThat(normalizeEmailAddress("foo+shop@example.com")).isEqualTo("foo+shop@example.com")
        assertThat(normalizeEmailAddress("john.smith@example.com")).isEqualTo("john.smith@example.com")
    }

    @Test
    fun `normalizeEmailAddress should keep different addresses different`() {
        assertThat(normalizeEmailAddress("notifications@github.com"))
            .isNotEqualTo(normalizeEmailAddress("noreply@github.com"))
    }
}
