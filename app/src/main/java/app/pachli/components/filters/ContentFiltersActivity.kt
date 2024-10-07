package app.pachli.components.filters

import android.content.DialogInterface.BUTTON_POSITIVE
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import app.pachli.R
import app.pachli.core.activity.BaseActivity
import app.pachli.core.activity.extensions.TransitionKind
import app.pachli.core.activity.extensions.startActivityWithTransition
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.model.ContentFilter
import app.pachli.core.navigation.EditContentFilterActivityIntent
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.ui.BackgroundMessage
import app.pachli.databinding.ActivityContentFiltersBinding
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ContentFiltersActivity : BaseActivity(), ContentFiltersListener {

    private val binding by viewBinding(ActivityContentFiltersBinding::inflate)
    private val viewModel: ContentFiltersViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.run {
            // Back button
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.addFilterButton.setOnClickListener {
            launchEditContentFilterActivity()
        }

        binding.swipeRefreshLayout.setOnRefreshListener { loadFilters() }
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))
        binding.includedToolbar.appbar.setLiftOnScrollTargetView(binding.filtersList)

        setTitle(R.string.pref_title_content_filters)
    }

    override fun onResume() {
        super.onResume()
        loadFilters()
        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                binding.progressBar.visible(state.loadingState == ContentFiltersViewModel.LoadingState.LOADING)
                binding.swipeRefreshLayout.isRefreshing = state.loadingState == ContentFiltersViewModel.LoadingState.LOADING
                binding.addFilterButton.visible(state.loadingState == ContentFiltersViewModel.LoadingState.LOADED)

                when (state.loadingState) {
                    ContentFiltersViewModel.LoadingState.INITIAL, ContentFiltersViewModel.LoadingState.LOADING -> binding.messageView.hide()
                    ContentFiltersViewModel.LoadingState.ERROR_NETWORK -> {
                        binding.messageView.setup(BackgroundMessage.Network()) {
                            loadFilters()
                        }
                        binding.messageView.show()
                    }

                    ContentFiltersViewModel.LoadingState.ERROR_OTHER -> {
                        binding.messageView.setup(BackgroundMessage.GenericError()) {
                            loadFilters()
                        }
                        binding.messageView.show()
                    }

                    ContentFiltersViewModel.LoadingState.LOADED -> {
                        binding.filtersList.adapter = FiltersAdapter(this@ContentFiltersActivity, state.contentFilters)
                        if (state.contentFilters.isEmpty()) {
                            binding.messageView.setup(BackgroundMessage.Empty())
                            binding.messageView.show()
                        } else {
                            binding.messageView.hide()
                        }
                    }
                }
            }
        }
    }

    private fun loadFilters() {
        viewModel.load()
    }

    private fun launchEditContentFilterActivity(contentFilter: ContentFilter? = null) {
        val intent = contentFilter?.let {
            EditContentFilterActivityIntent.edit(this, intent.pachliAccountId, contentFilter)
        } ?: EditContentFilterActivityIntent(this, intent.pachliAccountId)
        startActivityWithTransition(intent, TransitionKind.SLIDE_FROM_END)
    }

    override fun deleteContentFilter(contentFilter: ContentFilter) {
        lifecycleScope.launch {
            if (showDeleteFilterDialog(contentFilter.title) == BUTTON_POSITIVE) {
                viewModel.deleteContentFilter(contentFilter, binding.root)
            }
        }
    }

    override fun updateContentFilter(updatedContentFilter: ContentFilter) {
        launchEditContentFilterActivity(updatedContentFilter)
    }
}
