package com.github.ashutoshgngwr.noice.widget

import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.R
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MaterialSwitchFixTest {

  private lateinit var switch: MaterialSwitchFix

  @Before
  fun setUp() {
    switch = MaterialSwitchFix(
      ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_App)
    )
  }

  @Test
  fun setChecked() {
    val mockListener = mockk<OnCheckedChangeListener>(relaxed = true)
    switch.setOnCheckedChangeListener(mockListener)
    switch.performClick()
    verify(exactly = 1) { mockListener.onCheckedChanged(any(), switch.isChecked) }

    clearMocks(mockListener)
    switch.setChecked(checked = !switch.isChecked, notifyListener = false)
    verify(exactly = 0) { mockListener.onCheckedChanged(any(), any()) }

    switch.setChecked(checked = !switch.isChecked, notifyListener = true)
    verify(exactly = 1) { mockListener.onCheckedChanged(any(), switch.isChecked) }
  }
}
