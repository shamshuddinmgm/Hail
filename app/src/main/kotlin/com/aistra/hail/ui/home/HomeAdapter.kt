package com.aistra.hail.ui.home

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.aistra.hail.app.HailData.tags

class HomeAdapter(fragment: HomeFragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = tags.size

    override fun getItemId(position: Int): Long =
        tags.getOrNull(position)?.id?.toLong() ?: position.toLong()

    override fun containsItem(itemId: Long): Boolean =
        tags.any { it.id.toLong() == itemId }

    override fun createFragment(position: Int): Fragment {
        val tagId = tags.getOrNull(position)?.id ?: 0
        return PagerFragment().apply {
            arguments = Bundle().apply { putInt(PagerFragment.ARG_TAG_ID, tagId) }
        }
    }
}
