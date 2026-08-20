package com.example.basaya.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.example.basaya.R
import com.example.basaya.controller.BottomNavController
import com.example.basaya.data.auth.AuthHelper
import com.example.basaya.ui.fragment.HomeFragment
import com.example.basaya.ui.fragment.ProfileFragment
import com.example.basaya.ui.fragment.ProgressFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var tvName: TextView
    private var isUserReady = false

    private var isProfileMenuActive = false

    // Class-level now — needed by animateChevron(), and by the avatar click listener below.
    private lateinit var ivProfileChevron: ImageView
    private lateinit var tvAvatarInitial: TextView

    private lateinit var authStateListener: FirebaseAuth.AuthStateListener

    private val homeFragment by lazy { HomeFragment() }
    private val progressFragment by lazy { ProgressFragment() }
    private val profileFragment by lazy { ProfileFragment() }
    private var activeFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            startActivity(Intent(this, LogInActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val statusBarBg = findViewById<View>(R.id.statusBarBg)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            statusBarBg.layoutParams.height = statusBarHeight
            statusBarBg.requestLayout()
            insets
        }

        ivProfileChevron = findViewById(R.id.ivProfileChevron)

        findViewById<View>(R.id.tvAvatarInitial).setOnClickListener { view ->
            isProfileMenuActive = !isProfileMenuActive
            animateChevron(isProfileMenuActive)
            showToolbarMenu(view)
        }

        window.statusBarColor = ContextCompat.getColor(this, R.color.toolbar_bar_color)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser

            if (user == null) {
                if (!isDestroyed && !isFinishing) {
                    startActivity(Intent(this@MainActivity, LogInActivity::class.java))
                    finish()
                }
            } else {
                if (!isUserReady) {
                    isUserReady = true
                    setupUI()
                }
            }
        }

        findViewById<ImageButton>(R.id.btnNotification).setOnClickListener {
            showConfirmDialog()
        }

        auth.addAuthStateListener(authStateListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::authStateListener.isInitialized) {
            auth.removeAuthStateListener(authStateListener)
        }
    }

    private fun setupUI() {
        tvName = findViewById(R.id.tvFirstName)
        tvName.text = "..."
        tvAvatarInitial = findViewById(R.id.tvAvatarInitial)

        tvAvatarInitial.text = "0"

        val authHelper = AuthHelper()
        val uid = authHelper.getCurrentUser()?.uid

        if (uid != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    val firstName = doc.getString("firstName") ?: ""

                    tvName.text = doc.getString("firstName") ?: ""
                    tvAvatarInitial.text = firstName.firstOrNull()?.uppercase() ?: "B"
                }
                .addOnFailureListener { e ->
                    Log.e("MainActivity", "Failed to fetch user", e)
                }
        }

        supportFragmentManager.beginTransaction().apply {
            add(R.id.frameLayout, profileFragment, "profile").hide(profileFragment)
            add(R.id.frameLayout, progressFragment, "progress").hide(progressFragment)
            add(R.id.frameLayout, homeFragment, "home")
        }.commitAllowingStateLoss()

        activeFragment = homeFragment

        setupBottomNav()
    }

    private fun setupBottomNav() {
        val navUnderline = findViewById<View>(R.id.navUnderline)

        val navHome = findViewById<View>(R.id.navHome)
        val iconHome = findViewById<ImageView>(R.id.iconHome)
        val labelHome = findViewById<TextView>(R.id.labelHome)

        val navProgress = findViewById<View>(R.id.navProgress)
        val iconProgress = findViewById<ImageView>(R.id.iconPractice)
        val labelProgress = findViewById<TextView>(R.id.labelPractice)

        val navProfile = findViewById<View>(R.id.navProfile)
        val iconProfile = findViewById<ImageView>(R.id.iconProfile)
        val labelProfile = findViewById<TextView>(R.id.labelProfile)

        BottomNavController(
            underline = navUnderline,
            items = listOf(
                BottomNavController.NavItem(navHome, iconHome, labelHome),
                BottomNavController.NavItem(navProgress, iconProgress, labelProgress),
                BottomNavController.NavItem(navProfile, iconProfile, labelProfile)
            )
        ) { index ->
            val target = when (index) {
                0 -> homeFragment
                1 -> progressFragment
                2 -> profileFragment
                else -> homeFragment
            }
            switchTo(target)
        }
    }

    // btnMoreOptions now delegates to the same shared menu as the avatar tap.
   /* private fun setupToolbarMenu() {
        val btnMoreOptions = findViewById<ImageButton>(R.id.btnMoreOptions)
        btnMoreOptions.setOnClickListener { view ->
            showToolbarMenu(view)
        }
    }*/

    // Shared popup logic — called from both the avatar and the 3-dot button.
    private fun showToolbarMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.toolbar_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_logout -> {
                    auth.signOut()
                    true
                }
                else -> false
            }
        }

        // Reset the chevron if this menu was opened via the avatar and the
        // user dismisses it without picking anything.
        popup.setOnDismissListener {
            if (isProfileMenuActive) {
                isProfileMenuActive = false
                animateChevron(false)
            }
        }

        popup.show()
    }

    private fun switchTo(fragment: Fragment) {
        if (fragment === activeFragment) return
        if (isDestroyed || isFinishing) return

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
            .hide(activeFragment!!)
            .show(fragment)
            .commitAllowingStateLoss()

        activeFragment = fragment
    }

    private fun showConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Wala papo")
            .setMessage("Mga 1 week pa.")
            .setPositiveButton("Oki") { _, _ -> }
            .setCancelable(false)
            .show()
    }

    private fun animateChevron(active: Boolean) {
        val targetRotation = if (active) 90f else 0f
        ivProfileChevron.animate()
            .rotation(targetRotation)
            .setDuration(200)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .start()
    }
}