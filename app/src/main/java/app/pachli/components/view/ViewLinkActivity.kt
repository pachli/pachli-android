package app.pachli.components.view

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import app.pachli.core.activity.BaseActivity
import app.pachli.core.navigation.MainActivityIntent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ViewLinkActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == Intent.ACTION_SEND) {
            val link = intent.getStringExtra(Intent.EXTRA_TEXT)

            val launchIntent = MainActivityIntent(this)
            launchIntent.action = "dev.zwander.mastodonredirect.intent.action.OPEN_FEDI_LINK"
            launchIntent.data = link?.let { Uri.parse(it) }

            startActivity(launchIntent)
            finish()
        }
    }
}
