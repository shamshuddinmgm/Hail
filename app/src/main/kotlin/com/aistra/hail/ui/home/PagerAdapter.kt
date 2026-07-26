package com.aistra.hail.ui.home

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aistra.hail.R
import com.aistra.hail.app.AppInfo
import com.aistra.hail.app.HailData
import com.aistra.hail.utils.AppIconCache
import com.aistra.hail.utils.HPackages.myUserId
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Job

class PagerAdapter(
    private val selectedList: List<AppInfo>,
    private val flags: MutableMap<String, Int> = mutableMapOf()
) : ListAdapter<AppInfo, PagerAdapter.ViewHolder>(HomeDiff(selectedList, flags)) {
    lateinit var onItemClickListener: OnItemClickListener
    lateinit var onItemLongClickListener: OnItemLongClickListener

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_home, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val info = currentList[position]
        flags[info.packageName] = info.getFlag(selectedList)
        holder.loadIconJob?.cancel()
        holder.itemView.setOnClickListener { onItemClickListener.onItemClick(info) }
        holder.itemView.setOnLongClickListener { onItemLongClickListener.onItemLongClick(holder, info) }

        holder.icon.run {
            info.applicationInfo?.let {
                holder.loadIconJob = AppIconCache.loadIconBitmapAsync(
                    context,
                    it,
                    myUserId,
                    this,
                    HailData.grayscaleIcon && info.state == AppInfo.State.FROZEN
                )
            } ?: run {
                setImageDrawable(context.packageManager.defaultActivityIcon)
                colorFilter = null
            }
        }
        holder.name.run {
            text = buildString {
                if (info.pinned) append("\uD83D\uDCCC")
                if (!HailData.grayscaleIcon && info.state == AppInfo.State.FROZEN) append("\u2744\uFE0F")
                if (info.whitelisted) append("\uD83D\uDD12")
                append(info.name)
            }
            isEnabled = !HailData.grayscaleIcon || info.state != AppInfo.State.FROZEN
            when {
                info in selectedList -> setTextColor(
                    MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
                )
                info.state == AppInfo.State.NOT_FOUND -> setTextColor(
                    MaterialColors.getColor(this, androidx.appcompat.R.attr.colorError)
                )
                else -> setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, HailData.homeFontSize)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.loadIconJob?.cancel()
        holder.loadIconJob = null
        super.onViewRecycled(holder)
    }

    fun onDestroy() {
        // no-op: jobs cancelled per ViewHolder
    }

    fun invalidateContentFlags() = flags.clear()

    private class HomeDiff(
        private val selectedList: List<AppInfo>, private val flags: Map<String, Int>
    ) : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean = oldItem == newItem

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            val cached = flags[oldItem.packageName]
            val fresh = newItem.getFlag(selectedList)
            return cached != null && cached == fresh
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val name: TextView = view.findViewById(R.id.app_name)
        var loadIconJob: Job? = null
    }

    interface OnItemClickListener {
        fun onItemClick(info: AppInfo)
    }

    interface OnItemLongClickListener {
        fun onItemLongClick(holder: ViewHolder, info: AppInfo): Boolean
    }
}

private fun AppInfo.getFlag(selectedList: List<AppInfo>) =
    (1 shl state.ordinal) or
            (this in selectedList).shl(3) or
            whitelisted.shl(4) or
            pinned.shl(5) or
            ((pinOrder and 0xFF) shl 6) or
            ((frozenMode?.hashCode() ?: 0) and 0x3FFF).shl(14)

private fun Boolean.shl(bitCount: Int) = if (this) 1 shl bitCount else 0
