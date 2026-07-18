package com.example.basaya.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.basaya.R
import com.example.basaya.ui.fragment.HomeFragment
import com.example.basaya.ui.fragment.ProfileFragment
import com.example.basaya.ui.fragment.ProgressFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import android.widget.ImageButton
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.basaya.data.auth.AuthHelper
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var tvName: TextView
    private var isUserReady = false

    private lateinit var authStateListener: FirebaseAuth.AuthStateListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // check auth state BEFORE inflating any layout
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

        window.statusBarColor = ContextCompat.getColor(this, R.color.toolbar_bar_color)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        //  Define listener separately so it can be removed later
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

        auth.addAuthStateListener(authStateListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::authStateListener.isInitialized) {
            auth.removeAuthStateListener(authStateListener)
        }
    }

    private fun setupUI() {

        tvName = findViewById(R.id.tvName)
        tvName.text = "..."

        val authHelper = AuthHelper()

        val uid = authHelper.getCurrentUser()?.uid

        if (uid != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    tvName.text = doc.getString("firstName") ?: ""
                }
                .addOnFailureListener { e ->
                    Log.e("HomeFragment", "Failed to fetch user", e)
                }
        }

        loadFragment(HomeFragment())

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.progress -> {
                    loadFragment(ProgressFragment())
                    true
                }
                R.id.accountInfo -> {
                    loadFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }

        val btnMoreOptions = findViewById<ImageButton>(R.id.btnMoreOptions)

        btnMoreOptions.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
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

            popup.show()
        }
    }

    private fun loadFragment(fragment: Fragment) {
        if (isDestroyed || isFinishing) return

        supportFragmentManager.beginTransaction()
            .replace(R.id.frameLayout, fragment)
            .commitAllowingStateLoss()
    }
}