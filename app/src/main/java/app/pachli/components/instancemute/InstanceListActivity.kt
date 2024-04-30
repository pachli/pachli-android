package app.pachli.components.instancemute

import android.os.Bundle
import androidx.fragment.app.commit
import app.pachli.R
import app.pachli.components.instancemute.fragment.InstanceListFragment
import app.pachli.core.activity.BaseActivity
import app.pachli.databinding.ActivityAccountListBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class InstanceListActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAccountListBinding.inflate(layoutInflater)
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
