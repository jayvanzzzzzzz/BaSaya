package com.example.basaya.ui.fragment

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.basaya.R
import com.example.basaya.adapter.LessonAdapter
import com.example.basaya.data.auth.AuthHelper
import com.example.basaya.data.mock.MockData
import com.google.firebase.firestore.FirebaseFirestore

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

                    val fullname = firstName + lastName
                }
                .addOnFailureListener { e ->
                    Log.e("HomeFragment", "Failed to fetch user", e)
                }
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerLessons)

        val mockData = MockData

        val adapter = LessonAdapter(MockData.lesson) { lesson ->

            Toast.makeText(
                requireContext(),
                lesson.title,
                Toast.LENGTH_SHORT
            ).show()

        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        return view
    }
}