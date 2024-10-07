package app.pachli.components.search.fragments

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import app.pachli.R
import app.pachli.components.search.SearchViewModel
import app.pachli.core.activity.BottomSheetActivity
import app.pachli.core.activity.extensions.startActivityWithDefaultTransition
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.common.extensions.visible
import app.pachli.core.navigation.AccountActivityIntent
import app.pachli.core.navigation.TimelineActivityIntent
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.ui.LinkListener
import app.pachli.databinding.FragmentSearchBinding
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import javax.inject.Inject
import kotlin.properties.Delegates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

abstract class SearchFragment<T : Any> :
    Fragment(R.layout.fragment_search),
    LinkListener,
    SwipeRefreshLayout.OnRefreshListener,
    MenuProvider {

    @Inject
    lateinit var mastodonApi: MastodonApi

    protected val viewModel: SearchViewModel by activityViewModels()

    protected val binding by viewBinding(FragmentSearchBinding::bind)

    private var snackbarErrorRetry: Snackbar? = null

    abstract fun createAdapter(): PagingDataAdapter<T, *>

    abstract val data: Flow<PagingData<T>>
    protected lateinit var adapter: PagingDataAdapter<T, *>

    private var currentQuery: String = ""

    protected var pachliAccountId by Delegates.notNull<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pachliAccountId = requireArguments().getLong(ARG_PACHLI_ACCOUNT_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initAdapter()
        setupSwipeRefreshLayout()
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        subscribeObservables()
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        binding.swipeRefreshLayout.setColorSchemeColors(MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary))
    }

    private fun subscribeObservables() {
        viewLifecycleOwner.lifecycleScope.launch {
            data.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }

        adapter.addLoadStateListener { loadState ->
            if (loadState.refresh is LoadState.Error) {
                showError()
            }

            val isNewSearch = currentQuery != viewModel.currentQuery

            if (loadState.refresh == LoadState.Loading && isNewSearch && !binding.swipeRefreshLayout.isRefreshing) {
                binding.searchProgressBar.show()
            } else {
                binding.searchProgressBar.hide()
            }
            binding.searchRecyclerView.visible(loadState.refresh is LoadState.NotLoading || !isNewSearch || binding.swipeRefreshLayout.isRefreshing)

            if (loadState.refresh != LoadState.Loading) {
                binding.swipeRefreshLayout.isRefreshing = false
                currentQuery = viewModel.currentQuery
            }

            binding.progressBarBottom.visible(loadState.append == LoadState.Loading)

            binding.searchNoResultsText.visible(loadState.refresh is LoadState.NotLoading && adapter.itemCount == 0 && viewModel.currentQuery.isNotEmpty())
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_timeline, menu)
        menu.findItem(R.id.action_refresh)?.apply {
            icon = IconicsDrawable(requireContext(), GoogleMaterial.Icon.gmd_refresh).apply {
                sizeDp = 20
                colorInt = MaterialColors.getColor(binding.root, android.R.attr.textColorPrimary)
            }
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_refresh -> {
                binding.swipeRefreshLayout.isRefreshing = true
                onRefresh()
                true
            }
            else -> false
        }
    }

    private fun initAdapter() {
        binding.searchRecyclerView.layoutManager = LinearLayoutManager(binding.searchRecyclerView.context)
        adapter = createAdapter()
        binding.searchRecyclerView.adapter = adapter
        binding.searchRecyclerView.setHasFixedSize(true)
        (binding.searchRecyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    }

    private fun showError() {
        if (snackbarErrorRetry?.isShown != true) {
            snackbarErrorRetry = Snackbar.make(binding.root, R.string.failed_search, Snackbar.LENGTH_INDEFINITE)
            snackbarErrorRetry?.setAction(app.pachli.core.ui.R.string.action_retry) {
                snackbarErrorRetry = null
                adapter.retry()
            }
            snackbarErrorRetry?.show()
        }
    }

    override fun onViewAccount(id: String) {
        bottomSheetActivity?.startActivityWithDefaultTransition(
            AccountActivityIntent(requireContext(), pachliAccountId, id),
        )
    }

    override fun onViewTag(tag: String) {
        bottomSheetActivity?.startActivityWithDefaultTransition(
            TimelineActivityIntent.hashtag(requireContext(), pachliAccountId, tag),
        )
    }

    override fun onViewUrl(url: String) {
        bottomSheetActivity?.viewUrl(pachliAccountId, url)
    }

    protected val bottomSheetActivity
        get() = (activity as? BottomSheetActivity)

    override fun onRefresh() {
        adapter.refresh()
    }

    companion object {
        const val ARG_PACHLI_ACCOUNT_ID = "app.pachli.ARG_PACHLI_ACCOUNT_ID"

        inline fun <reified T : SearchFragment<*>> newInstance(pachliAccountId: Long): T {
            val fragment = T::class.java.getDeclaredConstructor().newInstance()
            fragment.arguments = Bundle(1).apply {
                putLong(ARG_PACHLI_ACCOUNT_ID, pachliAccountId)
            }
            return fragment
        }
    }
}
