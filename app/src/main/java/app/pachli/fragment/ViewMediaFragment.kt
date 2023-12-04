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

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import app.pachli.ViewMediaActivity
import app.pachli.core.network.model.Attachment

abstract class ViewMediaFragment : Fragment() {
    private var toolbarVisibilityDisposable: Function0<Boolean>? = null

    abstract fun setupMediaView(
        showingDescription: Boolean,
    )

    abstract fun onToolbarVisibilityChange(visible: Boolean)

    protected var showingDescription = false
    protected var isDescriptionVisible = false

    /** The attachment to show. Set in [onViewCreated] */
    protected lateinit var attachment: Attachment

    protected var shouldStartTransition = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        attachment = arguments?.getParcelable<Attachment>(ARG_ATTACHMENT)
            ?: throw IllegalArgumentException("ARG_ATTACHMENT has to be set")

        shouldStartTransition = arguments?.getBoolean(ARG_START_POSTPONED_TRANSITION)
            ?: throw IllegalArgumentException("ARG_START_POSTPONED_TRANSITION has to be set")
    }

    companion object {
        @JvmStatic
        protected val ARG_START_POSTPONED_TRANSITION = "startPostponedTransition"

        @JvmStatic
        protected val ARG_ATTACHMENT = "attach"

        @JvmStatic
        @OptIn(UnstableApi::class)
        fun newInstance(attachment: Attachment, shouldStartPostponedTransition: Boolean): ViewMediaFragment {
            val arguments = Bundle(2)
            arguments.putParcelable(ARG_ATTACHMENT, attachment)
            arguments.putBoolean(ARG_START_POSTPONED_TRANSITION, shouldStartPostponedTransition)

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
            arguments.putBoolean(ARG_START_POSTPONED_TRANSITION, true)

            fragment.arguments = arguments
            return fragment
        }
    }

    abstract fun onTransitionEnd()

    protected fun finalizeViewSetup() {
        val mediaActivity = activity as ViewMediaActivity

        showingDescription = !TextUtils.isEmpty(attachment.description)
        isDescriptionVisible = showingDescription
        setupMediaView(showingDescription && mediaActivity.isToolbarVisible)

        toolbarVisibilityDisposable = (activity as ViewMediaActivity)
            .addToolbarVisibilityListener { isVisible ->
                onToolbarVisibilityChange(isVisible)
            }
    }

    override fun onDestroyView() {
        toolbarVisibilityDisposable?.invoke()
        super.onDestroyView()
    }
}
