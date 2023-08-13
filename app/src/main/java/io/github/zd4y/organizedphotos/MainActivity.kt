package io.github.zd4y.organizedphotos

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
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
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Date

private const val TAG = "MainActivity"
private const val APP_DIR = "OrganizedPhotos"

class MainActivity : AppCompatActivity() {
    private lateinit var rvFolders: RecyclerView
    private lateinit var searchView: SearchView
    private lateinit var tvNewFolder: TextView
    private lateinit var takePicture: ActivityResultLauncher<Uri>
    private var folders: MutableList<Folder> = mutableListOf()
    private lateinit var folderAdapter: FolderAdapter
    private var cacheImageUri: Uri? = null
    private var cacheImageFile: File? = null
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
                Toast.makeText(this, R.string.image_not_saved, Toast.LENGTH_LONG).show()
            }

        }

        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    launchTakePicture()
                } else {
                    finishAffinity()
                }
            }

        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            launchTakePicture()
        }
    }

    private fun launchTakePicture() {
        takePicture.launch(createTempFile())
    }

    private fun createTempFile(): Uri {
        val tempFile = File.createTempFile("picture", ".jpg", externalCacheDir)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", tempFile)
        cacheImageUri = uri
        cacheImageFile = tempFile
        return uri
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
        searchView.setQuery("", false)
    }

    private fun onFolderClick(folder: Folder) {
        val saved = saveImage(folder.name)

        if (saved) {
            Toast.makeText(this, R.string.image_saved_success, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                R.string.image_saved_error,
                Toast.LENGTH_LONG
            ).show()
        }

        launchTakePicture()
    }

    private fun saveImage(folder: String): Boolean {
        val cacheUri = cacheImageUri ?: run {
            return false
        }
        val newImageUri = createImageFile(folder) ?: run {
            return false
        }

        val resolver = applicationContext.contentResolver
        resolver.openInputStream(cacheUri)?.use {cacheStream ->
            resolver.openOutputStream(newImageUri)?.use { newStream ->
                cacheStream.copyTo(newStream)
                cacheImageFile?.delete()
                return true
            }
        }
        return false
    }

    private fun getAvailableFolders(): List<String> {
        val folders = mutableListOf<String>()

        val resolver = applicationContext.contentResolver
        val collection = getImageCollection()
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val qOrGreater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        val column =
            if (qOrGreater) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
        val projection = arrayOf(column)
        val selection = "$column LIKE ?"
        val basePath =
            if (qOrGreater) getRelativePath(null) else getBaseFolderAbsolutePath()
        val selectionArgs =
            arrayOf("$basePath%$searchText%")
        val query = resolver.query(collection, projection, selection, selectionArgs, sortOrder)

        query?.use { cursor ->
            val pc = cursor.getColumnIndexOrThrow(column)

            while (cursor.moveToNext()) {
                val p = cursor.getString(pc)
                val folder = if (qOrGreater) {
                    p.removePrefix(Environment.DIRECTORY_PICTURES + File.separator + APP_DIR + File.separator)
                        .removeSuffix(File.separator)
                } else {
                    File(p).parentFile?.name
                }
                if (folder != null && !folders.contains(folder)) {
                    folders.add(folder)
                }
            }
        }

        return folders
    }

    private fun createImageFile(folder: String): Uri? {
        val fileName = getImageFileName()

        val contentValues = ContentValues()
        contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, fileName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, getRelativePath(folder))
        } else {
            val path = getAbsolutePath(folder, fileName)
            File(path).parentFile?.also {
                if (!it.exists()) {
                    it.mkdirs()
                }
            }
            contentValues.put(MediaStore.Images.Media.DATA, path)
        }

        return applicationContext.contentResolver.insert(getImageCollection(), contentValues)
    }

    private fun getImageCollection(): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return MediaStore.Images.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
        }
        return MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getRelativePath(folder: String?): String {
        var path = APP_DIR + File.separator
        if (folder != null) {
            path += folder
        }
        return Environment.DIRECTORY_PICTURES + File.separator + path
    }

    private fun getAbsolutePath(folder: String, fileName: String): String {
        return StringBuilder(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath)
            .append(File.separator)
            .append(APP_DIR).append(File.separator)
            .append(folder).append(File.separator)
            .append(fileName)
            .toString()
    }

    private fun getBaseFolderAbsolutePath(): String {
        return StringBuilder(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath)
            .append(File.separator)
            .append(APP_DIR).append(File.separator).toString()
    }

    private fun getImageFileName(): String {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        return "JPEG_$timeStamp.jpg"
    }
}