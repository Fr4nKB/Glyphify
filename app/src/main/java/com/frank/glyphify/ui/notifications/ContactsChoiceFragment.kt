package com.frank.glyphify.ui.notifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frank.glyphify.Constants.PHONE1_MODEL_ID
import com.frank.glyphify.Constants.PHONE2A_MODEL_ID
import com.frank.glyphify.PermissionHandling
import com.frank.glyphify.R
import com.frank.glyphify.Util.fromStringToNum
import com.frank.glyphify.Util.fromNumToString
import com.frank.glyphify.Util.loadPreferences
import com.frank.glyphify.Util.resizeDrawable
import com.frank.glyphify.databinding.FragmentContactsChoiceBinding
import com.frank.glyphify.glyph.notificationmanager.ExtendedEssentialService
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger


class ContactsChoiceFragment: Fragment() {

    private var _binding: FragmentContactsChoiceBinding? = null

    private var numZones: Int = 11
    private var zone = -1
    private var glyphId = -1

    private lateinit var glyphsMapping: MutableList<Triple<Int, List<BigInteger>, Boolean>>
    private lateinit var permHandler: PermissionHandling

    private lateinit var contactsAdapter: ContactsAdapter

    private fun savePreferences() {
        val sharedPref: SharedPreferences =
            requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPref.edit()

        val mapping = JSONObject()
        for (i in glyphsMapping.indices) {
            try {
                val triple = glyphsMapping[i]
                val jsonArray = JSONArray().apply {
                    put(triple.first)
                    put(JSONArray(triple.second))
                    put(triple.third)
                }
                mapping.put(i.toString(), jsonArray)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        editor.putString("contactsMapping", mapping.toString())
        editor.apply()

        // send an intent to the service to make changes
        val intent = Intent(activity, ExtendedEssentialService::class.java).apply {
            action = "UPDATE_MAPPING"
        }
        requireActivity().startService(intent)
    }

    private fun getContactName(contactId: BigInteger): String? {
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

    private fun setEEmode(btn: MaterialButton, invertCurrValue: Boolean) {
        var currState = glyphsMapping[zone].third
        if(invertCurrValue) currState = currState.not()

        if(currState) {
            btn.text = getString(R.string.btn_ee_pulse)
            btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            btn.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.red)
        }
        else {
            btn.text = getString(R.string.btn_ee_static)
            btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
            btn.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.white)
        }

        glyphsMapping[zone] = Triple(
            glyphsMapping[zone].first,
            glyphsMapping[zone].second,
            currState
        )
        savePreferences()

    }

    private fun getAppInfo(id: BigInteger): Pair<String, Drawable> {
        val packageName = fromNumToString(id)

        val packageManager = requireContext().packageManager
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)

        val appName = packageManager.getApplicationLabel(applicationInfo).toString()
        val icon = packageManager.getApplicationIcon(packageName)

        return Pair(appName, icon)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        permHandler = PermissionHandling(requireActivity())

        val binding = FragmentContactsChoiceBinding.inflate(inflater, container, false)
        when(Build.MODEL) {
            PHONE1_MODEL_ID -> numZones = 5
            PHONE2A_MODEL_ID -> numZones = 3
            else -> numZones = 11
        }

        glyphsMapping = loadPreferences(requireContext(), numZones)

        zone = arguments?.getInt("zoneIndex")?: -1
        glyphId = arguments?.getInt("glyphId")?: -1

        if(zone != -1) {
            contactsAdapter = ContactsAdapter()
            binding.recyclerViewContacts.adapter = contactsAdapter
            binding.recyclerViewContacts.layoutManager = LinearLayoutManager(requireContext())
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val permissions = mutableListOf(Manifest.permission.READ_CONTACTS)
        if(!permHandler.checkRequiredPermission(permissions)) {
            permHandler.askRequiredPermissions(permissions, R.layout.dialog_perm_contacts)
        }

        val btnEEmode = requireView().findViewById<MaterialButton>(R.id.btn_ee_mode)
        setEEmode(btnEEmode, false)
        btnEEmode.setOnClickListener {
            setEEmode(btnEEmode, true)
        }

        val btnAddContact = requireView().findViewById<MaterialButton>(R.id.btn_add_contact)
        btnAddContact.setOnClickListener {
            val contactPickerIntent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
            startActivityForResult(contactPickerIntent, zone)
        }

        setFragmentResultListener("app_choice") { requestKey, bundle ->
            val chosenPackageName = bundle.getString("chosen_package_name")
            if(chosenPackageName != null) {
                val contactMapping = glyphsMapping[zone]
                val mappedIds = contactMapping.second.toMutableList()

                val appId = fromStringToNum(chosenPackageName)

                if(!mappedIds.contains(appId)) { // modify only if app wasn't already present
                    mappedIds.add(appId)
                    glyphsMapping[zone] = Triple(glyphId, mappedIds.toList(), glyphsMapping[zone].third)
                    savePreferences()
                    contactsAdapter.notifyItemChanged(mappedIds.size - 1)
                }
            }
        }

        val btnAddApp = requireView().findViewById<MaterialButton>(R.id.btn_add_app)
        btnAddApp.setOnClickListener {
            findNavController().navigate(R.id.navigation_apps_choice)
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<LinearLayout>(R.id.toolbar_btns_wrapper).visibility = View.GONE

        val bottomNavigationView: BottomNavigationView = requireActivity().findViewById(R.id.nav_view)
        val menu: Menu = bottomNavigationView.menu
        val menuItem: MenuItem = menu.getItem(0)
        menuItem.isChecked = true
    }

    inner class ContactsAdapter() : RecyclerView.Adapter<ContactsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val btnMapping: MaterialButton = view.findViewById(R.id.btn_item)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rr_centered_btn, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val id = glyphsMapping[zone].second[position]

            if(id >= BigInteger("0")) {
                holder.btnMapping.text = getContactName(id)
                holder.btnMapping.setCompoundDrawablesWithIntrinsicBounds(
                    null, null, null, null
                )

                holder.btnMapping.setOnClickListener {
                    val contactPickerIntent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                    startActivityForResult(contactPickerIntent, zone)
                }
            }
            else {
                val appInfo = getAppInfo(id)
                holder.btnMapping.text = appInfo.first
                val resizedIcon = resizeDrawable(resources, appInfo.second, 64, 64)
                holder.btnMapping.setCompoundDrawablesWithIntrinsicBounds(
                    resizedIcon,
                    null,
                    null,
                    null
                )
            }

            holder.btnMapping.setOnLongClickListener {
                val tmp = glyphsMapping[zone].second.toMutableList()
                tmp.remove(id)

                glyphsMapping[zone] = Triple(
                    glyphsMapping[zone].first,
                    tmp,
                    glyphsMapping[zone].third
                )
                savePreferences()
                notifyDataSetChanged()
                true
            }
        }

        override fun getItemCount() = glyphsMapping[zone].second.size
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
                val contactId = BigInteger(cursor.getString(contactIdIndex))

                val glyphMapping = glyphsMapping[zone]
                val ids = glyphMapping.second.toMutableList()

                if(!ids.contains(contactId)) {
                    ids.add(contactId)
                    glyphsMapping[zone] = Triple(glyphId, ids.toList(), glyphsMapping[zone].third)
                    savePreferences()

                    contactsAdapter.notifyItemChanged(ids.size - 1)
                }

            }
            cursor?.close()
        }
    }

}