package com.example.aistudyassistant_moudatir.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Surface
import android.view.TextureView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aistudyassistant_moudatir.R
import com.example.aistudyassistant_moudatir.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var chatAdapter: ChatAdapter
    private val viewModel: ChatViewModel by viewModels()

    // ── Lecteur vidéo ────────────────────────────────────────────────────────
    private var mediaPlayer: MediaPlayer? = null
    private var currentVideoRes: Int = -1
    private var surfaceReady   : Boolean = false
    private var pendingVideoRes: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAvatarTexture()
        setupBlur()
        setupRecyclerView()
        setupSendButton()
        observeViewModel()
    }

    // ── Blur RenderEffect (API 31+) ──────────────────────────────────────
    private fun setupBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Fond global — blur fort sur le dégradé radial
            binding.bgBlurLayer.setRenderEffect(
                RenderEffect.createBlurEffect(80f, 80f, Shader.TileMode.CLAMP)
            )
            // Header frosted
            binding.headerBlurLayer.setRenderEffect(
                RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP)
            )
        }
    }

    // ── TextureView + MediaPlayer ─────────────────────────────────────────────
    private fun setupAvatarTexture() {
        binding.avatarTextureView.apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                    surfaceReady = true
                    // Joue la vidéo en attente ou l'idle par défaut
                    val toPlay = if (pendingVideoRes != -1) pendingVideoRes else R.raw.neya_idle
                    playVideo(toPlay)
                    pendingVideoRes = -1
                }
                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                    releasePlayer()
                    surfaceReady = false
                    return true
                }
                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
        }
    }

    private fun playVideo(resId: Int) {
        if (!surfaceReady) {
            // Surface pas encore prête → mémoriser pour jouer dès qu'elle l'est
            pendingVideoRes = resId
            return
        }
        if (currentVideoRes == resId && mediaPlayer?.isPlaying == true) return

        currentVideoRes = resId
        releasePlayer()

        try {
            val st      = binding.avatarTextureView.surfaceTexture ?: return
            val surface = Surface(st)

            val uri = Uri.parse("android.resource://$packageName/$resId")
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                setSurface(surface)
                isLooping = true
                setOnPreparedListener { start() }
                setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                prepareAsync()
            }
            surface.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releasePlayer() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    private fun setSpeakingState(speaking: Boolean) {
        playVideo(if (speaking) R.raw.neya_speaking else R.raw.neya_idle)
        binding.speakingIndicator.isVisible = speaking
        binding.waveformView.isAnimating    = speaking
    }

    // ── RecyclerView ─────────────────────────────────────────────────────────
    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.recyclerViewChat.apply {
            adapter       = chatAdapter
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
        }
    }

    // ── Envoi de message ─────────────────────────────────────────────────────
    private fun setupSendButton() {
        // Action clavier "Envoyer"
        binding.etMessage.setOnEditorActionListener { _, _, _ ->
            sendMessage(); true
        }

        // Toggle mic ↔ send selon le contenu du champ
        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hasText = !s.isNullOrBlank()
                updateActionButton(hasText)
            }
        })

        // État initial : micro
        updateActionButton(hasText = false)
    }

    private fun updateActionButton(hasText: Boolean) {
        binding.btnAction.apply {
            if (hasText) {
                setIconResource(R.drawable.ic_send)
                setOnClickListener { sendMessage() }
            } else {
                setIconResource(R.drawable.ic_mic)
                setOnClickListener { /* TODO : voix */ }
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
    // Vrai quand la réponse est reçue mais pas encore rendue à l'écran
    private var pendingStopSpeaking = false

    private fun observeViewModel() {
        viewModel.messages.observe(this) { messages ->
            chatAdapter.submitList(messages.toList()) {
                // Ce callback s'exécute APRÈS que le message est affiché
                if (messages.isNotEmpty())
                    binding.recyclerViewChat.scrollToPosition(messages.size - 1)
                // L'avatar s'arrête seulement maintenant que le message est visible
                if (pendingStopSpeaking) {
                    setSpeakingState(false)
                    pendingStopSpeaking = false
                }
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            if (loading) {
                setSpeakingState(true)
            } else {
                // Ne pas arrêter tout de suite — attendre que le message soit rendu
                pendingStopSpeaking = true
            }
        }

        viewModel.error.observe(this) { errorMsg ->
            binding.tvError.isVisible = errorMsg != null
            binding.tvError.text      = errorMsg
            // En cas d'erreur, arrêter l'avatar immédiatement (pas de message à attendre)
            if (errorMsg != null) {
                pendingStopSpeaking = false
                setSpeakingState(false)
            }
        }
    }

    // ── Cycle de vie ─────────────────────────────────────────────────────────
    override fun onPause()   { super.onPause();   mediaPlayer?.pause() }
    override fun onResume()  { super.onResume();  mediaPlayer?.start() }
    override fun onDestroy() { super.onDestroy(); releasePlayer()       }
}
