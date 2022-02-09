package com.github.ashutoshgngwr.noice.fragment

import android.annotation.SuppressLint
import android.content.DialogInterface
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
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.TextViewCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.DialogFragmentBaseBinding
import com.github.ashutoshgngwr.noice.databinding.DialogFragmentTextInputBinding
import com.github.ashutoshgngwr.noice.widget.MarkdownTextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider


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

  private lateinit var baseBinding: DialogFragmentBaseBinding
  private lateinit var textInputBinding: DialogFragmentTextInputBinding

  private var onDismissListener: (() -> Unit)? = null

  private val viewModel: ViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    baseBinding = DialogFragmentBaseBinding.inflate(inflater, container, false)
    return baseBinding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    viewModel.displayOptions?.invoke(this)
  }

  override fun onDismiss(dialog: DialogInterface) {
    onDismissListener?.invoke()
    super.onDismiss(dialog)
  }

  /**
   * Always called when the dialog is closed.
   */
  fun onDismiss(listener: () -> Unit) {
    onDismissListener = listener
  }

  /**
   * Adds given [View] to the [R.id.content] layout in the dialog
   */
  private fun addView(view: View) {
    baseBinding.content.addView(view)
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
    baseBinding.title.text = getString(resId)
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
    showNow(fragmentManager, this::class.simpleName)
    viewModel.displayOptions = options // save state for configuration changes.

    // manually invoke the callback to apply display options.
    // fragment may be added to the stack before its owner activity is created/visible.
    view?.also { onViewCreated(it, null) }
  }

  /**
   * Creates a [MarkdownTextView] with given string resource and adds it to [R.id.content] layout
   * in the dialog.
   */
  fun message(@StringRes resId: Int, vararg formatArgs: Any) {
    addView(
      MarkdownTextView(requireContext()).apply {
        val textAppearance = android.R.attr.textAppearance.resolveAttributeValue()
        TextViewCompat.setTextAppearance(this, textAppearance)
        setMarkdown(getString(resId, *formatArgs))
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
    textInputBinding =
      DialogFragmentTextInputBinding.inflate(layoutInflater, baseBinding.content, false)
    addView(textInputBinding.root)

    baseBinding.positive.isEnabled = false
    textInputBinding.textInputLayout.hint = getString(hintRes)
    textInputBinding.editText.inputType = type
    textInputBinding.editText.isSingleLine = singleLine
    textInputBinding.editText.setText(preFillValue)
    textInputBinding.editText.addTextChangedListener {
      val errResID = validator(it.toString())
      if (errResID == 0) {
        baseBinding.positive.isEnabled = true
        textInputBinding.textInputLayout.error = ""
      } else {
        baseBinding.positive.isEnabled = false
        textInputBinding.textInputLayout.error = getString(errResID)
      }
    }
  }

  /**
   * returns the text in the text field added using [input]. don't know what it'll do if called
   * without invoking [input] \o/
   */
  fun getInputText(): String {
    if (!this::textInputBinding.isInitialized) {
      throw IllegalStateException("getInputText() called without setting up input field")
    }

    return textInputBinding.editText.text.toString()
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
    addView(
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

  /**
   * Adds a [Slider] to the dialog with given parameters.
   */
  fun slider(
    @IdRes viewID: Int = ResourcesCompat.ID_NULL,
    step: Float = 1.0f,
    from: Float = 0.0f,
    to: Float = -1.0f,
    value: Float = -1.0f,
    labelFormatter: (Float) -> String = { "$it" },
    changeListener: (Float) -> Unit = { },
  ) {
    with(Slider(requireContext())) {
      id = viewID
      stepSize = step
      valueFrom = from
      valueTo = to
      setValue(value)
      setLabelFormatter(labelFormatter)
      addOnChangeListener { _, value, fromUser ->
        if (fromUser) {
          changeListener.invoke(value)
        }
      }

      addView(this)
    }
  }

  /**
   * Since [setRetainInstance] is deprecated, need to persist state in a view model.
   */
  class ViewModel : androidx.lifecycle.ViewModel() {
    /**
     * A lambda for calling functions to configure the dialog, passed while invoking [show].
     */
    internal var displayOptions: (DialogFragment.() -> Unit)? = null
  }
}
