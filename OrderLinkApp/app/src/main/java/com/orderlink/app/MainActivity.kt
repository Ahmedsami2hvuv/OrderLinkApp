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
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.FrameLayout
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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

    private val handler = Handler(Looper.getMainLooper())
    private var threeFingerRunnable: Runnable? = null
    private var twoFingerRunnable: Runnable? = null

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
        private const val ADMIN_WHATSAPP = "9647733921468"
        private const val KEY_FLOAT_X_PERCENT = "float_back_x_percent"
        private const val KEY_FLOAT_Y_PERCENT = "float_back_y_percent"
        private const val DEFAULT_FLOAT_X_PERCENT = 85f
        private const val DEFAULT_FLOAT_Y_PERCENT = 10f
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
        binding.swipeRefresh.visibility = View.GONE
        binding.floatBackButton.visibility = View.GONE
        binding.root.setOnTouchListener(null)
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
        // فتح الكاميرا مباشرة عند طلب الموقع لصورة (بدون سؤال كاميرا أم معرض)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCameraForPhoto()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
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
        binding.swipeRefresh.visibility = View.VISIBLE
        binding.floatBackButton.visibility = View.VISIBLE
        supportActionBar?.hide()
        invalidateOptionsMenu()
        setupFloatingBackButton()

        binding.webView.apply {
            setBackgroundColor(0xFFEEEEEE.toInt())
            isLongClickable = true
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

                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    super.onPageFinished(view, pageUrl)
                    binding.swipeRefresh.isRefreshing = false
                    view?.evaluateJavascript(
                        "(function() {" +
                        "var m=document.querySelector('meta[name=viewport]');" +
                        "var c='width=device-width,initial-scale=0.67,minimum-scale=0.25,maximum-scale=4,user-scalable=yes';" +
                        "if(m){m.setAttribute('content',c);}else{" +
                        "var meta=document.createElement('meta');meta.name='viewport';meta.content=c;document.head.appendChild(meta);}" +
                        "})();",
                        null
                    )
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
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            setInitialScale(67)
            setOnTouchListener(threeFingerLongPressListener)
            post { loadUrl(url) }
        }
        binding.swipeRefresh.setOnRefreshListener {
            binding.webView.reload()
        }
    }

    private fun setupFloatingBackButton() {
        val btn = binding.floatBackButton
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val lp = btn.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.gravity = Gravity.TOP or Gravity.START
        lp.leftMargin = 0
        lp.topMargin = 0
        lp.marginStart = 0
        lp.marginEnd = 0
        btn.layoutParams = lp
        btn.post {
            val parent = btn.parent as? View ?: return@post
            val pw = parent.width
            val ph = parent.height
            val bw = btn.width
            val bh = btn.height
            if (pw <= 0 || ph <= 0) return@post
            val xPercent = prefs.getFloat(KEY_FLOAT_X_PERCENT, DEFAULT_FLOAT_X_PERCENT).coerceIn(0f, 100f)
            val yPercent = prefs.getFloat(KEY_FLOAT_Y_PERCENT, DEFAULT_FLOAT_Y_PERCENT).coerceIn(0f, 100f)
            val x = (pw * xPercent / 100f - bw / 2f).coerceIn(0f, (pw - bw).toFloat())
            val y = (ph * yPercent / 100f - bh / 2f).coerceIn(0f, (ph - bh).toFloat())
            btn.x = x
            btn.y = y
        }
        var dragStartX = 0f
        var dragStartY = 0f
        var viewStartX = 0f
        var viewStartY = 0f
        var isDragging = false
        val dragThreshold = 20
        btn.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    viewStartX = v.x
                    viewStartY = v.y
                    isDragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - dragStartX
                    val dy = event.rawY - dragStartY
                    if (!isDragging && (kotlin.math.abs(dx) > dragThreshold || kotlin.math.abs(dy) > dragThreshold)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val parent = v.parent as? View ?: return@setOnTouchListener true
                        val newX = (viewStartX + dx).coerceIn(0f, (parent.width - v.width).toFloat())
                        val newY = (viewStartY + dy).coerceIn(0f, (parent.height - v.height).toFloat())
                        v.x = newX
                        v.y = newY
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        val parent = v.parent as? View ?: return@setOnTouchListener true
                        if (parent.width > 0 && parent.height > 0) {
                            val centerX = v.x + v.width / 2f
                            val centerY = v.y + v.height / 2f
                            val xPercent = (centerX / parent.width * 100f).coerceIn(0f, 100f)
                            val yPercent = (centerY / parent.height * 100f).coerceIn(0f, 100f)
                            prefs.edit()
                                .putFloat(KEY_FLOAT_X_PERCENT, xPercent)
                                .putFloat(KEY_FLOAT_Y_PERCENT, yPercent)
                                .apply()
                        }
                    } else {
                        if (binding.webView.canGoBack()) {
                            binding.webView.goBack()
                        } else {
                            finish()
                        }
                    }
                }
            }
            true
        }
    }

    private val threeFingerLongPressListener = View.OnTouchListener { _, event ->
        if (event.pointerCount == 1) return@OnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                when (event.pointerCount) {
                    2 -> {
                        threeFingerRunnable?.let { handler.removeCallbacks(it) }
                        threeFingerRunnable = null
                        twoFingerRunnable?.let { handler.removeCallbacks(it) }
                        twoFingerRunnable = Runnable {
                            twoFingerRunnable = null
                            if (binding.swipeRefresh.visibility == View.VISIBLE) {
                                openWhatsAppAdmin()
                            }
                        }
                        handler.postDelayed(twoFingerRunnable!!, 1000)
                    }
                    3 -> {
                        twoFingerRunnable?.let { handler.removeCallbacks(it) }
                        twoFingerRunnable = null
                        threeFingerRunnable?.let { handler.removeCallbacks(it) }
                        threeFingerRunnable = Runnable {
                            threeFingerRunnable = null
                            if (binding.swipeRefresh.visibility == View.VISIBLE) {
                                showChangeLinkDialog()
                            }
                        }
                        handler.postDelayed(threeFingerRunnable!!, 1000)
                    }
                }
                false
            }
            MotionEvent.ACTION_MOVE -> {
                twoFingerRunnable?.let { handler.removeCallbacks(it) }
                twoFingerRunnable = null
                threeFingerRunnable?.let { handler.removeCallbacks(it) }
                threeFingerRunnable = null
                false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.pointerCount <= 1) {
                    twoFingerRunnable?.let { handler.removeCallbacks(it) }
                    twoFingerRunnable = null
                    threeFingerRunnable?.let { handler.removeCallbacks(it) }
                    threeFingerRunnable = null
                }
                false
            }
            else -> false
        }
    }

    private fun openWhatsAppAdmin() {
        val url = "https://wa.me/$ADMIN_WHATSAPP"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            Toast.makeText(this, getString(R.string.opening_admin_whatsapp), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.cannot_open_whatsapp), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showChangeLinkDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.change_link))
            .setMessage(getString(R.string.change_link_confirm))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                clearSavedUrl()
                binding.urlEditText.text.clear()
                showUrlInputScreen()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        if (binding.swipeRefresh.visibility == View.VISIBLE && binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
