package app.pachli.components.followedtags

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.AutoCompleteTextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.core.view.ViewGroupCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import app.pachli.R
import app.pachli.components.compose.ComposeAutoCompleteAdapter
import app.pachli.core.activity.BaseActivity
import app.pachli.core.activity.extensions.startActivityWithDefaultTransition
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.repository.Loadable
import app.pachli.core.model.Hashtag
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.ui.ActionButtonScrollListener
import app.pachli.core.ui.appbar.FadeChildScrollEffect
import app.pachli.core.ui.extensions.addScrollEffect
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.databinding.ActivityFollowedTagsBinding
import com.bumptech.glide.Glide
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FollowedTagsActivity :
    BaseActivity(),
    MenuProvider,
    ComposeAutoCompleteAdapter.AutocompletionProvider {
    private val binding by viewBinding(ActivityFollowedTagsBinding::inflate)

    private val viewModel: FollowedTagsViewModel by viewModels()

    private var snackbar: Snackbar? = null

    private val adapter = FollowedTagsAdapter(this::onUnfollowTag, this::onViewTag)

    private val pachliAccountId by unsafeLazy { intent.pachliAccountId }

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

        addMenuProvider(this)

        binding.fab.setOnClickListener {
            val dialog: DialogFragment = FollowTagDialog.newInstance()
            dialog.show(supportFragmentManager, "dialog")
        }

        setupRecyclerView(adapter)

        with(binding.swipeRefreshLayout) {
            setOnRefreshListener {
                viewModel.accept(FallibleUiAction.Reload(pachliAccountId))
                isRefreshing = false
            }
            setColorSchemeColors(
                MaterialColors.getColor(
                    binding.root,
                    androidx.appcompat.R.attr.colorPrimary,
                ),
            )
        }

        bind()
    }

    private fun bind() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    viewModel.showProgress.collect {
                        if (it) binding.progressIndicator.show() else binding.progressIndicator.hide()
                    }
                }

                launch {
                    viewModel.followedHashtags.collectLatest { loadable ->
                        binding.followedTagsMessageView.hide()
                        binding.followedTagsView.show()

                        when (loadable) {
                            Loadable.Loading -> {}
                            is Loadable.Loaded<List<Hashtag>> -> adapter.submitList(loadable.data)
                        }
                    }
                }

                launch {
                    viewModel.uiResult.collect(::bindUiResult)
                }

                launch {
                    // TODO: Maybe move this bit of setup back in to onCreate
                    val actionButtonScrollListener = ActionButtonScrollListener(binding.fab)
                    binding.followedTagsView.addOnScrollListener(actionButtonScrollListener)

                    viewModel.showFabWhileScrolling.collect { showFabWhileScrolling ->
                        actionButtonScrollListener.showActionButtonWhileScrolling = showFabWhileScrolling
                    }
                }

                viewModel.accept(InfallibleUiAction.SetPachliAccountId(pachliAccountId))
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

    private fun bindUiResult(uiResult: Result<UiSuccess, UiError>) {
        uiResult
            .onFailure(::bindUiError)
            .onSuccess(::bindUiSuccess)
    }

    private fun bindUiError(uiError: UiError) {
        val message = uiError.fmt(this)
        snackbar?.dismiss()
        try {
            Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE).apply {
                setAction(app.pachli.core.ui.R.string.action_retry) {
                    viewModel.accept(uiError.action)
                }
                show()
                snackbar = this
            }
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun bindUiSuccess(uiSuccess: UiSuccess) {
        when (uiSuccess) {
            is UiSuccess.FollowHashtag -> Unit
            is UiSuccess.UnfollowHashtag -> {
                snackbar?.dismiss()
                try {
                    Snackbar.make(
                        binding.followedTagsView,
                        getString(R.string.confirmation_hashtag_unfollowed, uiSuccess.action.tagName),
                        Snackbar.LENGTH_LONG,
                    ).apply {
                        setAction(R.string.action_undo) {
                            viewModel.accept(
                                FallibleUiAction.FollowHashtag(
                                    uiSuccess.action.pachliAccountId,
                                    uiSuccess.action.tagName,
                                ),
                            )
                        }
                        show()
                        snackbar = this
                    }
                } catch (_: IllegalArgumentException) {
                }
            }
        }
    }

    /**
     * @param tagName Name of the tag, without the leading `#`.
     */
    private fun follow(tagName: String) {
        viewModel.accept(FallibleUiAction.FollowHashtag(pachliAccountId, tagName))
    }

    /**
     * @param tagName Name of the tag, without the leading `#`.
     */
    fun onUnfollowTag(tagName: String) {
        viewModel.accept(FallibleUiAction.UnfollowHashtag(pachliAccountId, tagName))
    }

    fun onViewTag(tagName: String) {
        startActivityWithDefaultTransition(TimelineActivityIntent.hashtag(this, intent.pachliAccountId, tagName))
    }

    override suspend fun search(token: String): List<ComposeAutoCompleteAdapter.AutocompleteResult> {
        return viewModel.searchAutocompleteSuggestions(token)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        super.onCreateMenu(menu, menuInflater)
        menuInflater.inflate(R.menu.activity_followed_tags, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                viewModel.accept(FallibleUiAction.Reload(pachliAccountId))
                true
            }
            else -> super.onMenuItemSelected(menuItem)
        }
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
                    showPronouns = false,
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
