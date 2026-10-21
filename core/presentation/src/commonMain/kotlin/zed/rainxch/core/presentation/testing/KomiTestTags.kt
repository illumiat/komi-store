package zed.rainxch.core.presentation.testing

/**
 * Test-only anchors for the instrumented target-SDK verification suite.
 * Kept in production code so the Compose tree can be located deterministically.
 */
object KomiTestTags {
    const val SCREEN_EXPLORE = "screen_explore"
    const val SCREEN_DETAILS = "screen_details"
    const val TOP_BAR_CONTENT = "top_bar_content"
    const val WHATS_NEW_DISMISS = "whats_new_dismiss"
    const val RATE_LIMIT_DISMISS = "rate_limit_dismiss"
    const val CLASSIC_TOP_BAR = "classic_top_bar"

    fun navItem(id: String): String = "nav_item_$id"

    fun navLabel(id: String): String = "nav_label_$id"
}
