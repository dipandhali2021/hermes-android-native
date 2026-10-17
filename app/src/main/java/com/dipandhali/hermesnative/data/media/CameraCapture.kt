package com.dipandhali.hermesnative.data.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * A fresh destination for `ActivityResultContracts.TakePicture`.
 *
 * The system camera app needs a writable Uri, not a file path; a FileProvider
 * URI in the cache keeps the full-resolution photo readable by this app
 * afterwards, so the capture stages and uploads like any picker result. No
 * CAMERA permission is declared for this — declaring one would turn it into a
 * runtime prerequisite and gate capture behind it for devices that do not
 * need it.
 */
fun newCameraCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
