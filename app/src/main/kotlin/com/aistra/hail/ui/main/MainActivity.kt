package com.aistra.hail.ui.main

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.MenuCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.aistra.hail.R
import com.aistra.hail.app.HailData
import com.aistra.hail.databinding.ActivityMainBinding
import com.aistra.hail.extensions.*
import com.aistra.hail.ui.home.HomeFragment
import com.aistra.hail.utils.HPolicy
import com.aistra.hail.utils.HTheme
import com.aistra.hail.utils.HUI
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.navigation.NavigationBarView

class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener {
    lateinit var fab: ExtendedFloatingActionButton
    lateinit var fabContainer: LinearLayout
    lateinit var appbar: AppBarLayout
    private lateinit var navController: NavController
    private lateinit var navHostFragment: NavHostFragment
    private var navChips: List<Pair<Int, View>> = emptyList()

    private val panelNavOptions by lazy {
        val startId = navController.graph.findStartDestination().id
        NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .setPopUpTo(startId, inclusive = false, saveState = true)
            .setEnterAnim(R.anim.nav_enter)
            .setExitAnim(R.anim.nav_exit)
            .setPopEnterAnim(R.anim.nav_pop_enter)
            .setPopExitAnim(R.anim.nav_pop_exit)
            .build()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        HTheme.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        HTheme.enableHighRefreshRate(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val binding = try {
            initView()
        } catch (t: Throwable) {
            // Last-resort: if themed chrome fails to inflate, retry once with stock Theme.Hail
            android.util.Log.e("MainActivity", "initView failed", t)
            setTheme(R.style.Theme_Hail)
            initView()
        }
        if (!HailData.biometricLogin || BiometricManager.from(this)
                .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS
        ) return
        binding.root.isVisible = false
        val biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    HUI.showToast(errString)
                    finishAndRemoveTask()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    binding.root.isVisible = true
                }
            })
        val promptInfo = BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.action_biometric))
            .setSubtitle(getString(R.string.msg_biometric)).setNegativeButtonText(getString(android.R.string.cancel))
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    private fun initView() = ActivityMainBinding.inflate(layoutInflater).apply {
        setContentView(root)
        setSupportActionBar(appBarMain.toolbar)
        fab = appBarMain.fab
        fabContainer = appBarMain.fabContainer!!
        appbar = appBarMain.appBarLayout

        navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        navController.addOnDestinationChangedListener(this@MainActivity)
        val appBarConfiguration = AppBarConfiguration.Builder(
            R.id.nav_home, R.id.nav_apps, R.id.nav_settings
        ).build()
        setupActionBarWithNavController(navController, appBarConfiguration)

        val isRtl = isRtl
        val isLandscape = isLandscape

        val navigateTo = fun(destId: Int) {
            if (destId != navController.currentDestination?.id) {
                runCatching {
                    navController.navigate(destId, null, panelNavOptions)
                }.onFailure {
                    runCatching { navController.popBackStack(destId, false) }
                }
            }
        }

        // Portrait: compact cluster + wide Search
        bottomNav?.let { bar ->
            val home = bar.findViewById<View>(R.id.nav_home) ?: return@let
            val apps = bar.findViewById<View>(R.id.nav_apps) ?: return@let
            val settings = bar.findViewById<View>(R.id.nav_settings) ?: return@let
            val search = bar.findViewById<View>(R.id.nav_search) ?: return@let

            setupNavChip(home, R.drawable.ic_round_frozen, R.string.title_home)
            setupNavChip(apps, R.drawable.ic_baseline_android, R.string.title_apps)
            setupNavChip(settings, R.drawable.ic_settings_selector, R.string.title_settings)

            navChips = listOf(
                R.id.nav_home to home,
                R.id.nav_apps to apps,
                R.id.nav_settings to settings
            )
            navChips.forEach { (id, chip) ->
                chip.setOnClickListener { navigateTo(id) }
            }
            search.setOnClickListener { openHomeSearch() }
            tintSearch(search)

            navController.addOnDestinationChangedListener { _, destination, _ ->
                syncNavChipSelection(destination.id)
            }
            syncNavChipSelection(navController.currentDestination?.id)
            bar.applyDefaultInsetter { paddingRelative(isRtl, start = true, end = true, bottom = true) }
        }

        // Landscape: Material NavigationRail (equal items is fine sideways)
        val navListener = NavigationBarView.OnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_search -> {
                    openHomeSearch()
                    false
                }
                else -> {
                    navigateTo(item.itemId)
                    true
                }
            }
        }
        navRail?.setOnItemSelectedListener(navListener)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val id = destination.id
            navRail?.menu?.findItem(id)?.isChecked = true
        }

        appBarMain.appBarLayout.applyDefaultInsetter {
            paddingRelative(isRtl, start = !isLandscape, end = true, top = true)
        }
        navRail?.applyDefaultInsetter { paddingRelative(isRtl, start = true, top = true, bottom = true) }
        appBarMain.fabContainer!!.applyDefaultInsetter { marginRelative(isRtl, end = true, bottom = isLandscape) }
    }

    private fun setupNavChip(chip: View, iconRes: Int, labelRes: Int) {
        chip.findViewById<ImageView>(R.id.nav_chip_icon)?.setImageResource(iconRes)
        chip.findViewById<TextView>(R.id.nav_chip_label)?.setText(labelRes)
        chip.contentDescription = getString(labelRes)
        applyChipStyle(chip, selected = false)
    }

    private fun syncNavChipSelection(destinationId: Int?) {
        navChips.forEach { (id, chip) ->
            applyChipStyle(chip, selected = id == destinationId)
        }
    }

    private fun applyChipStyle(chip: View, selected: Boolean) {
        chip.isSelected = selected
        chip.setBackgroundResource(
            if (selected) R.drawable.bg_nav_chip_selected else R.drawable.bg_nav_chip
        )
        val accent = MaterialColors.getColor(chip, androidx.appcompat.R.attr.colorPrimary)
        val muted = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val tint = ColorStateList.valueOf(if (selected) accent else muted)
        chip.findViewById<ImageView>(R.id.nav_chip_icon)?.imageTintList = tint
        chip.findViewById<TextView>(R.id.nav_chip_label)?.setTextColor(tint)
    }

    private fun tintSearch(search: View) {
        val accent = ColorStateList.valueOf(
            MaterialColors.getColor(search, androidx.appcompat.R.attr.colorPrimary)
        )
        search.findViewById<ImageView>(R.id.nav_search_icon)?.imageTintList = accent
        search.findViewById<TextView>(R.id.nav_search_label)?.setTextColor(accent)
    }

    fun openHomeSearch() {
        if (navController.currentDestination?.id != R.id.nav_home) {
            runCatching { navController.navigate(R.id.nav_home, null, panelNavOptions) }
        }
        fab.post {
            (navHostFragment.childFragmentManager.primaryNavigationFragment as? HomeFragment)
                ?.expandSearch()
        }
    }

    fun goHomeTab() {
        if (navController.currentDestination?.id != R.id.nav_home) {
            runCatching { navController.navigate(R.id.nav_home, null, panelNavOptions) }
        }
        fab.post {
            (navHostFragment.childFragmentManager.primaryNavigationFragment as? HomeFragment)
                ?.goToDefaultTag()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.let { MenuCompat.setGroupDividerEnabled(it, true) }
        return super.onCreateOptionsMenu(menu)
    }

    fun ownerRemoveDialog() {
        MaterialAlertDialogBuilder(this).setTitle(R.string.title_remove_owner).setMessage(R.string.msg_remove_owner)
            .setPositiveButton(R.string.action_continue) { _, _ ->
                HPolicy.setOrganizationName()
                HPolicy.removeDeviceOwner()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    override fun onDestinationChanged(
        controller: NavController, destination: NavDestination, arguments: Bundle?
    ) {
        fab.tag = destination.id == R.id.nav_home
        if (fab.tag == true) fab.show() else fab.hide()
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp() || super.onSupportNavigateUp()
}
