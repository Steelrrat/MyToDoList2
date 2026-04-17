package com.example.mytodolist2;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.speech.RecognizerIntent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TaskAdapter taskAdapter;
    private ArrayList<Task> taskList;
    private FloatingActionButton fabAdd;
    private static final String PREFS_NAME = "TodoPrefs";
    private static final String PREFS_THEME = "theme_prefs";
    private static final String THEME_KEY = "is_dark_theme";
    private static final String CHANNEL_ID = "todo_channel";
    
    private ActivityResultLauncher<Intent> voiceLauncher;
    private ActivityResultLauncher<String> fileLauncher;
    
    private String tempFileUri = null;
    private Date tempDate = null;
    private View currentDialogView = null;
    private EditText currentEditText = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Загрузка темы ДО создания активности
        SharedPreferences themePrefs = getSharedPreferences(PREFS_THEME, MODE_PRIVATE);
        boolean isDark = themePrefs.getBoolean(THEME_KEY, false);
        if (isDark) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        fabAdd = findViewById(R.id.fabAdd);

        taskList = new ArrayList<>();
        loadTasks();
        sortTasksByDateAndStatus();

        taskAdapter = new TaskAdapter(taskList, 
            this::showViewDialog,
            this::copyTask,
            position -> deleteTaskWithConfirm(position),
            (position, task) -> showEditDialog(position, task),
            this::toggleTaskDone
        );
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(taskAdapter);

        setupSwipe();
        setupLaunchers();
        createNotificationChannel();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        fabAdd.setOnClickListener(v -> showAddDialog());
        
        FloatingActionButton fabInfo = findViewById(R.id.fabInfo);
        fabInfo.setOnClickListener(v -> showInstructions());
        
        FloatingActionButton fabTheme = findViewById(R.id.fabTheme);
        fabTheme.setOnClickListener(v -> toggleTheme());
        
        checkTodayReminders();
        showWelcomeMessage();
    }

    private void toggleTheme() {
        SharedPreferences themePrefs = getSharedPreferences(PREFS_THEME, MODE_PRIVATE);
        boolean isDark = themePrefs.getBoolean(THEME_KEY, false);
        
        if (isDark) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            themePrefs.edit().putBoolean(THEME_KEY, false).apply();
            Toast.makeText(this, "Светлая тема", Toast.LENGTH_SHORT).show();
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            themePrefs.edit().putBoolean(THEME_KEY, true).apply();
            Toast.makeText(this, "Ночная тема", Toast.LENGTH_SHORT).show();
        }
        recreate();
    }

    private void showInstructions() {
        new AlertDialog.Builder(this)
            .setTitle("Как пользоваться")
            .setMessage(
                "ДОБАВИТЬ ЗАДАЧУ\n" +
                "Нажмите зеленую кнопку с плюсом в правом нижнем углу\n\n" +
                "ОТМЕТИТЬ ВЫПОЛНЕННУЮ\n" +
                "Нажмите на квадратик слева от задачи\n\n" +
                "ПРОСМОТРЕТЬ ЗАДАЧУ\n" +
                "Нажмите на саму задачу\n\n" +
                "СКОПИРОВАТЬ ТЕКСТ\n" +
                "Свайп влево по задаче\n\n" +
                "УДАЛИТЬ ЗАДАЧУ\n" +
                "Свайп вправо по задаче\n\n" +
                "РЕДАКТИРОВАТЬ ЗАДАЧУ\n" +
                "Нажмите и удерживайте задачу\n\n" +
                "ДОБАВИТЬ ДАТУ ИЛИ ФАЙЛ\n" +
                "При создании или редактировании используйте кнопки в диалоге\n\n" +
                "ГОЛОСОВОЙ ВВОД\n" +
                "При создании задачи нажмите на кнопку с микрофоном\n\n" +
                "РЕАКЦИИ\n" +
                "В режиме просмотра нажмите сердечко, молнию или котенка\n\n" +
                "ПРИКРЕПЛЕННЫЙ ФАЙЛ\n" +
                "В режиме просмотра нажмите на название файла, чтобы открыть\n\n" +
                "НОЧНАЯ ТЕМА\n" +
                "Нажмите на фиолетовую кнопку с точками внизу"
            )
            .setPositiveButton("Понятно", null)
            .show();
    }

    private void toggleTaskDone(int position) {
        Task task = taskList.get(position);
        task.setDone(!task.isDone());
        saveTasks();
        sortTasksByDateAndStatus();
        taskAdapter.notifyDataSetChanged();
        
        if (task.isDone()) {
            Toast.makeText(this, "Задача выполнена!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Отметка снята", Toast.LENGTH_SHORT).show();
        }
    }

    private void sortTasksByDateAndStatus() {
        Collections.sort(taskList, (t1, t2) -> {
            if (t1.isDone() != t2.isDone()) {
                return Boolean.compare(t1.isDone(), t2.isDone());
            }
            Date d1 = t1.getDate();
            Date d2 = t2.getDate();
            if (d1 == null && d2 == null) return 0;
            if (d1 == null) return 1;
            if (d2 == null) return -1;
            return d1.compareTo(d2);
        });
    }

    private void openFile(String uriString) {
        if (uriString == null || uriString.isEmpty()) {
            Toast.makeText(this, "Файл не найден", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = Uri.parse(uriString);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(uri);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Открыть файл"));
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть файл", Toast.LENGTH_SHORT).show();
        }
    }

    private void showViewDialog(Task task) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_view_task, null);
        
        TextView textTask = view.findViewById(R.id.viewTaskText);
        TextView textDate = view.findViewById(R.id.viewTaskDate);
        LinearLayout fileContainer = view.findViewById(R.id.fileContainer);
        TextView textFile = view.findViewById(R.id.viewTaskFile);
        
        Button buttonLike = view.findViewById(R.id.buttonLike);
        Button buttonLightning = view.findViewById(R.id.buttonLightning);
        Button buttonCat = view.findViewById(R.id.buttonCat);
        
        textTask.setText(task.getText());
        String dateStr = task.getFormattedDate();
        textDate.setText(dateStr.equals("Без даты") ? dateStr : dateStr);
        
        if (task.hasFile() && task.getFileUri() != null) {
            String fileUri = task.getFileUri();
            String fileName = fileUri;
            if (fileName.contains("/")) {
                fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
            }
            textFile.setText(fileName);
            fileContainer.setVisibility(View.VISIBLE);
            fileContainer.setOnClickListener(v -> openFile(task.getFileUri()));
        }
        
        String currentReaction = task.getReaction();
        updateReactionButtons(buttonLike, buttonLightning, buttonCat, currentReaction);
        
        buttonLike.setOnClickListener(v -> {
            String newReaction = "like";
            if (currentReaction != null && currentReaction.equals(newReaction)) {
                task.setReaction(null);
                updateReactionButtons(buttonLike, buttonLightning, buttonCat, null);
                Toast.makeText(this, "Реакция убрана", Toast.LENGTH_SHORT).show();
            } else {
                task.setReaction(newReaction);
                updateReactionButtons(buttonLike, buttonLightning, buttonCat, newReaction);
                Toast.makeText(this, "Like", Toast.LENGTH_SHORT).show();
            }
            saveTasks();
            taskAdapter.notifyDataSetChanged();
        });
        
        buttonLightning.setOnClickListener(v -> {
            String newReaction = "lightning";
            if (currentReaction != null && currentReaction.equals(newReaction)) {
                task.setReaction(null);
                updateReactionButtons(buttonLike, buttonLightning, buttonCat, null);
                Toast.makeText(this, "Реакция убрана", Toast.LENGTH_SHORT).show();
            } else {
                task.setReaction(newReaction);
                updateReactionButtons(buttonLike, buttonLightning, buttonCat, newReaction);
                Toast.makeText(this, "Молния", Toast.LENGTH_SHORT).show();
            }
            saveTasks();
            taskAdapter.notifyDataSetChanged();
        });
        
        buttonCat.setOnClickListener(v -> {
            String newReaction = "cat";
            if (currentReaction != null && currentReaction.equals(newReaction)) {
                task.setReaction(null);
                updateReactionButtons(buttonLike, buttonLightning, buttonCat, null);
                Toast.makeText(this, "Реакция убрана", Toast.LENGTH_SHORT).show();
            } else {
                task.setReaction(newReaction);
                updateReactionButtons(buttonLike, buttonLightning, buttonCat, newReaction);
                Toast.makeText(this, "Котёнок", Toast.LENGTH_SHORT).show();
            }
            saveTasks();
            taskAdapter.notifyDataSetChanged();
        });
        
        builder.setTitle("Просмотр задачи").setView(view).setPositiveButton("Закрыть", null).show();
    }
    
    private void updateReactionButtons(Button like, Button lightning, Button cat, String selectedReaction) {
        like.setAlpha(0.5f);
        lightning.setAlpha(0.5f);
        cat.setAlpha(0.5f);
        
        if (selectedReaction != null) {
            switch (selectedReaction) {
                case "like":
                    like.setAlpha(1.0f);
                    break;
                case "lightning":
                    lightning.setAlpha(1.0f);
                    break;
                case "cat":
                    cat.setAlpha(1.0f);
                    break;
            }
        }
    }

    private void setupSwipe() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override public boolean onMove(RecyclerView r, RecyclerView.ViewHolder vh, RecyclerView.ViewHolder t) { return false; }
            @Override public void onSwiped(RecyclerView.ViewHolder vh, int dir) {
                int pos = vh.getAdapterPosition();
                if (pos == -1) return;
                if (dir == ItemTouchHelper.LEFT) {
                    copyTask(taskList.get(pos));
                    taskAdapter.notifyItemChanged(pos);
                } else {
                    deleteTaskWithConfirm(pos);
                }
            }
        }).attachToRecyclerView(recyclerView);
    }

    private void showWelcomeMessage() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean("welcome_shown", false)) {
            new AlertDialog.Builder(this).setTitle("Добро пожаловать!")
                .setMessage("Нажмите на квадратик - отметить выполненным\nНажмите на задачу - просмотр\nСвайп влево - копировать\nСвайп вправо - удалить\nДолгое нажатие - редактировать\nФиолетовая кнопка - ночная тема")
                .setPositiveButton("Понятно", (d, w) -> prefs.edit().putBoolean("welcome_shown", true).apply())
                .show();
        }
    }

    private void setupLaunchers() {
        voiceLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && currentEditText != null) {
                    ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        String voiceText = matches.get(0);
                        String currentText = currentEditText.getText().toString();
                        String newText = currentText + (currentText.isEmpty() ? "" : " ") + voiceText;
                        currentEditText.setText(newText);
                        currentEditText.setSelection(newText.length());
                    }
                }
            }
        );

        fileLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null && currentDialogView != null) {
                    String fileName = getFileName(uri);
                    TextView tv = currentDialogView.findViewById(R.id.textAttachedFile);
                    if (tv != null) {
                        tv.setText(fileName);
                        tv.setTag(uri.toString());
                        tv.setVisibility(View.VISIBLE);
                    }
                    tempFileUri = uri.toString();
                }
            }
        );
    }

    private String getFileName(Uri uri) {
        String name = "Файл";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx != -1) name = c.getString(idx);
            }
        }
        return name;
    }

    private void showAddDialog() {
        tempDate = null;
        tempFileUri = null;
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, null);
        currentDialogView = view;
        
        EditText editText = view.findViewById(R.id.editTaskText);
        currentEditText = editText;
        
        Button btnVoice = view.findViewById(R.id.buttonVoice);
        Button btnDate = view.findViewById(R.id.buttonDate);
        Button btnAttach = view.findViewById(R.id.buttonAttach);
        TextView txtDate = view.findViewById(R.id.textSelectedDate);
        TextView txtFile = view.findViewById(R.id.textAttachedFile);
        
        btnVoice.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 100);
            }
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите задачу...");
            voiceLauncher.launch(intent);
        });
        
        btnDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view1, year, month, day) -> {
                Calendar selected = Calendar.getInstance();
                selected.set(year, month, day);
                tempDate = selected.getTime();
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                txtDate.setText(sdf.format(tempDate));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        
        btnAttach.setOnClickListener(v -> fileLauncher.launch("*/*"));
        
        builder.setView(view)
            .setPositiveButton("Добавить", (dialog, which) -> {
                String text = editText.getText().toString().trim();
                if (!text.isEmpty()) {
                    taskList.add(new Task(text, tempDate, tempFileUri));
                    sortTasksByDateAndStatus();
                    taskAdapter.notifyDataSetChanged();
                    saveTasks();
                    Toast.makeText(this, "Добавлено!", Toast.LENGTH_SHORT).show();
                    if (tempDate != null && isSameDay(tempDate, new Date())) showNotification(text);
                    currentEditText = null;
                } else {
                    Toast.makeText(this, "Введите задачу!", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Отмена", (d, w) -> { currentEditText = null; })
            .show();
    }

    private void showEditDialog(int pos, Task oldTask) {
        tempDate = oldTask.getDate();
        tempFileUri = oldTask.getFileUri();
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_task, null);
        currentDialogView = view;
        
        EditText editText = view.findViewById(R.id.editTaskText);
        currentEditText = editText;
        editText.setText(oldTask.getText());
        editText.selectAll();
        
        Button btnVoice = view.findViewById(R.id.buttonEditVoice);
        Button btnDate = view.findViewById(R.id.buttonEditDate);
        Button btnAttach = view.findViewById(R.id.buttonEditAttach);
        Button btnRemoveFile = view.findViewById(R.id.buttonRemoveFile);
        TextView txtDate = view.findViewById(R.id.textEditDate);
        TextView txtFile = view.findViewById(R.id.textEditFile);
        LinearLayout fileLayout = view.findViewById(R.id.fileManageLayout);
        
        if (tempDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            txtDate.setText(sdf.format(tempDate));
        }
        if (tempFileUri != null) {
            String fileName = getFileName(Uri.parse(tempFileUri));
            txtFile.setText(fileName);
            fileLayout.setVisibility(View.VISIBLE);
        }
        
        btnVoice.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 100);
            }
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите задачу...");
            voiceLauncher.launch(intent);
        });
        
        btnDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view1, year, month, day) -> {
                Calendar selected = Calendar.getInstance();
                selected.set(year, month, day);
                tempDate = selected.getTime();
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                txtDate.setText(sdf.format(tempDate));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        
        btnAttach.setOnClickListener(v -> fileLauncher.launch("*/*"));
        btnRemoveFile.setOnClickListener(v -> {
            tempFileUri = null;
            txtFile.setText("");
            fileLayout.setVisibility(View.GONE);
            Toast.makeText(this, "Файл удалён", Toast.LENGTH_SHORT).show();
        });
        
        builder.setView(view)
            .setPositiveButton("Сохранить", (dialog, which) -> {
                String newText = editText.getText().toString().trim();
                if (!newText.isEmpty()) {
                    taskList.set(pos, new Task(newText, tempDate, tempFileUri, oldTask.getReaction(), oldTask.isDone()));
                    sortTasksByDateAndStatus();
                    taskAdapter.notifyDataSetChanged();
                    saveTasks();
                    Toast.makeText(this, "Изменено!", Toast.LENGTH_SHORT).show();
                    currentEditText = null;
                } else {
                    Toast.makeText(this, "Задача не может быть пустой!", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Отмена", (d, w) -> { currentEditText = null; })
            .show();
    }

    private void copyTask(Task task) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Задача", task.getText()));
        Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show();
    }

    private void deleteTaskWithConfirm(int pos) {
        new AlertDialog.Builder(this)
            .setTitle("Удалить задачу?")
            .setMessage(taskList.get(pos).getText())
            .setPositiveButton("Удалить", (d, w) -> {
                taskList.remove(pos);
                sortTasksByDateAndStatus();
                taskAdapter.notifyDataSetChanged();
                saveTasks();
                Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Отмена", (d, w) -> taskAdapter.notifyItemChanged(pos))
            .show();
    }

    private void saveTasks() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor ed = prefs.edit();
        ed.putInt("count", taskList.size());
        for (int i = 0; i < taskList.size(); i++) {
            Task t = taskList.get(i);
            ed.putString("task_" + i, t.getText());
            if (t.getDate() != null) ed.putLong("date_" + i, t.getDate().getTime());
            if (t.getFileUri() != null) ed.putString("file_" + i, t.getFileUri());
            if (t.getReaction() != null) ed.putString("reaction_" + i, t.getReaction());
            ed.putBoolean("done_" + i, t.isDone());
        }
        ed.apply();
    }

    private void loadTasks() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int cnt = prefs.getInt("count", 0);
        taskList.clear();
        for (int i = 0; i < cnt; i++) {
            String text = prefs.getString("task_" + i, "");
            if (!text.isEmpty()) {
                long d = prefs.getLong("date_" + i, 0);
                Date date = d > 0 ? new Date(d) : null;
                String file = prefs.getString("file_" + i, null);
                String reaction = prefs.getString("reaction_" + i, null);
                boolean done = prefs.getBoolean("done_" + i, false);
                taskList.add(new Task(text, date, file, reaction, done));
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManagerCompat.from(this).createNotificationChannel(
                new NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("Напоминания").build());
        }
    }

    private void showNotification(String text) {
        NotificationManagerCompat.from(this).notify(1, 
            new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_today)
                .setContentTitle("Напоминание").setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT).build());
    }

    private void checkTodayReminders() {
        Date today = new Date();
        for (Task t : taskList) {
            if (t.getDate() != null && isSameDay(t.getDate(), today)) showNotification(t.getText());
        }
    }

    private boolean isSameDay(Date d1, Date d2) {
        Calendar c1 = Calendar.getInstance(), c2 = Calendar.getInstance();
        c1.setTime(d1); c2.setTime(d2);
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }
}