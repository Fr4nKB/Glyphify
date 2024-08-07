package com.frank.glyphify.ui.notifications

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frank.glyphify.R
import com.frank.glyphify.Util.resizeDrawable
import com.frank.glyphify.databinding.FragmentAppsChoiceBinding
import com.frank.glyphify.databinding.FragmentContactsChoiceBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class AppsChoiceFragment: Fragment() {

    private var _binding: FragmentContactsChoiceBinding? = null

    private var appDetails: List<Triple<String, String, Drawable>> = emptyList()
    private lateinit var appsAdapter: AppsChoiceFragment.AppsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        // get installed apps list
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val appList = requireContext().packageManager.queryIntentActivities(intent, 0)

        // filter out apps not using notifications
        val notificationManager = NotificationManagerCompat.from(requireContext())
        val currentPackageName = requireContext().packageName

        val appsWithNotifications = appList.filter { resolveInfo ->
            val appInfo = resolveInfo.activityInfo.applicationInfo
            notificationManager.areNotificationsEnabled() &&
                    appInfo.packageName != currentPackageName
        }

        // get package name, app name and icon for each app
        val packageManager = requireContext().packageManager
        appDetails = appsWithNotifications.map { resolveInfo ->
            val appInfo = resolveInfo.activityInfo.applicationInfo

            val packageName = appInfo.packageName
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            val icon = packageManager.getApplicationIcon(appInfo)

            Triple(packageName, appName, icon)
        }.sortedBy { it.second }

        val binding = FragmentAppsChoiceBinding.inflate(inflater, container, false)

        appsAdapter = AppsAdapter()
        binding.recyclerViewApps.adapter = appsAdapter
        binding.recyclerViewApps.layoutManager = LinearLayoutManager(requireContext())

        return binding.root
    }

    inner class AppsAdapter(): RecyclerView.Adapter<AppsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val btnChooseApp: MaterialButton = view.findViewById(R.id.btn_item)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rr_centered_btn, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.btnChooseApp.text = appDetails[position].second
            val resizedIcon = resizeDrawable(resources, appDetails[position].third, 64, 64)
            holder.btnChooseApp.setCompoundDrawablesWithIntrinsicBounds(
                resizedIcon,
                null,
                null,
                null
            )

            holder.btnChooseApp.setOnClickListener {
                val result = Bundle().apply {
                    putString("chosen_package_name", appDetails[position].first)
                }
                setFragmentResult("app_choice", result)
                findNavController().popBackStack()
            }
        }

        override fun getItemCount() = appDetails.size
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<LinearLayout>(R.id.toolbar_btns_wrapper).visibility = View.GONE

        val bottomNavigationView: BottomNavigationView = requireActivity().findViewById(R.id.nav_view)
        val menu: Menu = bottomNavigationView.menu
        val menuItem: MenuItem = menu.getItem(0)
        menuItem.isChecked = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}