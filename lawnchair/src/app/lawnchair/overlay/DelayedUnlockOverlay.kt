package app.lawnchair.overlay

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.lawnchair.todo.TodoAdapter
import app.lawnchair.todo.TodoRepository
import com.android.launcher3.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class DelayedUnlockOverlay : AppCompatActivity() {

    private lateinit var repository: TodoRepository
    private lateinit var adapter: TodoAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var countdownText: TextView
    private var countDownTimer: CountDownTimer? = null
    private var locked = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over the lock screen and turn screen on if needed
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )

        setContentView(R.layout.activity_delayed_unlock)

        // Block back navigation during the countdown
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!locked) finish()
                }
            },
        )

        repository = TodoRepository(this)

        progressBar = findViewById(R.id.unlock_progress)
        countdownText = findViewById(R.id.unlock_countdown_text)

        setupRecyclerView()
        setupAddButton()
        observeTodos()
        startCountdown()
    }

    private fun setupRecyclerView() {
        adapter = TodoAdapter { item ->
            lifecycleScope.launch { repository.toggle(item) }
        }

        val recyclerView: RecyclerView = findViewById(R.id.todo_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Swipe left or right to delete a task
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
        ) {
            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val item = adapter.getItemAt(viewHolder.adapterPosition)
                lifecycleScope.launch { repository.delete(item) }
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)
    }

    private fun setupAddButton() {
        val fab: FloatingActionButton = findViewById(R.id.fab_add_task)
        fab.setOnClickListener { showAddTaskDialog() }
    }

    private fun observeTodos() {
        lifecycleScope.launch {
            repository.allTodos.collect { todos ->
                adapter.submitList(todos)
            }
        }
    }

    private fun startCountdown() {
        val totalMs = COUNTDOWN_SECONDS * 1000L
        progressBar.max = COUNTDOWN_SECONDS
        progressBar.progress = COUNTDOWN_SECONDS

        countDownTimer = object : CountDownTimer(totalMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000).toInt() + 1
                progressBar.progress = secondsLeft
                countdownText.text = getString(R.string.unlock_countdown_format, secondsLeft)
            }

            override fun onFinish() {
                locked = false
                progressBar.progress = 0
                countdownText.text = getString(R.string.unlock_countdown_done)
                finish()
            }
        }.start()
    }

    private fun showAddTaskDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.todo_hint_task_title)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.todo_add_task_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) {
                    lifecycleScope.launch { repository.insert(title) }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    companion object {
        private const val COUNTDOWN_SECONDS = 20

        fun createIntent(context: Context): Intent =
            Intent(context, DelayedUnlockOverlay::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
    }
}
