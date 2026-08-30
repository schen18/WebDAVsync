package com.dissonance.webdav

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
  @Test
  fun useAppContext() {
    // Context of the app under test: matches the applicationId from app/build.gradle.kts.
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals("com.dissonance.webdav", appContext.packageName)
  }
}
