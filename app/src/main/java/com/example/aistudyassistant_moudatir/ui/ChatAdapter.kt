package com.example.aistudyassistant_moudatir.ui

import android.animation.ObjectAnimator
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aistudyassistant_moudatir.data.model.Message
import com.example.aistudyassistant_moudatir.databinding.ItemMessageAiBinding
import com.example.aistudyassistant_moudatir.databinding.ItemMessageUserBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_AI   = 1
    }

    override fun getItemViewType(position: Int) =
        if (getItem(position).isFromUser) TYPE_USER else TYPE_AI

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserViewHolder(ItemMessageUserBinding.inflate(inflater, parent, false))
            else      -> AiViewHolder(ItemMessageAiBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is UserViewHolder -> holder.bind(getItem(position))
            is AiViewHolder   -> holder.bind(getItem(position))
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is AiViewHolder) holder.stopDots()
    }

    // ── User ─────────────────────────────────────────────────────────────────
    inner class UserViewHolder(private val b: ItemMessageUserBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(msg: Message) {
            // Texte
            if (msg.content.isNotEmpty()) {
                b.tvMessageContent.visibility = View.VISIBLE
                b.tvMessageContent.text = msg.content
            } else {
                b.tvMessageContent.visibility = View.GONE
            }

            // Image
            val imageUri = msg.imageUri
            if (imageUri != null) {
                b.cardImage.visibility = View.VISIBLE
                try {
                    val uri = when {
                        imageUri.startsWith("content://") || imageUri.startsWith("file://") ->
                            Uri.parse(imageUri)
                        else -> Uri.fromFile(File(imageUri))
                    }
                    b.ivMessageImage.setImageURI(uri)
                } catch (e: Exception) {
                    b.cardImage.visibility = View.GONE
                }
            } else {
                b.cardImage.visibility = View.GONE
            }

            b.tvTimestamp.text = formatTime(msg.timestamp)
        }
    }

    // ── AI ───────────────────────────────────────────────────────────────────
    inner class AiViewHolder(private val b: ItemMessageAiBinding) :
        RecyclerView.ViewHolder(b.root) {

        private val animators = mutableListOf<ObjectAnimator>()

        fun bind(msg: Message) {
            if (msg.content.isEmpty()) {
                // Afficher les 3 points animés
                b.tvMessageContent.visibility = View.GONE
                b.tvTimestamp.visibility      = View.GONE
                b.typingContainer.visibility  = View.VISIBLE
                startDots()
            } else {
                // Afficher le message texte
                stopDots()
                b.typingContainer.visibility  = View.GONE
                b.tvMessageContent.visibility = View.VISIBLE
                b.tvTimestamp.visibility      = View.VISIBLE
                b.tvMessageContent.text = msg.content
                b.tvTimestamp.text      = formatTime(msg.timestamp)
            }
        }

        fun startDots() {
            stopDots()
            listOf(b.dot1, b.dot2, b.dot3).forEachIndexed { i, dot ->
                dot.translationY = 0f
                ObjectAnimator.ofFloat(dot, "translationY", 0f, -7f, 0f).apply {
                    duration      = 500
                    startDelay    = (i * 170).toLong()
                    repeatCount   = ObjectAnimator.INFINITE
                    interpolator  = AccelerateDecelerateInterpolator()
                    start()
                }.also { animators.add(it) }
            }
        }

        fun stopDots() {
            animators.forEach { it.cancel() }
            animators.clear()
        }
    }

    private fun formatTime(ts: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

    class DiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(old: Message, new: Message) = old.id == new.id
        override fun areContentsTheSame(old: Message, new: Message) = old == new
    }
}
