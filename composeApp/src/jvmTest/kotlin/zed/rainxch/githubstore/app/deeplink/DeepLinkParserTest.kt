package zed.rainxch.githubstore.app.deeplink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeepLinkParserTest {
    @Test
    fun signedStateIsAccepted() {
        val state = "0a_JJK_CRK1BVLZbTSTNhPOAEajl9ywV6_S7x2MTAaY.AU__DMjcaFuduXpsE9AxtNLMNEF4VuDJvQ4CeO3DDvuhcwjvgCCc"

        val result = DeepLinkParser.parse("githubstore://auth?handoff=abcDEF123&state=$state")

        val handoff = assertIs<DeepLinkDestination.AuthHandoff>(result)
        assertEquals(state, handoff.state)
        assertEquals("abcDEF123", handoff.handoffId)
    }

    @Test
    fun unsignedStateStillParses() {
        val state = "0a_JJK_CRK1BVLZbTSTNhPOAEajl9ywV6_S7x2MTAaY"

        val result = DeepLinkParser.parse("githubstore://auth?handoff=abcDEF123&state=$state")

        assertIs<DeepLinkDestination.AuthHandoff>(result)
    }

    @Test
    fun malformedStateIsRejected() {
        val state = "signed.state.with/illegal"

        val result = DeepLinkParser.parse("githubstore://auth?handoff=abcDEF123&state=$state")

        assertIs<DeepLinkDestination.None>(result)
    }

    @Test
    fun handoffDoesNotAcceptDots() {
        val state = "valid_state"

        val result = DeepLinkParser.parse("githubstore://auth?handoff=abc.def&state=$state")

        assertIs<DeepLinkDestination.None>(result)
    }

    @Test
    fun errorCallbackCarriesSignedState() {
        val state = "valid_state.signature"

        val result = DeepLinkParser.parse("githubstore://auth?error=exchange_failed&state=$state")

        val error = assertIs<DeepLinkDestination.AuthError>(result)
        assertEquals("exchange_failed", error.reason)
        assertEquals(state, error.state)
    }
}
