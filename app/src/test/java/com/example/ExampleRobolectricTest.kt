package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Web App Converter", appName)
  }

  @Test
  fun `verify config defaults`() {
    assertEquals("index.html", com.example.Config.START_PAGE)
    assertTrue(com.example.Config.ENABLE_JAVASCRIPT)
    assertTrue(com.example.Config.ENABLE_DOM_STORAGE)
  }
}
