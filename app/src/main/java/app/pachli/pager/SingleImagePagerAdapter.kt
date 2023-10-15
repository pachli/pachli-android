package app.pachli.pager

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import app.pachli.ViewMediaAdapter
import app.pachli.fragment.ViewMediaFragment

class SingleImagePagerAdapter(
    activity: FragmentActivity,
    private val imageUrl: String,
) : ViewMediaAdapter(activity) {

    override fun createFragment(position: Int): Fragment {
        return if (position == 0) {
            ViewMediaFragment.newInstance(imageUrl)
        } else {
            throw IllegalStateException()
        }
    }

    override fun getItemCount() = 1

    override fun onTransitionEnd(position: Int) {
    }
}
