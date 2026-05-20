package com.example.aistudyassistant_moudatir.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aistudyassistant_moudatir.data.model.Conversation
import com.example.aistudyassistant_moudatir.databinding.ItemConversationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationsAdapter(
    private val onClick: (Conversation) -> Unit,
    private val onDelete: (Conversation) -> Unit
) : ListAdapter<Conversation, ConversationsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class ViewHolder(private val b: ItemConversationBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(conv: Conversation) {
            b.tvConvTitle.text = conv.title
            b.tvConvTime.text  = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                .format(Date(conv.timestamp))
            b.root.setOnClickListener { onClick(conv) }
            b.btnDeleteConv.setOnClickListener { onDelete(conv) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(old: Conversation, new: Conversation) = old.id == new.id
        override fun areContentsTheSame(old: Conversation, new: Conversation) = old == new
    }
}
