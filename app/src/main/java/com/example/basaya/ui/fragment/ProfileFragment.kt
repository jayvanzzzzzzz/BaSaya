package com.example.basaya.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.basaya.R
import com.example.basaya.data.auth.AuthHelper
import com.example.basaya.ui.LogInActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * The student's account: who they are (editable name, gender, birthday; email is read-only),
 * password change, and logout.
 */
class ProfileFragment : Fragment() {

    /** What's stored on users/{uid}. */
    private data class Profile(
        val firstName: String = "",
        val middleName: String = "",
        val lastName: String = "",
        val gender: String = "",
        val dob: String = "", // yyyy-MM-dd
        val classId: String = "",
        val className: String = ""
    ) {
        val fullName: String get() = listOf(firstName, middleName, lastName).filter { it.isNotBlank() }.joinToString(" ")
    }

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var profile = Profile()

    private lateinit var pfAvatar: TextView
    private lateinit var pfName: TextView
    private lateinit var pfEmail: TextView
    private lateinit var pfInfoName: TextView
    private lateinit var pfInfoGender: TextView
    private lateinit var pfInfoBirthday: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        pfAvatar = view.findViewById(R.id.pfAvatar)
        pfName = view.findViewById(R.id.pfName)
        pfEmail = view.findViewById(R.id.pfEmail)
        pfInfoName = view.findViewById(R.id.pfInfoName)
        pfInfoGender = view.findViewById(R.id.pfInfoGender)
        pfInfoBirthday = view.findViewById(R.id.pfInfoBirthday)

        pfEmail.text = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
        view.findViewById<View>(R.id.btnEditProfile).setOnClickListener { showEditSheet() }
        view.findViewById<View>(R.id.rowChangePassword).setOnClickListener { showPasswordSheet() }
        view.findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener { confirmLogout() }
        setupAppVersion(view.findViewById(R.id.pfVersion))

        loadProfile()
        return view
    }

    // ───────────────────────────── Load & show ─────────────────────────────

    private fun loadProfile() {
        val uid = AuthHelper().getCurrentUser()?.uid ?: return
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener
                profile = Profile(
                    firstName = doc.getString("firstName").orEmpty(),
                    middleName = doc.getString("middleName").orEmpty(),
                    lastName = doc.getString("lastName").orEmpty(),
                    gender = doc.getString("gender").orEmpty(),
                    dob = doc.getString("dob").orEmpty(),
                    classId = doc.getString("classId").orEmpty(),
                    className = doc.getString("className").orEmpty()
                )
                showProfile()
            }
            .addOnFailureListener { e ->
                Log.e("ProfileFragment", "Failed to fetch user info", e)
                if (isAdded) showProfile()
            }
    }

    private fun showProfile() {
        val name = profile.fullName.ifBlank { getString(R.string.pf_student) }
        pfName.text = name
        pfAvatar.text = profile.firstName.firstOrNull()?.uppercase() ?: "B"

        setValue(pfInfoName, profile.fullName)
        setValue(pfInfoGender, profile.gender)
        setValue(pfInfoBirthday, formatBirthday(profile.dob))
    }

    /** Shows the value, or a soft "Hindi pa nailalagay" when empty. */
    private fun setValue(view: TextView, value: String) {
        val empty = value.isBlank()
        view.text = if (empty) getString(R.string.pf_not_set) else value
        view.setTextColor(ContextCompat.getColor(requireContext(), if (empty) R.color.pg_ink_soft else R.color.pg_ink))
    }

    // ───────────────────────────── Edit account ─────────────────────────────

    private fun showEditSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val content = layoutInflater.inflate(R.layout.sheet_edit_profile, null)
        sheet.setContentView(content)

        val tilFirst = content.findViewById<TextInputLayout>(R.id.tilFirstName)
        val tilLast = content.findViewById<TextInputLayout>(R.id.tilLastName)
        val etFirst = content.findViewById<TextInputEditText>(R.id.etFirstName)
        val etMiddle = content.findViewById<TextInputEditText>(R.id.etMiddleName)
        val etLast = content.findViewById<TextInputEditText>(R.id.etLastName)
        val genderGroup = content.findViewById<ChipGroup>(R.id.chipGroupGender)
        val tilBirthday = content.findViewById<TextInputLayout>(R.id.tilBirthday)
        val etBirthday = content.findViewById<TextInputEditText>(R.id.etBirthday)
        val btnSave = content.findViewById<MaterialButton>(R.id.btnSave)

        etFirst.setText(profile.firstName)
        etMiddle.setText(profile.middleName)
        etLast.setText(profile.lastName)
        genderChipFor(profile.gender)?.let { genderGroup.check(it) }

        var dob = profile.dob
        etBirthday.setText(formatBirthday(dob))
        val pickBirthday = View.OnClickListener {
            showBirthdayPicker(dob) { picked ->
                dob = picked
                etBirthday.setText(formatBirthday(picked))
            }
        }
        etBirthday.setOnClickListener(pickBirthday)
        tilBirthday.setEndIconOnClickListener(pickBirthday)

        content.findViewById<View>(R.id.btnCancel).setOnClickListener { sheet.dismiss() }
        btnSave.setOnClickListener {
            val first = etFirst.text?.toString()?.trim().orEmpty()
            val middle = etMiddle.text?.toString()?.trim().orEmpty()
            val last = etLast.text?.toString()?.trim().orEmpty()
            tilFirst.error = if (first.isEmpty()) getString(R.string.pf_error_first_name) else null
            tilLast.error = if (last.isEmpty()) getString(R.string.pf_error_last_name) else null
            if (first.isEmpty() || last.isEmpty()) return@setOnClickListener

            val updated = profile.copy(
                firstName = first,
                middleName = middle,
                lastName = last,
                gender = genderLabelFor(genderGroup.checkedChipId),
                dob = dob
            )
            saveProfile(updated, btnSave) { sheet.dismiss() }
        }

        sheet.show()
    }

    private fun saveProfile(updated: Profile, btnSave: MaterialButton, onSaved: () -> Unit) {
        val uid = AuthHelper().getCurrentUser()?.uid ?: return
        btnSave.isEnabled = false
        btnSave.setText(R.string.pf_saving)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                firestore.collection("users").document(uid).update(
                    mapOf(
                        "firstName" to updated.firstName,
                        "middleName" to updated.middleName,
                        "lastName" to updated.lastName,
                        "gender" to updated.gender,
                        "dob" to updated.dob
                    )
                ).await()

                // Keep the name the teacher sees in the class roster in step ("Last, First").
                if (updated.classId.isNotBlank()) {
                    try {
                        firestore.collection("classes").document(updated.classId)
                            .collection("students").document(uid)
                            .update("fullName", "${updated.lastName}, ${updated.firstName}")
                            .await()
                    } catch (e: Exception) {
                        Log.w("ProfileFragment", "Could not update class roster name", e)
                    }
                }

                profile = updated
                showProfile()
                updateToolbar(updated.firstName)
                Toast.makeText(requireContext(), R.string.pf_saved, Toast.LENGTH_SHORT).show()
                onSaved()
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Failed to save profile", e)
                Toast.makeText(requireContext(), R.string.pf_save_failed, Toast.LENGTH_SHORT).show()
            } finally {
                btnSave.isEnabled = true
                btnSave.setText(R.string.pf_save)
            }
        }
    }

    /** The top bar in MainActivity shows the first name and initial. */
    private fun updateToolbar(firstName: String) {
        val activity = activity ?: return
        activity.findViewById<TextView>(R.id.tvFirstName)?.text = firstName
        activity.findViewById<TextView>(R.id.tvAvatarInitial)?.text =
            firstName.firstOrNull()?.uppercase() ?: "B"
    }

    private fun genderChipFor(gender: String): Int? = when (gender) {
        getString(R.string.pf_gender_male) -> R.id.chipMale
        getString(R.string.pf_gender_female) -> R.id.chipFemale
        getString(R.string.pf_gender_unsaid) -> R.id.chipUnsaid
        else -> null
    }

    private fun genderLabelFor(chipId: Int): String = when (chipId) {
        R.id.chipMale -> getString(R.string.pf_gender_male)
        R.id.chipFemale -> getString(R.string.pf_gender_female)
        R.id.chipUnsaid -> getString(R.string.pf_gender_unsaid)
        else -> ""
    }

    // ───────────────────────────── Birthday ─────────────────────────────

    private fun showBirthdayPicker(current: String, onPicked: (String) -> Unit) {
        val selection = parseDob(current)
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.pf_birthday_hint)
            .setCalendarConstraints(
                CalendarConstraints.Builder().setValidator(DateValidatorPointBackward.now()).build()
            )
            .apply { if (selection != null) setSelection(selection) }
            .build()
        picker.addOnPositiveButtonClickListener { millis -> onPicked(dobFormat().format(millis)) }
        picker.show(childFragmentManager, "birthday")
    }

    // MaterialDatePicker works in UTC milliseconds.
    private fun dobFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private fun parseDob(dob: String): Long? = try {
        if (dob.isBlank()) null else dobFormat().parse(dob)?.time
    } catch (e: Exception) {
        null
    }

    /** "2016-10-12" → "Oktubre 12, 2016". */
    private fun formatBirthday(dob: String): String {
        val millis = parseDob(dob) ?: return ""
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
        val month = FILIPINO_MONTHS[calendar.get(Calendar.MONTH)]
        return "$month ${calendar.get(Calendar.DAY_OF_MONTH)}, ${calendar.get(Calendar.YEAR)}"
    }

    // ───────────────────────────── Password ─────────────────────────────

    private fun showPasswordSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val content = layoutInflater.inflate(R.layout.sheet_change_password, null)
        sheet.setContentView(content)

        val tilCurrent = content.findViewById<TextInputLayout>(R.id.tilCurrentPassword)
        val tilNew = content.findViewById<TextInputLayout>(R.id.tilNewPassword)
        val tilConfirm = content.findViewById<TextInputLayout>(R.id.tilConfirmPassword)
        val etCurrent = content.findViewById<TextInputEditText>(R.id.etCurrentPassword)
        val etNew = content.findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConfirm = content.findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val btnSave = content.findViewById<MaterialButton>(R.id.btnSave)

        content.findViewById<View>(R.id.btnCancel).setOnClickListener { sheet.dismiss() }
        btnSave.setOnClickListener {
            val current = etCurrent.text?.toString().orEmpty()
            val new = etNew.text?.toString().orEmpty()
            val confirm = etConfirm.text?.toString().orEmpty()

            tilCurrent.error = if (current.isEmpty()) getString(R.string.pf_password_error_current) else null
            tilNew.error = when {
                new.length < 8 -> getString(R.string.pf_password_error_short)
                new == current -> getString(R.string.pf_password_error_same)
                else -> null
            }
            tilConfirm.error = if (confirm != new) getString(R.string.pf_password_error_match) else null
            if (tilCurrent.error != null || tilNew.error != null || tilConfirm.error != null) return@setOnClickListener

            changePassword(current, new, btnSave, tilCurrent, tilNew) { sheet.dismiss() }
        }

        sheet.show()
    }

    private fun changePassword(
        current: String,
        new: String,
        btnSave: MaterialButton,
        tilCurrent: TextInputLayout,
        tilNew: TextInputLayout,
        onChanged: () -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val email = user.email ?: return
        btnSave.isEnabled = false
        btnSave.setText(R.string.pf_saving)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Firebase requires a recent sign-in to change the password.
                user.reauthenticate(EmailAuthProvider.getCredential(email, current)).await()
                user.updatePassword(new).await()
                Toast.makeText(requireContext(), R.string.pf_password_changed, Toast.LENGTH_SHORT).show()
                onChanged()
            } catch (e: FirebaseAuthWeakPasswordException) {
                tilNew.error = getString(R.string.pf_password_error_weak)
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                tilCurrent.error = getString(R.string.pf_password_error_wrong)
            } catch (e: FirebaseTooManyRequestsException) {
                Toast.makeText(requireContext(), R.string.pf_password_error_retry, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Failed to change password", e)
                Toast.makeText(requireContext(), R.string.pf_password_failed, Toast.LENGTH_SHORT).show()
            } finally {
                btnSave.isEnabled = true
                btnSave.setText(R.string.pf_password_save)
            }
        }
    }

    // ───────────────────────────── Logout & version ─────────────────────────────

    private fun confirmLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.pf_logout)
            .setMessage(R.string.pf_logout_confirm)
            .setPositiveButton(R.string.pf_yes) { _, _ ->
                FirebaseAuth.getInstance().signOut()
                val intent = Intent(requireContext(), LogInActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
            }
            .setNegativeButton(R.string.pf_no, null)
            .show()
    }

    private fun setupAppVersion(tvVersion: TextView) {
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            tvVersion.text = getString(R.string.pf_version, packageInfo.versionName)
        } catch (e: Exception) {
            tvVersion.text = getString(R.string.app_name)
        }
    }

    private companion object {
        val FILIPINO_MONTHS = listOf(
            "Enero", "Pebrero", "Marso", "Abril", "Mayo", "Hunyo",
            "Hulyo", "Agosto", "Setyembre", "Oktubre", "Nobyembre", "Disyembre"
        )
    }
}
