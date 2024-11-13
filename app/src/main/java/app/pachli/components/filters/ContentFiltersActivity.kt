package app.pachli.components.filters

import android.content.DialogInterface.BUTTON_POSITIVE
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.pachli.R
import app.pachli.core.activity.BaseActivity
import app.pachli.core.activity.extensions.TransitionKind
import app.pachli.core.activity.extensions.startActivityWithTransition
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.model.ContentFilter
import app.pachli.core.navigation.EditContentFilterActivityIntent
import app.pachli.core.navigation.pachliAccountId
import app.pachli.core.ui.BackgroundMessage
import app.pachli.databinding.ActivityContentFiltersBinding
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ContentFiltersActivity : BaseActivity(), ContentFiltersListener {
    private val binding by viewBinding(ActivityContentFiltersBinding::inflate)
    private val viewModel: ContentFiltersViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<ContentFiltersViewModel.Factory> { factory ->
                factory.create(intent.pachliAccountId)
            }
        },
    )

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

        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.swipeRefreshLayout.isRefreshing = false
            viewModel.refreshContentFilters()
        }

        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))
        binding.includedToolbar.appbar.setLiftOnScrollTargetView(binding.filtersList)

        setTitle(R.string.pref_title_content_filters)

        bind()
    }

    private fun bind() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.contentFilters.collect { contentFilters ->
                    binding.filtersList.adapter = FiltersAdapter(this@ContentFiltersActivity, contentFilters.contentFilters)
                    if (contentFilters.contentFilters.isEmpty()) {
                        binding.messageView.setup(BackgroundMessage.Empty())
                        binding.messageView.show()
                    } else {
                        binding.messageView.hide()
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.operationCount.collectLatest {
                if (it == 0) binding.progressIndicator.hide() else binding.progressIndicator.show()
            }
        }
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
