package com.example.leafyapp.ui.camera

import android.os.Bundle
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.leafyapp.R

class CaptureTipsDialog : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.popup_capture_tips, container, false)

        // Ánh xạ View
        val title = view.findViewById<TextView>(R.id.tvPopupTitle)
        val content = view.findViewById<TextView>(R.id.tvPopupContent)

        // Animation fade-in
        val fadeIn = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in)
        view.startAnimation(fadeIn)

        return view
    }

    override fun onStart() {
        super.onStart()

        // Set popup to 2/3 width
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.80).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Nhấn ra ngoài để tắt
        dialog?.setCanceledOnTouchOutside(true)
    }
}
