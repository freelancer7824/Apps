package com.example

import android.graphics.Color

/**
 * ========================================================================
 * CENTRAL CONFIGURATION FILE
 * ========================================================================
 * This file contains all settings for converting your HTML/CSS/JavaScript
 * web project into a native Android app.
 *
 * Customize the values below to match your application requirements.
 * Every option includes detailed inline documentation.
 */
object Config {

    // ====================================================================
    // GENERAL APP SETTINGS
    // ====================================================================

    /**
     * App display name.
     * Keep synchronized with strings.xml and metadata.json.
     */
    const val APP_NAME = "Web App Converter"

    /**
     * Entry HTML file loaded on app launch from the `app/src/main/assets/` directory.
     * Example: "index.html" or "dist/index.html" or "public/home.html"
     */
    const val START_PAGE = "index.html"

    /**
     * Virtual secure domain used by WebViewAssetLoader.
     * Assets are securely mapped to: https://appassets.androidplatform.net/assets/index.html
     * This avoids file:/// protocol issues, supports CORS, fetch, service workers,
     * and local storage while running 100% offline.
     */
    const val ALLOWED_ORIGIN_HOST = "appassets.androidplatform.net"


    // ====================================================================
    // ADMOB ADVERTISING SETTINGS
    // ====================================================================

    /**
     * AdMob Banner Ad Unit ID.
     * - Production: Replace with your banner ad unit ID (e.g., "ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY")
     * - Testing: Google test banner ID is "ca-app-pub-3940256099942544/6300978111"
     * - Disabled: Leave as empty string "" to completely disable banner ads.
     */
    const val ADMOB_BANNER_ID = ""

    /**
     * AdMob Interstitial Ad Unit ID.
     * - Production: Replace with your interstitial ad unit ID (e.g., "ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY")
     * - Testing: Google test interstitial ID is "ca-app-pub-3940256099942544/1033173712"
     * - Disabled: Leave as empty string "" to completely disable interstitial ads.
     */
    const val ADMOB_INTERSTITIAL_ID = ""

    /**
     * Minimum time interval (in seconds) between displaying interstitial ads.
     * Prevents showing ads too frequently and maintains a good user experience.
     */
    const val INTERSTITIAL_INTERVAL_SECONDS = 60


    // ====================================================================
    // WEBVIEW ENGINE & FEATURE SETTINGS
    // ====================================================================

    /**
     * Enable or disable JavaScript execution within the WebView.
     * Set to true for modern single-page applications (React, Vue, Vanilla JS, etc.).
     */
    const val ENABLE_JAVASCRIPT = true

    /**
     * Enable or disable HTML5 DOM Local Storage (localStorage, sessionStorage).
     */
    const val ENABLE_DOM_STORAGE = true

    /**
     * Enable or disable database storage API.
     */
    const val ENABLE_DATABASE_STORAGE = true

    /**
     * Enable or disable hardware-accelerated mixed content or media playback.
     */
    const val ALLOW_FILE_ACCESS = true


    // ====================================================================
    // USER INTERFACE & NAVIGATION SETTINGS
    // ====================================================================

    /**
     * Enable or disable Swipe-to-Refresh (pull-to-refresh) gesture.
     * When pulled, reloads the current WebView page.
     */
    const val ENABLE_PULL_TO_REFRESH = true

    /**
     * Enable or disable the thin Chrome-style loading progress bar at the top of the screen.
     */
    const val ENABLE_PROGRESS_BAR = true

    /**
     * Primary brand color (Hex) used for the loading progress bar and pull-to-refresh indicator.
     * Example: "#2563EB" (Royal Blue)
     */
    const val THEME_COLOR_PRIMARY_HEX = "#2563EB"

    /**
     * Accent/secondary color (Hex) for loading highlights.
     * Example: "#06B6D4" (Cyan)
     */
    const val THEME_COLOR_ACCENT_HEX = "#06B6D4"

    /**
     * Background color (Hex) for the WebView container before content loads.
     * Example: "#0F172A" (Dark Slate)
     */
    const val WEBVIEW_BACKGROUND_COLOR_HEX = "#0F172A"

    val THEME_COLOR_PRIMARY: Int
        get() = Color.parseColor(THEME_COLOR_PRIMARY_HEX)

    val THEME_COLOR_ACCENT: Int
        get() = Color.parseColor(THEME_COLOR_ACCENT_HEX)

    val WEBVIEW_BACKGROUND_COLOR: Int
        get() = Color.parseColor(WEBVIEW_BACKGROUND_COLOR_HEX)


    // ====================================================================
    // PERMISSIONS & HARDWARE ACCESS SETTINGS
    // ====================================================================

    /**
     * Enable or disable HTML5 camera capture (e.g. file input with camera capture).
     * Only requests android.permission.CAMERA when the user triggers the camera.
     */
    const val ENABLE_CAMERA_CAPTURE = true

    /**
     * Enable or disable HTML file chooser / image & document upload (<input type="file">).
     */
    const val ENABLE_FILE_UPLOAD = true

    /**
     * Enable or disable HTML5 Geolocation API (navigator.geolocation).
     * Requests location permissions dynamically only when requested by web JavaScript.
     */
    const val ENABLE_GEOLOCATION = true


    // ====================================================================
    // NETWORKING & URL ROUTING SETTINGS
    // ====================================================================

    /**
     * When an external link (http/https outside ALLOWED_ORIGIN_HOST) is clicked:
     * - true: Opens link in the user's default web browser (Chrome, Firefox, etc.)
     * - false: Attempts to open inside the embedded WebView
     */
    const val OPEN_EXTERNAL_URLS_IN_BROWSER = true

    /**
     * Allow custom URL schemes like mailto:, tel:, sms:, whatsapp:, market:, intent:
     * Automatically launches corresponding system apps when tapped.
     */
    const val ENABLE_CUSTOM_INTENT_SCHEMES = true

    /**
     * Enable offline application cache fallback for network requests.
     */
    const val ENABLE_OFFLINE_CACHE = true


    // ====================================================================
    // DEBUGGING & DIAGNOSTICS
    // ====================================================================

    /**
     * Enable detailed logcat logging for debugging WebView, AdMob, and file chooser events.
     * Recommended: Set to false for production release builds.
     */
    const val DEBUG_LOGGING = true
}
