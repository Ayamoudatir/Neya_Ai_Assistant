package com.example.aistudyassistant_moudatir.ui

import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aistudyassistant_moudatir.data.local.SettingsStore
import com.example.aistudyassistant_moudatir.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var store: SettingsStore

    private val voices = listOf(
        "Automatique (détection)" to "auto",
        "Français — Denise"       to "fr-FR-DeniseNeural",
        "Français — Henri"        to "fr-FR-HenriNeural",
        "عربي — Zariyah"          to "ar-SA-ZariyahNeural",
        "عربي — Hamed"            to "ar-SA-HamedNeural",
        "English — Jenny"         to "en-US-JennyNeural"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = SettingsStore(this)

        window.navigationBarColor = Color.parseColor("#CCF1EAE4")

        populateFields()
        setupSaveButton()

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun populateFields() {
        binding.etUserName.setText(store.userName)

        val voiceLabels = voices.map { it.first }
        val voiceAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, voiceLabels)
        binding.actvVoice.setAdapter(voiceAdapter)
        val currentVoice = voices.firstOrNull { it.second == store.voice } ?: voices[0]
        binding.actvVoice.setText(currentVoice.first, false)

        binding.etServerUrl.setText(store.serverUrl)
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            store.userName  = binding.etUserName.text?.toString()?.trim() ?: ""
            store.voice     = voices.firstOrNull { it.first == binding.actvVoice.text?.toString() }?.second ?: "auto"
            val url         = binding.etServerUrl.text?.toString()?.trim() ?: ""
            store.serverUrl = url.ifEmpty { "http://10.0.2.2:8000" }

            Toast.makeText(this, "Paramètres enregistrés ✓", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
