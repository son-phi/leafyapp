package com.example.leafyapp.ui.garden

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.example.leafyapp.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AddPlantBottomSheet(
    private val onTakePhotoClick: () -> Unit,
    private val onGalleryClick: () -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_add_plant, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Xử lý nút Chụp ảnh
        view.findViewById<LinearLayout>(R.id.btn_take_photo).setOnClickListener {
            onTakePhotoClick()
            dismiss() // Đóng bảng sau khi chọn
        }

        // Xử lý nút Chọn từ thư viện
        view.findViewById<LinearLayout>(R.id.btn_choose_gallery).setOnClickListener {
            onGalleryClick()
            dismiss()
        }
    }
}