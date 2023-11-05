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
import app.pachli.databinding.FragmentInstanceListBinding
import app.pachli.network.MastodonApi
import app.pachli.util.HttpHeaderLink
import app.pachli.util.hide
import app.pachli.util.show
import app.pachli.util.viewBinding
import app.pachli.view.EndlessOnScrollListener
import at.connyduck.calladapter.networkresult.fold
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

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
                api.blockDomain(instance).fold({
                    adapter.addItem(instance)
                }, { e ->
                    Timber.e("Error muting domain $instance", e)
                },)
            } else {
                api.unblockDomain(instance).fold({
                    adapter.removeItem(position)
                    Snackbar.make(binding.recyclerView, getString(R.string.confirmation_domain_unmuted, instance), Snackbar.LENGTH_LONG)
                        .setAction(R.string.action_undo) {
                            mute(true, instance, position)
                        }
                        .show()
                }, { e ->
                    Timber.e("Error unmuting domain $instance", e)
                },)
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
            val response = api.domainBlocks(id, bottomId)
            val instances = response.body()

            if (response.isSuccessful && instances != null) {
                onFetchInstancesSuccess(instances, response.headers()["Link"])
            } else {
                onFetchInstancesFailure(Exception(response.message()))
            }
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
            binding.messageView.setup(
                R.drawable.elephant_friend_empty,
                R.string.message_empty,
                null,
            )
        } else {
            binding.messageView.hide()
        }
    }

    private fun onFetchInstancesFailure(throwable: Throwable) {
        fetching = false
        binding.instanceProgressBar.hide()
        Timber.e("Fetch failure", throwable)

        if (adapter.itemCount == 0) {
            binding.messageView.show()
            binding.messageView.setup(throwable) {
                binding.messageView.hide()
                this.fetchInstances(null)
            }
        }
    }
}
