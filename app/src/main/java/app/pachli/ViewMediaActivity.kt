/*
 * Copyright 2017 Andrew Dawson
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

package app.pachli

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.transition.Transition
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import app.pachli.BuildConfig.APPLICATION_ID
import app.pachli.core.activity.BaseActivity
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.navigation.AttachmentViewData
import app.pachli.core.navigation.ViewMediaActivityIntent
import app.pachli.core.navigation.ViewThreadActivityIntent
import app.pachli.databinding.ActivityViewMediaBinding
import app.pachli.fragment.MediaActionsListener
import app.pachli.pager.ImagePagerAdapter
import app.pachli.pager.SingleImagePagerAdapter
import app.pachli.util.getTemporaryMediaFilename
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import timber.log.Timber

typealias ToolbarVisibilityListener = (isVisible: Boolean) -> Unit
typealias MenuItemActionListener = () -> Unit

/**
 * Show one or more media items (pictures, video, audio, etc).
 */
@AndroidEntryPoint
class ViewMediaActivity : BaseActivity(), MediaActionsListener {
    @Inject
    lateinit var okHttpClient: OkHttpClient

    private val binding by viewBinding(ActivityViewMediaBinding::inflate)

    val toolbar: View
        get() = binding.toolbar

    var isToolbarVisible = true
        private set

    private var attachmentViewData: List<AttachmentViewData>? = null
    private val toolbarVisibilityListeners = mutableListOf<ToolbarVisibilityListener>()
    private val menuItemActionListeners = mutableListOf<MenuItemActionListener>()
    private var imageUrl: String? = null

    /** True if a download to share media is in progress */
    private var isDownloading: Boolean = false

    /**
     * Adds [listener] to the list of toolbar listeners and immediately calls
     * it with the current toolbar visibility.
     * The [listener] will be removed when the provided [lifecycle] reaches [Lifecycle.State.DESTROYED].
     */
    fun addToolbarVisibilityListener(lifecycle: Lifecycle, listener: ToolbarVisibilityListener) {
        toolbarVisibilityListeners.add(listener)
        listener(isToolbarVisible)
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    toolbarVisibilityListeners.remove(listener)
                }
            },
        )
    }

    /**
     * Adds [listener] to the list of menu item action listeners.
     * The [listener] will be removed when the provided [lifecycle] reaches [Lifecycle.State.DESTROYED].
     */
    fun addMenuItemActionListener(lifecycle: Lifecycle, listener: MenuItemActionListener) {
        menuItemActionListeners.add(listener)
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    menuItemActionListeners.remove(listener)
                }
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        supportPostponeEnterTransition()

        // Gather the parameters.
        attachmentViewData = ViewMediaActivityIntent.getAttachments(intent)
        val initialPosition = ViewMediaActivityIntent.getAttachmentIndex(intent)

        // Adapter is actually of existential type PageAdapter & SharedElementsTransitionListener
        // but it cannot be expressed and if I don't specify type explicitly compilation fails
        // (probably a bug in compiler)
        val adapter: ViewMediaAdapter = if (attachmentViewData != null) {
            val attachments = attachmentViewData!!.map(AttachmentViewData::attachment)
            // Setup the view pager.
            ImagePagerAdapter(this, attachments, initialPosition)
        } else {
            imageUrl = ViewMediaActivityIntent.getImageUrl(intent)
                ?: throw IllegalArgumentException("attachment list or image url has to be set")

            SingleImagePagerAdapter(this, imageUrl!!)
        }

        binding.viewPager.adapter = adapter
        binding.viewPager.setCurrentItem(initialPosition, false)
        binding.viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    binding.toolbar.title = getPageTitle(position)
                }
            },
        )

        // Setup the toolbar.
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getPageTitle(initialPosition)
        }

        binding.toolbar.setNavigationOnClickListener { supportFinishAfterTransition() }
        binding.toolbar.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.action_download -> requestDownloadMedia()
                R.id.action_open_status -> onOpenStatus()
                R.id.action_share_media -> shareMedia()
                R.id.action_copy_media_link -> copyLink()
            }
            for (listener in menuItemActionListeners) {
                listener()
            }
            true
        }

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE

        window.statusBarColor = Color.BLACK
        window.sharedElementEnterTransition.addListener(
            object : NoopTransitionListener {
                override fun onTransitionEnd(transition: Transition) {
                    adapter.onTransitionEnd(binding.viewPager.currentItem)
                    window.sharedElementEnterTransition.removeListener(this)
                }
            },
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.view_media_toolbar, menu)
        // We don't support 'open status' from single image views
        menu.findItem(R.id.action_open_status)?.isVisible = (attachmentViewData != null)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.action_share_media)?.isEnabled = !isDownloading
        return true
    }

    override fun onMediaReady() {
        supportStartPostponedEnterTransition()
    }

    override fun onMediaDismiss() {
        supportFinishAfterTransition()
    }

    override fun onMediaTap() {
        isToolbarVisible = !isToolbarVisible
        for (listener in toolbarVisibilityListeners) {
            listener(isToolbarVisible)
        }

        val visibility = if (isToolbarVisible) View.VISIBLE else View.INVISIBLE
        val alpha = if (isToolbarVisible) 1.0f else 0.0f
        if (isToolbarVisible) {
            // If to be visible, need to make visible immediately and animate alpha
            binding.toolbar.alpha = 0.0f
            binding.toolbar.visibility = visibility
        }

        binding.toolbar.animate().alpha(alpha)
            .setListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        binding.toolbar.visibility = visibility
                        animation.removeListener(this)
                    }
                },
            )
            .start()
    }

    private fun getPageTitle(position: Int): CharSequence {
        attachmentViewData ?: return ""
        return String.format(Locale.getDefault(), "%d/%d", position + 1, attachmentViewData?.size)
    }

    private fun downloadMedia() {
        val url = imageUrl ?: attachmentViewData!![binding.viewPager.currentItem].attachment.url
        val filename = Uri.parse(url).lastPathSegment
        Toast.makeText(applicationContext, resources.getString(R.string.download_image, filename), Toast.LENGTH_SHORT).show()

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        downloadManager.enqueue(request)
    }

    private fun requestDownloadMedia() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { _, grantResults ->
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    downloadMedia()
                } else {
                    showErrorDialog(binding.toolbar, R.string.error_media_download_permission, R.string.action_retry) {
                        requestDownloadMedia()
                    }
                }
            }
        } else {
            downloadMedia()
        }
    }

    private fun onOpenStatus() {
        val attach = attachmentViewData!![binding.viewPager.currentItem]
        startActivityWithSlideInAnimation(ViewThreadActivityIntent(this, attach.statusId, attach.statusUrl))
    }

    private fun copyLink() {
        val url = imageUrl ?: attachmentViewData!![binding.viewPager.currentItem].attachment.url
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(null, url))
    }

    private fun shareMedia() {
        val directory = applicationContext.getExternalFilesDir(null)
        if (directory == null || !(directory.exists())) {
            Timber.e("Error obtaining directory to save temporary media.")
            return
        }

        if (imageUrl != null) {
            shareMediaFile(directory, imageUrl!!)
        } else {
            val attachment = attachmentViewData!![binding.viewPager.currentItem].attachment
            shareMediaFile(directory, attachment.url)
        }
    }

    /**
     * Share media by downloading it to a temporary file and then sharing that
     * file.
     *
     * [DownloadManager] is not used for this as it is not guaranteed to start
     * downloading the file expediently, and the user may wait a long time.
     */
    private fun shareMediaFile(directory: File, url: String) {
        isDownloading = true
        binding.progressBarShare.show()
        invalidateOptionsMenu()

        val mimeTypeMap = MimeTypeMap.getSingleton()
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        val mimeType = mimeTypeMap.getMimeTypeFromExtension(extension)
        val filename = getTemporaryMediaFilename(extension)
        val file = File(directory, filename)

        lifecycleScope.launch {
            val request = Request.Builder().url(url).build()
            val response = async(Dispatchers.IO) {
                val response = okHttpClient.newCall(request).execute()
                response.body?.let { body ->
                    file.sink().buffer().use { it.writeAll(body.source()) }
                }
                return@async response
            }.await()

            isDownloading = false
            binding.progressBarShare.hide()
            invalidateOptionsMenu()

            if (!response.isSuccessful) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.error_media_download, url, response.code, response.message),
                    Snackbar.LENGTH_INDEFINITE,
                ).show()
                return@launch
            }

            ShareCompat.IntentBuilder(this@ViewMediaActivity)
                .setType(mimeType)
                .addStream(
                    FileProvider.getUriForFile(
                        applicationContext,
                        "$APPLICATION_ID.fileprovider",
                        file,
                    ),
                )
                .setChooserTitle(R.string.send_media_to)
                .startChooser()
        }
    }
}

abstract class ViewMediaAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    abstract fun onTransitionEnd(position: Int)
}

interface NoopTransitionListener : Transition.TransitionListener {
    override fun onTransitionEnd(transition: Transition) {
    }

    override fun onTransitionResume(transition: Transition) {
    }

    override fun onTransitionPause(transition: Transition) {
    }

    override fun onTransitionCancel(transition: Transition) {
    }

    override fun onTransitionStart(transition: Transition) {
    }
}
