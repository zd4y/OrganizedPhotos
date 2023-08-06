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
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.nio.file.Files
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
    private var lastSavedImage: Uri? = null // only for Android Q or greater
    private var lastSavedImageFile: File? = null // only for lower than Android Q
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
                Toast.makeText(this, "Image was not saved", Toast.LENGTH_LONG).show()
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

    private fun onAddNewFolder() {
        if (searchText.isNotEmpty()) {
            onFolderClick(Folder(searchText))
        }
        searchText = ""
        searchView.setQuery("", false)
    }

    private fun onFolderClick(folder: Folder) {
        val numImagesUpdated = moveLastSavedImage(folder)
        when (numImagesUpdated) {
            0 -> Toast.makeText(
                this,
                "A problem occurred moving the image to that folder",
                Toast.LENGTH_LONG
            ).show()

            1 -> Toast.makeText(this, "Image saved successfully", Toast.LENGTH_SHORT).show()
            else -> Toast.makeText(
                this,
                "More than one image modified? This shouldn't happen...",
                Toast.LENGTH_LONG
            ).show()
        }
        lastSavedImage = null
        lastSavedImageFile = null
        launchTakePicture()
    }

    private fun moveLastSavedImage(to: Folder): Int {
        var numImagesUpdated = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val updatedImageDetails = ContentValues().apply {
                put(MediaStore.Images.Media.RELATIVE_PATH, getRelativePath(to.name))
            }
            val resolver = applicationContext.contentResolver
            lastSavedImage?.let { uri ->
                numImagesUpdated = resolver.update(
                    uri,
                    updatedImageDetails,
                    null,
                    null
                )
            }
        } else {
            val imageDir = getRelativePath(to.name)
            File(imageDir).mkdirs()
            lastSavedImageFile?.let { saved ->
                val image = File(imageDir, saved.name)
                saved.renameTo(image)
                numImagesUpdated = 1
            }
        }

        return numImagesUpdated
    }

    private fun getAvailableFolders(): List<String> {
        val folders = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = applicationContext.contentResolver
            val collection = getImageCollection()
            val projection =
                arrayOf(MediaStore.Images.Media.RELATIVE_PATH)
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs =
                arrayOf(Environment.DIRECTORY_PICTURES + "/" + APP_DIR + "/" + searchText + "%")
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            val query = resolver.query(collection, projection, selection, selectionArgs, sortOrder)

            query?.use { cursor ->
                val pc = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val p = cursor.getString(pc)
                    val folder =
                        p.removePrefix(Environment.DIRECTORY_PICTURES + "/" + APP_DIR + "/")
                            .removeSuffix("/")
                    if (!folders.contains(folder)) {
                        folders.add(folder)
                    }
                }
            }
        } else {
            val files = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    .toString() + "/" + APP_DIR + "/"
            ).listFiles()
            val search = searchText.lowercase()
            for (file in files.sortedBy { file -> file.lastModified() }.reversed()) {
                if (file.isDirectory) {
                    if (searchText.isNotEmpty() && !file.name.lowercase().contains(search)) {
                        continue
                    }
                    folders.add(file.name)
                }
            }
        }

        return folders
    }

    private fun createImageFile(): Uri? {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val fileName = "JPEG_$timeStamp.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = applicationContext.contentResolver

            val imagesCollection = getImageCollection()

            val newImageDetails = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.RELATIVE_PATH, getRelativePath(DEFAULT_DIR))
            }

            lastSavedImage = resolver.insert(imagesCollection, newImageDetails)
            lastSavedImageFile = null
            return lastSavedImage
        } else {
            val imageDir = getRelativePath(DEFAULT_DIR)
            File(imageDir).mkdirs()
            val newLastSavedImageFile = File(imageDir, fileName)
            newLastSavedImageFile.createNewFile()
            lastSavedImageFile = newLastSavedImageFile
            lastSavedImage = null

            return FileProvider.getUriForFile(
                this,
                "io.github.zd4y.organizedphotos.fileprovider",
                newLastSavedImageFile
            )
        }
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
        val path = "$APP_DIR/$folder"
        val picturesDir =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Environment.DIRECTORY_PICTURES else Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            ).toString()
        return "$picturesDir/$path"
    }
}