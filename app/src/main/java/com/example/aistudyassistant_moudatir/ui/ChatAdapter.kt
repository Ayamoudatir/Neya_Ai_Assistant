package com.example.aistudyassistant_moudatir.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aistudyassistant_moudatir.data.model.Message
import com.example.aistudyassistant_moudatir.databinding.ItemMessageAiBinding
import com.example.aistudyassistant_moudatir.databinding.ItemMessageUserBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_AI = 1
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

    inner class UserViewHolder(private val b: ItemMessageUserBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(msg: Message) {
            b.tvMessageContent.text = msg.content
            b.tvTimestamp.text = formatTime(msg.timestamp)
        }
    }

    inner class AiViewHolder(private val b: ItemMessageAiBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(msg: Message) {
            b.tvMessageContent.text = msg.content
            b.tvTimestamp.text = formatTime(msg.timestamp)
        }
    }

    private fun formatTime(ts: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

    class DiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(old: Message, new: Message) = old.id == new.id
        override fun areContentsTheSame(old: Message, new: Message) = old == new
    }
}
