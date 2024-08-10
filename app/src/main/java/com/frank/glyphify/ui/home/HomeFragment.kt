package com.frank.glyphify.ui.home

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.frank.glyphify.Constants.DIMMING_VALUES
import com.frank.glyphify.Constants.PHONE2_MODEL_ID
import com.frank.glyphify.PermissionHandling
import com.frank.glyphify.R
import com.frank.glyphify.databinding.FragmentHomeBinding
import com.frank.glyphify.glyph.composer.FileHandling.getFileNameFromUri
import com.frank.glyphify.glyph.composer.Glyphifier
import com.frank.glyphify.ui.dialogs.Dialog
import com.frank.glyphify.ui.dialogs.Dialog.supportMe
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlin.random.Random


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private fun showNameDialog(context: Context, onNameEntered: (String) -> Unit) {
        Dialog.showDialog(
            context,
            R.layout.dialog_output_name,
            mapOf(
                R.id.positiveButton to {
                    dialogView -> onNameEntered(
                    (dialogView.findViewById<EditText>(R.id.editText))
                        .text
                        .toString()) },
                R.id.negativeButton to { }
            )
        )
    }

    private fun glyphifySong(selectedFileUri: Uri) {
        showNameDialog(requireContext()) { name ->

            val glyphifyBtn = requireView().findViewById<MaterialButton>(R.id.btn_glyphify)
            glyphifyBtn.isEnabled = false

            val linLayoutProgress = requireView().findViewById<LinearLayout>(R.id.lin_layout_progress)
            linLayoutProgress.visibility = View.VISIBLE

            val selectFileButton = requireView().findViewById<MaterialButton>(R.id.btn_select_file)
            selectFileButton.isEnabled = false // disable the button

            val expandedToggle = requireView().findViewById<MaterialButtonToggleGroup>(R.id.expanded_toggle)
            expandedToggle.isEnabled = false

            val dimmingToggle = requireView().findViewById<MaterialButtonToggleGroup>(R.id.dimming_toggle)
            val selectedButtonIndex = dimmingToggle.indexOfChild(
                dimmingToggle.findViewById(
                    dimmingToggle.checkedButtonId
                )
            )
            dimmingToggle.isEnabled = false

            val data = Data.Builder()
                .putString("uri", selectedFileUri.toString())
                .putBoolean("expanded", expandedToggle.checkedButtonId == R.id.expanded_toggle_33)
                .putInt("dimming", DIMMING_VALUES[selectedButtonIndex])
                .putString("outputName", name)
                .build()

            val workReq = OneTimeWorkRequestBuilder<Glyphifier>()
                .setInputData(data)
                .build()

            // listen for progress updates
            WorkManager.getInstance(requireContext()).getWorkInfoByIdLiveData(workReq.id)
                .observe(this, Observer { workInfo ->
                    if (workInfo != null) {
                        val progress = workInfo.progress.getInt("PROGRESS", 0)

                        val progressBar = requireView().findViewById<ProgressBar>(R.id.progress_bar)
                        progressBar.progress = progress

                        val textView = requireView().findViewById<TextView>(R.id.text_progress)

                        when {
                            progress <= 35 -> {
                                textView.text = requireContext().getString(R.string.progress_text_1)
                            }
                            progress in 36..80 -> {
                                textView.text = requireContext().getString(R.string.progress_text_2)
                            }
                            progress in 81..100 -> {
                                textView.text = requireContext().getString(R.string.progress_text_3)
                            }
                        }

                        if (workInfo.state.isFinished) {    // re-enable and show everything
                            selectFileButton.isEnabled = true
                            selectFileButton.text = getString(R.string.btn_file_selection)
                            selectFileButton.backgroundTintList =
                                ContextCompat.getColorStateList(requireContext(), R.color.black_russian);

                            expandedToggle.isEnabled = true
                            dimmingToggle.isEnabled = true

                            linLayoutProgress.visibility = View.GONE

                            glyphifyBtn.visibility = View.GONE
                            glyphifyBtn.isEnabled = true

                            //  randomly show support dialog
                            val randomNumber = Random.nextInt(0, 5)
                            if(randomNumber == 2) {
                                val permHandler = PermissionHandling(requireActivity())
                                supportMe(requireContext(), permHandler)
                            }
                        }
                    }
                })

            WorkManager.getInstance(requireContext()).enqueue(workReq)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireView().findViewById<MaterialButton>(R.id.btn_donate).setOnClickListener() {
            startActivity(
                Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.paypal.com/donate/?hosted_button_id=HJU8Y7F34Z6TL"))
            )
        }

        requireView().findViewById<MaterialButton>(R.id.btn_select_file).setOnClickListener {
            val intent = Intent()
                .setType("audio/*")
                .setAction(Intent.ACTION_GET_CONTENT)
            startActivityForResult(Intent.createChooser(intent, "Select a file"), 10)
        }


        // color the progress bar in red and make it visible
        val progressBar = requireView().findViewById<ProgressBar>(R.id.progress_bar)
        progressBar.setProgressTintList(
            ColorStateList.valueOf(
                ContextCompat.getColor(
                    requireContext(), R.color.red
                )
            )
        )


        val sharedPref: SharedPreferences =
            requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)

        val dimmingToggle = requireView().findViewById<MaterialButtonToggleGroup>(R.id.dimming_toggle)
        var lastSelectionToggleId = sharedPref.getInt("dimming_glyphifier_toggle_id", R.id.dimming_toggle_var)

        dimmingToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val editor: SharedPreferences.Editor = sharedPref.edit()
                editor.putInt("dimming_glyphifier_toggle_id", checkedId)
                editor.apply()
            }
        }

        dimmingToggle.check(lastSelectionToggleId)

        if(PHONE2_MODEL_ID.contains(Build.MODEL)) {
            val expandedToggleLayout = requireView().findViewById<LinearLayout>(R.id.expanded_toggle_layout)
            val expandedToggle = requireView().findViewById<MaterialButtonToggleGroup>(R.id.expanded_toggle)

            lastSelectionToggleId = sharedPref.getInt("expanded_toggle_id", R.id.expanded_toggle_5)

            expandedToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    val editor: SharedPreferences.Editor = sharedPref.edit()
                    editor.putInt("expanded_toggle_id", checkedId)
                    editor.apply()
                }
            }

            expandedToggle.check(lastSelectionToggleId)
            expandedToggleLayout.visibility = View.VISIBLE

        }
    }

    override fun onResume() {
        super.onResume()
        val activity = requireActivity()
        activity.findViewById<MaterialButton>(R.id.toolbar_btn_back).visibility = View.GONE
        activity.findViewById<TextView>(R.id.toolbar_app_name).visibility = View.VISIBLE
        activity.findViewById<LinearLayout>(R.id.toolbar_btns_wrapper).visibility = View.GONE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 10 && resultCode == RESULT_OK) {
            val selectedFileUri = data?.data        // contains the Uri

            if (selectedFileUri != null) {
                val filename = getFileNameFromUri(requireContext(), selectedFileUri)
                val selectFileButton = requireView().findViewById<MaterialButton>(R.id.btn_select_file)
                selectFileButton.text = filename    // set text to display file name
                selectFileButton.backgroundTintList =
                    ContextCompat.getColorStateList(requireContext(), R.color.red);

                val glyphifyBtn = requireActivity().findViewById<MaterialButton>(R.id.btn_glyphify)
                glyphifyBtn.setOnClickListener { glyphifySong(selectedFileUri) }
                glyphifyBtn.visibility = View.VISIBLE
            }
        }
    }

}