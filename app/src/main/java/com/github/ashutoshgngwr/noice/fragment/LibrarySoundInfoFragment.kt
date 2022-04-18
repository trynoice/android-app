package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.navArgs
import com.github.ashutoshgngwr.noice.databinding.LibrarySoundInfoFragmentBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class LibrarySoundInfoFragment : BottomSheetDialogFragment() {

  private lateinit var binding: LibrarySoundInfoFragmentBinding
  private val args: LibrarySoundInfoFragmentArgs by navArgs()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = LibrarySoundInfoFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.sound = args.sound
    binding.dismiss.setOnClickListener { dismiss() }
  }
}
