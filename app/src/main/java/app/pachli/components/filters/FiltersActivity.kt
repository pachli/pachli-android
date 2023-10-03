package app.pachli.components.filters

import android.content.DialogInterface.BUTTON_POSITIVE
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import app.pachli.BaseActivity
import app.pachli.R
import app.pachli.databinding.ActivityFiltersBinding
import app.pachli.entity.Filter
import app.pachli.util.hide
import app.pachli.util.show
import app.pachli.util.viewBinding
import app.pachli.util.visible
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FiltersActivity : BaseActivity(), FiltersListener {

    private val binding by viewBinding(ActivityFiltersBinding::inflate)
    private val viewModel: FiltersViewModel by viewModels()

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
            launchEditFilterActivity()
        }

        binding.swipeRefreshLayout.setOnRefreshListener { loadFilters() }
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))
        binding.includedToolbar.appbar.setLiftOnScrollTargetView(binding.filtersList)

        setTitle(R.string.pref_title_timeline_filters)
    }

    override fun onResume() {
        super.onResume()
        loadFilters()
        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                binding.progressBar.visible(state.loadingState == FiltersViewModel.LoadingState.LOADING)
                binding.swipeRefreshLayout.isRefreshing = state.loadingState == FiltersViewModel.LoadingState.LOADING
                binding.addFilterButton.visible(state.loadingState == FiltersViewModel.LoadingState.LOADED)

                when (state.loadingState) {
                    FiltersViewModel.LoadingState.INITIAL, FiltersViewModel.LoadingState.LOADING -> binding.messageView.hide()
                    FiltersViewModel.LoadingState.ERROR_NETWORK -> {
                        binding.messageView.setup(R.drawable.errorphant_offline, R.string.error_network) {
                            loadFilters()
                        }
                        binding.messageView.show()
                    }
                    FiltersViewModel.LoadingState.ERROR_OTHER -> {
                        binding.messageView.setup(R.drawable.errorphant_error, R.string.error_generic) {
                            loadFilters()
                        }
                        binding.messageView.show()
                    }
                    FiltersViewModel.LoadingState.LOADED -> {
                        binding.filtersList.adapter = FiltersAdapter(this@FiltersActivity, state.filters)
                        if (state.filters.isEmpty()) {
                            binding.messageView.setup(
                                R.drawable.elephant_friend_empty,
                                R.string.message_empty,
                                null,
                            )
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

    private fun launchEditFilterActivity(filter: Filter? = null) {
        val intent = Intent(this, EditFilterActivity::class.java).apply {
            if (filter != null) {
                putExtra(EditFilterActivity.FILTER_TO_EDIT, filter)
            }
        }
        startActivity(intent)
        overridePendingTransition(R.anim.slide_from_right, R.anim.slide_to_left)
    }

    override fun deleteFilter(filter: Filter) {
        lifecycleScope.launch {
            if (showDeleteFilterDialog(filter.title) == BUTTON_POSITIVE) {
                viewModel.deleteFilter(filter, binding.root)
            }
        }
    }

    override fun updateFilter(updatedFilter: Filter) {
        launchEditFilterActivity(updatedFilter)
    }
}
