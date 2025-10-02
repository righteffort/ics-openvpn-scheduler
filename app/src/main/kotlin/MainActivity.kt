package org.righteffort.vpnscheduler

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), PermissionHandler {
    private lateinit var urlEditText: EditText
    private lateinit var downloadButton: Button
    private lateinit var loadConfigButton: Button
    private lateinit var showLogsButton: Button
    private lateinit var filesContainer: LinearLayout
    private var currentSchedule: ScheduleStore? = null
    private lateinit var remoteVpn: RemoteVpn

    companion object {
        private const val PREFS_NAME = "TextDownloaderPrefs"
        private const val KEY_LAST_URL = "last_url"
        private const val DOWNLOADS_DIR = "downloads"
        private const val TAG = "VPNScheduler"
    }

    override fun registerForActivityResult(
        contract: ActivityResultContracts.StartActivityForResult,
        callback: (androidx.activity.result.ActivityResult) -> Unit
    ): ActivityResultLauncher<Intent> {
        return super.registerForActivityResult(contract, callback)
    }

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { loadConfigFromUri(it) }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.init(this)
        setContentView(R.layout.activity_main)

        urlEditText = findViewById(R.id.urlEditText)
        downloadButton = findViewById(R.id.downloadButton)
        loadConfigButton = findViewById(R.id.loadConfigButton)
        showLogsButton = findViewById(R.id.showLogsButton)
        filesContainer = findViewById(R.id.filesContainer)

        downloadButton.setOnClickListener {
            val urlString = urlEditText.text.toString().trim()
            if (urlString.isNotEmpty()) {
                downloadFile(urlString)
            } else {
                Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show()
            }
        }

        loadConfigButton.setOnClickListener {
            filePickerLauncher.launch("text/csv")
        }

        showLogsButton.setOnClickListener {
            showLogsDialog()
        }

        loadSavedUrl()
        loadSavedConfig()
        refreshFilesList()

        // TODO: this seems wrong, how about instead we provide MainApplication's remoteVpn with ourselves as permissionHandler ? (and maybe retract when we lose focus?)
        remoteVpn = RemoteVpn(this, this)

        // Set up callback to enable button when service is ready
        remoteVpn.onServiceReady = {
            runOnUiThread {
                val msg = "OpenVPN service ready"
                Logger.i(TAG, msg)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                schedulePeriodicCheck()
                // TODO also check right now?
            }
        }
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData("vpn_schedule_check")
            .observe(this) { workInfos ->
                workInfos?.forEach { workInfo ->
                    Logger.d(TAG, "Work status: ${workInfo.state}, tags: ${workInfo.tags}")
                    if (workInfo.state.isFinished) {
                        Logger.d(TAG, "Work finished with result: ${workInfo.outputData}")
                    }
                }
            }
    }

    private fun schedulePeriodicCheck() {
        // Always schedule the work - let the worker handle schedule presence/absence
        // TODO hardcoded short interval
        val workRequest = PeriodicWorkRequestBuilder<UpdateVpnWorker>(
            20,
            TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "vpn_schedule_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        Logger.i(TAG, "Scheduled periodic VPN checks every 20 minutes")
    }

    // override fun onStart() {
    //     super.onStart()
    //     if (!remoteVpn.bindService()) {
    //         val msg = "Failed to bind to OpenVPN service"
    //         Logger.e(TAG, msg)
    //         Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    //     }
    // }

    // override fun onStop() {
    //     super.onStop()
    //     // seems like a bad idea remoteVpn.unbindService()
    // }

    private fun downloadFile(urlString: String) {
        lifecycleScope.launch {
            try {
                saveUrl(urlString)
                Toast.makeText(this@MainActivity, "Starting download...", Toast.LENGTH_SHORT).show()

                val result = withContext(Dispatchers.IO) {
                    downloadFileFromUrl(urlString)
                }

                Toast.makeText(
                    this@MainActivity,
                    "Download completed: ${result.name}",
                    Toast.LENGTH_LONG
                ).show()
                refreshFilesList()

            } catch (e: Exception) {
                showErrorDialog("Download Failed", getErrorMessage(e))
            }
        }
    }

    private fun downloadFileFromUrl(urlString: String): File {
        val url = try {
            URL(urlString)
        } catch (_: MalformedURLException) {
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

            downloadsDir.listFiles()?.forEach { it.delete() }

            val fileName = generateFileName(urlString)
            val file = File(downloadsDir, fileName)

            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            return file

        } catch (_: SocketTimeoutException) {
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
            path.endsWith("/") && path.length > 1 -> {
                val pathWithoutTrailingSlash = path.dropLast(1)
                val lastSegment = pathWithoutTrailingSlash.substringAfterLast("/")
                lastSegment.ifEmpty { url.host ?: "downloaded_file" }
            }

            path.isNotEmpty() && path != "/" -> {
                val lastSegment = path.substringAfterLast("/")
                lastSegment.ifEmpty { "downloaded_file" }
            }

            else -> url.host ?: "downloaded_file"
        }
        return fileName
    }

    private fun refreshFilesList() {
        lifecycleScope.launch(Dispatchers.IO) {
            val downloadsDir = File(filesDir, DOWNLOADS_DIR)
            val files = if (downloadsDir.exists()) {
                downloadsDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
            } else {
                emptyList()
            }

            withContext(Dispatchers.Main) {
                filesContainer.removeAllViews()

                if (files.isEmpty()) {
                    val textView = TextView(this@MainActivity)
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
        }
    }

    private fun createFileView(file: File): LinearLayout {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.HORIZONTAL
        container.setPadding(16, 8, 16, 8)

        val textView = TextView(this)
        textView.text = file.name
        textView.layoutParams =
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

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
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val content = file.readText()

                withContext(Dispatchers.Main) {
                    val dialog = AlertDialog.Builder(this@MainActivity)
                        .setTitle(file.name)
                        .setMessage(content)
                        .setPositiveButton("OK", null)
                        .create()

                    dialog.show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showErrorDialog("Error Reading File", "Could not read file: ${e.message}")
                }
            }
        }
    }

    private fun deleteFile(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete File")
            .setMessage("Are you sure you want to delete ${file.name}?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val success = file.delete()
                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(this@MainActivity, "File deleted", Toast.LENGTH_SHORT)
                                .show()
                            refreshFilesList()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Failed to delete file",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
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
        lifecycleScope.launch(Dispatchers.IO) {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit {
                putString(KEY_LAST_URL, url)
            }
        }
    }

    private fun loadSavedUrl() {
        lifecycleScope.launch(Dispatchers.IO) {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val savedUrl = prefs.getString(KEY_LAST_URL, "")
            if (!savedUrl.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    urlEditText.setText(savedUrl)
                }
            }
        }
    }

    private fun loadConfigFromUri(uri: Uri) {
        Logger.d(TAG, "Planning to open $uri")
        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().readText()
                    } ?: throw IOException("Could not read file")
                }

                val schedule = ScheduleStore.fromCsv(content)
                currentSchedule = schedule

                // Update the application's schedule store
                val app = MainApplication.getInstance()
                app.updateScheduleStore(schedule)

                Toast.makeText(
                    this@MainActivity,
                    "Configuration loaded successfully",
                    Toast.LENGTH_LONG
                ).show()

            } catch (e: Exception) {
                showErrorDialog(
                    "Configuration Load Failed",
                    "Failed to load configuration: ${e.message}"
                )
            }
        }
    }


    private fun loadSavedConfig() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Get schedule from application instead of loading from file
                val app = MainApplication.getInstance()
                val schedule = app.scheduleStore
                if (schedule != null) {
                    withContext(Dispatchers.Main) {
                        currentSchedule = schedule

                        Toast.makeText(
                            this@MainActivity,
                            "Saved configuration loaded",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                val msg = "Failed to load saved configuration: ${e.message}"
                Logger.e(TAG, msg)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showLogsDialog() {
        val logs = Logger.getLogs()

        // Create a TextView that's focusable and scrollable with D-pad
        val textView = TextView(this).apply {
            text = logs.ifEmpty { "No logs available" }
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 14f
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
            isFocusable = true
            isFocusableInTouchMode = true

            // Enable D-pad scrolling
            movementMethod = android.text.method.ScrollingMovementMethod()
            isVerticalScrollBarEnabled = true
            maxLines = Integer.MAX_VALUE

            // Make it easier to see focus on TV
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    setBackgroundColor(resources.getColor(android.R.color.holo_blue_dark, null))
                } else {
                    setBackgroundColor(resources.getColor(android.R.color.transparent, null))
                }
            }
        }

        val scrollView = ScrollView(this).apply {
            addView(textView)
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Application Logs (Use D-pad ↑↓ to scroll)")
            .setView(scrollView)
            .setPositiveButton("Close") { _, _ -> }
            .setNeutralButton("Clear Logs") { _, _ ->
                Logger.clearLogs()
                Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
            }
            .create()

        dialog.show()

        // Make the dialog TV-friendly
        dialog.window?.apply {
            setLayout(
                (resources.displayMetrics.widthPixels * 0.95).toInt(),
                (resources.displayMetrics.heightPixels * 0.85).toInt()
            )

            // Ensure the text view gets focus for D-pad navigation
            decorView.post {
                textView.requestFocus()
            }
        }
    }
}
