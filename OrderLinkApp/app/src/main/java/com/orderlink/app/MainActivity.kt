package com.orderlink.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.orderlink.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

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
        menu?.findItem(R.id.action_change_link)?.isVisible = (binding.webView.visibility == View.VISIBLE)
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun showWebViewAndLoad(url: String) {
        binding.urlInputSection.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        invalidateOptionsMenu()

        binding.webView.apply {
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                loadWithOverviewMode = true
                useWideViewPort = true
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
