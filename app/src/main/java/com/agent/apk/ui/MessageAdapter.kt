// File: app/src/main/java/com/agent/apk/ui/MessageAdapter.kt
package com.agent.apk.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.agent.apk.R
import com.google.android.material.card.MaterialCardView

/**
 * 消息列表适配器
 */
class MessageAdapter(
    private val messages: List<MessageItem>
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userMessageCard: MaterialCardView = itemView.findViewById(R.id.userMessageCard)
        val userMessageText: TextView = itemView.findViewById(R.id.userMessageText)
        val assistantMessageCard: MaterialCardView = itemView.findViewById(R.id.assistantMessageCard)
        val assistantMessageText: TextView = itemView.findViewById(R.id.assistantMessageText)
        val assistantThoughtText: TextView = itemView.findViewById(R.id.assistantThoughtText)
        val assistantActionText: TextView = itemView.findViewById(R.id.assistantActionText)
        val systemMessageText: TextView = itemView.findViewById(R.id.systemMessageText)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        // 重置可见性
        holder.userMessageCard.visibility = View.GONE
        holder.assistantMessageCard.visibility = View.GONE
        holder.systemMessageText.visibility = View.GONE

        when (message.role) {
            "user" -> {
                holder.userMessageCard.visibility = View.VISIBLE
                holder.userMessageText.text = message.content
            }
            "assistant" -> {
                holder.assistantMessageCard.visibility = View.VISIBLE
                holder.assistantMessageText.text = message.content

                if (message.thought != null && message.thought.isNotEmpty()) {
                    holder.assistantThoughtText.visibility = View.VISIBLE
                    holder.assistantThoughtText.text = "思考：${message.thought}"
                } else {
                    holder.assistantThoughtText.visibility = View.GONE
                }

                if (message.action != null && message.action.isNotEmpty()) {
                    holder.assistantActionText.visibility = View.VISIBLE
                    holder.assistantActionText.text = "执行：${message.action}"
                } else {
                    holder.assistantActionText.visibility = View.GONE
                }
            }
            "system" -> {
                holder.systemMessageText.visibility = View.VISIBLE
                holder.systemMessageText.text = message.content
            }
        }
    }

    override fun getItemCount() = messages.size
}
