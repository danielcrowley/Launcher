package app.lawnchair.todo

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.R

class TodoAdapter(
    private val onToggle: (TodoItem) -> Unit,
) : ListAdapter<TodoItem, TodoAdapter.ViewHolder>(DIFF_CALLBACK) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkBox: CheckBox = view.findViewById(R.id.todo_checkbox)
        val title: TextView = view.findViewById(R.id.todo_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_todo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        // Update checkbox without triggering the listener
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = item.completed
        holder.title.text = item.title
        if (item.completed) {
            holder.title.paintFlags = holder.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.title.paintFlags = holder.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
        holder.checkBox.setOnCheckedChangeListener { _, _ -> onToggle(item) }
        holder.itemView.setOnClickListener { onToggle(item) }
    }

    fun getItemAt(position: Int): TodoItem = getItem(position)

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TodoItem>() {
            override fun areItemsTheSame(oldItem: TodoItem, newItem: TodoItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: TodoItem, newItem: TodoItem) =
                oldItem == newItem
        }
    }
}
