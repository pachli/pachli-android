package app.pachli.interfaces

interface HashtagActionListener {
    fun unfollow(tagName: String)
    fun onViewTag(tag: String)
}
