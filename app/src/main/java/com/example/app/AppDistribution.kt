package com.example.app

import android.net.Uri

/**
 * Keeps APK distribution on the business's own HTTPS download page instead of
 * exposing a cloud-storage sharing URL to customers.
 */
object AppDistribution {

    fun isOfficialDownloadUrl(value: String): Boolean {
        val uri = runCatching { Uri.parse(value.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false

        return uri.scheme == "https" &&
                host.isNotBlank() &&
                host != "drive.google.com" &&
                host != "docs.google.com"
    }
}
