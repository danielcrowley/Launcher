package app.lawnchair.todo

import android.content.Context
import app.lawnchair.data.AppDatabase
import kotlinx.coroutines.flow.Flow

class TodoRepository(context: Context) {

    private val dao = AppDatabase.INSTANCE.get(context).todoDao()

    val allTodos: Flow<List<TodoItem>> = dao.observeAll()

    suspend fun insert(title: String) {
        dao.insert(TodoItem(title = title))
    }

    suspend fun toggle(item: TodoItem) {
        dao.update(item.copy(completed = !item.completed))
    }

    suspend fun delete(item: TodoItem) {
        dao.delete(item)
    }
}
