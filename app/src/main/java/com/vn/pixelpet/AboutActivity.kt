package com.vn.pixelpet

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class AboutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (t: Throwable) {
            "1.0.0"
        }
        findViewById<TextView>(R.id.about_version).text =
            getString(R.string.about_version, version)
    }
}
