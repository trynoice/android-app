package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.databinding.AlarmsFragmentBinding

class AlarmsFragment : Fragment() {

  private lateinit var binding: AlarmsFragmentBinding

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = AlarmsFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.addAlarmButton.setOnClickListener { Log.d("AlarmsFragment", "add alarm clicked") }
  }
}
