package app.lawnchair.todo

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "TodoItems")
data class TodoItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val completed: Boolean = false,
)
