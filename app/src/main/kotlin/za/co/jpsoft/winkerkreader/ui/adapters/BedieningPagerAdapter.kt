package za.co.jpsoft.winkerkreader.ui.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import za.co.jpsoft.winkerkreader.ui.fragments.BedieningVandagFragment

class BedieningPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 1   // Phase 2 adds Gevalle, Besoeke tabs here

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> BedieningVandagFragment()
        else -> BedieningVandagFragment()
    }

    fun tabTitle(position: Int) = when (position) {
        0 -> "Vandag"
        else -> ""
    }
}