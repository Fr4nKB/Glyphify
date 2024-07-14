package com.frank.glyphify.ui.home

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.frank.glyphify.Constants.PHONE2_MODEL_ID
import com.frank.glyphify.PermissionHandling
import com.frank.glyphify.R
import com.frank.glyphify.databinding.FragmentHomeBinding
import com.frank.glyphify.glyph.Glyphifier
import com.frank.glyphify.ui.dialogs.Dialog
import com.frank.glyphify.ui.dialogs.Dialog.supportMe
import com.google.android.material.button.MaterialButton
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

        requireView().findViewById<MaterialButton>(R.id.select_file).setOnClickListener {
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
        );

        if(Build.MODEL == PHONE2_MODEL_ID) {
            requireView().findViewById<LinearLayout>(R.id.expanded_lin).visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().findViewById<LinearLayout>(R.id.toolbar_btns_wrapper).visibility = View.GONE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 10 && resultCode == RESULT_OK) {
            val selectedFileUri = data?.data // The URI with the location of the file
            if (selectedFileUri != null) {

                showNameDialog(requireContext()) { name ->
                    val expanded = requireView().findViewById<CheckBox>(R.id.expanded_checkbox).isChecked
                    val data = Data.Builder()
                        .putString("uri", selectedFileUri.toString())
                        .putBoolean("expanded", expanded)
                        .putString("outputName", name)
                        .build()

                    val workReq = OneTimeWorkRequestBuilder<Glyphifier>()
                        .setInputData(data)
                        .build()

                    val selectFileButton = requireView().findViewById<MaterialButton>(R.id.select_file)
                    selectFileButton.isEnabled = false // Disable the button

                    val progressBar = requireView().findViewById<ProgressBar>(R.id.progress_bar)
                    progressBar.visibility = View.VISIBLE

                    // Listen for progress updates
                    WorkManager.getInstance(requireContext()).getWorkInfoByIdLiveData(workReq.id)
                        .observe(this, Observer { workInfo ->
                            if (workInfo != null) {
                                val progress = workInfo.progress.getInt("PROGRESS", 0)
                                progressBar.progress = progress
                                if (workInfo.state.isFinished) {
                                    progressBar.visibility = View.GONE
                                    selectFileButton.isEnabled = true // Re-enable the button

                                    val randomNumber = Random.nextInt(0, 3)
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
        }
    }

}