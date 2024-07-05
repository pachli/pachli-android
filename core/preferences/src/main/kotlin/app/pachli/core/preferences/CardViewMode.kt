package app.pachli.core.preferences

enum class CardViewMode {
    /** User has disabled link previews. */
    NONE,

    /**
     * Display the card as the full-width of the view, used in a status'
     * detailed view.
     */
    FULL_WIDTH,

    /** Display the card indented from the left edge, the normal timeline view. */
    INDENTED,
}
