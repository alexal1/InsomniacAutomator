package com.alexal1.starter

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.StringBuilder
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class StarterActivity : Activity() {

    private val bgThread = HandlerThread("starter_activity_worker").also { it.start() }
    private val bgHandler = Handler(bgThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val instagramTitleRegex = Regex("<title>\\s*Instagram\\s*</title>")

    private val rootLayout by lazy { findViewById<ConstraintLayout>(R.id.rootLayout) }
    private val textView by lazy { findViewById<TextView>(R.id.textView) }

    private var isAlive = true
    private var packageNameToStart = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_starter)
        packageNameToStart = intent.getStringExtra(EXTRA_PACKAGE_NAME)
            ?: savedInstanceState?.getString(EXTRA_PACKAGE_NAME)
            ?: ""

        checkConnection()
    }

    private fun checkConnection() {
        setStatus(STATUS_PROGRESS)

        bgHandler.post {
            try {
                val url = URL("https://www.instagram.com")
                val connection = url.openConnection() as HttpsURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val status = connection.responseCode
                if (status != 200) {
                    mainHandler.post {
                        setStatus(STATUS_FAIL)
                    }
                    return@post
                }

                val response = connection.inputStream
                val responseReader = BufferedReader(InputStreamReader(response))
                val responseString = StringBuilder()
                var line = ""
                while (responseReader.readLine()?.also { line = it } != null) {
                    responseString.append(line).append("\n")
                }

                if (responseString.contains(instagramTitleRegex)) {
                    mainHandler.post {
                        setStatus(STATUS_SUCCESS)
                    }
                    return@post
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error while checking connection", e)
            }
            mainHandler.post {
                setStatus(STATUS_FAIL)
            }
        }
    }

    private fun setStatus(status: Int) {
        if (!isAlive) {
            return
        }

        when (status) {
            STATUS_PROGRESS -> {
                rootLayout.setBackgroundResource(R.color.white)
                textView.setTextColor(ContextCompat.getColor(this, R.color.black))
                textView.text = getString(R.string.title_progress)
            }

            STATUS_FAIL -> {
                rootLayout.setBackgroundResource(R.color.red)
                textView.setTextColor(ContextCompat.getColor(this, R.color.white))
                textView.text = getString(R.string.title_fail)

                mainHandler.postDelayed({
                      checkConnection()
                }, RETRY_DELAY)
            }

            STATUS_SUCCESS -> {
                val intent = packageManager.getLaunchIntentForPackage(packageNameToStart)
                if (intent != null) {
                    startActivity(intent)
                }
                finish()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(EXTRA_PACKAGE_NAME, packageNameToStart)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        isAlive = false
        mainHandler.removeCallbacksAndMessages(null)
        bgHandler.removeCallbacksAndMessages(null)
        bgThread.quit()
    }

    companion object {
        private const val TAG = "StarterActivity"
        private const val EXTRA_PACKAGE_NAME = "package"

        private const val STATUS_PROGRESS = 0
        private const val STATUS_FAIL = 1
        private const val STATUS_SUCCESS = 2

        private const val RETRY_DELAY = 60 * 1000L
    }
}
