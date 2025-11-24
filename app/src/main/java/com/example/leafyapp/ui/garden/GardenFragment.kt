package com.example.leafyapp.ui.garden

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.leafyapp.databinding.FragmentGardenBinding
import com.google.android.material.tabs.TabLayoutMediator

class GardenFragment : Fragment() {

    private var _binding: FragmentGardenBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGardenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Tạo danh sách Fragment con
        val fragmentList = listOf(
            MyPlantsFragment(),
            TasksFragment()
        )

        // 2. Gắn Adapter vào ViewPager2
        val adapter = GardenPagerAdapter(this, fragmentList)
        binding.viewPager.adapter = adapter

        // 3. Kết nối Tab với ViewPager
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "My Garden"
                1 -> "Tasks"
                else -> ""
            }
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}