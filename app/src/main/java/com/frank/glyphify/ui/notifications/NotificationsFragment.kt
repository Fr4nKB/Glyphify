package com.frank.glyphify.ui.notifications

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.frank.glyphify.Constants.DIMMING_VALUES
import com.frank.glyphify.Constants.PHONE1_MODEL_ID
import com.frank.glyphify.Constants.PHONE2A_MODEL_ID
import com.frank.glyphify.PermissionHandling
import com.frank.glyphify.R
import com.frank.glyphify.Util.loadPreferences
import com.frank.glyphify.databinding.FragmentHomeBinding
import com.frank.glyphify.databinding.FragmentNotifications1Binding
import com.frank.glyphify.databinding.FragmentNotifications2Binding
import com.frank.glyphify.databinding.FragmentNotifications2aBinding
import com.frank.glyphify.glyph.notificationmanager.ExtendedEssentialService
import com.frank.glyphify.ui.dialogs.Dialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import java.math.BigInteger


class NotificationsFragment: Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var numZones: Int = 11
    private lateinit var glyphsMapping: MutableList<Triple<Int, List<BigInteger>, Boolean>>
    private lateinit var permHandler: PermissionHandling

    private fun loadContactsMapping() {
        val buttonContainer: ViewGroup = requireView().findViewById(R.id.buttonContainer)

        for (i in 0 until buttonContainer.childCount) {
            val child = buttonContainer.getChildAt(i)

            if (child is MaterialButton) {
                val mappingTriple = glyphsMapping[i]
                when {
                    mappingTriple.second.size == 1 ->
                        child.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_single)
                    mappingTriple.second.size > 1 ->
                        child.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_apps)
                    else ->
                        child.icon = null
                }

                val glyphBtn = resources.getResourceEntryName(child.id).split("_")
                val glyphId = glyphBtn[3].toInt()

                child.setOnClickListener {
                    val bundle = Bundle().apply {
                        putInt("zoneIndex", i)
                        putInt("glyphId", glyphId)
                    }
                    findNavController().navigate(R.id.navigation_contacts_choice, bundle)
                }
            }

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

        glyphsMapping = loadPreferences(requireContext(), numZones)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if(!isNotificationServiceEnabled()) {
            showDisclaimer()
        }
        else loadContactsMapping()

        val sharedPref: SharedPreferences =
            requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)

        val dimmingToggle = requireView().findViewById<MaterialButtonToggleGroup>(R.id.dimming_toggle)
        var lastSelectionToggleId = sharedPref.getInt("dimming_ee_toggle_id", R.id.dimming_toggle_mid)

        dimmingToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val editor: SharedPreferences.Editor = sharedPref.edit()
                editor.putInt("dimming_ee_toggle_id", checkedId)
                editor.apply()

                val index = dimmingToggle.indexOfChild(dimmingToggle.findViewById(checkedId))
                val intent = Intent(activity, ExtendedEssentialService::class.java).apply {
                    action = "UPDATE_INTENSITY"
                    putExtra("intensity", DIMMING_VALUES[index])
                }
                requireActivity().startService(intent)
            }
        }

        dimmingToggle.check(lastSelectionToggleId)
    }


    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<LinearLayout>(R.id.toolbar_btns_wrapper).visibility = View.GONE
    }

}