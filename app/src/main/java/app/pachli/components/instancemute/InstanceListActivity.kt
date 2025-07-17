package app.pachli.components.instancemute

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewGroupCompat
import androidx.fragment.app.commit
import app.pachli.R
import app.pachli.components.instancemute.fragment.InstanceListFragment
import app.pachli.core.activity.BaseActivity
import app.pachli.core.common.extensions.viewBinding
import app.pachli.core.ui.appbar.FadeChildScrollEffect
import app.pachli.core.ui.extensions.addScrollEffect
import app.pachli.core.ui.extensions.applyDefaultWindowInsets
import app.pachli.databinding.ActivityAccountListBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class InstanceListActivity : BaseActivity() {
    private val binding by viewBinding(ActivityAccountListBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        binding.includedToolbar.appbar.applyDefaultWindowInsets()
        binding.includedToolbar.toolbar.addScrollEffect(FadeChildScrollEffect)

        setContentView(binding.root)

        setSupportActionBar(binding.includedToolbar.toolbar)
        supportActionBar?.apply {
            setTitle(R.string.title_domain_mutes)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        supportFragmentManager.commit {
            replace(R.id.fragment_container, InstanceListFragment())
        }
    }
}
