package app.pachli.pager

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentViewHolder
import app.pachli.ViewMediaAdapter
import app.pachli.core.model.Attachment
import app.pachli.fragment.ViewMediaFragment
import java.lang.ref.WeakReference

class ImagePagerAdapter(
    activity: FragmentActivity,
    private val attachments: List<Attachment>,
    private val initialPosition: Int,
) : ViewMediaAdapter(activity) {

    /** True if the animated transition to the fragment has completed */
    private var transitionComplete = false
    private val fragments = MutableList<WeakReference<ViewMediaFragment>?>(attachments.size) { null }

    override fun getItemCount() = attachments.size

    override fun createFragment(position: Int): Fragment {
        if (position >= 0 && position < attachments.size) {
            // Fragment should not wait for or start transition if it already happened but we
            // instantiate the same fragment again, e.g. open the first photo, scroll to the
            // forth photo and then back to the first. The first fragment will try to start the
            // transition and wait until it's over and it will never take place.
            val fragment = ViewMediaFragment.newInstance(
                attachment = attachments[position],
                shouldCallMediaReady = !transitionComplete && position == initialPosition,
            )
            fragments[position] = WeakReference(fragment)
            return fragment
        } else {
            throw IllegalStateException()
        }
    }

    override fun onBindViewHolder(holder: FragmentViewHolder, position: Int, payloads: List<Any?>) {
        super.onBindViewHolder(holder, position, payloads)

        // We might be binding a new fragment after the transition completed. If so,
        // make sure to call the fragment's `onTransitionEnd` so it can start
        // immediately. Otherwise the fragment hangs waiting for the transition
        // to complete.
        if (transitionComplete) fragments.getOrNull(position)?.get()?.onTransitionEnd()
    }

    override fun onViewDetachedFromWindow(holder: FragmentViewHolder) {
        super.onViewDetachedFromWindow(holder)
        // Inform the fragment it is no longer visible so it can take appropriate
        // action (e.g., pause playback).
        fragments.getOrNull(holder.bindingAdapterPosition)?.get()?.onDetachedFromWindow()
    }

    override fun onAudioBecomingNoisy() {
        fragments.forEach { it?.get()?.onAudioBecomingNoisy() }
    }

    /**
     * Called by the hosting activity to notify the adapter that the shared element
     * transition to the first displayed item in the adapter has completed.
     *
     * Forward the notification to the fragment.
     */
    override fun onTransitionEnd(position: Int) {
        this.transitionComplete = true
        fragments[position]?.get()?.onTransitionEnd()
    }
}
