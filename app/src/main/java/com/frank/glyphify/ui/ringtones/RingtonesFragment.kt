package com.frank.glyphify.ui.ringtones

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frank.glyphify.R
import com.frank.glyphify.databinding.FragmentRingtonesBinding
import com.frank.glyphify.glyph.visualizer.GlyphVisualizer
import com.frank.glyphify.ui.dialogs.Dialog
import com.google.android.material.button.MaterialButton
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream

class RingtonesFragment : Fragment() {

    private var _binding: FragmentRingtonesBinding? = null
    private val binding get() = _binding!!

    private lateinit var gv: GlyphVisualizer
    private var playing = false

    private fun showPermissionDialog(context: Context) {
        Dialog.showDialog(
            context,
            R.layout.dialog_perm_settings,
            mapOf(
                R.id.positiveButton to { startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)) },
                R.id.negativeButton to {}
            )
        )
    }

    private fun setMediaReproductionIcon() {
        val btn = requireActivity().findViewById<ImageButton>(R.id.btn_playRingtone)

        if(!playing) {
            val playIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_play)
            btn.setImageDrawable(playIcon)
        }
        else {
            val pauseIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_pause)
            btn.setImageDrawable(pauseIcon)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRingtonesBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Load the files
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES),
            "Compositions")
        var files = directory.listFiles()
        if(files == null) files = emptyArray<File>()

        // Create an adapter for the RecyclerView
        val adapter = FilesAdapter(files)

        // Set the adapter to the RecyclerView
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        gv = GlyphVisualizer(requireContext())
    }

    override fun onResume() {
        super.onResume()
        val activity = requireActivity()
        activity.findViewById<MaterialButton>(R.id.toolbar_btn_back).visibility = View.GONE
        activity.findViewById<TextView>(R.id.toolbar_app_name).visibility = View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        gv.stopVisualization()
    }

    inner class FilesAdapter(private var files: Array<File>) : RecyclerView.Adapter<FilesAdapter.ViewHolder>() {

        var lastKnownPos = -1  // keep track of the last selected item

        init {
            // Filter out only .ogg files
            files = files.filter { it.extension == "ogg" }.sortedByDescending { it.lastModified() }
                .toTypedArray()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view.findViewById(R.id.textView)
            val toolbarBtnsWrapper: LinearLayout = requireActivity().findViewById(R.id.toolbar_btns_wrapper)
            val btnPlayRingtone: ImageButton = requireActivity().findViewById(R.id.btn_playRingtone)
            val btnApplyRingtone: ImageButton = requireActivity().findViewById(R.id.btn_applyRingtone)
            val btnShareRingtone: ImageButton = requireActivity().findViewById(R.id.btn_shareRingtone)
            val btnDeleteRingtone: ImageButton = requireActivity().findViewById(R.id.btn_deleteRingtone)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ringtone, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            holder.textView.text = file.nameWithoutExtension

            holder.itemView.setOnClickListener {
                val previousPos = lastKnownPos
                if(lastKnownPos == position) {  // the same item has been tapped
                    lastKnownPos = -1
                    holder.toolbarBtnsWrapper.visibility = View.GONE

                    gv.stopVisualization()
                }
                else {  // a different item was tapped
                    lastKnownPos = position
                    holder.toolbarBtnsWrapper.visibility = View.VISIBLE

                    gv.stopVisualization()
                }

                // If an item was previously selected, refresh it to remove the highlight and hide the buttons
                if(previousPos != -1) {
                    notifyItemChanged(previousPos)
                }

                // If an item is currently selected, refresh it to show the highlight and display the buttons
                if(lastKnownPos != -1) {
                    notifyItemChanged(lastKnownPos)
                }
            }

            // Set the background color and visibility of the toolbar buttons based on whether the current item is selected
            if(position == lastKnownPos) {
                holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.red))
                holder.toolbarBtnsWrapper.visibility = View.VISIBLE
            }
            else {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            }

            // map each button to a function on the selected file
            holder.btnPlayRingtone.setOnClickListener {
                if(!playing) {
                    gv.startVisualization(files[lastKnownPos].absolutePath) {
                        playing = false
                        setMediaReproductionIcon()
                    }
                    playing = true
                    setMediaReproductionIcon()
                }
                else {
                    gv.stopVisualization()
                }
            }

            holder.btnApplyRingtone.setOnClickListener {
                applyRingtone(files[lastKnownPos])
                // unselect item and refresh RecyclerView
                lastKnownPos = -1
                holder.toolbarBtnsWrapper.visibility = View.GONE
                notifyDataSetChanged()
            }

            holder.btnShareRingtone.setOnClickListener {
                shareRingtone(files[lastKnownPos])
                lastKnownPos = -1
                holder.toolbarBtnsWrapper.visibility = View.GONE
                notifyDataSetChanged()
            }

            holder.btnDeleteRingtone.setOnClickListener {
                if(deleteRingtone(files[lastKnownPos])) {
                    // update recycler view
                    files = files.filterIndexed { index, _ -> index != lastKnownPos }.toTypedArray()
                    lastKnownPos = -1
                    holder.toolbarBtnsWrapper.visibility = View.GONE
                    notifyDataSetChanged()
                }
            }
        }

        override fun getItemCount() = files.size
    }

    private fun applyRingtone(file: File) {
        if(!Settings.System.canWrite(requireContext())) {
            showPermissionDialog(requireContext())
        }
        else {
            try {
                val toastMSG = requireContext().getString(R.string.toast_ringtone_applied)
                val ringtoneType = RingtoneManager.TYPE_RINGTONE

                val sharedPref: SharedPreferences =
                    requireContext().getSharedPreferences("URIS", Context.MODE_PRIVATE)
                val uri = Uri.parse(sharedPref.getString(file.nameWithoutExtension, null))

                requireContext().contentResolver.openOutputStream(uri!!).use { os ->
                    val size = file.length().toInt()
                    val bytes = ByteArray(size)
                    try {
                        val buf =
                            BufferedInputStream(FileInputStream(file))
                        buf.read(bytes, 0, bytes.size)
                        buf.close()
                        os!!.write(bytes)
                        os.close()
                        os.flush()
                    }
                    catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                RingtoneManager.setActualDefaultRingtoneUri(
                    requireContext(), ringtoneType,
                    uri
                )

                Toast.makeText(requireContext(), toastMSG, Toast.LENGTH_SHORT).show()
            }
            catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun shareRingtone(file: File) {

        val uri: Uri = FileProvider.getUriForFile(
            requireContext(),
            "it.frank.glyphify.provider",
            file
        )

        val shareIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "audio/ogg"
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        startActivity(Intent.createChooser(shareIntent, "Share file via"))
    }

    private fun deleteRingtone(file: File): Boolean {
        val fileName = file.nameWithoutExtension

        if(file.delete()) {
            //remove Uri from shared pref
            val sharedPref: SharedPreferences =
                requireContext().getSharedPreferences("URIS", Context.MODE_PRIVATE)
            val editor = sharedPref.edit()
            editor.remove(fileName)
            editor.apply()

            return true
        }

        return false
    }


}