package app.pachli.components.instancemute.fragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.components.instancemute.adapter.DomainMutesAdapter
import app.pachli.components.instancemute.interfaces.InstanceActionListener
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.network.model.HttpHeaderLink
import app.pachli.core.network.retrofit.MastodonApi
import app.pachli.core.ui.BackgroundMessage
import app.pachli.databinding.FragmentInstanceListBinding
import app.pachli.view.EndlessOnScrollListener
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class InstanceListFragment :
    Fragment(R.layout.fragment_instance_list),

    InstanceActionListener {

    @Inject
    lateinit var api: MastodonApi

    private val binding by viewBinding(FragmentInstanceListBinding::bind)

    private var fetching = false
    private var bottomId: String? = null
    private var adapter = DomainMutesAdapter(this)
    private lateinit var scrollListener: EndlessOnScrollListener

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.addItemDecoration(
            MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL),
        )
        binding.recyclerView.adapter = adapter

        val layoutManager = LinearLayoutManager(view.context)
        binding.recyclerView.layoutManager = layoutManager

        scrollListener = object : EndlessOnScrollListener(layoutManager) {
            override fun onLoadMore(totalItemsCount: Int, view: RecyclerView) {
                if (bottomId != null) {
                    fetchInstances(bottomId)
                }
            }
        }

        binding.recyclerView.addOnScrollListener(scrollListener)
        fetchInstances()
    }

    override fun mute(mute: Boolean, instance: String, position: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (mute) {
                api.blockDomain(instance)
                    .onSuccess { adapter.addItem(instance) }
                    .onFailure { Timber.e(it.throwable, "Error muting domain %s", instance) }
            } else {
                api.unblockDomain(instance)
                    .onSuccess {
                        adapter.removeItem(position)
                        Snackbar.make(binding.recyclerView, getString(R.string.confirmation_domain_unmuted, instance), Snackbar.LENGTH_LONG)
                            .setAction(R.string.action_undo) {
                                mute(true, instance, position)
                            }
                            .show()
                    }
                    .onFailure { e ->
                        Timber.e(e.throwable, "Error unmuting domain %s", instance)
                    }
            }
        }
    }

    private fun fetchInstances(id: String? = null) {
        if (fetching) {
            return
        }
        fetching = true
        binding.instanceProgressBar.show()

        if (id != null) {
            binding.recyclerView.post { adapter.bottomLoading = true }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            api.domainBlocks(id, bottomId)
                .onSuccess { onFetchInstancesSuccess(it.body, it.headers["Link"]) }
                .onFailure { onFetchInstancesFailure(it.throwable) }
        }
    }

    private fun onFetchInstancesSuccess(instances: List<String>, linkHeader: String?) {
        adapter.bottomLoading = false
        binding.instanceProgressBar.hide()

        val links = HttpHeaderLink.parse(linkHeader)
        val next = HttpHeaderLink.findByRelationType(links, "next")
        val fromId = next?.uri?.getQueryParameter("max_id")
        adapter.addItems(instances)
        bottomId = fromId
        fetching = false

        if (adapter.itemCount == 0) {
            binding.messageView.show()
            binding.messageView.setup(BackgroundMessage.Empty())
        } else {
            binding.messageView.hide()
        }
    }

    private fun onFetchInstancesFailure(throwable: Throwable) {
        fetching = false
        binding.instanceProgressBar.hide()
        Timber.e(throwable, "Fetch failure")

        if (adapter.itemCount == 0) {
            binding.messageView.show()
            binding.messageView.setup(throwable) {
                binding.messageView.hide()
                this.fetchInstances(null)
            }
        }
    }
}
