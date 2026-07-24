package com.aistra.hail.ui.main

import android.os.Bundle
import android.view.Menu
import android.widget.LinearLayout
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
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.navigation.NavigationBarView

class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener {
    lateinit var fab: ExtendedFloatingActionButton
    lateinit var fabContainer: LinearLayout
    lateinit var appbar: AppBarLayout
    private lateinit var navController: NavController
    private lateinit var navHostFragment: NavHostFragment

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
            R.id.nav_home, R.id.nav_apps, R.id.nav_settings, R.id.nav_about
        ).build()
        setupActionBarWithNavController(navController, appBarConfiguration)

        val navListener = NavigationBarView.OnItemSelectedListener { item ->
            if (item.itemId == R.id.nav_search) {
                openHomeSearch()
                false
            } else {
                NavigationUI.onNavDestinationSelected(item, navController)
            }
        }
        bottomNav?.setOnItemSelectedListener(navListener)
        navRail?.setOnItemSelectedListener(navListener)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val id = destination.id
            bottomNav?.menu?.findItem(id)?.isChecked = true
            navRail?.menu?.findItem(id)?.isChecked = true
        }

        val isRtl = isRtl
        val isLandscape = isLandscape
        appBarMain.appBarLayout.applyDefaultInsetter {
            paddingRelative(isRtl, start = !isLandscape, end = true, top = true)
        }
        bottomNav?.applyDefaultInsetter { paddingRelative(isRtl, start = true, end = true, bottom = true) }
        navRail?.applyDefaultInsetter { paddingRelative(isRtl, start = true, top = true, bottom = true) }
        appBarMain.fabContainer!!.applyDefaultInsetter { marginRelative(isRtl, end = true, bottom = isLandscape) }
    }

    fun openHomeSearch() {
        if (navController.currentDestination?.id != R.id.nav_home) {
            navController.navigate(R.id.nav_home)
        }
        fab.post {
            (navHostFragment.childFragmentManager.primaryNavigationFragment as? HomeFragment)
                ?.expandSearch()
        }
    }

    fun goHomeTab() {
        if (navController.currentDestination?.id != R.id.nav_home) {
            navController.navigate(R.id.nav_home)
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
}
