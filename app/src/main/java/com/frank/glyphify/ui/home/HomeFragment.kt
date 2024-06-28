package com.frank.glyphify.ui.home

import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.frank.glyphify.R
import com.frank.glyphify.databinding.FragmentHomeBinding
import com.frank.glyphify.filehandling.Glyphifier
import com.google.android.material.button.MaterialButton


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private fun showNameDialog(context: Context, onNameEntered: (String) -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.output_name_dialog, null)
        val editText = dialogView.findViewById<EditText>(R.id.editText)
        val positiveButton = dialogView.findViewById<Button>(R.id.positiveButton)
        val negativeButton = dialogView.findViewById<Button>(R.id.negativeButton)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        positiveButton.isEnabled = false // disable the button initially

        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                positiveButton.isEnabled = !s.isNullOrEmpty() // enable the button only if editText is not empty
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        positiveButton.setOnClickListener {
            val name = editText.text.toString()
            onNameEntered(name)
            dialog.dismiss()
        }

        negativeButton.setOnClickListener {
            dialog.dismiss()
        }

        // ignore theme style and apply custom style for these buttons
        positiveButton.backgroundTintList = null
        negativeButton.backgroundTintList = null

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
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

        requireView().findViewById<MaterialButton>(R.id.select_file).setOnClickListener {
            val intent = Intent()
                .setType("audio/*")
                .setAction(Intent.ACTION_GET_CONTENT)
            startActivityForResult(Intent.createChooser(intent, "Select a file"), 1)
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
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1 && resultCode == RESULT_OK) {
            val selectedFileUri = data?.data // The URI with the location of the file
            if (selectedFileUri != null) {

                showNameDialog(requireContext()) { name ->
                    val data = Data.Builder()
                        .putString("uri", selectedFileUri.toString())
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
                                }
                            }
                        })

                    WorkManager.getInstance(requireContext()).enqueue(workReq)
                }
            }
        }
    }

}