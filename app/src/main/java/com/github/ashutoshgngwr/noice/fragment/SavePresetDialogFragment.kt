package com.github.ashutoshgngwr.noice.fragment

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.ashutoshgngwr.noice.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.dialog_save_preset.view.*

class SavePresetDialogFragment : BottomSheetDialogFragment() {

  lateinit var preset: PresetFragment.Preset

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return inflater.inflate(R.layout.dialog_save_preset, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    view.button_cancel.setOnClickListener {
      dismiss()
    }

    view.button_save.setOnClickListener {
      val name = view.edit_name.text.toString()
      if (name.isBlank()) {
        view.layout_edit_name.error = getString(R.string.preset_name_cannot_be_empty)
      } else {
        preset.name = name
        PresetFragment.Preset.appendToUserPreferences(context!!, preset)
        targetFragment!!.onActivityResult(targetRequestCode, Activity.RESULT_OK, null)
        dismiss()
      }
    }
  }
}
