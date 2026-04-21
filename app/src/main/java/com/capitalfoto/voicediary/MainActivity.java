package com.capitalfoto.voicediary;

import android.Manifest;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.util.Log;
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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private TaskAdapter taskAdapter;
    private ArrayList<Task> taskList;
    private FloatingActionButton fabAdd;
    private static final String PREFS_NAME = "TodoPrefs";
    private static final String PREFS_THEME = "theme_prefs";
    private static final String THEME_KEY = "is_dark_theme";
    private static final String PREFS_EXACT_ALARM_SHOWN = "exact_alarm_shown";
    private ActivityResultLauncher<Intent> voiceLauncher;
    private ActivityResultLauncher<String> fileLauncher;

    private String tempFileUri = null;
    private Date tempDate = null;
    private int tempHour = -1;
    private int tempMinute = -1;

    private EditText currentEditText = null;
    private AlertDialog currentDialog = null;
    private ActivityResultLauncher<String[]> permissionsLauncher;

    private final Handler saveHandler = new Handler(Looper.getMainLooper());
    private final Runnable saveRunnable = this::saveTasksImmediate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences themePrefs = getSharedPreferences(PREFS_THEME, MODE_PRIVATE);
        boolean isDark = themePrefs.getBoolean(THEME_KEY, false);
        if (isDark) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        NotificationHelper.createNotificationChannel(this);

        recyclerView = findViewById(R.id.recyclerView);
        fabAdd = findViewById(R.id.fabAdd);
        taskList = new ArrayList<>();

        loadTasksAndMigrate();

        String openTaskId = getIntent().getStringExtra("open_task_id");
        if (openTaskId != null) {
            new Handler().postDelayed(() -> {
                for (int i = 0; i < taskList.size(); i++) {
                    if (taskList.get(i).getId().equals(openTaskId)) {
                        showViewDialog(taskList.get(i));
                        break;
                    }
                }
            }, 500);
        }

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
        setupPermissionsLauncher();

        fabAdd.setOnClickListener(v -> showAddDialog());
        FloatingActionButton fabTheme = findViewById(R.id.fabTheme);
        fabTheme.setOnClickListener(v -> toggleTheme());

        showWelcomeMessage();
        restoreTasksFromBackup();
    }

    // ========== ПРОВЕРКА ТОЧНЫХ БУДИЛЬНИКОВ ==========
    private void checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

                int showCount = prefs.getInt("exact_alarm_show_count", 0);
                boolean neverAsk = prefs.getBoolean("exact_alarm_never_ask", false);

                if (!neverAsk && showCount < 3) {
                    prefs.edit().putInt("exact_alarm_show_count", showCount + 1).apply();

                    new AlertDialog.Builder(this)
                            .setTitle("⏰ Точные уведомления")
                            .setMessage("Для точного времени уведомлений необходимо разрешение.\n\n" +
                                    "Без него напоминания могут задерживаться на 10-15 минут.\n\n" +
                                    "Разрешить?")
                            .setPositiveButton("Разрешить", (dialog, which) -> {
                                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                            })
                            .setNegativeButton("Отмена", null)
                            .setNeutralButton("Не напоминать", (dialog, which) -> {
                                prefs.edit().putBoolean("exact_alarm_never_ask", true).apply();
                            })
                            .show();
                }
            }
        }
    }

    private void rescheduleAllNotifications() {
        for (Task task : taskList) {
            if (task.hasTime() && !task.isDone()) {
                NotificationHelper.cancelNotification(this, task.getId());
                NotificationHelper.scheduleNotification(this, task);
            }
        }
        Toast.makeText(this, "Уведомления обновлены", Toast.LENGTH_SHORT).show();
    }

    private void checkAndShowExactAlarmForFirstTime() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean alreadyShown = prefs.getBoolean(PREFS_EXACT_ALARM_SHOWN, false);

            if (alreadyShown) {
                return;
            }

            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("⏰ Разрешение на точные уведомления")
                        .setMessage("Чтобы уведомления приходили ТОЧНО ВОВРЕМЯ, включите разрешение на точные будильники.\n\n" +
                                "Нажмите «Открыть настройки» и включите переключатель.")
                        .setPositiveButton("Открыть настройки", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("Не сейчас", null)
                        .show();

                prefs.edit().putBoolean(PREFS_EXACT_ALARM_SHOWN, true).apply();
            }
        }
    }

    private void loadTasksAndMigrate() {
        new Thread(() -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            migrateDataIfNeeded(prefs);

            int cnt = prefs.getInt("count", 0);
            ArrayList<Task> newList = new ArrayList<>();

            for (int i = 0; i < cnt; i++) {
                String text = prefs.getString("task_" + i, "");
                if (text != null && !text.trim().isEmpty()) {
                    long d = prefs.getLong("date_" + i, 0);
                    Date date = d > 0 ? new Date(d) : null;

                    // ИСПРАВЛЕНО: Очищаем НЕКОРРЕКТНЫЕ значения из prefs
                    String file = prefs.getString("file_" + i, null);
                    if (file != null && (file.trim().isEmpty() || file.trim().equals("null") || file.trim().length() < 5 ||
                            (!file.trim().contains(":") && !file.trim().contains("/")))) {
                        file = null;
                        // Удаляем некорректное значение из SharedPreferences
                        prefs.edit().remove("file_" + i).apply();
                    } else if (file != null) {
                        file = file.trim();
                    }

                    String reaction = prefs.getString("reaction_" + i, null);
                    if (reaction != null && (reaction.trim().isEmpty() || reaction.trim().equals("null") ||
                            (!reaction.trim().equals("like") && !reaction.trim().equals("lightning") && !reaction.trim().equals("cat")))) {
                        reaction = null;
                        // Удаляем некорректное значение из SharedPreferences
                        prefs.edit().remove("reaction_" + i).apply();
                    } else if (reaction != null) {
                        reaction = reaction.trim();
                    }

                    boolean done = prefs.getBoolean("done_" + i, false);
                    int hour = prefs.getInt("hour_" + i, -1);
                    int minute = prefs.getInt("minute_" + i, -1);

                    String id = prefs.getString("id_" + i, null);
                    if (id == null) id = UUID.randomUUID().toString();

                    Task task = new Task(text, date, file, reaction, done, hour, minute);
                    task.setId(id);
                    newList.add(task);
                }
            }

            runOnUiThread(() -> {
                taskList.clear();
                taskList.addAll(newList);
                sortTasks();
                taskAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void migrateDataIfNeeded(SharedPreferences prefs) {
        int currentVersion = prefs.getInt("data_version", 0);
        SharedPreferences.Editor ed = prefs.edit();

        if (currentVersion < 2) {
            int count = prefs.getInt("count", 0);
            for (int i = 0; i < count; i++) {
                String file = prefs.getString("file_" + i, null);
                if (file != null && (file.isEmpty() || file.equals("null"))) {
                    ed.remove("file_" + i);
                }

                String reaction = prefs.getString("reaction_" + i, null);
                if (reaction != null && (reaction.isEmpty() || reaction.equals("null"))) {
                    ed.remove("reaction_" + i);
                }

                String id = prefs.getString("id_" + i, null);
                if (id == null) {
                    ed.putString("id_" + i, UUID.randomUUID().toString());
                }
            }
            ed.putInt("data_version", 2);

            for (int i = 0; i < count; i++) {
                String reaction = prefs.getString("reaction_" + i, null);
                if (reaction != null && (reaction.isEmpty() || reaction.equals("null") ||
                        (!reaction.equals("like") && !reaction.equals("lightning") && !reaction.equals("cat")))) {
                    ed.remove("reaction_" + i);
                }
            }
            ed.apply();
        }
    }

    private void setupPermissionsLauncher() {
        permissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean notificationsGranted = true;
                    boolean microphoneGranted = true;
                    for (java.util.Map.Entry<String, Boolean> entry : result.entrySet()) {
                        if (entry.getKey().equals(Manifest.permission.POST_NOTIFICATIONS) && !entry.getValue()) {
                            notificationsGranted = false;
                        }
                        if (entry.getKey().equals(Manifest.permission.RECORD_AUDIO) && !entry.getValue()) {
                            microphoneGranted = false;
                        }
                    }
                    if (!notificationsGranted) {
                        showNotificationSettingsHelp();
                    }
                    if (!microphoneGranted) {
                        Toast.makeText(this, "Голосовой ввод будет недоступен", Toast.LENGTH_LONG).show();
                    }

                    requestBatteryOptimization();
                }
        );
    }

    private void showWelcomeMessage() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean("welcome_shown", false)) {
            new AlertDialog.Builder(this)
                    .setTitle("Добро пожаловать!")
                    .setMessage("✓ Нажмите на квадратик - отметить выполненным\n" +
                            "✓ Нажмите на задачу - просмотр\n" +
                            "✓ Свайп влево - копировать\n" +
                            "✓ Свайп вправо - удалить\n" +
                            "✓ Долгое нажатие - редактировать")
                    .setPositiveButton("Понятно", (d, w) -> {
                        prefs.edit().putBoolean("welcome_shown", true).apply();
                        requestPermissionsNow();
                    })
                    .show();
        } else {
            requestPermissionsNow();
        }
    }

    private void requestPermissionsNow() {
        java.util.ArrayList<String> permissionsList = new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsList.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(Manifest.permission.RECORD_AUDIO);
        }
        if (!permissionsList.isEmpty()) {
            permissionsLauncher.launch(permissionsList.toArray(new String[0]));
        } else {
            requestBatteryOptimization();
        }
    }

    private void showNotificationSettingsHelp() {
        new AlertDialog.Builder(this)
                .setTitle("🔔 Настройка уведомлений")
                .setMessage("Включите уведомления в настройках телефона для приложения MyToDoList2")
                .setPositiveButton("Понятно", null)
                .show();
    }

    private void requestBatteryOptimization() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean alreadyShown = prefs.getBoolean("battery_optimization_shown", false);

        if (alreadyShown) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                new AlertDialog.Builder(this)
                        .setTitle("🔋 Оптимизация батареи")
                        .setMessage("Для надёжной работы уведомлений отключите оптимизацию батареи для приложения.")
                        .setPositiveButton("Перейти", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("Не напоминать", null)
                        .show();

                prefs.edit().putBoolean("battery_optimization_shown", true).apply();
            }
        }
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

    private void toggleTaskDone(int position) {
        Task task = taskList.get(position);
        boolean newState = !task.isDone();
        task.setDone(newState);

        // Сохраняем
        saveTasksImmediate();

        // Обновляем только этот элемент (не весь список)
        taskAdapter.notifyItemChanged(position);

        Toast.makeText(this, newState ? "✅ Выполнено" : "❌ Отмена", Toast.LENGTH_SHORT).show();

        // Если задача отмечена как выполненная - отменяем уведомление
        if (newState && task.hasTime()) {
            NotificationHelper.cancelNotification(this, task.getId());
        }
    }

    private void sortTasks() {
        Collections.sort(taskList, (t1, t2) -> {
            if (t1.isDone() != t2.isDone()) {
                return Boolean.compare(t1.isDone(), t2.isDone());
            }
            if (t1.getDate() == null && t2.getDate() == null) return 0;
            if (t1.getDate() == null) return 1;
            if (t2.getDate() == null) return -1;
            return t1.getDate().compareTo(t2.getDate());
        });
    }

    private String getFileName(Uri uri) {
        if (uri == null) return "Файл";
        String name = "Файл";
        if ("file".equals(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null && path.contains("/")) {
                name = path.substring(path.lastIndexOf("/") + 1);
            }
            return name;
        }
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    String displayName = cursor.getString(nameIndex);
                    if (displayName != null && !displayName.isEmpty()) {
                        name = displayName;
                    }
                }
            }
        } catch (Exception e) {
            String path = uri.getPath();
            if (path != null && path.contains("/")) {
                name = path.substring(path.lastIndexOf("/") + 1);
            }
        }
        return name;
    }

    private boolean isFileAccessible(String uriString) {
        if (uriString == null) return false;
        try {
            Uri uri = Uri.parse(uriString);
            getContentResolver().openInputStream(uri).close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void openFile(String uriString) {
        if (uriString == null || uriString.isEmpty() || uriString.equals("null")) {
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
        textDate.setText(task.getFormattedDate());

        if (task.hasFile() && task.getFileUri() != null && isFileAccessible(task.getFileUri())) {
            String fileName = getFileName(Uri.parse(task.getFileUri()));
            textFile.setText(fileName);
            fileContainer.setVisibility(View.VISIBLE);
            fileContainer.setOnClickListener(v -> openFile(task.getFileUri()));
        } else if (task.hasFile()) {
            task.setFileUri(null);
            saveTasksDebounced();
            taskAdapter.notifyDataSetChanged();
        }

        String currentReaction = task.getReaction();
        updateReactionButtons(buttonLike, buttonLightning, buttonCat, currentReaction);

        buttonLike.setOnClickListener(v -> {
            String newReaction = "like";
            task.setReaction(currentReaction != null && currentReaction.equals(newReaction) ? null : newReaction);
            updateReactionButtons(buttonLike, buttonLightning, buttonCat, task.getReaction());
            Toast.makeText(this, task.getReaction() != null ? "❤️" : "Реакция убрана", Toast.LENGTH_SHORT).show();
            saveTasksDebounced();
            taskAdapter.notifyDataSetChanged();
        });

        buttonLightning.setOnClickListener(v -> {
            String newReaction = "lightning";
            task.setReaction(currentReaction != null && currentReaction.equals(newReaction) ? null : newReaction);
            updateReactionButtons(buttonLike, buttonLightning, buttonCat, task.getReaction());
            Toast.makeText(this, task.getReaction() != null ? "⚡" : "Реакция убрана", Toast.LENGTH_SHORT).show();
            saveTasksDebounced();
            taskAdapter.notifyDataSetChanged();
        });

        buttonCat.setOnClickListener(v -> {
            String newReaction = "cat";
            task.setReaction(currentReaction != null && currentReaction.equals(newReaction) ? null : newReaction);
            updateReactionButtons(buttonLike, buttonLightning, buttonCat, task.getReaction());
            Toast.makeText(this, task.getReaction() != null ? "😺" : "Реакция убрана", Toast.LENGTH_SHORT).show();
            saveTasksDebounced();
            taskAdapter.notifyDataSetChanged();
        });

        builder.setTitle(null)
                .setView(view)
                .setPositiveButton("Закрыть", null)
                .show();
    }

    private void updateReactionButtons(Button like, Button lightning, Button cat, String selectedReaction) {
        like.setAlpha(0.5f);
        lightning.setAlpha(0.5f);
        cat.setAlpha(0.5f);
        if (selectedReaction != null) {
            switch (selectedReaction) {
                case "like": like.setAlpha(1.0f); break;
                case "lightning": lightning.setAlpha(1.0f); break;
                case "cat": cat.setAlpha(1.0f); break;
            }
        }
    }

    private void setupSwipe() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override public boolean onMove(RecyclerView r, RecyclerView.ViewHolder vh, RecyclerView.ViewHolder t) {
                return false;
            }
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
                    if (uri != null) {
                        tempFileUri = uri.toString();
                        if (currentDialog != null) {
                            View view = currentDialog.findViewById(R.id.textAttachedFile);
                            if (view == null) view = currentDialog.findViewById(R.id.textEditFile);
                            if (view instanceof TextView) {
                                TextView tv = (TextView) view;
                                String fileName = getFileName(uri);
                                tv.setText(fileName);
                                tv.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                }
        );
    }

    private void showAddDialog() {
        tempDate = null;
        tempFileUri = null;
        tempHour = -1;
        tempMinute = -1;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, null);
        EditText editText = view.findViewById(R.id.editTaskText);
        currentEditText = editText;

        Button btnVoice = view.findViewById(R.id.buttonVoice);
        Button btnDate = view.findViewById(R.id.buttonDate);
        Button btnTime = view.findViewById(R.id.buttonTime);
        Button btnAttach = view.findViewById(R.id.buttonAttach);
        TextView txtDate = view.findViewById(R.id.textSelectedDate);
        TextView txtFile = view.findViewById(R.id.textAttachedFile);

        btnVoice.setOnClickListener(v -> startVoiceInput());
        btnDate.setOnClickListener(v -> showDatePicker(txtDate));
        btnTime.setOnClickListener(v -> showTimePicker(txtDate));
        btnAttach.setOnClickListener(v -> fileLauncher.launch("*/*"));

        AlertDialog dialog = builder.setView(view)
                .setPositiveButton("Добавить", (d, which) -> {
                    String text = editText.getText().toString().trim();
                    if (!text.isEmpty()) {
                        Task newTask = new Task(text, tempDate, tempFileUri, null, false, tempHour, tempMinute);
                        newTask.setId(UUID.randomUUID().toString());
                        taskList.add(newTask);
                        sortTasks();
                        taskAdapter.notifyDataSetChanged();
                        saveTasksDebounced();

                        if (tempHour >= 0 && tempMinute >= 0) {
                            NotificationHelper.scheduleNotification(MainActivity.this, newTask);
                        }
                        Toast.makeText(MainActivity.this, "Добавлено", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Введите задачу", Toast.LENGTH_SHORT).show();
                    }
                    clearDialogReferences();
                })
                .setNegativeButton("Отмена", (d, w) -> clearDialogReferences())
                .create();

        dialog.setOnDismissListener(dialogInterface -> clearDialogReferences());
        currentDialog = dialog;
        dialog.show();
    }

    private void showEditDialog(int pos, Task oldTask) {
        tempDate = oldTask.getDate();
        tempFileUri = oldTask.getFileUri();
        tempHour = oldTask.getHour();
        tempMinute = oldTask.getMinute();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_task, null);
        EditText editText = view.findViewById(R.id.editTaskText);
        currentEditText = editText;
        editText.setText(oldTask.getText());
        editText.selectAll();

        Button btnVoice = view.findViewById(R.id.buttonEditVoice);
        Button btnDate = view.findViewById(R.id.buttonEditDate);
        Button btnTime = view.findViewById(R.id.buttonEditTime);
        Button btnAttach = view.findViewById(R.id.buttonEditAttach);
        TextView txtDate = view.findViewById(R.id.textEditDate);
        TextView txtFile = view.findViewById(R.id.textEditFile);
        LinearLayout fileLayout = view.findViewById(R.id.fileManageLayout);

        updateDateDisplay(txtDate);

        if (tempFileUri != null && !tempFileUri.isEmpty() && !tempFileUri.equals("null") && isFileAccessible(tempFileUri)) {
            String fileName = getFileName(Uri.parse(tempFileUri));
            if (fileName.length() > 50) fileName = fileName.substring(0, 47) + "...";
            txtFile.setText(fileName);
            fileLayout.setVisibility(View.VISIBLE);
        } else if (tempFileUri != null) {
            tempFileUri = null;
        }

        btnVoice.setOnClickListener(v -> startVoiceInput());
        btnDate.setOnClickListener(v -> showDatePicker(txtDate));
        btnTime.setOnClickListener(v -> showTimePicker(txtDate));
        btnAttach.setOnClickListener(v -> fileLauncher.launch("*/*"));

        AlertDialog dialog = builder.setView(view)
                .setPositiveButton("Сохранить", (d, which) -> {
                    String newText = editText.getText().toString().trim();
                    if (!newText.isEmpty()) {
                        Task updatedTask = new Task(newText, tempDate, tempFileUri,
                                oldTask.getReaction(), oldTask.isDone(), tempHour, tempMinute);
                        updatedTask.setId(oldTask.getId());
                        taskList.set(pos, updatedTask);
                        sortTasks();
                        taskAdapter.notifyDataSetChanged();
                        saveTasksDebounced();

                        NotificationHelper.cancelNotification(MainActivity.this, oldTask.getId());
                        if (tempHour >= 0 && tempMinute >= 0 && !updatedTask.isDone()) {
                            NotificationHelper.scheduleNotification(MainActivity.this, updatedTask);
                        }
                        Toast.makeText(MainActivity.this, "Изменено", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Задача не может быть пустой", Toast.LENGTH_SHORT).show();
                    }
                    clearDialogReferences();
                })
                .setNegativeButton("Отмена", (d, w) -> clearDialogReferences())
                .create();

        dialog.setOnDismissListener(dialogInterface -> clearDialogReferences());
        currentDialog = dialog;
        dialog.show();
    }

    private void clearDialogReferences() {
        currentEditText = null;
        currentDialog = null;
    }

    private void startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 100);
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите задачу...");
        voiceLauncher.launch(intent);
    }

    private void showNotificationTimeToast() {
        if (tempHour >= 0 && tempMinute >= 0) {
            Calendar now = Calendar.getInstance();
            Calendar notifyTime = Calendar.getInstance();

            if (tempDate != null) {
                notifyTime.setTime(tempDate);
            }
            notifyTime.set(Calendar.HOUR_OF_DAY, tempHour);
            notifyTime.set(Calendar.MINUTE, tempMinute);
            notifyTime.set(Calendar.SECOND, 0);

            long diffMillis = notifyTime.getTimeInMillis() - now.getTimeInMillis();
            long diffMinutes = diffMillis / (60 * 1000);
            long diffHours = diffMinutes / 60;
            diffMinutes = diffMinutes % 60;

            String timeMessage;
            if (diffHours > 0) {
                timeMessage = String.format("🔔 Уведомление через %d ч %d мин", diffHours, diffMinutes);
            } else if (diffMinutes > 0) {
                timeMessage = String.format("🔔 Уведомление через %d мин", diffMinutes);
            } else {
                timeMessage = "🔔 Уведомление сработает менее чем через минуту";
            }

            Toast.makeText(this, timeMessage, Toast.LENGTH_LONG).show();
        }
    }

    private void showDatePicker(TextView targetTextView) {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view1, year, month, day) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, day);
            tempDate = selected.getTime();
            updateDateDisplay(targetTextView);
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker(TextView targetTextView) {
        Calendar c = Calendar.getInstance();
        int hour = tempHour >= 0 ? tempHour : c.get(Calendar.HOUR_OF_DAY);
        int minute = tempMinute >= 0 ? tempMinute : c.get(Calendar.MINUTE);
        new TimePickerDialog(this, (view1, hourOfDay, minuteOfHour) -> {
            tempHour = hourOfDay;
            tempMinute = minuteOfHour;
            updateDateDisplay(targetTextView);

            if (tempDate == null) {
                showNotificationTimeToast();
            }
        }, hour, minute, true).show();
    }

    private void updateDateDisplay(TextView txtDate) {
        if (txtDate == null) return;
        if (tempDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            String dateStr = sdf.format(tempDate);
            if (tempHour >= 0 && tempMinute >= 0) {
                dateStr += " " + String.format("%02d:%02d", tempHour, tempMinute);
            }
            txtDate.setText(dateStr);
        } else if (tempHour >= 0 && tempMinute >= 0) {
            txtDate.setText(String.format("%02d:%02d", tempHour, tempMinute));
        } else {
            txtDate.setText("Дата не выбрана");
        }
    }

    private void copyTask(Task task) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Задача", task.getText()));
        Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show();
    }

    private void deleteTaskWithConfirm(int pos) {
        Task task = taskList.get(pos);
        new AlertDialog.Builder(this)
                .setTitle("Удалить задачу?")
                .setMessage(task.getText())
                .setPositiveButton("Удалить", (d, w) -> {
                    NotificationHelper.cancelNotification(this, task.getId());
                    taskList.remove(pos);
                    if (taskList.isEmpty()) {
                        // Если список пуст, просто обновляем адаптер
                        taskAdapter.notifyDataSetChanged();
                    } else {
                        sortTasks();
                        taskAdapter.notifyDataSetChanged();
                    }
                    saveTasksDebounced();
                    Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", (d, w) -> taskAdapter.notifyItemChanged(pos))
                .show();
    }

    private void saveTasksDebounced() {
        saveHandler.removeCallbacks(saveRunnable);
        saveHandler.postDelayed(saveRunnable, 300);
    }

    private void saveTasksImmediate() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Защита от случайного удаления всех задач
        if (taskList.isEmpty()) {
            int previousCount = prefs.getInt("count", -1);
            if (previousCount > 0) {
                Log.e("SAVE", "Попытка сохранить пустой список! Было задач: " + previousCount);
                // Восстанавливаем из резервной копии
                restoreTasksFromBackup();
                return;
            }
        }

        SharedPreferences.Editor ed = prefs.edit();
        ed.putInt("count", taskList.size());
        for (int i = 0; i < taskList.size(); i++) {
            Task t = taskList.get(i);
            ed.putString("task_" + i, t.getText());
            if (t.getDate() != null) {
                ed.putLong("date_" + i, t.getDate().getTime());
            } else {
                ed.remove("date_" + i);
            }
            if (t.hasFile() && t.getFileUri() != null) {
                ed.putString("file_" + i, t.getFileUri());
            } else {
                ed.remove("file_" + i);
            }
            if (t.hasValidReaction()) {
                ed.putString("reaction_" + i, t.getReaction());
            } else {
                ed.remove("reaction_" + i);
            }
            ed.putBoolean("done_" + i, t.isDone());
            ed.putInt("hour_" + i, t.getHour());
            ed.putInt("minute_" + i, t.getMinute());
            ed.putString("id_" + i, t.getId());
        }
        ed.apply();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        String openTaskId = intent.getStringExtra("open_task_id");
        if (openTaskId != null) {
            for (int i = 0; i < taskList.size(); i++) {
                if (taskList.get(i).getId().equals(openTaskId)) {
                    showViewDialog(taskList.get(i));
                    break;
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveHandler.removeCallbacks(saveRunnable);
        saveTasksImmediate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Проверяем, дал ли пользователь разрешение после возврата из настроек
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && alarmManager.canScheduleExactAlarms()) {
                rescheduleAllNotifications();
            }
        }
    }

    private void restoreTasksFromBackup() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int backupCount = prefs.getInt("backup_count", 0);

        if (backupCount > 0 && taskList.isEmpty()) {
            ArrayList<Task> restoredList = new ArrayList<>();
            for (int i = 0; i < backupCount; i++) {
                String text = prefs.getString("backup_task_" + i, "");
                if (!text.isEmpty()) {
                    Task task = new Task(text, null, null, null, false, -1, -1);
                    task.setId(UUID.randomUUID().toString());
                    restoredList.add(task);
                }
            }
            if (!restoredList.isEmpty()) {
                taskList.clear();
                taskList.addAll(restoredList);
                sortTasks();
                taskAdapter.notifyDataSetChanged();
                saveTasksImmediate();
            }
        }
    }
}