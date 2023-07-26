package io.github.zd4y.organizedphotos

import android.content.ContentValues
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Date

private const val TAG = "MainActivity"
private const val APP_DIR = "OrganizedPhotos"
private const val DEFAULT_DIR = "No folder"

class MainActivity : AppCompatActivity() {
    private lateinit var rvFolders: RecyclerView
    private lateinit var searchView: SearchView
    private lateinit var tvNewFolder: TextView
    private lateinit var takePicture: ActivityResultLauncher<Uri>
    private var folders: MutableList<Folder> = mutableListOf()
    private lateinit var folderAdapter: FolderAdapter
    private var lastSavedImage: Uri? = null
    private var searchText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        searchView = findViewById(R.id.searchView)
        searchView.isIconifiedByDefault = false

        tvNewFolder = findViewById(R.id.tvNewFolder)
        tvNewFolder.setOnClickListener {
            onAddNewFolder()
        }

        rvFolders = findViewById(R.id.rvFolders)
        rvFolders.layoutManager = LinearLayoutManager(this)
        folderAdapter = FolderAdapter(folders, this::onFolderClick)
        rvFolders.adapter = folderAdapter

        searchView.setOnQueryTextListener(object : View.OnFocusChangeListener,
            SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(p0: String?): Boolean {
                return false
            }

            override fun onFocusChange(p0: View?, p1: Boolean) {
            }

            override fun onQueryTextChange(text: String?): Boolean {
                Log.i(TAG, "onQueryTextChange $text")
                if (text.isNullOrEmpty()) {
                    searchText = ""
                    tvNewFolder.visibility = View.GONE
                } else {
                    searchText = text
                    tvNewFolder.visibility = View.VISIBLE
                }
                updateFolderList()
                return false
            }
        })


        takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            if (saved) {
                updateFolderList()
                searchView.requestFocus()
            } else {
                Toast.makeText(this, "Image was not saved", Toast.LENGTH_SHORT).show()
            }

        }

        launchTakePicture()
    }

    private fun launchTakePicture() {
        takePicture.launch(createImageFile())
    }

    private fun updateFolderList() {
        val newFolders = getAvailableFolders().map { f -> Folder(f) }
        folders.clear()
        folders.addAll(newFolders)
        folderAdapter.notifyDataSetChanged()
    }

    private fun onFolderClick(folder: Folder) {
        val updatedImageDetails = ContentValues().apply {
            put(MediaStore.Images.Media.RELATIVE_PATH, getRelativePath(folder.name))
        }
        val resolver = applicationContext.contentResolver
        lastSavedImage?.let { uri ->
            val numImagesUpdated = resolver.update(
                uri,
                updatedImageDetails,
                null,
                null
            )

            when (numImagesUpdated) {
                0 -> Toast.makeText(
                    this,
                    "A problem occurred moving the image to that folder",
                    Toast.LENGTH_SHORT
                ).show()

                1 -> Toast.makeText(this, "Image saved successfully", Toast.LENGTH_SHORT).show()
                else -> Toast.makeText(
                    this,
                    "More than one image modified? This shouldn't happen...",
                    Toast.LENGTH_SHORT
                ).show()
            }
            lastSavedImage = null
            launchTakePicture()
        }
    }

    private fun onAddNewFolder() {
        if (searchText.isNotEmpty()) {
            onFolderClick(Folder(searchText))
        }
        searchText = ""
        searchView.setQuery("", false)
    }

    private fun getAvailableFolders(): List<String> {
        val resolver = applicationContext.contentResolver
        val collection = getImageCollection()
        val projection =
            arrayOf(MediaStore.Images.Media.RELATIVE_PATH)
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs =
            arrayOf(Environment.DIRECTORY_PICTURES + File.pathSeparator + APP_DIR + File.pathSeparator + searchText + "%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val query = resolver.query(collection, projection, selection, selectionArgs, sortOrder)

        val folders = mutableListOf<String>()
        query?.use { cursor ->
            val pc = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val p = cursor.getString(pc)
                val folder =
                    p.removePrefix(Environment.DIRECTORY_PICTURES + File.pathSeparator + APP_DIR + File.pathSeparator)
                        .removeSuffix(File.pathSeparator)
                if (!folders.contains(folder)) {
                    folders.add(folder)
                }
            }
        }

        return folders
    }

    private fun createImageFile(): Uri? {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val resolver = applicationContext.contentResolver

        val imagesCollection = getImageCollection()

        val newImageDetails = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "JPEG_$timeStamp.jpg")
            put(MediaStore.Images.Media.RELATIVE_PATH, getRelativePath(DEFAULT_DIR))
        }

        lastSavedImage = resolver.insert(imagesCollection, newImageDetails)
        return lastSavedImage
    }

    private fun getImageCollection(): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return MediaStore.Images.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
        }
        return MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    private fun getRelativePath(folder: String): String {
        return Environment.DIRECTORY_PICTURES + File.pathSeparator + APP_DIR + File.pathSeparator + folder
    }
}