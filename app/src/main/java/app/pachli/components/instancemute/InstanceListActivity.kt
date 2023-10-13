package app.pachli.components.instancemute

import android.os.Bundle
import app.pachli.BaseActivity
import app.pachli.R
import app.pachli.components.instancemute.fragment.InstanceListFragment
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

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, InstanceListFragment())
            .commit()
    }
}
