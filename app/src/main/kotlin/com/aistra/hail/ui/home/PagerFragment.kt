package com.aistra.hail.ui.home

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.*
import android.widget.EditText
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.aistra.hail.HailApp.Companion.app
import com.aistra.hail.R
import com.aistra.hail.app.AppInfo
import com.aistra.hail.app.AppManager
import com.aistra.hail.app.HailApi
import com.aistra.hail.app.HailApi.addTag
import com.aistra.hail.app.HailData
import com.aistra.hail.databinding.DialogInputBinding
import com.aistra.hail.databinding.FragmentPagerBinding
import com.aistra.hail.extensions.*
import com.aistra.hail.ui.main.MainFragment
import com.aistra.hail.ui.theme.AppTheme
import com.aistra.hail.utils.AppIconCache
import com.aistra.hail.utils.AppMetaCache
import com.aistra.hail.utils.FuzzySearch
import com.aistra.hail.utils.HPackages
import com.aistra.hail.utils.HShortcuts
import com.aistra.hail.utils.HShizuku
import com.aistra.hail.utils.HUI
import com.aistra.hail.utils.LaunchReady
import com.aistra.hail.utils.NameComparator
import com.aistra.hail.utils.NineKeySearch
import com.aistra.hail.utils.PinyinSearch
import com.aistra.hail.work.HWork
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class PagerFragment : MainFragment(), PagerAdapter.OnItemClickListener, PagerAdapter.OnItemLongClickListener,
    MenuProvider {

    private var query: String = String()
    /** 0 = all apps, 1 = user apps only, 2 = system apps only */
    private var appTypeFilter: Int = APP_TYPE_ALL
    private var _binding: FragmentPagerBinding? = null
    private val binding get() = _binding!!
    private lateinit var pagerAdapter: PagerAdapter
    private var itemTouchHelper: ItemTouchHelper? = null
    private var dragWorkingList: MutableList<AppInfo>? = null
    private var multiselect: Boolean
        set(value) {
            (parentFragment as HomeFragment).multiselect = value
        }
        get() = (parentFragment as HomeFragment).multiselect
    private val selectedList get() = (parentFragment as HomeFragment).selectedList
    private val tabs: TabLayout get() = (parentFragment as HomeFragment).binding.tabs
    private val adapter get() = (parentFragment as HomeFragment).binding.pager.adapter as HomeAdapter
    /** Tag bound to this pager page (not the currently selected tab — avoids off-screen wrong-list bugs). */
    private val tag: com.aistra.hail.app.TagInfo
        get() {
            val argId = arguments?.getInt(ARG_TAG_ID, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }
            return (argId?.let { HailData.tagById(it) }
                ?: HailData.tags.getOrNull(tabs.selectedTabPosition)
                ?: HailData.tags.firstOrNull())
                ?: com.aistra.hail.app.TagInfo(getString(R.string.label_default), 0)
        }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val menuHost = requireActivity() as MenuHost
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        _binding = FragmentPagerBinding.inflate(inflater, container, false)
        pagerAdapter = PagerAdapter(selectedList).apply {
            onItemClickListener = this@PagerFragment
            onItemLongClickListener = this@PagerFragment
        }
        binding.recyclerView.run {
            layoutManager = GridLayoutManager(
                activity, resources.getInteger(
                    if (HailData.compactIcon) R.integer.home_span_compact else R.integer.home_span
                )
            )
            adapter = pagerAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(24)
            itemAnimator = null
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    when (newState) {
                        RecyclerView.SCROLL_STATE_IDLE -> activity.fab.run {
                            postDelayed({ if (tag == true) show() }, 1000)
                        }

                        RecyclerView.SCROLL_STATE_DRAGGING -> activity.fab.hide()
                    }
                }
            })
            applyDefaultInsetter { paddingRelative(isRtl, bottom = isLandscape) }
            activity.fabContainer.doOnLayout { container ->
                val lp = container.layoutParams as ViewGroup.MarginLayoutParams
                updatePadding(bottom = paddingBottom + container.height + lp.bottomMargin)
            }
            attachPinnedDragHelper(this)
        }
        binding.fastScroll.attachTo(binding.recyclerView)

        binding.refresh.apply {
            setOnRefreshListener {
                updateCurrentList(forceLiveRefresh = true)
                binding.refresh.isRefreshing = false
            }
            applyDefaultInsetter { marginRelative(isRtl, start = !isLandscape, end = true) }
        }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // Skip duplicate rebuild when returning to an already-rendered tag tab
        updateCurrentList(fromTagSwitch = lastRenderedTagId == tag.id && pagerAdapter.itemCount > 0)
        updateBarTitle()
        activity.appbar.setLiftOnScrollTargetView(binding.recyclerView)
        tabs.getTabAt(tabs.selectedTabPosition)?.view?.setOnLongClickListener {
            if (isResumed) showTagDialog()
            true
        }
        activity.fab.setOnClickListener {
            setListFrozen(true, pagerAdapter.currentList.filterNot { it.whitelisted }, preferredTagId = tag.id)
        }
        activity.fab.setOnLongClickListener {
            setListFrozen(true)
            true
        }
        updateFreezeFabLabel()
    }

    fun expandSearch() {
        val item = searchMenuItem ?: return
        item.isVisible = true
        item.expandActionView()
    }

    fun forceIconRefresh() {
        if (!isAdded || _binding == null) return
        HailData.checkedList.forEach { it.invalidateCaches() }
        lastRenderedTagId = null
        updateCurrentList(forceLiveRefresh = true)
    }

    private fun updateFreezeFabLabel() {
        val mode = HailData.workingModeForTag(tag.id)
        val short = HailData.workingModeShortLabel(mode)
        activity.fab.text = getString(R.string.action_freeze_mode, short)
    }

    private var searchMenuItem: MenuItem? = null
    private var listUpdateJob: Job? = null
    private var liveRefreshJob: Job? = null
    private var lastRenderedTagId: Int? = null
    private var lastRenderedQuery: String? = null
    private var lastRenderedTypeFilter: Int? = null

    /**
     * @param fromTagSwitch when true, skip if this tag's list is already on screen (swipe settle).
     * @param forceLiveRefresh force a background PM refresh (pull-to-refresh / icon pack).
     */
    internal fun updateCurrentList(fromTagSwitch: Boolean = false, forceLiveRefresh: Boolean = false) {
        if (!isAdded || _binding == null) return
        val tagId = tag.id
        val q = query
        val typeFilter = appTypeFilter
        if (fromTagSwitch &&
            lastRenderedTagId == tagId &&
            lastRenderedQuery == q &&
            lastRenderedTypeFilter == typeFilter &&
            pagerAdapter.itemCount > 0
        ) {
            if (isResumed) updateFreezeFabLabel()
            return
        }
        val showUninstalled = HailData.showUninstalled
        val nineKey = HailData.nineKeySearch
        listUpdateJob?.cancel()
        listUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
            val list = withContext(Dispatchers.Default) {
                val source = HailData.checkedList
                AppMetaCache.applyToAll(source)
                if (q.isNotEmpty() || typeFilter != APP_TYPE_ALL) {
                    source.forEach { it.refreshFromPackageManager() }
                }
                source.filter {
                    if (q.isEmpty()) tagId in it.tagIdList
                    else ((nineKey && NineKeySearch.search(
                        q, it.packageName, it.name.toString()
                    )) || FuzzySearch.search(it.packageName, q) || FuzzySearch.search(
                        it.name.toString(), q
                    ) || PinyinSearch.searchPinyinAll(it.name.toString(), q))
                }.filter {
                    when (typeFilter) {
                        APP_TYPE_USER -> !it.isSystemHint
                        APP_TYPE_SYSTEM -> it.isSystemHint
                        else -> true
                    }
                }.filter {
                    showUninstalled || it.isInstalledHint
                }.sortedWith(NameComparator)
            }
            if (!isAdded || _binding == null) return@launch
            binding.empty.isVisible = list.isEmpty()
            pagerAdapter.submitList(list) {
                LaunchReady.markHomeReady()
            }
            LaunchReady.markHomeReady()
            lastRenderedTagId = tagId
            lastRenderedQuery = q
            lastRenderedTypeFilter = typeFilter
            if (isResumed) updateFreezeFabLabel()

            // Background meta refresh — DiffUtil only, never notifyDataSetChanged
            if (forceLiveRefresh || (!fromTagSwitch && q.isEmpty())) {
                scheduleQuietLiveRefresh()
            }
        }
    }

    /** Soft PM refresh after paint — updates disk cache; DiffUtil rebinds only changed rows. */
    private fun scheduleQuietLiveRefresh() {
        liveRefreshJob?.cancel()
        liveRefreshJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            delay(450)
            HailData.checkedList.forEach { it.refreshFromPackageManager() }
            AppMetaCache.flush()
            withContext(Dispatchers.Main) {
                if (!isAdded || _binding == null) return@withContext
                // New list instance → DiffUtil compares flags without notifyDataSetChanged
                pagerAdapter.submitList(pagerAdapter.currentList.toList())
            }
        }
    }

    private fun updateBarTitle() {
        activity.supportActionBar?.title =
            if (multiselect) getString(R.string.msg_selected, selectedList.size.toString())
            else getString(R.string.app_name)
    }

    override fun onItemClick(info: AppInfo) {
        if (multiselect) {
            if (info in selectedList) selectedList.remove(info)
            else selectedList.add(info)
            updateCurrentList()
            updateBarTitle()
            return
        }
        if (info.applicationInfo == null) {
            Snackbar.make(activity.fab, R.string.app_not_installed, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_remove_home) { removeCheckedApp(info.packageName) }.show()
            return
        }
        launchApp(info.packageName)
    }

    override fun onItemLongClick(holder: PagerAdapter.ViewHolder, info: AppInfo): Boolean {
        if (info.applicationInfo == null && (!multiselect || info !in selectedList)) {
            exportToClipboard(listOf(info))
            return true
        }
        if (info in selectedList) {
            onMultiSelect()
            return true
        }
        val pkg = info.packageName
        val frozen = AppManager.isAppFrozen(pkg)
        val actionLabel = getString(if (frozen) R.string.action_unfreeze else R.string.action_freeze)

        data class HomeAction(val id: Int, val title: String)

        val actions = buildList {
            add(HomeAction(ACT_LAUNCH, getString(R.string.action_launch)))
            add(HomeAction(ACT_FREEZE_TOGGLE, actionLabel))
            add(HomeAction(ACT_DEFERRED, getString(R.string.action_deferred_task)))
            if (info.pinned) {
                add(HomeAction(ACT_PIN_TOP, getString(R.string.action_pin_move_top)))
                add(HomeAction(ACT_PIN_UP, getString(R.string.action_pin_move_up)))
                add(HomeAction(ACT_PIN_DOWN, getString(R.string.action_pin_move_down)))
                add(HomeAction(ACT_PIN_BOTTOM, getString(R.string.action_pin_move_bottom)))
                if (query.isEmpty() && !multiselect) {
                    add(HomeAction(ACT_PIN_DRAG, getString(R.string.action_pin_drag)))
                }
                add(HomeAction(ACT_PIN_TOGGLE, getString(R.string.action_unpin)))
            } else {
                add(HomeAction(ACT_PIN_TOGGLE, getString(R.string.action_pin)))
            }
            add(
                HomeAction(
                    ACT_WHITELIST_TOGGLE,
                    getString(if (info.whitelisted) R.string.action_remove_whitelist else R.string.action_whitelist)
                )
            )
            add(HomeAction(ACT_TAG, getString(R.string.action_tag_set)))
            add(HomeAction(ACT_SHORTCUT, getString(R.string.action_add_pin_shortcut)))
            add(HomeAction(ACT_EXPORT, getString(R.string.action_export_clipboard)))
            add(HomeAction(ACT_REMOVE, getString(R.string.action_remove_home)))
            if (frozen) add(HomeAction(ACT_UNFREEZE_REMOVE, getString(R.string.action_unfreeze_remove_home)))
        }

        MaterialAlertDialogBuilder(activity).setTitle(info.name)
            .setItems(actions.map { it.title }.toTypedArray()) { _, which ->
                when (actions[which].id) {
                    ACT_LAUNCH -> launchApp(pkg)
                    ACT_FREEZE_TOGGLE -> setListFrozen(!frozen, listOf(info))
                    ACT_DEFERRED -> {
                        val values = resources.getIntArray(R.array.deferred_task_values)
                        val entries = arrayOfNulls<String>(values.size)
                        values.forEachIndexed { i, it ->
                            entries[i] = resources.getQuantityString(R.plurals.deferred_task_entry, it, it)
                        }
                        MaterialAlertDialogBuilder(activity).setTitle(R.string.action_deferred_task)
                            .setItems(entries) { _, i ->
                                HWork.setDeferredFrozen(pkg, !frozen, values[i].toLong())
                                Snackbar.make(
                                    activity.fab, resources.getQuantityString(
                                        R.plurals.msg_deferred_task, values[i], values[i], actionLabel, info.name
                                    ), Snackbar.LENGTH_INDEFINITE
                                ).setAction(R.string.action_undo) { HWork.cancelWork(pkg) }.show()
                            }.setNegativeButton(android.R.string.cancel, null).show()
                    }

                    ACT_PIN_TOGGLE -> {
                        HailData.togglePinned(info)
                        updateCurrentList()
                    }

                    ACT_PIN_TOP -> {
                        if (HailData.movePinnedToExtreme(info, toStart = true)) updateCurrentList()
                    }

                    ACT_PIN_UP -> {
                        if (HailData.movePinned(info, -1)) updateCurrentList()
                    }

                    ACT_PIN_DOWN -> {
                        if (HailData.movePinned(info, 1)) updateCurrentList()
                    }

                    ACT_PIN_BOTTOM -> {
                        if (HailData.movePinnedToExtreme(info, toStart = false)) updateCurrentList()
                    }

                    ACT_PIN_DRAG -> {
                        Snackbar.make(activity.fab, R.string.msg_pin_drag, Snackbar.LENGTH_SHORT).show()
                        binding.recyclerView.post {
                            if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                                itemTouchHelper?.startDrag(holder)
                            }
                        }
                    }

                    ACT_WHITELIST_TOGGLE -> {
                        info.whitelisted = !info.whitelisted
                        HailData.saveApps()
                        updateCurrentList()
                    }

                    ACT_TAG -> tagDialog(info)

                    ACT_SHORTCUT -> if (tabs.tabCount > 1) MaterialAlertDialogBuilder(requireActivity())
                        .setTitle(R.string.action_unfreeze_tag)
                        .setItems(HailData.tags.map { it.name }.toTypedArray()) { _, index ->
                            showPrerequisiteDialog(
                                info, pkg,
                                HailApi.getIntentForPackage(HailApi.ACTION_LAUNCH, pkg)
                                    .addTag(HailData.tags[index].name)
                            )
                        }.setPositiveButton(R.string.action_skip) { _, _ ->
                            showPrerequisiteDialog(
                                info, pkg,
                                HailApi.getIntentForPackage(HailApi.ACTION_LAUNCH, pkg)
                            )
                        }.setNegativeButton(android.R.string.cancel, null).show()
                    else showPrerequisiteDialog(
                        info, pkg,
                        HailApi.getIntentForPackage(HailApi.ACTION_LAUNCH, pkg)
                    )

                    ACT_EXPORT -> exportToClipboard(listOf(info))
                    ACT_REMOVE -> removeCheckedApp(pkg)
                    ACT_UNFREEZE_REMOVE -> {
                        setListFrozen(false, listOf(info), false)
                        if (!AppManager.isAppFrozen(pkg)) removeCheckedApp(pkg)
                    }
                }
            }.setNeutralButton(R.string.action_details) { _, _ ->
                HUI.startActivity(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS, HPackages.packageUri(pkg)
                )
            }.setNegativeButton(android.R.string.cancel, null).show()
        return true
    }

    private fun attachPinnedDragHelper(recyclerView: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun isLongPressDragEnabled(): Boolean = false

            override fun getMovementFlags(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
            ): Int {
                val pos = viewHolder.bindingAdapterPosition
                val info = pagerAdapter.currentList.getOrNull(pos)
                if (info == null || !info.pinned || multiselect || query.isNotEmpty()) return 0
                return makeMovementFlags(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
                )
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                val working = dragWorkingList ?: pagerAdapter.currentList.toMutableList().also { dragWorkingList = it }
                val fromInfo = working.getOrNull(from) ?: return false
                val toInfo = working.getOrNull(to) ?: return false
                if (!fromInfo.pinned || !toInfo.pinned) return false
                val item = working.removeAt(from)
                working.add(to, item)
                // Keep pinOrder in sync live so DiffUtil/sort stays consistent mid-drag
                working.filter { it.pinned }.forEachIndexed { index, app -> app.pinOrder = index }
                pagerAdapter.submitList(working.toList())
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val working = dragWorkingList ?: return
                dragWorkingList = null
                HailData.applyPinnedOrder(working.filter { it.pinned })
                updateCurrentList()
            }
        }
        itemTouchHelper = ItemTouchHelper(callback).also { it.attachToRecyclerView(recyclerView) }
    }

    companion object {
        const val ARG_TAG_ID = "tag_id"
        private const val APP_TYPE_ALL = 0
        private const val APP_TYPE_USER = 1
        private const val APP_TYPE_SYSTEM = 2

        private const val ACT_LAUNCH = 1
        private const val ACT_FREEZE_TOGGLE = 2
        private const val ACT_DEFERRED = 3
        private const val ACT_PIN_TOGGLE = 4
        private const val ACT_PIN_TOP = 5
        private const val ACT_PIN_UP = 6
        private const val ACT_PIN_DOWN = 7
        private const val ACT_PIN_BOTTOM = 8
        private const val ACT_PIN_DRAG = 9
        private const val ACT_WHITELIST_TOGGLE = 10
        private const val ACT_TAG = 11
        private const val ACT_SHORTCUT = 12
        private const val ACT_EXPORT = 13
        private const val ACT_REMOVE = 14
        private const val ACT_UNFREEZE_REMOVE = 15
    }

    private fun tagDialog(info: AppInfo) {
        val allTags = HailData.tags
        val checkedItems = BooleanArray(allTags.size) { index ->
            allTags[index].id in info.tagIdList
        }
        val dialogView = layoutInflater.inflate(R.layout.dialog_tag_select, null)
        val searchEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.search_text)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.tag_list)
        val tagCheckAdapter = TagCheckAdapter(allTags, checkedItems)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = tagCheckAdapter
        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { tagCheckAdapter.filter(s?.toString() ?: "") }
        })
        MaterialAlertDialogBuilder(activity).setTitle(R.string.action_tag_set).setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                info.tagIdList.clear()
                checkedItems.forEachIndexed { index, checked ->
                    if (checked) info.tagIdList.add(allTags[index].id)
                }
                val defaultTagId = 0
                if (info.tagIdList.isEmpty()) {
                    // Nothing selected — restore Default tag instead of removing the app
                    info.tagIdList.add(defaultTagId)
                } else if (info.tagIdList.size > 1 || info.tagIdList.first() != defaultTagId) {
                    // Assigned to at least one real tag — remove Default tag if present
                    info.tagIdList.remove(defaultTagId)
                }
                HailData.saveApps()
                updateCurrentList()
            }.setNeutralButton(R.string.action_tag_add) { _, _ ->
                showTagDialog(listOf(info))
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private inner class TagCheckAdapter(
        private val tags: List<com.aistra.hail.app.TagInfo>,
        private val checked: BooleanArray
    ) : RecyclerView.Adapter<TagCheckAdapter.VH>() {

        private var displayed: List<IndexedValue<com.aistra.hail.app.TagInfo>> = tags.withIndex().toList()

        inner class VH(val checkBox: com.google.android.material.checkbox.MaterialCheckBox) :
            RecyclerView.ViewHolder(checkBox)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(layoutInflater.inflate(R.layout.item_tag_check, parent, false)
                as com.google.android.material.checkbox.MaterialCheckBox)

        override fun getItemCount() = displayed.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (srcIdx, tag) = displayed[position]
            holder.checkBox.setOnCheckedChangeListener(null)
            holder.checkBox.text = tag.name
            holder.checkBox.isChecked = checked[srcIdx]
            holder.checkBox.setOnCheckedChangeListener { _, isChecked -> checked[srcIdx] = isChecked }
        }

        fun filter(query: String) {
            displayed = if (query.isBlank()) tags.withIndex().toList()
            else tags.withIndex().filter { (_, tag) -> tag.name.contains(query, ignoreCase = true) }.toList()
            notifyDataSetChanged()
        }
    }

    private fun showPrerequisiteDialog(info: AppInfo, pkg: String, shortcutIntent: Intent) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_prerequisite, null)
        val editText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_text)
        val checkboxLaunch = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.checkbox_launch)
        val checkboxEnable = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.checkbox_enable)

        // Pre-fill with existing prereq config if any
        info.prereqPackage?.let { editText.setText(it) }
        checkboxLaunch.isChecked = info.prereqLaunch
        checkboxEnable.isChecked = info.prereqEnable

        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.prerequisite_app_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val prereqPkg = editText.text?.toString()?.trim().orEmpty()
                if (prereqPkg.isNotEmpty() && (checkboxLaunch.isChecked || checkboxEnable.isChecked)) {
                    info.prereqPackage = prereqPkg
                    info.prereqLaunch = checkboxLaunch.isChecked
                    info.prereqEnable = checkboxEnable.isChecked
                } else {
                    info.prereqPackage = null
                    info.prereqLaunch = false
                    info.prereqEnable = false
                }
                HailData.saveApps()
                HShortcuts.addPinShortcut(info, pkg, info.name, shortcutIntent)
            }
            .setNegativeButton(R.string.action_skip) { _, _ ->
                HShortcuts.addPinShortcut(info, pkg, info.name, shortcutIntent)
            }
            .show()
    }

    private fun deselect(update: Boolean = true) {
        selectedList.clear()
        if (!update) return
        updateCurrentList()
        updateBarTitle()
    }

    private fun onMultiSelect() {
        MaterialAlertDialogBuilder(activity).setTitle(
            getString(
                R.string.msg_selected, selectedList.size.toString()
            )
        ).setItems(
            intArrayOf(
                R.string.action_freeze,
                R.string.action_unfreeze,
                R.string.action_tag_set,
                R.string.action_export_clipboard,
                R.string.action_remove_home,
                R.string.action_unfreeze_remove_home
            ).map { getString(it) }.toTypedArray()
        ) { _, which ->
            when (which) {
                0 -> {
                    setListFrozen(true, selectedList, false)
                    deselect()
                }

                1 -> {
                    setListFrozen(false, selectedList, false)
                    deselect()
                }

                2 -> triStateTagDialog()

                3 -> {
                    exportToClipboard(selectedList)
                    deselect()
                }

                4 -> {
                    selectedList.forEach { removeCheckedApp(it.packageName, false) }
                    HailData.saveApps()
                    deselect()
                }

                5 -> {
                    setListFrozen(false, selectedList, false)
                    selectedList.forEach {
                        if (!AppManager.isAppFrozen(it.packageName)) removeCheckedApp(it.packageName, false)
                    }
                    HailData.saveApps()
                    deselect()
                }
            }
        }.setNegativeButton(R.string.action_deselect) { _, _ ->
            deselect()
        }.setNeutralButton(R.string.action_select_all) { _, _ ->
            selectedList.addAll(pagerAdapter.currentList.filterNot { it in selectedList })
            updateCurrentList()
            updateBarTitle()
            onMultiSelect()
        }.show()
    }

    private fun triStateTagDialog() {
        val initialStates = Array(HailData.tags.size) { index ->
            val tagId = HailData.tags[index].id
            when (selectedList.count { tagId in it.tagIdList }) {
                selectedList.size -> ToggleableState.On
                0 -> ToggleableState.Off
                else -> ToggleableState.Indeterminate
            }
        }
        val states = mutableStateListOf(*initialStates)
        MaterialAlertDialogBuilder(activity).setTitle(R.string.action_tag_set).setView(ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { AppTheme { TriStateTagList(initialStates, states) } }
        }).setPositiveButton(android.R.string.ok) { _, _ ->
            val defaultTagId = 0
            selectedList.forEach { info ->
                states.forEachIndexed { index, state ->
                    val tagId = HailData.tags[index].id
                    when (state) {
                        ToggleableState.On -> {
                            if (tagId !in info.tagIdList) info.tagIdList.add(tagId)
                        }
                        ToggleableState.Off -> info.tagIdList.remove(tagId)
                        ToggleableState.Indeterminate -> {}
                    }
                }
                if (info.tagIdList.isEmpty()) {
                    // No tags left — restore Default instead of removing the app
                    info.tagIdList.add(defaultTagId)
                } else if (info.tagIdList.any { it != defaultTagId }) {
                    // Has real tags — strip Default if present
                    info.tagIdList.remove(defaultTagId)
                }
            }
            HailData.saveApps()
            deselect()
        }.setNeutralButton(R.string.action_tag_add) { _, _ ->
            showTagDialog(selectedList)
        }.setNegativeButton(android.R.string.cancel, null).show()
    }

    @Composable
    private fun TriStateTagList(initialStates: Array<ToggleableState>, states: MutableList<ToggleableState>) = Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        HailData.tags.forEachIndexed { index, tag ->
            Row(modifier = Modifier.fillMaxWidth().clickable {
                states[index] = if (initialStates[index] == ToggleableState.Indeterminate) when (states[index]) {
                    ToggleableState.On -> ToggleableState.Off
                    ToggleableState.Off -> ToggleableState.Indeterminate
                    ToggleableState.Indeterminate -> ToggleableState.On
                }
                else if (states[index] == ToggleableState.On) ToggleableState.Off
                else ToggleableState.On
            }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TriStateCheckbox(
                    state = states[index],
                    onClick = null,
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.secondary)
                )
                Spacer(modifier = Modifier.width(24.dp))
                Text(
                    text = tag.name,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    private fun launchApp(packageName: String) {
        handlePrerequisiteApp(packageName)
        val info = HailData.checkedList.find { it.packageName == packageName }
        val mode = info?.frozenMode?.takeIf { it.isNotEmpty() }
            ?: info?.let { HailData.workingModeForApp(it, tag.id) }
            ?: HailData.workingMode
        if (AppManager.isAppFrozen(packageName, info?.frozenMode ?: mode) &&
            AppManager.setAppFrozen(packageName, false, mode)
        ) {
            info?.frozenMode = null
            HailData.saveApps()
            updateCurrentList()
        }
        app.packageManager.getLaunchIntentForPackage(packageName)?.let {
            HShortcuts.addDynamicShortcut(packageName)
            startActivity(it)
        } ?: run {
            HUI.showToast(R.string.activity_not_found)
            HUI.notifyShizukuRequired(packageName)
        }
    }

    private fun handlePrerequisiteApp(packageName: String) {
        val appInfo = HailData.checkedList.find { it.packageName == packageName } ?: return
        val prereqPkg = appInfo.prereqPackage ?: return
        val prereqInfo = HailData.checkedList.find { it.packageName == prereqPkg }
        val mode = prereqInfo?.frozenMode?.takeIf { it.isNotEmpty() }
            ?: prereqInfo?.let { HailData.workingModeForApp(it) }
            ?: HailData.workingMode
        if ((appInfo.prereqLaunch || appInfo.prereqEnable) &&
            AppManager.isAppFrozen(prereqPkg, prereqInfo?.frozenMode ?: mode)
        ) {
            if (AppManager.setAppFrozen(prereqPkg, false, mode)) {
                prereqInfo?.frozenMode = null
                HailData.saveApps()
                app.setAutoFreezeService()
            }
        }
        if (appInfo.prereqLaunch) {
            app.packageManager.getLaunchIntentForPackage(prereqPkg)?.let { startActivity(it) }
        }
    }

    private fun setListFrozen(
        frozen: Boolean,
        list: List<AppInfo> = HailData.checkedList,
        updateList: Boolean = true,
        preferredTagId: Int? = null
    ) {
        val scopeTagId = preferredTagId ?: tag.id
        val scopeMode = HailData.workingModeForTag(scopeTagId)
        val scopeAction = HailData.modeAction(scopeMode)

        val appsWithModes = list.mapNotNull { info ->
            if (frozen) {
                val mode = HailData.workingModeForApp(info, scopeTagId)
                val existing = info.frozenMode?.takeIf { it.isNotEmpty() }
                // Do not re-freeze an app already held by a different mode family (e.g. Disable vs Suspend)
                if (existing != null &&
                    AppManager.isAppFrozen(info.packageName, existing) &&
                    !HailData.modesCompatible(existing, mode)
                ) {
                    return@mapNotNull null
                }
                info to mode
            } else {
                val stored = info.frozenMode?.takeIf { it.isNotEmpty() }
                when {
                    stored != null -> {
                        if (scopeAction != null && !HailData.modesCompatible(stored, scopeMode)) null
                        else info to stored
                    }
                    // Legacy apps with no frozenMode: only touch if frozen via this tag's mode
                    scopeAction != null -> {
                        if (AppManager.isAppFrozen(info.packageName, scopeMode)) info to scopeMode
                        else null
                    }
                    else -> info to HailData.workingModeForApp(info, scopeTagId)
                }
            }
        }
        val modesUsed = appsWithModes.map { it.second }.distinct()
        if (modesUsed.any { it == HailData.MODE_DEFAULT } ||
            (appsWithModes.isEmpty() && HailData.workingMode == HailData.MODE_DEFAULT)
        ) {
            if (HailData.workingMode == HailData.MODE_DEFAULT &&
                (appsWithModes.isEmpty() || appsWithModes.all { it.second == HailData.MODE_DEFAULT })
            ) {
                MaterialAlertDialogBuilder(activity).setMessage(R.string.msg_guide)
                    .setPositiveButton(android.R.string.ok, null).show()
                return
            }
        }
        if (appsWithModes.isEmpty()) {
            HUI.showToast(if (frozen) R.string.msg_freeze else R.string.msg_unfreeze, "0")
            return
        }
        if (modesUsed.any { it == HailData.MODE_SHIZUKU_HIDE }) {
            runCatching { HShizuku.isRoot }.onSuccess {
                if (!it) {
                    MaterialAlertDialogBuilder(activity).setMessage(R.string.shizuku_hide_adb)
                        .setPositiveButton(android.R.string.ok, null).show()
                    return
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val filtered = withContext(Dispatchers.Default) {
                appsWithModes.filter { (info, mode) ->
                    AppManager.isAppFrozen(info.packageName, if (frozen) mode else info.frozenMode ?: mode) != frozen
                }
            }
            val result = AppManager.setListFrozenChunked(frozen, filtered)
            when (result) {
                null -> HUI.showToast(
                    R.string.permission_denied_pkg,
                    AppManager.lastDeniedPackage ?: getString(R.string.permission_denied)
                )
                else -> {
                    if (updateList) {
                        (parentFragment as? HomeFragment)?.refreshAllPagers()
                            ?: run {
                                pagerAdapter.invalidateContentFlags()
                                updateCurrentList()
                                pagerAdapter.notifyDataSetChanged()
                            }
                    }
                    HUI.showToast(
                        if (frozen) R.string.msg_freeze else R.string.msg_unfreeze, result
                    )
                }
            }
        }
    }

    private fun showTagDialog(list: List<AppInfo>? = null) {
        if (list != null) {
            // "Add tag" path — keep original simple dialog
            val binding = DialogInputBinding.inflate(layoutInflater)
            binding.inputLayout.setHint(R.string.tag)
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.action_tag_add)
                .setView(binding.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val tagName = binding.editText.text.toString()
                    val tagId = tagName.hashCode()
                    if (HailData.tags.any { it.name == tagName || it.id == tagId }) return@setPositiveButton
                    HailData.tags.add(com.aistra.hail.app.TagInfo(tagName, tagId))
                    adapter.notifyItemInserted(adapter.itemCount - 1)
                    if (query.isEmpty() && tabs.tabCount == 2) tabs.isVisible = true
                    if (list == selectedList) triStateTagDialog() else tagDialog(list.first())
                    HailData.saveTags()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        // "Rename tag + manage apps" path — long-press on a tab
        val position = tabs.selectedTabPosition
        val currentTag = HailData.tags[position]
        val currentTagId = currentTag.id

        // Build the view with ViewBinding equivalent via inflate
        val dialogView = layoutInflater.inflate(R.layout.dialog_tag_manage, null)
        val tagNameInput = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.input_layout)
        val tagNameEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_text)
        val searchEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.search_text)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.app_list)
        val modeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_tag_working_mode)

        tagNameInput.hint = getString(R.string.tag)
        tagNameEdit.setText(currentTag.name)
        modeButton.text = getString(
            R.string.tag_mode_button_label,
            HailData.workingModeDisplayName(currentTag.workingMode)
        )
        modeButton.setOnClickListener {
            val values = listOf("") + HailData.TAG_WORKING_MODE_VALUES
            val entries = listOf(getString(R.string.tag_mode_use_global)) +
                    HailData.TAG_WORKING_MODE_VALUES.map { HailData.workingModeDisplayName(it) }
            val checked = values.indexOf(currentTag.workingMode ?: "").coerceAtLeast(0)
            MaterialAlertDialogBuilder(activity)
                .setTitle(getString(R.string.tag_working_mode_for, currentTag.name))
                .setSingleChoiceItems(entries.toTypedArray(), checked) { d, which ->
                    val selected = values[which].ifEmpty { null }
                    currentTag.workingMode = selected
                    HailData.saveTags()
                    modeButton.text = getString(
                        R.string.tag_mode_button_label,
                        HailData.workingModeDisplayName(selected)
                    )
                    d.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // Build full app list: all checked apps sorted by name, excluding hidden apps, with checked state for this tag
        val allApps = HailData.checkedList
            .filter { it.packageName !in HailData.hiddenApps }
            .sortedWith(NameComparator)
            .toMutableList()
        // Track which ones are assigned to this tag (working copy)
        val tagAssigned = allApps.map { currentTagId in it.tagIdList }.toBooleanArray()

        // Simple adapter for the list
        val tagAppAdapter = TagAppAssignAdapter(allApps, tagAssigned)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = tagAppAdapter

        // Wire up "Show all apps" toggle
        val showAllCheck = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.check_show_all_apps)
        showAllCheck.setOnCheckedChangeListener { _, checked ->
            tagAppAdapter.setShowAll(checked)
        }

        // Wire up search filtering
        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                tagAppAdapter.filter(s?.toString() ?: "")
            }
        })

        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.action_tag_set)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // Apply rename
                val newName = tagNameEdit.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentTag.name) {
                    val newTagId = if (position == 0) 0 else newName.hashCode()
                    if (!HailData.tags.any { it.name == newName || (it.id == newTagId && it.id != currentTagId) }) {
                        HailData.tags[position] = com.aistra.hail.app.TagInfo(newName, newTagId, HailData.tags[position].workingMode)
                        if (position != 0 && newTagId != currentTagId) {
                            HailData.checkedList.forEach {
                                val idx = it.tagIdList.indexOf(currentTagId)
                                if (idx != -1) it.tagIdList[idx] = newTagId
                            }
                        }
                        adapter.notifyItemChanged(position)
                        HailData.saveTags()
                    }
                }
                // Apply app-tag assignments from the adapter's working state
                tagAppAdapter.applyAssignments(currentTagId)
                HailData.saveApps()
                updateCurrentList()
            }

        // Only show "Remove tag" for non-default tabs
        if (position != 0) {
            builder.setNeutralButton(R.string.action_tag_remove) { _, _ ->
                val defaultTagId = 0
                // Clean ALL home apps, not just the visible/filtered list
                HailData.checkedList.forEach { info ->
                    if (info.tagIdList.remove(currentTagId) && info.tagIdList.isEmpty()) {
                        info.tagIdList.add(defaultTagId)
                    }
                }
                HailData.tags.removeAt(position)
                adapter.notifyItemRemoved(position)
                if (tabs.tabCount == 1) tabs.isVisible = false
                HailData.saveApps()
                HailData.saveTags()
            }
        }

        builder.setNegativeButton(android.R.string.cancel, null).show()
    }

    /** Shows a dialog listing all whitelisted apps across all tags for selective freezing. */
    private fun showWhitelistDialog() {
        val whitelistedApps = HailData.checkedList
            .filter { it.whitelisted && it.applicationInfo != null }
            .sortedWith(compareBy<AppInfo> { AppManager.isAppFrozen(it.packageName) }.then(NameComparator))

        if (whitelistedApps.isEmpty()) {
            HUI.showToast(R.string.msg_no_whitelisted_apps)
            return
        }

        val selected = BooleanArray(whitelistedApps.size)
        val removeWhitelist = BooleanArray(whitelistedApps.size)
        val dialogView = layoutInflater.inflate(R.layout.dialog_whitelist, null)
        val searchEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.search_text)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.whitelist_app_list)
        val btnSelectAll = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_select_all)
        val btnDeselectAll = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_deselect_all)

        val whitelistAdapter = WhitelistFreezeAdapter(whitelistedApps, selected, removeWhitelist)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = whitelistAdapter

        searchEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                whitelistAdapter.filter(s?.toString() ?: "")
            }
        })
        btnSelectAll.setOnClickListener { whitelistAdapter.selectAll() }
        btnDeselectAll.setOnClickListener { whitelistAdapter.deselectAll() }

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.whitelisted_apps)
            .setView(dialogView)
            .setPositiveButton(R.string.action_process) { _, _ ->
                val toFreeze = whitelistedApps.filterIndexed { i, _ -> selected[i] }
                if (toFreeze.isNotEmpty()) setListFrozen(true, toFreeze)
                val anyRemoved = removeWhitelist.any { it }
                if (anyRemoved) {
                    whitelistedApps.forEachIndexed { i, info ->
                        if (removeWhitelist[i]) info.whitelisted = false
                    }
                    HailData.saveApps()
                    updateCurrentList()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show().also { dialog ->
                dialog.window?.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    (resources.displayMetrics.heightPixels * 0.95).toInt()
                )
            }
    }

    /** Adapter for the whitelisted-apps freeze dialog. */
    private inner class WhitelistFreezeAdapter(
        private val apps: List<AppInfo>,
        private val selected: BooleanArray,
        private val removeWhitelist: BooleanArray
    ) : RecyclerView.Adapter<WhitelistFreezeAdapter.VH>() {

        private var displayed: List<IndexedValue<AppInfo>> = apps.withIndex().toList()

        inner class VH(val view: View) : RecyclerView.ViewHolder(view) {
            val icon = view.findViewById<android.widget.ImageView>(R.id.app_icon)
            val name = view.findViewById<com.google.android.material.textview.MaterialTextView>(R.id.app_name)
            val pkg = view.findViewById<com.google.android.material.textview.MaterialTextView>(R.id.app_desc)
            val checkRemove = view.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.check_remove_whitelist)
            val check = view.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.app_star)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(layoutInflater.inflate(R.layout.item_whitelist, parent, false))

        override fun getItemCount() = displayed.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (srcIdx, info) = displayed[position]
            holder.name.text = info.name
            holder.pkg.text = info.packageName
            holder.checkRemove.setOnCheckedChangeListener(null)
            holder.checkRemove.isChecked = removeWhitelist[srcIdx]
            holder.checkRemove.setOnCheckedChangeListener { _, checked ->
                removeWhitelist[srcIdx] = checked
            }
            holder.check.setOnCheckedChangeListener(null)
            holder.check.isChecked = selected[srcIdx]
            info.applicationInfo?.let {
                AppIconCache.loadIconBitmapAsync(
                    requireContext(), it, HPackages.myUserId, holder.icon,
                    HailData.grayscaleIcon && info.state == AppInfo.State.FROZEN
                )
            } ?: holder.icon.setImageDrawable(requireContext().packageManager.defaultActivityIcon)
            holder.view.setOnClickListener {
                selected[srcIdx] = !selected[srcIdx]
                holder.check.isChecked = selected[srcIdx]
            }
            holder.check.setOnCheckedChangeListener { _, checked ->
                selected[srcIdx] = checked
            }
        }

        fun filter(query: String) {
            displayed = if (query.isBlank()) apps.withIndex().toList()
            else apps.withIndex().filter { (_, app) ->
                app.name.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true)
            }.toList()
            notifyDataSetChanged()
        }

        fun selectAll() { selected.fill(true); notifyDataSetChanged() }
        fun deselectAll() { selected.fill(false); notifyDataSetChanged() }
    }

    /** Adapter for the app-assign list inside the tag management dialog. */
    private inner class TagAppAssignAdapter(
        private val source: List<AppInfo>,
        private val assigned: BooleanArray   // indexed by position in `source`
    ) : RecyclerView.Adapter<TagAppAssignAdapter.VH>() {

        // When false (default), only apps currently on the Default page are shown.
        // When true, all apps are shown (original behaviour).
        private var showAll: Boolean = false
        private var currentQuery: String = ""

        // Displayed (filtered) subset — pairs of (sourceIndex, AppInfo)
        private var displayed: List<Pair<Int, AppInfo>> = computeDisplayed()

        private fun computeDisplayed(): List<Pair<Int, AppInfo>> {
            val base = if (showAll) {
                source.mapIndexed { i, a -> i to a }
            } else {
                source.mapIndexed { i, a -> i to a }.filter { (_, info) -> 0 in info.tagIdList }
            }
            return if (currentQuery.isBlank()) base else base.filter { (_, info) ->
                FuzzySearch.search(info.packageName, currentQuery) ||
                FuzzySearch.search(info.name.toString(), currentQuery) ||
                (HailData.nineKeySearch && NineKeySearch.search(currentQuery, info.packageName, info.name.toString())) ||
                PinyinSearch.searchPinyinAll(info.name.toString(), currentQuery)
            }
        }

        fun setShowAll(value: Boolean) {
            showAll = value
            displayed = computeDisplayed()
            notifyDataSetChanged()
        }

        inner class VH(val view: View) : RecyclerView.ViewHolder(view) {
            val icon = view.findViewById<android.widget.ImageView>(R.id.app_icon)
            val name = view.findViewById<com.google.android.material.textview.MaterialTextView>(R.id.app_name)
            val pkg  = view.findViewById<com.google.android.material.textview.MaterialTextView>(R.id.app_desc)
            val check = view.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.app_star)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = layoutInflater.inflate(R.layout.item_apps, parent, false)
            return VH(v)
        }

        override fun getItemCount() = displayed.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (srcIdx, info) = displayed[position]
            holder.name.text = info.name
            holder.pkg.text = info.packageName
            holder.check.setOnCheckedChangeListener(null)
            holder.check.isChecked = assigned[srcIdx]
            // Load icon using the correct AppIconCache signature
            info.applicationInfo?.let {
                AppIconCache.loadIconBitmapAsync(
                    requireContext(), it, HPackages.myUserId, holder.icon
                )
            } ?: holder.icon.setImageDrawable(
                requireContext().packageManager.defaultActivityIcon
            )
            holder.view.setOnClickListener {
                assigned[srcIdx] = !assigned[srcIdx]
                holder.check.isChecked = assigned[srcIdx]
            }
            holder.check.setOnCheckedChangeListener { _, checked ->
                assigned[srcIdx] = checked
            }
        }

        fun filter(query: String) {
            currentQuery = query
            displayed = computeDisplayed()
            notifyDataSetChanged()
        }

        /** Write the checked state back to each AppInfo's tagIdList. */
        fun applyAssignments(tagId: Int) {
            val defaultTagId = 0
            val isNonDefaultTag = tagId != defaultTagId
            source.forEachIndexed { i, info ->
                if (assigned[i]) {
                    // Assigning to this tag
                    if (tagId !in info.tagIdList) info.tagIdList.add(tagId)
                    // If assigned to a real (non-default) tag, remove the Default tag
                    if (isNonDefaultTag) info.tagIdList.remove(defaultTagId)
                } else {
                    // Unassigning from this tag
                    info.tagIdList.remove(tagId)
                    if (info.tagIdList.isEmpty()) {
                        // No tags left — restore Default tag instead of removing the app
                        info.tagIdList.add(defaultTagId)
                    }
                }
            }
        }
    }

    private fun exportToClipboard(list: List<AppInfo>) {
        if (list.isEmpty()) return
        HUI.copyText(if (list.size > 1) JSONArray().run {
            list.forEach { put(it.packageName) }
            toString()
        } else list[0].packageName)
        HUI.showToast(
            R.string.msg_exported, if (list.size > 1) list.size.toString() else list[0].name
        )
    }

    private fun importFromClipboard() = runCatching {
        val str = HUI.pasteText() ?: throw IllegalArgumentException()
        val json = if (str.contains('[')) JSONArray(
            str.substring(
                str.indexOf('[')..str.indexOf(']', str.indexOf('['))
            )
        )
        else JSONArray().put(str)
        var i = 0
        for (index in 0 until json.length()) {
            val pkg = json.getString(index)
            if (HPackages.getApplicationInfoOrNull(pkg) != null && !HailData.isChecked(pkg)) {
                HailData.addCheckedApp(pkg, tag.id, false)
                i++
            }
        }
        if (i > 0) {
            HailData.saveApps()
            updateCurrentList()
        }
        HUI.showToast(getString(R.string.msg_imported, i.toString()))
    }

    private suspend fun importFrozenApp() = withContext(Dispatchers.IO) {
        HPackages.getInstalledApplications().map { it.packageName }
            .filter { AppManager.isAppFrozen(it) && !HailData.isChecked(it) }
            .onEach { HailData.addCheckedApp(it, tag.id, false) }.size
    }

    private fun removeCheckedApp(packageName: String, saveApps: Boolean = true) {
        HailData.removeCheckedApp(packageName, saveApps)
        if (saveApps) updateCurrentList()
    }

    private fun MenuItem.updateIcon() = icon?.setTint(
        MaterialColors.getColor(
            activity.findViewById(R.id.toolbar),
            if (multiselect) androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorOnSurface
        )
    )

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_home -> {
                (parentFragment as HomeFragment).goToDefaultTag()
            }

            R.id.action_whitelist -> showWhitelistDialog()

            R.id.action_home_shortcuts -> {
                (parentFragment as HomeFragment).showPinShortcutsDialog()
            }

            R.id.action_multiselect -> {
                multiselect = !multiselect
                item.updateIcon()
                if (multiselect) {
                    updateBarTitle()
                    HUI.showToast(R.string.tap_to_select)
                } else deselect()
            }

            R.id.action_freeze_current -> setListFrozen(true, pagerAdapter.currentList.filterNot { it.whitelisted })

            R.id.action_unfreeze_current -> setListFrozen(false, pagerAdapter.currentList)
            R.id.action_freeze_all -> setListFrozen(true)
            R.id.action_unfreeze_all -> setListFrozen(false)
            R.id.action_freeze_non_whitelisted -> setListFrozen(true, HailData.checkedList.filterNot { it.whitelisted })

            R.id.action_filter_user_apps -> {
                appTypeFilter = if (appTypeFilter == APP_TYPE_USER) APP_TYPE_ALL else APP_TYPE_USER
                activity.invalidateOptionsMenu()
                updateCurrentList()
            }

            R.id.action_filter_system_apps -> {
                appTypeFilter = if (appTypeFilter == APP_TYPE_SYSTEM) APP_TYPE_ALL else APP_TYPE_SYSTEM
                activity.invalidateOptionsMenu()
                updateCurrentList()
            }

            R.id.action_show_uninstalled -> {
                HailData.showUninstalled = !HailData.showUninstalled
                activity.invalidateOptionsMenu()
                (parentFragment as HomeFragment).childFragmentManager.fragments
                    .filterIsInstance<PagerFragment>()
                    .forEach { it.updateCurrentList() }
            }

            R.id.action_import_clipboard -> importFromClipboard()
            R.id.action_import_frozen -> lifecycleScope.launch {
                val size = importFrozenApp()
                if (size > 0) {
                    HailData.saveApps()
                    updateCurrentList()
                }
                HUI.showToast(getString(R.string.msg_imported, size.toString()))
            }

            R.id.action_export_current -> exportToClipboard(pagerAdapter.currentList)
            R.id.action_export_all -> exportToClipboard(HailData.checkedList)
        }
        return false
    }

    override fun onPrepareMenu(menu: Menu) {
        super.onPrepareMenu(menu)
        menu.findItem(R.id.action_filter_user_apps)?.isChecked = appTypeFilter == APP_TYPE_USER
        menu.findItem(R.id.action_filter_system_apps)?.isChecked = appTypeFilter == APP_TYPE_SYSTEM
        menu.findItem(R.id.action_show_uninstalled)?.isChecked = HailData.showUninstalled
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_home, menu)
        val searchItem = menu.findItem(R.id.action_search)
        searchMenuItem = searchItem
        searchItem.isVisible = true
        val searchView = searchItem.actionView as SearchView
        if (HailData.nineKeySearch) {
            val editText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
            editText.inputType = InputType.TYPE_CLASS_PHONE
        }

        // Restore active query if one exists (e.g. after keyboard dismiss rebuilds the menu)
        if (query.isNotEmpty()) {
            searchItem.expandActionView()
            searchView.setQuery(query, false)
            searchView.clearFocus()  // show text without re-opening keyboard
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                // Ignore the empty event fired when the SearchView collapses
                if (newText.isEmpty() && !searchItem.isActionViewExpanded) return true
                query = newText
                tabs.isVisible = query.isEmpty() && tabs.tabCount > 1
                updateCurrentList()
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                searchView.clearFocus()
                // Enter launches the top search hit (unfreeze + launch), same as tapping an icon
                pagerAdapter.currentList.firstOrNull { it.applicationInfo != null }?.let {
                    launchApp(it.packageName)
                }
                return true
            }
        })

        // Only clear the query when the user explicitly closes the search (X button)
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                query = ""
                tabs.isVisible = tabs.tabCount > 1
                updateCurrentList()
                return true
            }
        })

        menu.findItem(R.id.action_multiselect).updateIcon()
    }

    override fun onDestroyView() {
        runCatching { binding.fastScroll.detach() }
        pagerAdapter.onDestroy()
        super.onDestroyView()
        _binding = null
    }
}