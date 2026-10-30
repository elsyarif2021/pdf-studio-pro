package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun readStringFromContext() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("PDF Studio Pro", appName)
  }

  @Test
  fun testDocTypeDetermination() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val parser = com.example.data.reader.DocumentContentParser(context)
    assertEquals(com.example.data.reader.DocType.PDF, parser.determineDocType("sample.pdf", "application/pdf"))
    assertEquals(com.example.data.reader.DocType.WORD, parser.determineDocType("laporan.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
    assertEquals(com.example.data.reader.DocType.WORD, parser.determineDocType("dokumen_lama.doc", "application/msword"))
    assertEquals(com.example.data.reader.DocType.PPT, parser.determineDocType("presentasi.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
    assertEquals(com.example.data.reader.DocType.TXT, parser.determineDocType("catatan.txt", "text/plain"))
    assertEquals(com.example.data.reader.DocType.MARKDOWN, parser.determineDocType("README.md", "text/markdown"))
    assertEquals(com.example.data.reader.DocType.IMAGE, parser.determineDocType("foto.jpg", "image/jpeg"))
    assertEquals(com.example.data.reader.DocType.IMAGE, parser.determineDocType("diagram.png", "image/png"))
  }
}
