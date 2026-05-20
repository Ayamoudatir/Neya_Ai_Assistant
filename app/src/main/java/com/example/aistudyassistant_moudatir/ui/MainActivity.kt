package com.example.aistudyassistant_moudatir.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority as LocationPriority
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aistudyassistant_moudatir.R
import com.example.aistudyassistant_moudatir.data.local.ConversationStore
import com.example.aistudyassistant_moudatir.data.local.SettingsStore
import com.example.aistudyassistant_moudatir.data.model.Conversation
import com.example.aistudyassistant_moudatir.databinding.ActivityMainBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var chatAdapter: ChatAdapter
    private val viewModel: ChatViewModel by viewModels()

    // ── Deux lecteurs vidéo — toujours actifs ────────────────────────────────
    private var playerIdle: MediaPlayer? = null
    private var playerSpeaking: MediaPlayer? = null
    private var idleSurfaceReady = false
    private var speakingSurfaceReady = false

    // ── Lecteur audio (TTS depuis le serveur) ────────────────────────────────
    private var audioPlayer: MediaPlayer? = null

    // ── Session counter — évite les callbacks obsolètes ──────────────────────
    private var speakingSession = 0
    private var isSpeaking = false

    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Reconnaissance vocale ─────────────────────────────────────────────────
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val MIC_PERMISSION_CODE      = 101
    private val LOCATION_PERMISSION_CODE = 102
    private val CAMERA_PERMISSION_CODE   = 103

    // Launcher Google Speech (popup) — fiable sur émulateur ET vrai device
    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: return@registerForActivityResult
            binding.etMessage.setText(text)
            binding.etMessage.setSelection(text.length)
            updateActionButton(true)
        } else {
            updateActionButton(false)
        }
    }

    // ── Caméra ────────────────────────────────────────────────────────────────
    private var cameraImageUri: Uri? = null

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let { uri ->
                viewModel.sendImageMessage(uri.toString())
            }
        }
    }

    // ── Galerie ───────────────────────────────────────────────────────────────
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { sourceUri ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Copie dans le stockage interne pour persistance
                    val destFile = File(filesDir, "images/${System.currentTimeMillis()}.jpg")
                    destFile.parentFile?.mkdirs()
                    contentResolver.openInputStream(sourceUri)?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    withContext(Dispatchers.Main) {
                        viewModel.sendImageMessage(Uri.fromFile(destFile).toString())
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Impossible de charger l'image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────
    private lateinit var store: ConversationStore
    private lateinit var settings: SettingsStore
    private lateinit var conversationsAdapter: ConversationsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store    = ConversationStore(this)
        settings = SettingsStore(this)
        fixStatusBarColor()
        setupAvatarTextures()
        setupBlur()
        setupRecyclerView()
        setupSendButton()
        setupDrawer()
        setupSpeechRecognizer()
        setupAddButton()
        observeViewModel()
    }

    // ── Status bar + nav bar — même couleur que header/inputBar ────────────
    // Le fond de fenêtre (bg_app_blur gradient) transparaît derrière #CCF1EAE4
    // → résultat identique au header et à l'input bar (même recette visuelle)
    private fun fixStatusBarColor() {
        // DrawerLayout (fitsSystemWindows=true) dessine le scrim du status bar
        // → on lui donne #CCF1EAE4 pour qu'il se blend avec le gradient du fond
        binding.drawerLayout.setStatusBarBackgroundColor(Color.TRANSPARENT)
        window.navigationBarColor = Color.TRANSPARENT
    }

    // ── Sidebar / Drawer ─────────────────────────────────────────────────────
    private fun setupDrawer() {
        val drawer = binding.drawerLayout

        // Ouvrir avec le bouton menu
        binding.btnMenu.setOnClickListener {
            if (drawer.isDrawerOpen(GravityCompat.START))
                drawer.closeDrawer(GravityCompat.START)
            else
                drawer.openDrawer(GravityCompat.START)
        }

        // Adapter conversations
        conversationsAdapter = ConversationsAdapter(
            onClick = { conv ->
                val msgs = store.getMessages(conv.id)
                viewModel.loadConversation(conv.id, msgs)
                drawer.closeDrawer(GravityCompat.START)
            },
            onDelete = { conv ->
                store.deleteConversation(conv.id)
                refreshConversationList()
            }
        )

        binding.navDrawer.rvConversations.apply {
            adapter = conversationsAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        // Nouvelle discussion
        binding.navDrawer.btnNewConversation.setOnClickListener {
            saveCurrentConversation()
            viewModel.startNewConversation()
            drawer.closeDrawer(GravityCompat.START)
        }

        // Paramètres
        binding.navDrawer.btnSettings.setOnClickListener {
            drawer.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        refreshConversationList()
    }

    private fun saveCurrentConversation() {
        val messages = viewModel.messages.value.orEmpty()
        if (messages.isEmpty()) return
        val conv = Conversation(
            id        = viewModel.currentConversationId,
            title     = viewModel.currentConversationTitle,
            timestamp = System.currentTimeMillis()
        )
        store.saveConversation(conv)
        store.saveMessages(conv.id, messages)
    }

    private fun refreshConversationList() {
        conversationsAdapter.submitList(store.getConversations())
    }

    // ── Téléchargement audio + lecture ───────────────────────────────────────
    private fun speak(text: String) {
        val session = ++speakingSession
        val voice = detectVoice(text)

        lifecycleScope.launch {
            try {
                val audioBytes = withContext(Dispatchers.IO) { downloadAudio(text, voice) }
                if (session != speakingSession) return@launch // session invalidée entre temps
                withContext(Dispatchers.Main) { playAudio(audioBytes, session) }
            } catch (e: Exception) {
                Log.e("TTS", "Erreur téléchargement audio: ${e.message}")
                // Fallback : serveur TTS injoignable → pas de vidéo speaking (pas de son)
                // On reste en idle, rien à faire
            }
        }
    }

    private fun downloadAudio(text: String, voice: String): ByteArray {
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val json = """{"text":"$escaped","voice":"$voice"}"""
        val conn = (URL("${settings.serverUrl}/speak").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15_000
            readTimeout    = 60_000
            doOutput = true
            outputStream.use { it.write(json.toByteArray()) }
        }
        return conn.inputStream.use { it.readBytes() }
    }

    private fun playAudio(bytes: ByteArray, session: Int) {
        val tmpFile = File.createTempFile("neya_audio", ".mp3", cacheDir)
        tmpFile.writeBytes(bytes)

        audioPlayer?.release()
        audioPlayer = MediaPlayer().apply {
            setDataSource(tmpFile.absolutePath)
            setOnCompletionListener {
                tmpFile.delete()
                if (session == speakingSession) stopSpeaking()
            }
            setOnErrorListener { _, _, _ ->
                tmpFile.delete()
                if (session == speakingSession) stopSpeaking()
                true
            }
            setOnPreparedListener {
                // La vidéo speaking démarre exactement quand le son démarre — 0 décalage
                if (session == speakingSession) setSpeakingState(true)
                start()
            }
            prepareAsync()
        }
    }

    private fun detectVoice(text: String): String {
        // Si l'utilisateur a choisi une voix fixe, on l'utilise directement
        val preferred = settings.voice
        if (preferred != "auto") return preferred

        // Sinon, détection automatique
        if (text.any { it.code in 0x0600..0x06FF }) return "ar-SA-ZariyahNeural"
        val frWords = setOf("le", "la", "les", "de", "du", "des", "et", "est",
            "je", "tu", "il", "elle", "nous", "vous", "pour", "pas", "que",
            "qui", "en", "au", "avec", "sur", "dans", "une", "un", "ce", "se",
            "mais", "donc", "car", "aussi", "comme", "plus", "bien", "tout")
        val words = text.lowercase().split("\\s+".toRegex())
        return if (words.count { it in frWords } >= 2) "fr-FR-DeniseNeural" else "en-US-JennyNeural"
    }

    private fun stopSpeaking() {
        setSpeakingState(false)
    }

    // ── Deux TextureViews superposés ─────────────────────────────────────────
    private fun setupAvatarTextures() {
        binding.avatarTextureIdle.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                idleSurfaceReady = true
                startVideoPlayer(R.raw.neya_idle, st) { playerIdle = it }
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                idleSurfaceReady = false; playerIdle?.release(); playerIdle = null; return true
            }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }

        binding.avatarTextureSpeaking.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                speakingSurfaceReady = true
                startVideoPlayer(R.raw.neya_speaking, st) { playerSpeaking = it }
            }
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                speakingSurfaceReady = false; playerSpeaking?.release(); playerSpeaking = null; return true
            }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
    }

    private fun startVideoPlayer(resId: Int, st: SurfaceTexture, onReady: (MediaPlayer) -> Unit) {
        val surface = Surface(st)
        val uri = Uri.parse("android.resource://$packageName/$resId")
        val player = MediaPlayer().apply {
            setDataSource(this@MainActivity, uri)
            setSurface(surface)
            isLooping = true
            setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            setOnPreparedListener { start() }
            prepareAsync()
        }
        surface.release()
        onReady(player)
    }

    // ── Crossfade fluide 350ms ────────────────────────────────────────────────
    private fun setSpeakingState(speaking: Boolean) {
        isSpeaking = speaking
        val idleAlpha    = if (speaking) 0f else 1f
        val speakAlpha   = if (speaking) 1f else 0f
        binding.avatarTextureIdle.animate()
            .alpha(idleAlpha).setDuration(350)
            .setInterpolator(AccelerateDecelerateInterpolator()).start()
        binding.avatarTextureSpeaking.animate()
            .alpha(speakAlpha).setDuration(350)
            .setInterpolator(AccelerateDecelerateInterpolator()).start()
        binding.speakingIndicator.isVisible = speaking
        binding.waveformView.isAnimating    = speaking
    }

    // ── Blur ─────────────────────────────────────────────────────────────────
    private fun setupBlur() {
        // Pas de blur sur l'image de fond — elle est utilisée telle quelle
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────
    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.recyclerViewChat.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@MainActivity).apply { stackFromEnd = true }
        }
    }

    // ── Saisie ────────────────────────────────────────────────────────────────
    private fun setupSendButton() {
        binding.etMessage.setOnEditorActionListener { _, _, _ -> sendMessage(); true }
        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateActionButton(!s.isNullOrBlank()) }
        })
        updateActionButton(hasText = false)
    }

    private fun updateActionButton(hasText: Boolean) {
        binding.btnAction.apply {
            if (hasText) {
                setIconResource(R.drawable.ic_send)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#E5A5A0"))
                setOnClickListener { sendMessage() }
            } else if (isListening) {
                setIconResource(R.drawable.ic_mic)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#D87878"))
                setOnClickListener { stopListening() }
            } else {
                setIconResource(R.drawable.ic_mic)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#E5A5A0"))
                setOnClickListener { startListening() }
            }
        }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return
        viewModel.sendMessage(text)
        binding.etMessage.text?.clear()
    }

    // ── Observateurs ─────────────────────────────────────────────────────────
    private fun observeViewModel() {
        viewModel.messages.observe(this) { messages ->
            chatAdapter.submitList(messages.toList()) {
                if (messages.isNotEmpty())
                    binding.recyclerViewChat.scrollToPosition(messages.size - 1)
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            if (loading) {
                speakingSession++
                audioPlayer?.stop()
                setSpeakingState(false)  // idle pendant les 3 dots + streaming
                saveCurrentConversation()
                refreshConversationList()
            } else {
                // Texte terminé → lancer le TTS (la vidéo speaking démarrera
                // uniquement quand l'audio est prêt, dans setOnPreparedListener)
                val lastAiMsg = viewModel.messages.value?.lastOrNull { !it.isFromUser }
                if (lastAiMsg != null) {
                    speak(lastAiMsg.content) // → setSpeakingState(true) déclenché dans playAudio
                    saveCurrentConversation()
                    refreshConversationList()
                }
            }
        }

        viewModel.error.observe(this) { errorMsg ->
            binding.tvError.isVisible = errorMsg != null
            binding.tvError.text = errorMsg
            if (errorMsg != null) {
                speakingSession++
                audioPlayer?.stop()
                setSpeakingState(false)
            }
        }
    }

    // ── Bouton "+" ────────────────────────────────────────────────────────────
    private fun setupAddButton() {
        binding.btnAdd.setOnClickListener { showAttachmentMenu() }
    }

    private fun showAttachmentMenu() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_attach, null)

        view.findViewById<LinearLayout>(R.id.optionLocation).setOnClickListener {
            dialog.dismiss()
            shareLocation()
        }
        view.findViewById<LinearLayout>(R.id.optionCamera).setOnClickListener {
            dialog.dismiss()
            openCamera()
        }
        view.findViewById<LinearLayout>(R.id.optionGallery).setOnClickListener {
            dialog.dismiss()
            openGallery()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    // ── Localisation ──────────────────────────────────────────────────────────
    private fun shareLocation() {
        val hasFine   = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_CODE
            )
            return
        }
        fetchAndSendLocation()
    }

    /** Détecte si le code tourne sur un émulateur Android. */
    private fun isEmulator(): Boolean =
        (android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.MODEL.contains("google_sdk", ignoreCase = true)
                || android.os.Build.MODEL.contains("Emulator", ignoreCase = true)
                || android.os.Build.MODEL.contains("Android SDK built for x86", ignoreCase = true)
                || android.os.Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)
                || (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                || android.os.Build.PRODUCT == "google_sdk")

    @SuppressLint("MissingPermission")
    private fun fetchAndSendLocation() {
        Toast.makeText(this, "📍 Détection de ta position…", Toast.LENGTH_SHORT).show()

        // Sur émulateur le GPS est faux (Mountain View par défaut) →
        // on utilise la géolocalisation par IP qui retourne la vraie position réseau
        if (isEmulator()) {
            fetchLocationByIp()
            return
        }

        // Sur vrai appareil → GPS haute précision via FusedLocationProviderClient
        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        fusedClient.getCurrentLocation(LocationPriority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    sendLocationFromGps(location.latitude, location.longitude)
                } else {
                    fusedClient.lastLocation.addOnSuccessListener { last ->
                        if (last != null) sendLocationFromGps(last.latitude, last.longitude)
                        else fetchLocationByIp() // dernier recours
                    }
                }
            }
            .addOnFailureListener { fetchLocationByIp() }
    }

    /** Géolocalisation par IP — fonctionne sur émulateur (partage la connexion du PC). */
    private fun fetchLocationByIp() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val conn = (URL("http://ip-api.com/json").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6_000
                    readTimeout    = 6_000
                }
                val raw  = conn.inputStream.bufferedReader().readText()
                val json = org.json.JSONObject(raw)

                if (json.getString("status") == "success") {
                    val lat     = json.getDouble("lat")
                    val lon     = json.getDouble("lon")
                    val city    = json.optString("city", "")
                    val region  = json.optString("regionName", "")
                    val country = json.optString("country", "")
                    val address = listOf(city, region, country)
                        .filter { it.isNotEmpty() }.joinToString(", ")

                    val text = buildString {
                        append("📍 Ma position actuelle : ")
                        if (address.isNotEmpty()) append("$address ")
                        append("(%.5f° N, %.5f° E)".format(lat, lon))
                    }
                    withContext(Dispatchers.Main) { viewModel.sendMessage(text) }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "📍 Position introuvable.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "📍 Erreur réseau lors de la localisation.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** Géocodage inversé pour vrai GPS (vrai appareil uniquement). */
    private fun sendLocationFromGps(lat: Double, lon: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            val address = try {
                @Suppress("DEPRECATION")
                Geocoder(this@MainActivity, Locale.getDefault())
                    .getFromLocation(lat, lon, 1)
                    ?.firstOrNull()
                    ?.let { addr ->
                        buildString {
                            addr.locality?.let    { append(it) }
                            addr.adminArea?.let   { if (isNotEmpty()) append(", "); append(it) }
                            addr.countryName?.let { if (isNotEmpty()) append(", "); append(it) }
                        }.ifEmpty { null }
                    }
            } catch (e: Exception) { null }

            val text = buildString {
                append("📍 Ma position actuelle : ")
                if (!address.isNullOrEmpty()) append("$address ")
                append("(%.5f° N, %.5f° E)".format(lat, lon))
            }
            withContext(Dispatchers.Main) { viewModel.sendMessage(text) }
        }
    }

    // ── Caméra ────────────────────────────────────────────────────────────────
    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
            return
        }
        launchCamera()
    }

    private fun launchCamera() {
        val imageFile = File(cacheDir, "camera_images/photo_${System.currentTimeMillis()}.jpg")
        imageFile.parentFile?.mkdirs()
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            imageFile
        )
        cameraImageUri = uri
        cameraLauncher.launch(uri)
    }

    // ── Galerie ───────────────────────────────────────────────────────────────
    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    // ── Reconnaissance vocale ─────────────────────────────────────────────────
    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Reconnaissance vocale non disponible", Toast.LENGTH_SHORT).show()
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {
                isListening = true
                updateActionButton(false)
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }
            override fun onError(err: Int) {
                isListening = false
                updateActionButton(!binding.etMessage.text.isNullOrBlank())
                val msg = when (err) {
                    SpeechRecognizer.ERROR_NO_MATCH    -> "Rien compris, réessaie"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout, réessaie"
                    SpeechRecognizer.ERROR_NETWORK     -> "Erreur réseau"
                    else -> null
                }
                msg?.let { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                binding.etMessage.setText(text)
                binding.etMessage.setSelection(text.length)
                updateActionButton(true)
            }
            override fun onPartialResults(partial: Bundle?) {
                val text = partial
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                binding.etMessage.setText(text)
                binding.etMessage.setSelection(text.length)
            }
            override fun onEvent(t: Int, p: Bundle?) {}
        })
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), MIC_PERMISSION_CODE)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            isListening = true
            updateActionButton(false)
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            isListening = false
            Toast.makeText(this, "Reconnaissance vocale non disponible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
        updateActionButton(!binding.etMessage.text.isNullOrBlank())
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            MIC_PERMISSION_CODE -> {
                if (granted) startListening()
                else Toast.makeText(this, "Permission micro refusée", Toast.LENGTH_SHORT).show()
            }
            LOCATION_PERMISSION_CODE -> {
                if (granted) fetchAndSendLocation()
                else Toast.makeText(this, "Permission localisation refusée", Toast.LENGTH_SHORT).show()
            }
            CAMERA_PERMISSION_CODE -> {
                if (granted) launchCamera()
                else Toast.makeText(this, "Permission caméra refusée", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Cycle de vie ─────────────────────────────────────────────────────────
    override fun onPause() {
        super.onPause()
        playerIdle?.pause(); playerSpeaking?.pause(); audioPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        playerIdle?.start(); playerSpeaking?.start()
        if (isSpeaking) audioPlayer?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerIdle?.release(); playerIdle = null
        playerSpeaking?.release(); playerSpeaking = null
        audioPlayer?.release(); audioPlayer = null
        speechRecognizer?.destroy(); speechRecognizer = null
    }
}
