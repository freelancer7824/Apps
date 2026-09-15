package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebViewAssetLoader
import java.io.File

/**
 * MainActivity: Production-ready WebView host and HTML5 converter.
 * Features:
 * - Secure local asset loading via WebViewAssetLoader
 * - HTML5 DOM storage, JavaScript execution, and offline cache
 * - Chrome-style thin loading progress bar
 * - SwipeRefreshLayout pull-to-refresh
 * - File chooser & camera capture with FileProvider
 * - Dynamic runtime permissions for Camera and Geolocation
 * - Native JavaScript dialogs (alert, confirm, prompt)
 * - Network state monitoring with offline recovery
 * - Seamless AdMob banner and interstitial integration
 */
class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var rootContainer: View
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var webView: WebView
    private lateinit var bannerContainer: FrameLayout

    private lateinit var assetLoader: WebViewAssetLoader
    private var admobManager: AdmobManager? = null

    // File chooser state
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhotoUri: Uri? = null

    // Geolocation permission callback state
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null

    // Network connectivity monitoring
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Activity Result Launcher for File Chooser & Camera
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (fileUploadCallback == null) return@registerForActivityResult

        val uris = when (result.resultCode) {
            RESULT_OK -> {
                val dataIntent = result.data
                when {
                    dataIntent?.clipData != null -> {
                        val clipData = dataIntent.clipData!!
                        val uriList = ArrayList<Uri>()
                        for (i in 0 until clipData.itemCount) {
                            clipData.getItemAt(i).uri?.let { uriList.add(it) }
                        }
                        uriList.toTypedArray()
                    }
                    dataIntent?.data != null -> {
                        arrayOf(dataIntent.data!!)
                    }
                    cameraPhotoUri != null -> {
                        val file = File(cacheDir, "camera_photo.jpg")
                        if (file.exists() && file.length() > 0) {
                            arrayOf(cameraPhotoUri!!)
                        } else {
                            null
                        }
                    }
                    else -> null
                }
            }
            else -> null
        }

        fileUploadCallback?.onReceiveValue(uris)
        fileUploadCallback = null
    }

    // Geolocation runtime permission launcher
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        pendingGeoCallback?.let { callback ->
            pendingGeoOrigin?.let { origin ->
                callback.invoke(origin, granted, false)
            }
        }
        pendingGeoOrigin = null
        pendingGeoCallback = null
    }

    // Camera runtime permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchFileChooserIntent()
        } else {
            // Still allow file picker even if camera permission is denied
            launchFileOnlyChooserIntent()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install Android 12+ Splash Screen
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        initViews()
        setupWindowInsets()
        setupAssetLoader()
        setupWebView()
        setupSwipeRefresh()
        setupBackNavigation()
        setupAdMob()
        setupNetworkMonitoring()

        loadStartPage()
    }

    private fun initViews() {
        rootContainer = findViewById(R.id.rootContainer)
        progressBar = findViewById(R.id.progressBar)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        webView = findViewById(R.id.webView)
        bannerContainer = findViewById(R.id.bannerContainer)

        rootContainer.setBackgroundColor(Config.WEBVIEW_BACKGROUND_COLOR)
        webView.setBackgroundColor(Config.WEBVIEW_BACKGROUND_COLOR)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            rootContainer.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            bannerContainer.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
    }

    private fun setupAssetLoader() {
        assetLoader = WebViewAssetLoader.Builder()
            .setDomain(Config.ALLOWED_ORIGIN_HOST)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings

        // JavaScript & Storage configuration
        settings.javaScriptEnabled = Config.ENABLE_JAVASCRIPT
        settings.domStorageEnabled = Config.ENABLE_DOM_STORAGE
        settings.databaseEnabled = Config.ENABLE_DATABASE_STORAGE
        settings.allowFileAccess = Config.ALLOW_FILE_ACCESS
        settings.allowContentAccess = true

        // Viewport and scaling
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false

        // Media and mixed content
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        // Cache mode: Prefer offline cache when network is unavailable
        if (Config.ENABLE_OFFLINE_CACHE) {
            settings.cacheMode = if (isNetworkAvailable()) {
                WebSettings.LOAD_DEFAULT
            } else {
                WebSettings.LOAD_CACHE_ELSE_NETWORK
            }
        }

        // WebChromeClient setup
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (Config.ENABLE_PROGRESS_BAR) {
                    if (newProgress in 1..99) {
                        progressBar.visibility = View.VISIBLE
                        progressBar.progress = newProgress
                    } else {
                        progressBar.visibility = View.GONE
                        progressBar.progress = 100
                    }
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (!Config.ENABLE_FILE_UPLOAD) return false

                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                if (Config.ENABLE_CAMERA_CAPTURE) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.CAMERA
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        return true
                    }
                }

                launchFileChooserIntent()
                return true
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (!Config.ENABLE_GEOLOCATION || origin == null || callback == null) {
                    callback?.invoke(origin, false, false)
                    return
                }

                val hasFine = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val hasCoarse = ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (hasFine || hasCoarse) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeoOrigin = origin
                    pendingGeoCallback = callback
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(Config.APP_NAME)
                    .setMessage(message ?: "")
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .create()
                    .show()
                return true
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(Config.APP_NAME)
                    .setMessage(message ?: "")
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .create()
                    .show()
                return true
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?
            ): Boolean {
                val input = EditText(this@MainActivity).apply {
                    setText(defaultValue ?: "")
                    setSelection(text.length)
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(Config.APP_NAME)
                    .setMessage(message ?: "")
                    .setView(input)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        result?.confirm(input.text.toString())
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        result?.cancel()
                    }
                    .setOnCancelListener { result?.cancel() }
                    .create()
                    .show()
                return true
            }
        }

        // WebViewClient setup
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url ?: return null
                return assetLoader.shouldInterceptRequest(url)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url ?: return false
                val scheme = url.scheme ?: ""

                // Handle system schemes (mailto, tel, sms, intent, etc.)
                if (Config.ENABLE_CUSTOM_INTENT_SCHEMES &&
                    (scheme == "tel" || scheme == "mailto" || scheme == "sms" ||
                            scheme == "whatsapp" || scheme == "market" || scheme == "intent")
                ) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, url)
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Cannot launch custom intent scheme for $url: ${e.message}")
                        return true
                    }
                }

                // Handle local asset navigation
                if (url.host == Config.ALLOWED_ORIGIN_HOST) {
                    return false
                }

                // Handle external URLs
                if (Config.OPEN_EXTERNAL_URLS_IN_BROWSER && (scheme == "http" || scheme == "https")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, url)
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Cannot open external browser: ${e.message}")
                    }
                }

                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (Config.ENABLE_PROGRESS_BAR) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = 0
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefreshLayout.isRefreshing = false
                if (Config.ENABLE_PROGRESS_BAR) {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    swipeRefreshLayout.isRefreshing = false
                    logDebug("WebView received error on main frame: ${error?.description}")
                }
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.isEnabled = Config.ENABLE_PULL_TO_REFRESH
        swipeRefreshLayout.setColorSchemeColors(
            Config.THEME_COLOR_PRIMARY,
            Config.THEME_COLOR_ACCENT
        )

        swipeRefreshLayout.setOnRefreshListener {
            webView.reload()
        }

        // Prevent SwipeRefreshLayout from intercepting scroll while scrolling down within WebView
        swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            webView.scrollY > 0
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }
    }

    private fun setupAdMob() {
        admobManager = AdmobManager(this, bannerContainer)
    }

    private fun loadStartPage() {
        val entryUrl = "https://${Config.ALLOWED_ORIGIN_HOST}/assets/${Config.START_PAGE}"
        logDebug("Loading initial entry URL: $entryUrl")
        webView.loadUrl(entryUrl)
    }

    private fun launchFileChooserIntent() {
        val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_TITLE, "Select File or Photo")
        }

        // Base file picker intent
        val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        chooserIntent.putExtra(Intent.EXTRA_INTENT, pickIntent)

        // Add Camera Capture intent if camera is enabled
        if (Config.ENABLE_CAMERA_CAPTURE) {
            try {
                val photoFile = File(cacheDir, "camera_photo.jpg")
                photoFile.delete()
                photoFile.createNewFile()

                cameraPhotoUri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    photoFile
                )

                val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(captureIntent))
            } catch (e: Exception) {
                Log.e(TAG, "Error creating camera capture intent: ${e.message}")
            }
        }

        fileChooserLauncher.launch(chooserIntent)
    }

    private fun launchFileOnlyChooserIntent() {
        val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        val chooserIntent = Intent.createChooser(pickIntent, "Select File")
        fileChooserLauncher.launch(chooserIntent)
    }

    private fun setupNetworkMonitoring() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    logDebug("Internet connection available. Updating cache mode.")
                    mainHandler.post {
                        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
                    }
                }

                override fun onLost(network: Network) {
                    logDebug("Internet connection lost. Switching to offline cache.")
                    mainHandler.post {
                        webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                    }
                }
            }

            networkCallback?.let {
                connectivityManager?.registerNetworkCallback(request, it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering network callback: ${e.message}")
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        admobManager?.onResume()
    }

    override fun onPause() {
        admobManager?.onPause()
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        try {
            networkCallback?.let {
                connectivityManager?.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            // Ignored if already unregistered
        }
        admobManager?.onDestroy()
        webView.destroy()
        super.onDestroy()
    }

    private fun logDebug(message: String) {
        if (Config.DEBUG_LOGGING) {
            Log.d(TAG, message)
        }
    }
}
