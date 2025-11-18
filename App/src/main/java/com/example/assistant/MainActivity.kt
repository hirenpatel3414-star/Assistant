package com.example.assistant

import android.Manifest
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tvOutput: TextView
    private lateinit var btnListen: ImageButton

    private val RECORD_AUDIO_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvOutput = findViewById(R.id.tvOutput)
        btnListen = findViewById(R.id.btnListen)

        tts = TextToSpeech(this, this)

        if (!hasRecordAudioPermission()) {
            requestRecordAudioPermission()
        }

        btnListen.setOnClickListener {
            if (!hasRecordAudioPermission()) {
                requestRecordAudioPermission()
            } else {
                startListening()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        speechRecognizer?.destroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts.setLanguage(Locale.getDefault())
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "TTS language not supported", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "TTS initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRecordAudioPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startListening() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        showTextAndSpeak("I couldn't hear that. Try again.")
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val spoken = matches[0]
                            handleCommand(spoken)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a command, e.g. 'what's the time' or 'open youtube'")

            speechRecognizer?.startListening(intent)
            showTextAndSpeak("Listening...")
        } else {
            Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleCommand(text: String) {
        val lower = text.lowercase(Locale.getDefault())
        tvOutput.text = "You said: $text"

        when {
            lower.contains("time") || lower.contains("what's the time") || lower.contains("current time") -> {
                val cal = Calendar.getInstance()
                val timeStr = String.format(Locale.getDefault(), "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                showTextAndSpeak("The time is $timeStr")
            }
            lower.startsWith("open ") || lower.contains("open ") -> {
                val target = lower.substringAfter("open ").trim()
                openWebsiteOrApp(target)
            }
            lower.contains("search for") || lower.startsWith("search ") -> {
                val query = if (lower.contains("search for")) lower.substringAfter("search for").trim() else lower.substringAfter("search ").trim()
                performWebSearch(query)
            }
            lower.contains("joke") || lower.contains("tell me a joke") -> {
                showTextAndSpeak("Why don't scientists trust atoms? Because they make up everything!")
            }
            lower.contains("call ") -> {
                val number = lower.substringAfter("call ").trim().replace(" ", "")
                makePhoneCall(number)
            }
            else -> {
                showTextAndSpeak("I heard: $text. Do you want me to search the web for that?")
            }
        }
    }

    private fun showTextAndSpeak(s: String) {
        tvOutput.text = s
        speak(s)
    }

    private fun speak(s: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_ID")
        } else {
            tts.speak(s, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    private fun performWebSearch(query: String) {
        try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH)
            intent.putExtra(SearchManager.QUERY, query)
            startActivity(intent)
            showTextAndSpeak("Searching for $query")
        } catch (e: ActivityNotFoundException) {
            val url = "https://www.google.com/search?q=" + Uri.encode(query)
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse(url)
            startActivity(i)
            showTextAndSpeak("Searching for $query in browser")
        }
    }

    private fun openWebsiteOrApp(target: String) {
        when {
            target.contains("youtube") -> {
                val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:"))
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
                try { startActivity(appIntent) } catch (e: Exception) { startActivity(webIntent) }
                showTextAndSpeak("Opening YouTube")
            }
            target.contains("google") -> {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                startActivity(i)
                showTextAndSpeak("Opening Google")
            }
            target.contains("gmail") || target.contains("mail") -> {
                val i = Intent(Intent.ACTION_SENDTO)
                i.data = Uri.parse("mailto:")
                try { startActivity(i) } catch (e: Exception) { showTextAndSpeak("No mail app found") }
            }
            else -> {
                var url = target
                if (!url.startsWith("http")) url = "https://$url"
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { showTextAndSpeak("Couldn't open $target") }
            }
        }
    }

    private fun makePhoneCall(number: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:$number")
            startActivity(intent)
            showTextAndSpeak("Dialing $number")
        } catch (e: Exception) {
            showTextAndSpeak("Couldn't make call")
        }
    }
}
