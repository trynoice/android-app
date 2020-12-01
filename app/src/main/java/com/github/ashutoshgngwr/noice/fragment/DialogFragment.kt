package com.github.ashutoshgngwr.noice.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.widget.TextViewCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.FragmentManager
import com.github.ashutoshgngwr.noice.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textview.MaterialTextView
import kotlinx.android.synthetic.main.fragment_dialog__base.view.*
import kotlinx.android.synthetic.main.fragment_dialog__text_input.view.*


/**
 * A generic implementation with extensions for use-case specific designs.
 * API inspired by https://github.com/afollestad/material-dialogs but not using it
 * due to its reliance on the old AppCompat API. I tried to make material-dialogs
 * work but it was bringing appearance inconsistencies and was generally rigid in
 * terms of styling.
 */
class DialogFragment : BottomSheetDialogFragment() {

  companion object {
    fun show(fm: FragmentManager, options: DialogFragment.() -> Unit): DialogFragment {
      return DialogFragment().also { it.show(fm, options) }
    }
  }

  /**
   * A lambda for calling functions to configure the dialog, passed while invoking [show].
   */
  private var displayOptions: DialogFragment.() -> Unit = { }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    retainInstance = true // so this instance is retained when screen orientation changes.
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.fragment_dialog__base, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    displayOptions()
  }

  /**
   * Adds given [View] to the [R.id.content] layout in the dialog
   */
  private fun addContentView(view: View) {
    requireView().content.addView(view)
  }

  /**
   * Inflates the given [layoutID] to the [R.id.content] layout in the dialog
   */
  fun addContentView(@LayoutRes layoutID: Int) {
    addContentView(layoutInflater.inflate(layoutID, requireView().content, false))
  }

  /**
   * Configures the given button [R.id.positive] or [R.id.negative] with the given text resId and
   * the onClick listener
   */
  private fun setButton(@IdRes which: Int, @StringRes resId: Int, onClick: () -> Unit) {
    val button = requireView().findViewById<Button>(which)
    button.visibility = View.VISIBLE
    button.text = getString(resId)
    button.setOnClickListener {
      onClick()
      dismiss()
    }
  }

  /**
   * An extension on the attribute resource [R.attr] for resolving its value as set in the current
   * theme.
   */
  private fun @receiver:androidx.annotation.AttrRes Int.resolveAttributeValue(): Int {
    val value = TypedValue()
    requireNotNull(dialog).context.theme.resolveAttribute(this, value, true)
    return value.data
  }

  /**
   * Sets the title of the dialog
   */
  fun title(@StringRes resId: Int) {
    requireView().title.text = getString(resId)
  }

  /**
   * Configures the positive button of the dialog. Wrapper around [setButton]
   */
  fun positiveButton(@StringRes resId: Int, onClick: () -> Unit = { }) {
    setButton(R.id.positive, resId, onClick)
  }

  /**
   * Configures the negative button of the dialog. Wrapper around [setButton]
   */
  fun negativeButton(@StringRes resId: Int, onClick: () -> Unit = { }) {
    setButton(R.id.negative, resId, onClick)
  }

  /**
   * Configures the neutral button of the dialog. Wrapper around [setButton]
   */
  fun neutralButton(@StringRes resId: Int, onClick: () -> Unit = { }) {
    setButton(R.id.neutral, resId, onClick)
  }

  /**
   * shows the dialog and schedules the passed `options` lambda to be invoked in [onViewCreated]
   */
  private fun show(fragmentManager: FragmentManager, options: DialogFragment.() -> Unit = { }) {
    displayOptions = options
    show(fragmentManager, javaClass.simpleName)
  }

  /**
   * Creates a [MaterialTextView] with given string resource and adds it to [R.id.content] layout
   * in the dialog
   */
  fun message(@StringRes resId: Int, vararg formatArgs: Any) {
    addContentView(
      MaterialTextView(requireContext()).apply {
        val textAppearance = android.R.attr.textAppearance.resolveAttributeValue()
        TextViewCompat.setTextAppearance(this, textAppearance)
        text = getString(resId, *formatArgs)
      }
    )
  }

  /**
   * Creates a [com.google.android.material.textfield.TextInputLayout] with given configuration
   * and adds it to [R.id.content] layout
   *
   * @param preFillValue value to pre-fill in the text field
   * @param type input type
   * @param validator a validation function that is called on text every time it is changed. It
   * should return a String resource id to display it as an error. If no error is to be displayed,
   * it should return 0.
   */
  fun input(
    @StringRes hintRes: Int = 0,
    preFillValue: CharSequence = "",
    type: Int = InputType.TYPE_CLASS_TEXT,
    singleLine: Boolean = true,
    validator: (String) -> Int = { 0 }
  ) {
    requireView().positive.isEnabled = false
    addContentView(R.layout.fragment_dialog__text_input)
    requireView().textInputLayout.hint = getString(hintRes)
    requireView().editText.inputType = type
    requireView().editText.isSingleLine = singleLine
    requireView().editText.setText(preFillValue)
    requireView().editText.addTextChangedListener {
      val errResID = validator(it.toString())
      if (errResID == 0) {
        requireView().positive.isEnabled = true
        requireView().textInputLayout.error = ""
      } else {
        requireView().positive.isEnabled = false
        requireView().textInputLayout.error = getString(errResID)
      }
    }
  }

  /**
   * returns the text in the text field added using [input]. don't know what it'll do if called
   * without invoking [input] \o/
   */
  fun getInputText(): String {
    return requireView().editText.text.toString()
  }

  /**
   * creates a single choice list in the dialog with given configuration.
   * Creating this control removes the button panel. Dialog is dismissed when user
   * selects an item from the list.
   *
   * @param currentChoice must be >= -1 and < arraySize
   * @param onItemSelected listener invoked when a choice is selected by the user
   */
  fun singleChoiceItems(
    items: Array<String>,
    currentChoice: Int = -1,
    onItemSelected: (Int) -> Unit = { }
  ) {
    require(currentChoice >= -1 && currentChoice < items.size)
    setButton(R.id.positive, android.R.string.cancel) { }
    addContentView(
      ListView(requireContext()).apply {
        id = android.R.id.list
        dividerHeight = 0
        choiceMode = ListView.CHOICE_MODE_SINGLE
        adapter = ArrayAdapter(context, android.R.layout.simple_list_item_single_choice, items)
        if (currentChoice > -1) {
          setItemChecked(currentChoice, true)
        }

        setOnItemClickListener { _, _, position, _ ->
          onItemSelected(position)
          dismiss()
        }

        setOnTouchListener @SuppressLint("ClickableViewAccessibility") { _, _ ->
          parent.requestDisallowInterceptTouchEvent(canScrollList(-1))
          false
        }
      }
    )
  }
}
