package com.example.nutrialize

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var errorView: LinearLayout
    private lateinit var retryButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        errorView = findViewById(R.id.errorView)
        retryButton = findViewById(R.id.retryButton)

        // Enable JS and DOM storage
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        // Configure WebViewClient
        webView.webViewClient = object : WebViewClient() {

            // Intercepts Open Food Facts requests, adds proper User-Agent/Accept headers, and injects CORS headers
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val urlString = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)

                if (urlString.contains("openfoodfacts.org")) {
                    try {
                        val url = URL(urlString)
                        val connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.setRequestProperty("User-Agent", "Nutrialize/1.0 (student-project)")
                        connection.setRequestProperty("Accept", "application/json")
                        connection.connectTimeout = 8000
                        connection.readTimeout = 8000

                        val responseCode = connection.responseCode
                        val inputStream = if (responseCode in 200..299) {
                            connection.inputStream
                        } else {
                            connection.errorStream
                        }

                        if (inputStream != null) {
                            val contentType = connection.contentType ?: "application/json"
                            val encoding = connection.contentEncoding ?: "UTF-8"

                            // Add CORS headers so the WebView allows the JavaScript fetch to read the response
                            val responseHeaders = mapOf("Access-Control-Allow-Origin" to "*")

                            return WebResourceResponse(
                                contentType,
                                encoding,
                                responseCode,
                                "OK",
                                responseHeaders,
                                inputStream
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val targetUrl = url ?: return false
                val uri = targetUrl.toUri()
                val host = uri.host ?: ""

                // If the URL belongs to your Vercel app, stay inside WebView
                if (host.contains("nutrialize.vercel.app")) {
                    return false
                }

                // Otherwise, open external links in the phone's default browser
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
                return true
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                // Only act on main frame errors (e.g., total loss of connection)
                if (request.isForMainFrame) {
                    webView.visibility = View.GONE
                    errorView.visibility = View.VISIBLE
                }
            }
        }

        // Retry Button behavior
        retryButton.setOnClickListener {
            errorView.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.reload()
        }

        // Handle Back button navigation inside WebView history
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Load your live Vercel web app
        webView.loadUrl("https://nutrialize.vercel.app")
    }
}