package com.example.leafyapp.ui.identify

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.leafyapp.R
import com.example.leafyapp.data.model.Species
import com.example.leafyapp.data.repository.ErrorMapper
import com.example.leafyapp.data.repository.SpeciesRepository
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Chọn loài trong danh mục `species`. Kết quả trả qua Fragment Result API với khóa [REQUEST_KEY]. */
class SpeciesPickerBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val REQUEST_KEY = "species_picker"
        const val RESULT_SPECIES_ID = "speciesId"
        const val TAG = "SpeciesPicker"
    }

    private val adapter = SpeciesAdapter { species ->
        setFragmentResult(REQUEST_KEY, bundleOf(RESULT_SPECIES_ID to species.id))
        dismiss()
    }
    private var searchJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.bottom_sheet_species_picker, container, false)

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.rvSpecies)
        val progress = view.findViewById<ProgressBar>(R.id.progressSpecies)
        val empty = view.findViewById<TextView>(R.id.tvSpeciesEmpty)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        fun load(query: String) {
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val list = SpeciesRepository.search(query)
                    progress.visibility = View.GONE
                    adapter.submit(list)
                    empty.text = getString(R.string.species_picker_empty)
                    empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                } catch (e: Exception) {
                    progress.visibility = View.GONE
                    empty.text = getString(R.string.species_picker_load_error) + "\n" + ErrorMapper.map(e, "Tải danh mục loài").userMessage
                    empty.visibility = View.VISIBLE
                }
            }
        }

        view.findViewById<EditText>(R.id.etSearchSpecies).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = load(s?.toString().orEmpty())
        })
        load("")
    }

    private class SpeciesAdapter(val onClick: (Species) -> Unit) : RecyclerView.Adapter<SpeciesAdapter.VH>() {
        private var items: List<Species> = emptyList()

        fun submit(list: List<Species>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val img: ImageView = view.findViewById(R.id.imgPick)
            val name: TextView = view.findViewById(R.id.tvPickName)
            val sub: TextView = view.findViewById(R.id.tvPickSub)
            val level: TextView = view.findViewById(R.id.tvPickCareLevel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_species_pick, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val s = items[position]
            holder.name.text = s.commonName
            holder.sub.text = s.scientificName
            holder.level.text = SpeciesInfoRenderer.careLevelText(holder.itemView.context, s.careLevel)
            SpeciesInfoRenderer.loadSpeciesImage(holder.img, s.imageUrl)
            holder.itemView.setOnClickListener { onClick(s) }
        }
    }
}
