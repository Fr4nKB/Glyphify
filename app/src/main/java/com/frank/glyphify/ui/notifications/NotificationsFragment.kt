package com.frank.glyphify.ui.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.frank.glyphify.Constants.PHONE1_MODEL_ID
import com.frank.glyphify.Constants.PHONE2A_MODEL_ID
import com.frank.glyphify.PermissionHandling
import com.frank.glyphify.R
import com.frank.glyphify.Util.loadPreferences
import com.frank.glyphify.databinding.FragmentHomeBinding
import com.frank.glyphify.databinding.FragmentNotifications1Binding
import com.frank.glyphify.databinding.FragmentNotifications2Binding
import com.frank.glyphify.databinding.FragmentNotifications2aBinding
import com.frank.glyphify.glyph.notificationmanager.GlyphNotificationManagerService
import com.frank.glyphify.ui.dialogs.Dialog
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject


class NotificationsFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var numZones: Int = 11
    private lateinit var contacts2glyphMapping: MutableList<Triple<Int, Long, Boolean>>
    private lateinit var permHandler: PermissionHandling

    private fun savePreferences() {
        val sharedPref: SharedPreferences =
            requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPref.edit()

        val mapping = JSONObject()
        for (i in contacts2glyphMapping.indices) {
            try {
                val triple = contacts2glyphMapping[i]
                val jsonArray = JSONArray()
                jsonArray.put(triple.first)
                jsonArray.put(triple.second)
                jsonArray.put(triple.third)
                mapping.put(i.toString(), jsonArray)
            }
            catch (e: Exception) {
                e.printStackTrace()
            }
        }

        editor.putString("contactsMapping", mapping.toString())
        editor.apply()

        // send an intent to the service to make changes
        val intent = Intent(activity, GlyphNotificationManagerService::class.java)
        requireActivity().startService(intent)
    }

    private fun getContactName(contactId: Long): String? {
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)
        val selection = ContactsContract.Data.CONTACT_ID + " = ?"
        val selectionArgs = arrayOf(contactId.toString())

        val permissions = mutableListOf(
            Manifest.permission.READ_CONTACTS
        )

        var contactName: String? = null
        if(permHandler.checkRequiredPermission(permissions)) {
            val cursor = requireContext().contentResolver.query(uri, projection, selection, selectionArgs, null)

            cursor?.let {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        contactName = it.getString(nameIndex)
                    }
                }
                it.close()
            }
        }
        return contactName
    }

    private fun setBtnTextColor(btn: MaterialButton, choice: Boolean) {
        val color: Int
        if(choice) {
            color = ContextCompat.getColor(requireContext(), R.color.red)
        }
        else {
            color = ContextCompat.getColor(requireContext(), R.color.white)
        }

        btn.setTextColor(color)
    }

    private fun setInitials(btn: MaterialButton, index: Int) {
        val mappingTriple = contacts2glyphMapping[index] as Triple<Int, Long, Boolean>

        if(mappingTriple.second != -1L) {
            val contactName = getContactName(mappingTriple.second)

            if(contactName != null) {
                setBtnTextColor(btn, mappingTriple.third)
                val fullName = contactName.split(" ")
                var initials = fullName[0][0].toString() + "."
                if(fullName.size > 1) initials += fullName[1][0] + "."
                btn.text = initials
            }
        }
    }

    private fun loadContactsMapping() {
        val buttonContainer: ViewGroup = requireView().findViewById(R.id.buttonContainer)
        val permissions = mutableListOf(
            Manifest.permission.READ_CONTACTS
        )

        val loadNames = permHandler.checkRequiredPermission(permissions)

        for (i in 0 until buttonContainer.childCount) {
            val child = buttonContainer.getChildAt(i)

            if (child is MaterialButton) {

                child.setOnClickListener {
                    val mappingTriple = contacts2glyphMapping[i]

                    if(mappingTriple.first == -1) {
                        if(permHandler.checkRequiredPermission(permissions)) {
                            permHandler.askRequiredPermissions(permissions, R.layout.dialog_perm_contacts)
                        }

                        val contactPickerIntent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                        startActivityForResult(contactPickerIntent, child.id)
                    }

                    else {
                        val pulse = mappingTriple.third.not()
                        setBtnTextColor(child, pulse)

                        contacts2glyphMapping[i] = Triple(
                            mappingTriple.first,
                            mappingTriple.second,
                            pulse
                        )

                        savePreferences()
                    }
                }

                child.setOnLongClickListener {
                    contacts2glyphMapping[i] = Triple(-1, -1L, false)
                    child.text = ""
                    setBtnTextColor(child, false)
                    savePreferences()
                    true
                }

                if(loadNames) {
                    setInitials(child, i)
                }
            }
        }

        if(!loadNames) {
            permHandler.askRequiredPermissions(permissions, R.layout.dialog_perm_contacts)
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val packageNames = NotificationManagerCompat.getEnabledListenerPackages(requireActivity())
        return packageNames.contains(requireContext().packageName)
    }

    private fun showDisclaimer() {
        Dialog.showDialog(
            requireContext(),
            R.layout.dialog_notification_listener_disclaimer,
            mapOf(
                R.id.positiveButton to {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    startActivity(intent)
                },
                R.id.negativeButton to { parentFragmentManager.popBackStack() }
            ),
            onDismiss = {
                loadContactsMapping()
            }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        permHandler = PermissionHandling(requireActivity())

        val root = when(Build.MODEL) {
            PHONE1_MODEL_ID -> {
                numZones = 5
                val binding = FragmentNotifications1Binding.inflate(inflater, container, false)
                binding.root
            }
            PHONE2A_MODEL_ID -> {
                numZones = 3
                val binding = FragmentNotifications2aBinding.inflate(inflater, container, false)
                binding.root
            }
            else -> {
                numZones = 11
                val binding = FragmentNotifications2Binding.inflate(inflater, container, false)
                binding.root
            }
        }

        contacts2glyphMapping = loadPreferences(requireContext(), numZones)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if(!isNotificationServiceEnabled()) {
            showDisclaimer()
        }
        else loadContactsMapping()
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<LinearLayout>(R.id.toolbar_btns_wrapper).visibility = View.GONE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            val contactUri: Uri? = data?.data
            val projection = arrayOf(ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME)

            val cursor = contactUri?.let {
                requireActivity().contentResolver.query(it, projection, null, null, null)
            }
            if (cursor != null && cursor.moveToFirst()) {
                val contactIdIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val contactId = cursor.getLong(contactIdIndex)

                val glyphBtn = resources.getResourceEntryName(requestCode).split("_")

                contacts2glyphMapping[glyphBtn[2].toInt()] = Triple(glyphBtn[3].toInt(), contactId, false)
                savePreferences()

                val buttonContainer = requireView().findViewById<ViewGroup>(R.id.buttonContainer)
                val btn = requireView().findViewById<MaterialButton>(requestCode)
                setInitials(btn, buttonContainer.indexOfChild(btn))
            }
            cursor?.close()
        }
    }

}