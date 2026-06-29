package com.example.basaya.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.basaya.R
import com.example.basaya.adapter.LessonAdapter
import com.example.basaya.data.auth.AuthHelper
import com.example.basaya.data.repository.LessonRepository
import com.example.basaya.ui.DashboardActivity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_home, container, false)

        val authHelper = AuthHelper()

        val uid = authHelper.getCurrentUser()?.uid

        if (uid != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    val firstName = doc.getString("firstName") ?: ""
                    val lastName = doc.getString("lastName") ?: ""

                }
                .addOnFailureListener { e ->
                    Log.e("HomeFragment", "Failed to fetch user", e)
                }
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerLessons)
        var adapter = LessonAdapter(emptyList()) { lesson ->
            if (isAdded && !requireActivity().isFinishing) {
                val intent = Intent(requireContext(), DashboardActivity::class.java).apply {
                    putExtra("LESSON_ID", lesson.id)
                }
                startActivity(intent)
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        lifecycleScope.launch {
            val repo = LessonRepository(requireContext())
            repo.syncLessons()
            val lessons = repo.getLessons()
            adapter = LessonAdapter(lessons) { lesson ->
                if (isAdded && !requireActivity().isFinishing) {
                    val intent = Intent(requireContext(), DashboardActivity::class.java).apply {
                        putExtra("LESSON_ID", lesson.id)
                    }
                    startActivity(intent)
                }
            }
            recyclerView.adapter = adapter
        }



        return view
    }
}