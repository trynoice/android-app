package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.navArgs
import com.github.ashutoshgngwr.noice.databinding.LibrarySoundInfoFragmentBinding
import com.github.ashutoshgngwr.noice.metrics.AnalyticsProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import javax.inject.Inject

class LibrarySoundInfoFragment : BottomSheetDialogFragment() {

  @set:Inject
  internal var analyticsProvider: AnalyticsProvider? = null

  private lateinit var binding: LibrarySoundInfoFragmentBinding
  private val args: LibrarySoundInfoFragmentArgs by navArgs()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = LibrarySoundInfoFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.info = args.info
    binding.dismiss.setOnClickListener { dismiss() }
    analyticsProvider?.setCurrentScreen(this::class)
  }
}
