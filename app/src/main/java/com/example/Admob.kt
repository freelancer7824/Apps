package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * AdMob Management Controller.
 * Handles AdMob SDK initialization, banner ads, interstitial ads, offline state,
 * and automatic retries when internet connectivity is re-established.
 *
 * If AdMob IDs in Config.kt are empty, all advertising logic is safely disabled
 * without any overhead or crashes.
 */
class AdmobManager(
    private val activity: ComponentActivity,
    private val bannerContainer: ViewGroup?
) {
    private val TAG = "AdmobManager"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var adView: AdView? = null
    private var interstitialAd: InterstitialAd? = null

    private var isAdMobInitialized = false
    private var isBannerLoaded = false
    private var isInterstitialLoading = false
    private var lastInterstitialTime: Long = 0

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Determines whether any AdMob features are enabled in Config.kt.
     */
    val isEnabled: Boolean
        get() = Config.ADMOB_BANNER_ID.isNotBlank() || Config.ADMOB_INTERSTITIAL_ID.isNotBlank()

    init {
        if (!isEnabled) {
            logDebug("AdMob is completely disabled (empty IDs in Config.kt).")
            bannerContainer?.visibility = View.GONE
        } else {
            setupConnectivityMonitoring()
            initializeAdMob()
        }
    }

    /**
     * Initializes Google Mobile Ads SDK safely in the background.
     */
    private fun initializeAdMob() {
        try {
            logDebug("Initializing Google Mobile Ads SDK...")
            MobileAds.initialize(activity) { initStatus ->
                isAdMobInitialized = true
                logDebug("AdMob SDK initialized: ${initStatus.adapterStatusMap.keys}")
                mainHandler.post {
                    if (isNetworkAvailable()) {
                        loadBannerAd()
                        loadInterstitialAd()
                    } else {
                        logDebug("Offline: Deferring ad loading until network is available.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MobileAds safely: ${e.message}", e)
        }
    }

    /**
     * Loads the bottom adaptive banner ad into the provided container.
     */
    fun loadBannerAd() {
        if (Config.ADMOB_BANNER_ID.isBlank() || bannerContainer == null) {
            bannerContainer?.visibility = View.GONE
            return
        }

        if (!isNetworkAvailable()) {
            logDebug("Cannot load banner: Device is currently offline.")
            bannerContainer.visibility = View.GONE
            return
        }

        try {
            if (adView == null) {
                val newAdView = AdView(activity).apply {
                    adUnitId = Config.ADMOB_BANNER_ID
                    setAdSize(getAdaptiveAdSize())
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            super.onAdLoaded()
                            isBannerLoaded = true
                            bannerContainer.visibility = View.VISIBLE
                            logDebug("Banner ad successfully loaded and visible.")
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            super.onAdFailedToLoad(error)
                            isBannerLoaded = false
                            bannerContainer.visibility = View.GONE
                            logDebug("Banner ad failed to load: ${error.message} (code: ${error.code})")
                        }

                        override fun onAdOpened() {
                            super.onAdOpened()
                            logDebug("Banner ad opened.")
                        }

                        override fun onAdClosed() {
                            super.onAdClosed()
                            logDebug("Banner ad closed.")
                        }
                    }
                }
                adView = newAdView
                bannerContainer.removeAllViews()
                bannerContainer.addView(newAdView)
            }

            logDebug("Requesting banner ad with ID: ${Config.ADMOB_BANNER_ID}")
            val adRequest = AdRequest.Builder().build()
            adView?.loadAd(adRequest)
        } catch (e: Exception) {
            Log.e(TAG, "Exception while loading banner ad: ${e.message}", e)
            bannerContainer.visibility = View.GONE
        }
    }

    /**
     * Loads an interstitial ad in the background.
     */
    fun loadInterstitialAd() {
        if (Config.ADMOB_INTERSTITIAL_ID.isBlank()) {
            return
        }

        if (interstitialAd != null || isInterstitialLoading) {
            return
        }

        if (!isNetworkAvailable()) {
            logDebug("Cannot load interstitial: Device is offline.")
            return
        }

        isInterstitialLoading = true
        logDebug("Requesting interstitial ad with ID: ${Config.ADMOB_INTERSTITIAL_ID}")
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            activity,
            Config.ADMOB_INTERSTITIAL_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                    logDebug("Interstitial ad loaded successfully and cached.")

                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            lastInterstitialTime = System.currentTimeMillis()
                            logDebug("Interstitial dismissed by user. Reloading next ad...")
                            loadInterstitialAd()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            interstitialAd = null
                            isInterstitialLoading = false
                            Log.e(TAG, "Failed to show interstitial: ${error.message}")
                            loadInterstitialAd()
                        }

                        override fun onAdShowedFullScreenContent() {
                            logDebug("Interstitial full screen content showed.")
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                    logDebug("Interstitial failed to load: ${error.message} (code: ${error.code})")
                }
            }
        )
    }

    /**
     * Displays an interstitial ad if available and interval condition is met.
     * @param forceIgnoreInterval If true, ignores the time interval check.
     * @param onDismissed Callback invoked after the ad is shown/dismissed, or immediately if not shown.
     */
    fun showInterstitialIfReady(
        forceIgnoreInterval: Boolean = false,
        onDismissed: () -> Unit = {}
    ) {
        if (Config.ADMOB_INTERSTITIAL_ID.isBlank()) {
            onDismissed()
            return
        }

        val currentTime = System.currentTimeMillis()
        val intervalMillis = Config.INTERSTITIAL_INTERVAL_SECONDS * 1000L
        val timeSinceLast = currentTime - lastInterstitialTime

        if (!forceIgnoreInterval && timeSinceLast < intervalMillis) {
            logDebug("Skipping interstitial: Interval not reached (${timeSinceLast / 1000}s / ${Config.INTERSTITIAL_INTERVAL_SECONDS}s)")
            onDismissed()
            return
        }

        val ad = interstitialAd
        if (ad != null) {
            logDebug("Displaying interstitial ad now...")
            ad.show(activity)
            onDismissed()
        } else {
            logDebug("Interstitial not ready yet. Triggering reload...")
            loadInterstitialAd()
            onDismissed()
        }
    }

    /**
     * Calculates adaptive banner size matching the current window width.
     */
    private fun getAdaptiveAdSize(): AdSize {
        val displayMetrics = activity.resources.displayMetrics
        val widthPixels = displayMetrics.widthPixels
        val density = displayMetrics.density
        val adWidth = (widthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }

    /**
     * Monitors network connectivity and automatically triggers ad reloading
     * once an active internet connection is detected.
     */
    private fun setupConnectivityMonitoring() {
        try {
            connectivityManager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    logDebug("Network connection restored. Retrying ad loading...")
                    mainHandler.post {
                        if (isAdMobInitialized) {
                            if (!isBannerLoaded) loadBannerAd()
                            if (interstitialAd == null) loadInterstitialAd()
                        }
                    }
                }

                override fun onLost(network: Network) {
                    logDebug("Network connection lost. Offline mode active.")
                }
            }

            networkCallback?.let {
                connectivityManager?.registerNetworkCallback(request, it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    /**
     * Returns true if active network has internet capability.
     */
    fun isNetworkAvailable(): Boolean {
        val cm = connectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Activity lifecycle handlers.
     */
    fun onResume() {
        adView?.resume()
        if (isNetworkAvailable() && !isBannerLoaded) {
            loadBannerAd()
        }
    }

    fun onPause() {
        adView?.pause()
    }

    fun onDestroy() {
        try {
            networkCallback?.let {
                connectivityManager?.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            // Callback might already be unregistered
        }
        adView?.destroy()
        adView = null
        interstitialAd = null
    }

    private fun logDebug(message: String) {
        if (Config.DEBUG_LOGGING) {
            Log.d(TAG, message)
        }
    }
}
