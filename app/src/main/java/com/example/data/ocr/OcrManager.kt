package com.example.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrManager(private val context: Context) {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): Result<String> = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        processInputImage(image)
    }

    suspend fun recognizeTextFromUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open image stream"))
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) return@withContext Result.failure(Exception("Cannot decode image"))

            val image = InputImage.fromBitmap(bitmap, 0)
            val result = processInputImage(image)
            bitmap.recycle()
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun processInputImage(image: InputImage): Result<String> =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text
                    continuation.resume(Result.success(text))
                }
                .addOnFailureListener { error ->
                    continuation.resume(Result.failure(error))
                }
        }

    suspend fun saveTextToFile(text: String, fileName: String = "ocr_result_${System.currentTimeMillis()}.txt"): File =
        withContext(Dispatchers.IO) {
            val docsDir = File(context.filesDir, "ocr_exports").apply { mkdirs() }
            val file = File(docsDir, fileName)
            FileOutputStream(file).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
            }
            file
        }
}
