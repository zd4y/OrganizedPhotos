package io.github.zd4y.organizedphotos

import android.content.ContentValues
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Date

private const val TAG = "MainActivity"
private const val APP_DIR = "OrganizedPhotos"
private const val DEFAULT_DIR = "No folder"

class MainActivity : AppCompatActivity() {
    private lateinit var takePicture: ActivityResultLauncher<Uri>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
        takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            val message = if (saved) {
                "Image saved successfully"
            } else {
                "Image was not saved"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        dispatchTakePictureIntent()
    }

    private fun dispatchTakePictureIntent() {
        takePicture.launch(createImageFile())
    }

    private fun createImageFile(): Uri? {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val resolver = applicationContext.contentResolver

        val imagesCollection = getImageCollection()

        val newImageDetails = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "JPEG_$timeStamp.jpg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$APP_DIR/$DEFAULT_DIR")
        }

        return resolver.insert(imagesCollection, newImageDetails)
    }

    private fun getImageCollection(): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return MediaStore.Images.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
        }
        return MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
}