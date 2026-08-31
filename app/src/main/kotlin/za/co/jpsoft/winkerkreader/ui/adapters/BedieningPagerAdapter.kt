package za.co.jpsoft.winkerkreader.ui.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import za.co.jpsoft.winkerkreader.ui.fragments.BedieningAllesFragment

class BedieningPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 2

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> BedieningAllesFragment.newInstance(isVandagOnly = false) // "Vandag (Alles)"
        1 -> BedieningAllesFragment.newInstance(isVandagOnly = true)  // "Vandag (Herinnerings)"
        else -> BedieningAllesFragment.newInstance(isVandagOnly = false)
    }

    fun tabTitle(position: Int) = when (position) {
        0 -> "Vandag (AllVandag Tabes)"
        1 -> "Bedieningsherinnerings"
        else -> ""
    }
}