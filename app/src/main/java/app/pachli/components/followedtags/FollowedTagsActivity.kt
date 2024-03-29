package app.pachli.components.followedtags

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.AutoCompleteTextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
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
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.ui.ActionButtonScrollListener
import app.pachli.databinding.ActivityFollowedTagsBinding
import app.pachli.interfaces.HashtagActionListener
import at.connyduck.calladapter.networkresult.fold
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

    private val actionButtonScrollListener = ActionButtonScrollListener(binding.fab)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        binding.followedTagsView.adapter = adapter
        binding.followedTagsView.setHasFixedSize(true)
        binding.followedTagsView.layoutManager = LinearLayoutManager(this)
        binding.followedTagsView.addItemDecoration(
            MaterialDividerItemDecoration(this, MaterialDividerItemDecoration.VERTICAL),
        )
        (binding.followedTagsView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    }

    private fun setupAdapter(): FollowedTagsAdapter {
        return FollowedTagsAdapter(this, viewModel).apply {
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

    private fun follow(tagName: String, position: Int = -1) {
        lifecycleScope.launch {
            api.followTag(tagName).fold(
                {
                    if (position == -1) {
                        viewModel.tags.add(it)
                    } else {
                        viewModel.tags.add(position, it)
                    }
                    viewModel.currentSource?.invalidate()
                },
                {
                    Snackbar.make(
                        this@FollowedTagsActivity,
                        binding.followedTagsView,
                        getString(R.string.error_following_hashtag_format, tagName),
                        Snackbar.LENGTH_SHORT,
                    )
                        .show()
                },
            )
        }
    }

    override fun unfollow(tagName: String, position: Int) {
        lifecycleScope.launch {
            api.unfollowTag(tagName).fold(
                {
                    viewModel.tags.removeAt(position)
                    Snackbar.make(
                        this@FollowedTagsActivity,
                        binding.followedTagsView,
                        getString(R.string.confirmation_hashtag_unfollowed, tagName),
                        Snackbar.LENGTH_LONG,
                    )
                        .setAction(R.string.action_undo) {
                            follow(tagName, position)
                        }
                        .show()
                    viewModel.currentSource?.invalidate()
                },
                {
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
                },
            )
        }
    }

    override fun onViewTag(tag: String) {
        startActivityWithSlideInAnimation(TimelineActivityIntent.hashtag(this, tag))
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
