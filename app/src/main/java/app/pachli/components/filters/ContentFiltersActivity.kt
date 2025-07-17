package app.pachli.components.filters

import android.content.DialogInterface.BUTTON_POSITIVE
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewGroupCompat
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
import app.pachli.core.ui.appbar.FadeChildScrollEffect
import app.pachli.core.ui.extensions.addScrollEffect
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.databinding.ActivityContentFiltersBinding
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import java.text.Collator
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

    private val adapter = ContentFiltersAdapter(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        binding.includedToolbar.appbar.applyDefaultWindowInsets()
        binding.includedToolbar.toolbar.addScrollEffect(FadeChildScrollEffect)
        binding.filtersList.applyDefaultWindowInsets()
        binding.addFilterButton.applyDefaultWindowInsets()

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

        binding.filtersList.adapter = adapter

        setTitle(R.string.pref_title_content_filters)

        bind()
    }

    private fun bind() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.contentFilters.collect { contentFilters ->
                    adapter.submitList(contentFilters.contentFilters.sortedWith(comparebyTitle))
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

    companion object {
        /** Locale aware collator by text. */
        private val text: Collator = Collator.getInstance().apply { strength = Collator.SECONDARY }

        /**
         * Locale-aware comparator for content filters. Case-insenstive comparison by
         * the filter's title.
         */
        val comparebyTitle: Comparator<ContentFilter> = compareBy(text) { it.title }
    }
}
