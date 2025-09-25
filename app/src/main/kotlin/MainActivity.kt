package org.righteffort.openvpnscheduler

import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var urlEditText: EditText
    private lateinit var downloadButton: Button
    private lateinit var filesContainer: LinearLayout
    
    companion object {
        private const val PREFS_NAME = "TextDownloaderPrefs"
        private const val KEY_LAST_URL = "last_url"
        private const val DOWNLOADS_DIR = "downloads"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        urlEditText = findViewById(R.id.urlEditText)
        downloadButton = findViewById(R.id.downloadButton)
        filesContainer = findViewById(R.id.filesContainer)
        
        downloadButton.setOnClickListener {
            val urlString = urlEditText.text.toString().trim()
            if (urlString.isNotEmpty()) {
                downloadFile(urlString)
            } else {
                Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show()
            }
        }
        
        loadSavedUrl()
        refreshFilesList()
    }
    
    private fun downloadFile(urlString: String) {
        lifecycleScope.launch {
            try {
                saveUrl(urlString)
                Toast.makeText(this@MainActivity, "Starting download...", Toast.LENGTH_SHORT).show()
                
                val result = withContext(Dispatchers.IO) {
                    downloadFileFromUrl(urlString)
                }
                
                Toast.makeText(this@MainActivity, "Download completed: ${result.name}", Toast.LENGTH_LONG).show()
                refreshFilesList()
                
            } catch (e: Exception) {
                showErrorDialog("Download Failed", getErrorMessage(e))
            }
        }
    }
    
    private suspend fun downloadFileFromUrl(urlString: String): File {
        val url = try {
            URL(urlString)
        } catch (e: MalformedURLException) {
            throw Exception("Invalid URL format")
        }
        
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 60000
        connection.readTimeout = 60000
        connection.requestMethod = "GET"
        
        try {
            connection.connect()
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP error: $responseCode ${connection.responseMessage}")
            }
            
            val contentType = connection.contentType?.lowercase()
            if (contentType != null && !contentType.startsWith("text/")) {
                throw Exception("File is not a text file (Content-Type: $contentType)")
            }
            
            val downloadsDir = File(filesDir, DOWNLOADS_DIR)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            // Delete any existing files in downloads directory
            downloadsDir.listFiles()?.forEach { it.delete() }
            
            val fileName = generateFileName(urlString)
            val file = File(downloadsDir, fileName)
            
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            return file
            
        } catch (e: SocketTimeoutException) {
            throw Exception("Download timed out after 60 seconds")
        } catch (e: IOException) {
            throw Exception("Network error: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }
    
    private fun generateFileName(urlString: String): String {
        val url = URL(urlString)
        val path = url.path
        
        val fileName = when {
            // If URL ends with slash, use the component before the final slash
            path.endsWith("/") && path.length > 1 -> {
                val pathWithoutTrailingSlash = path.dropLast(1)
                val lastSegment = pathWithoutTrailingSlash.substringAfterLast("/")
                if (lastSegment.isNotEmpty()) lastSegment else url.host ?: "downloaded_file"
            }
            // If path is not empty and doesn't end with slash
            path.isNotEmpty() && path != "/" -> {
                val lastSegment = path.substringAfterLast("/")
                if (lastSegment.isNotEmpty()) lastSegment else "downloaded_file"
            }
            // Default case
            else -> url.host ?: "downloaded_file"
        }
	return fileName
    }
    
    private fun refreshFilesList() {
        filesContainer.removeAllViews()
        
        val downloadsDir = File(filesDir, DOWNLOADS_DIR)
        val files = if (downloadsDir.exists()) {
            downloadsDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else {
            emptyList()
        }
        
        if (files.isEmpty()) {
            val textView = TextView(this)
            textView.text = "No files downloaded yet"
            textView.setPadding(16, 16, 16, 16)
            filesContainer.addView(textView)
        } else {
            files.forEach { file ->
                val fileView = createFileView(file)
                filesContainer.addView(fileView)
            }
        }
    }
    
    private fun createFileView(file: File): LinearLayout {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.HORIZONTAL
        container.setPadding(16, 8, 16, 8)
        
        val textView = TextView(this)
        textView.text = file.name
        textView.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        
        val viewButton = Button(this)
        viewButton.text = "View"
        viewButton.setOnClickListener {
            viewFile(file)
        }
        
        val deleteButton = Button(this)
        deleteButton.text = "Delete"
        deleteButton.setOnClickListener {
            deleteFile(file)
        }
        
        container.addView(textView)
        container.addView(viewButton)
        container.addView(deleteButton)
        
        return container
    }
    
    private fun viewFile(file: File) {
        try {
            val content = file.readText()
            
            val dialog = AlertDialog.Builder(this)
                .setTitle(file.name)
                .setMessage(content)
                .setPositiveButton("OK", null)
                .create()
            
            dialog.show()
            
        } catch (e: Exception) {
            showErrorDialog("Error Reading File", "Could not read file: ${e.message}")
        }
    }
    
    private fun deleteFile(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete File")
            .setMessage("Are you sure you want to delete ${file.name}?")
            .setPositiveButton("Delete") { _, _ ->
                if (file.delete()) {
                    Toast.makeText(this, "File deleted", Toast.LENGTH_SHORT).show()
                    refreshFilesList()
                } else {
                    Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun getErrorMessage(exception: Exception): String {
        return when (exception) {
            is SocketTimeoutException -> "Download timed out. Please check your internet connection and try again."
            is IOException -> "Network error occurred. Please check your internet connection."
            else -> exception.message ?: "An unknown error occurred"
        }
    }
    
    private fun showErrorDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun saveUrl(url: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_URL, url).apply()
    }
    
    private fun loadSavedUrl() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUrl = prefs.getString(KEY_LAST_URL, "")
        if (!savedUrl.isNullOrEmpty()) {
            urlEditText.setText(savedUrl)
        }
    }
}
