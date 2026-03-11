package com.orderlink.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.orderlink.app.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var lastCameraFile: File? = null

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && lastCameraFile != null && lastCameraFile!!.exists()) {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                lastCameraFile!!
            )
            filePathCallback?.onReceiveValue(arrayOf(uri))
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
        lastCameraFile = null
    }

    private val takePictureWithIntentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && lastCameraFile != null && lastCameraFile!!.exists()) {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                lastCameraFile!!
            )
            filePathCallback?.onReceiveValue(arrayOf(uri))
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
        lastCameraFile = null
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openCameraForPhoto()
        } else {
            Toast.makeText(this, getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    private val getContentLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            filePathCallback?.onReceiveValue(arrayOf(uri))
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    companion object {
        private const val PREFS_NAME = "OrderLinkPrefs"
        private const val KEY_SAVED_URL = "saved_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val savedUrl = getSavedUrl()

        if (savedUrl.isNullOrBlank()) {
            showUrlInputScreen()
        } else {
            showWebViewAndLoad(savedUrl)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.action_change_link)?.isVisible = false
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_change_link) {
            clearSavedUrl()
            binding.urlEditText.text.clear()
            showUrlInputScreen()
            invalidateOptionsMenu()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showUrlInputScreen() {
        binding.urlInputSection.visibility = View.VISIBLE
        binding.webView.visibility = View.GONE
        supportActionBar?.show()
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        invalidateOptionsMenu()

        binding.saveButton.setOnClickListener {
            val url = binding.urlEditText.text.toString().trim()
            if (url.isBlank()) {
                Toast.makeText(this, getString(R.string.link_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val validUrl = ensureHttps(url)
            if (validUrl == null) {
                Toast.makeText(this, getString(R.string.invalid_link), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveUrl(validUrl)
            showWebViewAndLoad(validUrl)
        }
    }

    private fun ensureHttps(input: String): String? {
        var u = input.trim()
        if (u.startsWith("http://")) u = "https://" + u.removePrefix("http://")
        else if (!u.startsWith("https://")) u = "https://$u"
        return u.takeIf { it.startsWith("https://") && u.length > 10 }
    }

    private fun handleUrl(requestUrl: String, baseUrl: String): Boolean {
        val scheme = Uri.parse(requestUrl).scheme?.lowercase() ?: ""
        when {
            scheme == "tel" || scheme == "mailto" || scheme == "sms" -> return openExternalUrl(requestUrl)
            scheme == "whatsapp" || scheme == "wa" -> return openExternalUrl(requestUrl)
            requestUrl.contains("wa.me", ignoreCase = true) || requestUrl.contains("api.whatsapp.com", ignoreCase = true) -> return openExternalUrl(requestUrl)
            scheme == "http" || scheme == "https" -> {
                val baseHost = Uri.parse(getSavedUrl() ?: baseUrl).host ?: ""
                val requestHost = Uri.parse(requestUrl).host ?: ""
                if (requestHost != baseHost) return openExternalUrl(requestUrl)
                return false
            }
            else -> return openExternalUrl(requestUrl)
        }
    }

    private fun openExternalUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase() ?: ""
        val isWhatsApp = url.contains("wa.me", ignoreCase = true) ||
            url.contains("api.whatsapp.com", ignoreCase = true) ||
            scheme == "whatsapp" || scheme == "wa"
        val intent = when {
            scheme == "tel" -> Intent(Intent.ACTION_DIAL, uri)
            scheme == "mailto" -> Intent(Intent.ACTION_SENDTO, uri)
            scheme == "sms" -> Intent(Intent.ACTION_VIEW, uri)
            isWhatsApp -> Intent(Intent.ACTION_VIEW, Uri.parse(url))
            else -> Intent(Intent.ACTION_VIEW, uri)
        }
        return try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                startActivity(Intent.createChooser(intent, null))
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    private fun showFileChooserOptions() {
        val options = arrayOf(
            getString(R.string.camera),
            getString(R.string.gallery)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_photo))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            openCameraForPhoto()
                        } else {
                            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                    1 -> getContentLauncher.launch("image/*")
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
            }
            .setOnCancelListener {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
            }
            .show()
    }

    private fun openCameraForPhoto() {
        if (filePathCallback == null) return
        try {
            val photoFile = File(
                cacheDir,
                "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
            )
            photoFile.parentFile?.mkdirs()
            photoFile.createNewFile()
            lastCameraFile = photoFile
            val photoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (intent.resolveActivity(packageManager) != null) {
                takePictureWithIntentLauncher.launch(intent)
            } else {
                Toast.makeText(this, getString(R.string.camera_error), Toast.LENGTH_SHORT).show()
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
                lastCameraFile = null
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.camera_error), Toast.LENGTH_SHORT).show()
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            lastCameraFile = null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showWebViewAndLoad(url: String) {
        binding.urlInputSection.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        supportActionBar?.hide()
        invalidateOptionsMenu()

        binding.webView.apply {
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val requestUrl = request?.url?.toString() ?: return false
                    return handleUrl(requestUrl, url)
                }

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, urlStr: String?): Boolean {
                    if (urlStr != null) return handleUrl(urlStr, url)
                    return false
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    if (this@MainActivity.filePathCallback != null) {
                        this@MainActivity.filePathCallback?.onReceiveValue(null)
                    }
                    this@MainActivity.filePathCallback = filePathCallback
                    showFileChooserOptions()
                    return true
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }
            }
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                loadWithOverviewMode = true
                useWideViewPort = true
                allowFileAccess = true
                allowContentAccess = true
                javaScriptCanOpenWindowsAutomatically = true
            }
            loadUrl(url)
        }
    }

    private fun getSavedUrl(): String? {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_SAVED_URL, null)
    }

    private fun saveUrl(url: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_SAVED_URL, url)
            .apply()
        invalidateOptionsMenu()
    }

    private fun clearSavedUrl() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .remove(KEY_SAVED_URL)
            .apply()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.visibility == View.VISIBLE && binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
