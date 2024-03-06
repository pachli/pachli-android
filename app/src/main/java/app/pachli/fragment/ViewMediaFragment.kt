/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Pachli.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Pachli is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Pachli; if not,
 * see <http://www.gnu.org/licenses>.
 */

package app.pachli.fragment

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.annotation.CallSuper
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import app.pachli.ViewMediaActivity
import app.pachli.core.network.model.Attachment
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Interface for actions that may happen while media is being displayed */
interface MediaActionsListener {
    /**
     * The media fragment is ready for the hosting activity to complete the
     * fragment transition; typically because any media to be displayed has
     * been loaded.
     */
    fun onMediaReady()

    /** The user is dismissing the media (e.g., by flinging up) */
    fun onMediaDismiss()

    /** The user has tapped on the media (typically to show/hide UI controls) */
    fun onMediaTap()
}

abstract class ViewMediaFragment : Fragment() {
    /** Function to remove the toolbar listener */
    private var removeToolbarListener: Function0<Boolean>? = null

    /**
     * Called after [onResume], subclasses should override this and update
     * the contents of views (including loading any media).
     *
     * @param isToolbarVisible True if the toolbar is visible
     * @param showingDescription True if the media's description should be shown
     */
    abstract fun setupMediaView(
        isToolbarVisible: Boolean,
        showingDescription: Boolean,
    )

    /**
     * Called when the visibility of the toolbar changes.
     *
     * @param visible True if the toolbar is visible
     */
    @CallSuper
    protected open fun onToolbarVisibilityChange(visible: Boolean) {
        if (visible && shouldScheduleToolbarHide()) {
            hideToolbarAfterDelay()
        } else {
            hideToolbarJob?.cancel()
        }
    }

    /**
     * Called when the toolbar becomes visible, returns whether or not to schedule hiding the toolbar
     */
    protected abstract fun shouldScheduleToolbarHide(): Boolean

    /** Hoist toolbar hiding to activity so it can track state across different fragments */
    private var hideToolbarJob: Job? = null

    /**
     * Schedule hiding the toolbar after a delay
     */
    protected fun hideToolbarAfterDelay() {
        hideToolbarJob?.cancel()
        hideToolbarJob = lifecycleScope.launch {
            delay(CONTROLS_TIMEOUT)
            mediaActivity.onMediaTap()
        }
    }

    /**
     * Cancel previously scheduled hiding of the toolbar
     */
    protected fun cancelToolbarHide() {
        hideToolbarJob?.cancel()
    }

    protected lateinit var mediaActivity: ViewMediaActivity
        private set

    protected var showingDescription = false
    protected var isDescriptionVisible = false

    /** The attachment to show */
    protected lateinit var attachment: Attachment

    /** Listener to call as media is loaded or on user interaction */
    protected lateinit var mediaActionsListener: MediaActionsListener

    /**
     * True if the fragment should call [MediaActionsListener.onMediaReady]
     * when the media is loaded.
     */
    protected var shouldCallMediaReady = false

    /** Awaitable signal that the transition has completed */
    var transitionComplete: CompletableDeferred<Unit>? = CompletableDeferred()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mediaActivity = activity as ViewMediaActivity
        mediaActionsListener = context as MediaActionsListener
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        attachment = arguments?.getParcelable<Attachment>(ARG_ATTACHMENT)
            ?: throw IllegalArgumentException("ARG_ATTACHMENT has to be set")

        shouldCallMediaReady = arguments?.getBoolean(ARG_SHOULD_CALL_MEDIA_READY)
            ?: throw IllegalArgumentException("ARG_START_POSTPONED_TRANSITION has to be set")
    }

    /**
     * Called by the fragment adapter to notify the fragment that the shared
     * element transition has been completed.
     */
    open fun onTransitionEnd() {
        this.transitionComplete?.complete(Unit)
    }

    override fun onResume() {
        super.onResume()
        finalizeViewSetup()
    }

    private fun finalizeViewSetup() {
        showingDescription = !TextUtils.isEmpty(attachment.description)
        isDescriptionVisible = showingDescription
        setupMediaView(mediaActivity.isToolbarVisible, showingDescription && mediaActivity.isToolbarVisible)

        removeToolbarListener = mediaActivity
            .addToolbarVisibilityListener { isVisible ->
                onToolbarVisibilityChange(isVisible)
            }
    }

    override fun onPause() {
        super.onPause()

        // If <= API 23 then multi-window mode is not available, so this is a good time to
        // pause everything
        if (Build.VERSION.SDK_INT <= 23) {
            hideToolbarJob?.cancel()
        }
    }

    override fun onStop() {
        super.onStop()

        // If > API 23 then this might be multi-window, and definitely wasn't paused in onPause,
        // so pause everything now.
        if (Build.VERSION.SDK_INT > 23) {
            hideToolbarJob?.cancel()
        }
    }

    override fun onDestroyView() {
        removeToolbarListener?.invoke()
        transitionComplete = null
        super.onDestroyView()
    }

    companion object {
        protected const val ARG_SHOULD_CALL_MEDIA_READY = "shouldCallMediaReady"

        protected const val ARG_ATTACHMENT = "attach"

        @JvmStatic
        protected val CONTROLS_TIMEOUT = 2.seconds // Consistent with YouTube player

        /**
         * @param attachment The media attachment to display in the fragment
         * @param shouldCallMediaReady If true this fragment should call
         *  [MediaActionsListener.onMediaReady] when it has finished loading
         *  media, so the calling activity can perform any final actions.
         * @return A fragment that shows [attachment]
         */
        @JvmStatic
        @OptIn(UnstableApi::class)
        fun newInstance(attachment: Attachment, shouldCallMediaReady: Boolean): ViewMediaFragment {
            val arguments = Bundle(2)
            arguments.putParcelable(ARG_ATTACHMENT, attachment)
            arguments.putBoolean(ARG_SHOULD_CALL_MEDIA_READY, shouldCallMediaReady)

            val fragment = when (attachment.type) {
                Attachment.Type.IMAGE -> ViewImageFragment()
                Attachment.Type.VIDEO,
                Attachment.Type.GIFV,
                Attachment.Type.AUDIO,
                -> ViewVideoFragment()
                else -> ViewImageFragment() // it probably won't show anything, but its better than crashing
            }
            fragment.arguments = arguments
            return fragment
        }

        @JvmStatic
        fun newInstance(imageUrl: String): ViewMediaFragment {
            val arguments = Bundle(2)
            val fragment = ViewImageFragment()
            arguments.putParcelable(
                ARG_ATTACHMENT,
                Attachment(
                    id = "unused",
                    url = imageUrl,
                    type = Attachment.Type.IMAGE,
                    previewUrl = null,
                    meta = null,
                    description = null,
                    blurhash = null,
                ),
            )
            arguments.putBoolean(ARG_SHOULD_CALL_MEDIA_READY, true)

            fragment.arguments = arguments
            return fragment
        }
    }
}
