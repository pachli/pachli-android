package app.pachli.interfaces

interface HashtagActionListener {
    fun unfollow(tagName: String, position: Int)
    fun onViewTag(tag: String)
}
