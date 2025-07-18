package app.pachli.components.followedtags

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.AutoCompleteTextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewGroupCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import app.pachli.R
import app.pachli.components.compose.ComposeAutoCompleteAdapter
import app.pachli.core.activity.BaseActivity
import app.pachli.core.activity.extensions.startActivityWithDefaultTransition
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.ui.ActionButtonScrollListener
import app.pachli.core.ui.appbar.FadeChildScrollEffect
import app.pachli.core.ui.extensions.addScrollEffect
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.databinding.ActivityFollowedTagsBinding
import app.pachli.interfaces.HashtagActionListener
import com.bumptech.glide.Glide
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class FollowedTagsActivity :
    BaseActivity(),
    HashtagActionListener,
    ComposeAutoCompleteAdapter.AutocompletionProvider {
    @Inject
    lateinit var api: MastodonApi

    private val binding by viewBinding(ActivityFollowedTagsBinding::inflate)
    private val viewModel: FollowedTagsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        binding.includedToolbar.appbar.applyDefaultWindowInsets()
        binding.includedToolbar.toolbar.addScrollEffect(FadeChildScrollEffect)
        binding.fab.applyDefaultWindowInsets()

        setContentView(binding.root)
        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            setTitle(R.string.title_followed_hashtags)
            // Back button
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.fab.setOnClickListener {
            val dialog: DialogFragment = FollowTagDialog.newInstance()
            dialog.show(supportFragmentManager, "dialog")
        }

        setupAdapter().let { adapter ->
            setupRecyclerView(adapter)

            lifecycleScope.launch {
                viewModel.pager.collectLatest { pagingData ->
                    adapter.submitData(pagingData)
                }
            }
        }

        binding.includedToolbar.appbar.setLiftOnScrollTargetView(binding.followedTagsView)

        val actionButtonScrollListener = ActionButtonScrollListener(binding.fab)
        binding.followedTagsView.addOnScrollListener(actionButtonScrollListener)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.showFabWhileScrolling.collect { showFabWhileScrolling ->
                    actionButtonScrollListener.showActionButtonWhileScrolling = showFabWhileScrolling
                }
            }
        }
    }

    private fun setupRecyclerView(adapter: FollowedTagsAdapter) {
        binding.followedTagsView.applyDefaultWindowInsets()
        binding.followedTagsView.adapter = adapter
        binding.followedTagsView.setHasFixedSize(true)
        binding.followedTagsView.layoutManager = LinearLayoutManager(this)
        binding.followedTagsView.addItemDecoration(
            MaterialDividerItemDecoration(this, MaterialDividerItemDecoration.VERTICAL),
        )
        (binding.followedTagsView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    }

    private fun setupAdapter(): FollowedTagsAdapter {
        return FollowedTagsAdapter(this).apply {
            addLoadStateListener { loadState ->
                binding.followedTagsProgressBar.visible(loadState.refresh == LoadState.Loading && itemCount == 0)

                if (loadState.refresh is LoadState.Error) {
                    binding.followedTagsView.hide()
                    binding.followedTagsMessageView.show()
                    val errorState = loadState.refresh as LoadState.Error
                    binding.followedTagsMessageView.setup(errorState.error) { retry() }
                    Timber.w(errorState.error, "error loading followed hashtags")
                } else {
                    binding.followedTagsView.show()
                    binding.followedTagsMessageView.hide()
                }
            }
        }
    }

    /**
     * @param tagName Name of the tag, without the leading `#`.
     */
    private fun follow(tagName: String) {
        lifecycleScope.launch {
            api.followTag(tagName).onSuccess {
                viewModel.tags.add(it.body)
                viewModel.currentSource?.invalidate()
            }.onFailure {
                Snackbar.make(
                    this@FollowedTagsActivity,
                    binding.followedTagsView,
                    getString(R.string.error_following_hashtag_format, tagName),
                    Snackbar.LENGTH_SHORT,
                )
                    .show()
            }
        }
    }

    /**
     * @param tagName Name of the tag, without the leading `#`.
     */
    override fun unfollow(tagName: String) {
        lifecycleScope.launch {
            api.unfollowTag(tagName).onSuccess {
                viewModel.tags.removeIf { it.name == tagName }
                Snackbar.make(
                    this@FollowedTagsActivity,
                    binding.followedTagsView,
                    getString(R.string.confirmation_hashtag_unfollowed, tagName),
                    Snackbar.LENGTH_LONG,
                )
                    .setAction(R.string.action_undo) {
                        follow(tagName)
                    }
                    .show()
                viewModel.currentSource?.invalidate()
            }.onFailure {
                Snackbar.make(
                    this@FollowedTagsActivity,
                    binding.followedTagsView,
                    getString(
                        R.string.error_unfollowing_hashtag_format,
                        tagName,
                    ),
                    Snackbar.LENGTH_SHORT,
                )
                    .show()
            }
        }
    }

    override fun onViewTag(tag: String) {
        startActivityWithDefaultTransition(TimelineActivityIntent.hashtag(this, intent.pachliAccountId, tag))
    }

    override suspend fun search(token: String): List<ComposeAutoCompleteAdapter.AutocompleteResult> {
        return viewModel.searchAutocompleteSuggestions(token)
    }

    class FollowTagDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val layout = layoutInflater.inflate(R.layout.dialog_follow_hashtag, null)
            val autoCompleteTextView = layout.findViewById<AutoCompleteTextView>(R.id.hashtag)!!
            autoCompleteTextView.setAdapter(
                ComposeAutoCompleteAdapter(
                    Glide.with(this),
                    requireActivity() as FollowedTagsActivity,
                    animateAvatar = false,
                    animateEmojis = false,
                    showBotBadge = false,
                ),
            )

            return AlertDialog.Builder(requireActivity())
                .setTitle(R.string.dialog_follow_hashtag_title)
                .setView(layout)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    (requireActivity() as FollowedTagsActivity).follow(
                        autoCompleteTextView.text.toString().removePrefix("#"),
                    )
                }
                .setNegativeButton(android.R.string.cancel) { _: DialogInterface, _: Int -> }
                .create()
        }

        companion object {
            fun newInstance(): FollowTagDialog = FollowTagDialog()
        }
    }
}
