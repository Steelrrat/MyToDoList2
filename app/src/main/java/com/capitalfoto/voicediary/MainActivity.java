package com.capitalfoto.voicediary;

import android.Manifest;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
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
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
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
import androidx.recyclerview.widget.GridLayoutManager;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private TaskAdapter taskAdapter;
    private ArrayList<Task> taskList;
    private FloatingActionButton fabAdd;
    private Button fabTheme;;
    private static final String PREFS_NAME = "TodoPrefs";
    private static final String PREFS_THEME = "theme_prefs";
    private static final String THEME_KEY = "is_dark_theme";
    private static final String PREFS_EXACT_ALARM_SHOWN = "exact_alarm_shown";
    private ActivityResultLauncher<Intent> voiceLauncher;
    private ActivityResultLauncher<String> fileLauncher;

    private String tempStoredFilePath = null;
    private Date tempDate = null;
    private int tempHour = -1;
    private int tempMinute = -1;

    private EditText currentEditText = null;
    private AlertDialog currentDialog = null;
    private ActivityResultLauncher<String[]> permissionsLauncher;
    private EmojiRecyclerAdapter emojiAdapter;

    private RewardManager rewardManager;
    private EmojiUnlockManager emojiUnlockManager;

    private final Handler saveHandler = new Handler(Looper.getMainLooper());
    private final Runnable saveRunnable = this::saveTasksImmediate;
    private final Object saveLock = new Object();

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

        createTasksFilesDir();

        recyclerView = findViewById(R.id.recyclerView);
        fabAdd = findViewById(R.id.fabAdd);
        fabTheme = findViewById(R.id.fabTheme);
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

// Добавляем обработчик клика на файл
        taskAdapter.setOnFileClickListener(task -> {
            if (task.hasFile() && task.getFilePath() != null) {
                openFile(task.getFilePath());
            } else {
                Toast.makeText(this, "Файл не найден", Toast.LENGTH_SHORT).show();
            }
        });

        // Добавляем обработчик клика на смайл в карточке
        taskAdapter.setOnEmojiClickListener((task, position) -> {
            showQuickEmojiPicker(task, position);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(taskAdapter);

        setupSwipe();
        setupLaunchers();
        rewardManager = new RewardManager(this);
        emojiUnlockManager = new EmojiUnlockManager(this);
        setupPermissionsLauncher();

        fabAdd.setOnClickListener(v -> showAddDialog());

        updateThemeIcon(fabTheme);
        fabTheme.setOnClickListener(v -> toggleTheme());

        showWelcomeMessage();
        checkAndShowExactAlarmForFirstTime();
    }

    private void showQuickEmojiPicker(Task task, int position) {
        // Создаем диалог с сеткой эмодзи
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_quick_emoji_picker, null);

        RecyclerView emojiRecyclerView = view.findViewById(R.id.quickEmojiRecyclerView);
        Button unlockAllButton = view.findViewById(R.id.quickUnlockAllButton);
        TextView hintText = view.findViewById(R.id.quickUnlockHintText);
        TextView currentEmojiText = view.findViewById(R.id.currentEmojiText);

        // Показываем текущий смайл задачи
        String currentReaction = task.getReaction();
        if (currentReaction != null && !currentReaction.isEmpty()) {
            currentEmojiText.setText("Текущий смайл: " + currentReaction);
            currentEmojiText.setVisibility(View.VISIBLE);
        } else {
            currentEmojiText.setVisibility(View.GONE);
        }

        // Настройка сетки смайлов
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 6);
        emojiRecyclerView.setLayoutManager(gridLayoutManager);
        emojiRecyclerView.setNestedScrollingEnabled(true);

        boolean allUnlocked = emojiUnlockManager.areAllNewEmojisUnlocked();
        List<String> allEmojis;

        if (allUnlocked) {
            allEmojis = emojiUnlockManager.getAllAvailableEmojis();
            unlockAllButton.setVisibility(View.GONE);
            if (hintText != null) hintText.setVisibility(View.GONE);
        } else {
            allEmojis = emojiUnlockManager.getAllAvailableEmojis();
            unlockAllButton.setVisibility(View.VISIBLE);
            unlockAllButton.setOnClickListener(v -> {
                showRewardedAdForUnlockAll(() -> {
                    showQuickEmojiPicker(task, position);
                });
            });
            if (hintText != null) hintText.setVisibility(View.VISIBLE);
        }

        EmojiRecyclerAdapter adapter = new EmojiRecyclerAdapter(
                allEmojis,
                emoji -> {
                    // Обновляем реакцию задачи
                    task.setReaction(emoji);
                    saveTasksDebounced();
                    taskAdapter.notifyItemChanged(position);
                    Toast.makeText(this, "Смайл " + emoji + " добавлен", Toast.LENGTH_SHORT).show();
                }
        );
        emojiRecyclerView.setAdapter(adapter);

        builder.setTitle("Выберите смайл")
                .setView(view)
                .setNegativeButton("Отмена", null)
                .show();
    }
    private void createTasksFilesDir() {
        File tasksDir = new File(getFilesDir(), "task_files");
        if (!tasksDir.exists()) {
            tasksDir.mkdirs();
        }
    }

    private String copyFileToInternalStorage(Uri sourceUri) {
        try {
            String fileName = getFileName(sourceUri);
            String uniqueName = System.currentTimeMillis() + "_" + fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            File destFile = new File(getFilesDir(), "task_files/" + uniqueName);

            ContentResolver resolver = getContentResolver();
            try (InputStream inputStream = resolver.openInputStream(sourceUri);
                 FileOutputStream outputStream = new FileOutputStream(destFile)) {

                if (inputStream == null) return null;

                byte[] buffer = new byte[8192];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
                outputStream.flush();
            }

            return destFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e("FILE_COPY", "Ошибка копирования файла", e);
            Toast.makeText(this, "Не удалось сохранить файл", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private boolean isFileExists(String filePath) {
        if (filePath == null) return false;
        File file = new File(filePath);
        return file.exists() && file.length() > 0;
    }

    private void openFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            Toast.makeText(this, "Файл не найден", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                Toast.makeText(this, "Файл не найден", Toast.LENGTH_SHORT).show();
                return;
            }

            Uri uri = androidx.core.content.FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(uri);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Открыть файл"));
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть файл", Toast.LENGTH_SHORT).show();
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

                    String oldFileUri = prefs.getString("file_" + i, null);
                    String filePath = null;
                    if (oldFileUri != null && !oldFileUri.trim().isEmpty() && !oldFileUri.trim().equals("null")) {
                        if (oldFileUri.startsWith("/") || oldFileUri.contains("/task_files/")) {
                            filePath = oldFileUri;
                        } else {
                            try {
                                Uri uri = Uri.parse(oldFileUri);
                                String newPath = copyFileToInternalStorage(uri);
                                if (newPath != null) {
                                    filePath = newPath;
                                }
                            } catch (Exception e) {
                                Log.e("MIGRATE", "Не удалось мигрировать файл: " + oldFileUri, e);
                            }
                        }
                    }

                    String reaction = prefs.getString("reaction_" + i, null);
                    if (reaction != null && (reaction.trim().isEmpty() || reaction.trim().equals("null"))) {
                        reaction = null;
                    } else if (reaction != null) {
                        reaction = reaction.trim();
                    }

                    boolean done = prefs.getBoolean("done_" + i, false);
                    int hour = prefs.getInt("hour_" + i, -1);
                    int minute = prefs.getInt("minute_" + i, -1);

                    String id = prefs.getString("id_" + i, null);
                    if (id == null) id = UUID.randomUUID().toString();

                    Task task = new Task(text, date, filePath, reaction, done, hour, minute);
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
        if (currentVersion < 3) {
            SharedPreferences.Editor ed = prefs.edit();
            int count = prefs.getInt("count", 0);
            for (int i = 0; i < count; i++) {
                String id = prefs.getString("id_" + i, null);
                if (id == null) {
                    ed.putString("id_" + i, UUID.randomUUID().toString());
                }
            }
            ed.putInt("data_version", 3);
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
                .setMessage("Включите уведомления в настройках телефона для приложения Голосовой ежедневник")
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
            Toast.makeText(this, "☀️ Светлая тема", Toast.LENGTH_SHORT).show();
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            themePrefs.edit().putBoolean(THEME_KEY, true).apply();
            Toast.makeText(this, "🌙 Ночная тема", Toast.LENGTH_SHORT).show();
        }

        updateThemeIcon(fabTheme);
        recreate();
    }

    private void updateThemeIcon(Button fabTheme) {
        SharedPreferences themePrefs = getSharedPreferences(PREFS_THEME, MODE_PRIVATE);
        boolean isDark = themePrefs.getBoolean(THEME_KEY, false);

        if (isDark) {
            fabTheme.setText("☀️");
            fabTheme.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF9800));
        } else {
            fabTheme.setText("🌙");
            fabTheme.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF9C27B0));
        }
    }

    private void toggleTaskDone(int position) {
        Task task = taskList.get(position);
        boolean newState = !task.isDone();
        task.setDone(newState);

        saveTasksImmediate();

        int oldPosition = position;
        sortTasks();
        int newPosition = taskList.indexOf(task);

        if (newPosition != oldPosition) {
            taskAdapter.notifyItemMoved(oldPosition, newPosition);
            taskAdapter.notifyItemChanged(newPosition);
        } else {
            taskAdapter.notifyItemChanged(oldPosition);
        }

        Toast.makeText(this, newState ? "✅ Выполнено" : "❌ Отмена", Toast.LENGTH_SHORT).show();

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

    private void showViewDialog(Task task) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_view_task, null);
        TextView textTask = view.findViewById(R.id.viewTaskText);
        TextView textDate = view.findViewById(R.id.viewTaskDate);
        CardView dateTimeContainer = view.findViewById(R.id.dateTimeContainer);
        CardView fileContainer = view.findViewById(R.id.fileContainer);
        TextView btnOpenFile = view.findViewById(R.id.btnOpenFile);

        RecyclerView emojiRecyclerView = view.findViewById(R.id.emojiRecyclerView);
        Button unlockAllButton = view.findViewById(R.id.unlockAllButton);
        TextView hintText = view.findViewById(R.id.unlockHintText);

        textTask.setText(task.getText());

        // --- НАСТРОЙКА ДАТЫ И ВРЕМЕНИ ---
        String formattedDate = task.getFormattedDate();
        if (formattedDate != null && !formattedDate.equals("Без даты")) {
            textDate.setText(formattedDate);
            dateTimeContainer.setVisibility(View.VISIBLE);

            // Устанавливаем цвет даты в зависимости от темы
            SharedPreferences themePrefs = getSharedPreferences(PREFS_THEME, MODE_PRIVATE);
            boolean isDark = themePrefs.getBoolean(THEME_KEY, false);
            int accentColor;
            if (isDark) {
                // Темная тема - синий
                accentColor = ContextCompat.getColor(this, R.color.dark_accent);
            } else {
                // Светлая тема - зеленый
                accentColor = ContextCompat.getColor(this, R.color.light_accent);
            }
            textDate.setTextColor(accentColor);
        } else {
            dateTimeContainer.setVisibility(View.GONE);
        }

        // --- НАСТРОЙКА ФАЙЛА ---
        String filePath = task.getFilePath();
        if (task.hasFile() && filePath != null && isFileExists(filePath)) {
            fileContainer.setVisibility(View.VISIBLE);

            // Устанавливаем цвет кнопки в зависимости от темы
            SharedPreferences themePrefs = getSharedPreferences(PREFS_THEME, MODE_PRIVATE);
            boolean isDark = themePrefs.getBoolean(THEME_KEY, false);

            int buttonColor;
            if (isDark) {
                // Темная тема - синий
                buttonColor = ContextCompat.getColor(this, R.color.dark_accent);
            } else {
                // Светлая тема - зеленый
                buttonColor = ContextCompat.getColor(this, R.color.light_accent);
            }

            fileContainer.setCardBackgroundColor(buttonColor);

            // Обработчик открытия файла
            View.OnClickListener openFileListener = v -> openFile(filePath);

            // Клик на весь контейнер
            fileContainer.setOnClickListener(openFileListener);
            // Клик на кнопку
            btnOpenFile.setOnClickListener(openFileListener);
        } else {
            fileContainer.setVisibility(View.GONE);
            if (task.hasFile()) {
                task.setFilePath(null);
                saveTasksDebounced();
                taskAdapter.notifyDataSetChanged();
            }
        }

        // --- НАСТРОЙКА СМАЙЛОВ ---
        if (emojiRecyclerView != null) {
            GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 6);
            emojiRecyclerView.setLayoutManager(gridLayoutManager);
            emojiRecyclerView.setNestedScrollingEnabled(true);

            boolean allUnlocked = emojiUnlockManager.areAllNewEmojisUnlocked();

            if (allUnlocked) {
                unlockAllButton.setVisibility(View.GONE);
                if (hintText != null) hintText.setVisibility(View.GONE);
            } else {
                unlockAllButton.setVisibility(View.VISIBLE);
                unlockAllButton.setOnClickListener(v -> {
                    showRewardedAdForUnlockAll(() -> {
                        setupEmojiRecyclerView(emojiRecyclerView, unlockAllButton, task);
                    });
                });
                if (hintText != null) hintText.setVisibility(View.VISIBLE);
            }

            setupEmojiRecyclerView(emojiRecyclerView, unlockAllButton, task);
        }

        AlertDialog dialog = builder.setTitle(null)
                .setView(view)
                .setPositiveButton("Закрыть", null)
                .show();

        // Устанавливаем цвет кнопки "Закрыть" в соответствии с темой
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positiveButton != null) {
            SharedPreferences themePrefs = getSharedPreferences(PREFS_THEME, MODE_PRIVATE);
            boolean isDark = themePrefs.getBoolean(THEME_KEY, false);

            if (isDark) {
                positiveButton.setTextColor(ContextCompat.getColor(this, R.color.dark_accent));
            } else {
                positiveButton.setTextColor(ContextCompat.getColor(this, R.color.light_accent));
            }
        }
    }

    private void setupEmojiRecyclerView(RecyclerView recyclerView, Button unlockAllButton, Task task) {
        boolean allUnlocked = emojiUnlockManager.areAllNewEmojisUnlocked();

        // Находим текст подсказки
        View parentView = (View) recyclerView.getParent();
        TextView hintText = null;
        if (parentView != null) {
            hintText = parentView.findViewById(R.id.unlockHintText);
        }

        List<String> allEmojis = new ArrayList<>();

        if (allUnlocked) {
            allEmojis.addAll(emojiUnlockManager.getAllAvailableEmojis());
            if (unlockAllButton != null) {
                unlockAllButton.setVisibility(View.GONE);
            }
            if (hintText != null) {
                hintText.setVisibility(View.GONE);
            }
        } else {
            allEmojis.addAll(emojiUnlockManager.getAllAvailableEmojis());
            if (unlockAllButton != null) {
                unlockAllButton.setVisibility(View.VISIBLE);
                unlockAllButton.setOnClickListener(v -> {
                    showRewardedAdForUnlockAll(() -> {
                        setupEmojiRecyclerView(recyclerView, unlockAllButton, task);
                    });
                });
            }
            if (hintText != null) {
                hintText.setVisibility(View.VISIBLE);
            }
        }

        EmojiRecyclerAdapter adapter = new EmojiRecyclerAdapter(
                allEmojis,
                emoji -> {
                    task.setReaction(emoji);
                    saveTasksDebounced();
                    taskAdapter.notifyDataSetChanged();
                    Toast.makeText(this, "Смайл " + emoji + " добавлен", Toast.LENGTH_SHORT).show();

                    // Обновляем отображение в списке, но не показываем в диалоге
                    // (в диалоге больше нет блока с текущей реакцией)
                }
        );
        recyclerView.setAdapter(adapter);
    }

    private void showRewardedAdForUnlockAll(Runnable onUnlocked) {
        if (isFinishing() || isDestroyed()) {
            Toast.makeText(this, "Не удалось показать рекламу", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Загрузка рекламы...");
        progressDialog.setCancelable(false);

        // ВСЕГДА показываем прогресс (без проверки isMockMode)
        progressDialog.show();

        rewardManager.showRewardedAd(
                () -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    runOnUiThread(() -> {
                        emojiUnlockManager.unlockAllNewEmojis();
                        Toast.makeText(this, "🎉 ВСЕ СМАЙЛЫ РАЗБЛОКИРОВАНЫ! 🎉", Toast.LENGTH_LONG).show();
                        if (onUnlocked != null) onUnlocked.run();
                    });
                },
                () -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    runOnUiThread(() -> {
                        Toast.makeText(this, "⚠️ Реклама не загрузилась. Проверьте интернет и попробуйте позже.", Toast.LENGTH_LONG).show();
                    });
                }
        );

        // Таймаут для скрытия прогресса (без проверки isMockMode)
        new Handler().postDelayed(() -> {
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
                Toast.makeText(this, "Реклама не загрузилась", Toast.LENGTH_SHORT).show();
            }
        }, 10000);
    }

    private void setupSwipe() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView r, RecyclerView.ViewHolder vh, RecyclerView.ViewHolder t) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder vh, int dir) {
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
                        tempStoredFilePath = copyFileToInternalStorage(uri);
                        if (currentDialog != null) {
                            View view = currentDialog.findViewById(R.id.textAttachedFile);
                            if (view == null) view = currentDialog.findViewById(R.id.textEditFile);
                            if (view instanceof TextView) {
                                TextView tv = (TextView) view;
                                if (tempStoredFilePath != null) {
                                    String fileName = getFileName(uri);
                                    tv.setText("📎 " + fileName);
                                    tv.setVisibility(View.VISIBLE);
                                } else {
                                    tv.setText("Ошибка прикрепления");
                                    tv.setVisibility(View.VISIBLE);
                                }
                            }
                        }
                    }
                }
        );
    }

    private void showAddDialog() {
        tempDate = null;
        tempStoredFilePath = null;
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

        // Находим карточки (если в dialog_add_task.xml тоже добавили карточки)
        // Если нет, то пропустите этот блок

        // Определяем цвет темы
        SharedPreferences themePrefs = getSharedPreferences(PREFS_THEME, MODE_PRIVATE);
        boolean isDark = themePrefs.getBoolean(THEME_KEY, false);
        int accentColor;
        if (isDark) {
            accentColor = ContextCompat.getColor(this, R.color.dark_accent);
        } else {
            accentColor = ContextCompat.getColor(this, R.color.light_accent);
        }

        // Устанавливаем цвет текста
        txtDate.setTextColor(accentColor);
        txtFile.setTextColor(accentColor);

        btnVoice.setOnClickListener(v -> startVoiceInput());
        btnDate.setOnClickListener(v -> showDatePicker(txtDate));
        btnTime.setOnClickListener(v -> showTimePicker(txtDate, null));
        btnAttach.setOnClickListener(v -> fileLauncher.launch("*/*"));

        AlertDialog dialog = builder.setView(view)
                .setPositiveButton("Добавить", (d, which) -> {
                    String text = editText.getText().toString().trim();
                    if (!text.isEmpty()) {
                        Task newTask = new Task(text, tempDate, tempStoredFilePath, null, false, tempHour, tempMinute);
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

        // Устанавливаем цвет кнопок диалога
        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            if (positiveButton != null) {
                positiveButton.setTextColor(accentColor);
            }
            if (negativeButton != null) {
                negativeButton.setTextColor(accentColor);
            }
        });

        dialog.setOnDismissListener(dialogInterface -> clearDialogReferences());
        currentDialog = dialog;
        dialog.show();
    }

    private void showEditDialog(int pos, Task oldTask) {
        tempDate = oldTask.getDate();
        tempStoredFilePath = oldTask.getFilePath();
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
        TextView txtTime = view.findViewById(R.id.textEditTime);  // НОВЫЙ TextView для времени
        TextView txtFile = view.findViewById(R.id.textEditFile);
        LinearLayout fileManageLayout = view.findViewById(R.id.fileManageLayout);
        CardView dateCardView = view.findViewById(R.id.dateCardView);
        CardView timeCardView = view.findViewById(R.id.timeCardView);  // НОВАЯ карточка времени
        CardView fileCardView = view.findViewById(R.id.fileCardView);

        // Определяем цвета темы
        SharedPreferences themePrefs = getSharedPreferences(PREFS_THEME, MODE_PRIVATE);
        boolean isDark = themePrefs.getBoolean(THEME_KEY, false);
        int accentColor;
        if (isDark) {
            accentColor = ContextCompat.getColor(this, R.color.dark_accent);
        } else {
            accentColor = ContextCompat.getColor(this, R.color.light_accent);
        }

        // Устанавливаем цвет текста для даты, времени и файла
        txtDate.setTextColor(accentColor);
        txtTime.setTextColor(accentColor);
        txtFile.setTextColor(accentColor);

        // Обновляем отображение даты и времени
        updateDateDisplay(txtDate);
        updateTimeDisplay(txtTime);  // НОВЫЙ метод для времени

        if (tempStoredFilePath != null && !tempStoredFilePath.isEmpty() && isFileExists(tempStoredFilePath)) {
            File file = new File(tempStoredFilePath);
            String fileName = file.getName();
            if (fileName.contains("_") && fileName.indexOf("_") < fileName.length() - 1) {
                String displayName = fileName.substring(fileName.indexOf("_") + 1);
                txtFile.setText(displayName);
            } else {
                txtFile.setText(fileName);
            }
            fileManageLayout.setVisibility(View.VISIBLE);
        } else if (tempStoredFilePath != null) {
            tempStoredFilePath = null;
        }

        // Обработчики для кнопок
        btnVoice.setOnClickListener(v -> startVoiceInput());
        btnDate.setOnClickListener(v -> showDatePicker(txtDate));
        btnTime.setOnClickListener(v -> showTimePicker(txtDate, txtTime));  // Обновленный метод
        btnAttach.setOnClickListener(v -> fileLauncher.launch("*/*"));

        // Обработчики для кликабельных карточек
        dateCardView.setOnClickListener(v -> showDatePicker(txtDate));
        timeCardView.setOnClickListener(v -> showTimePicker(txtDate, txtTime));  // Открывает выбор времени
        fileCardView.setOnClickListener(v -> fileLauncher.launch("*/*"));

        AlertDialog dialog = builder.setView(view)
                .setPositiveButton("Сохранить", (d, which) -> {
                    String newText = editText.getText().toString().trim();
                    if (!newText.isEmpty()) {
                        Task updatedTask = new Task(newText, tempDate, tempStoredFilePath,
                                oldTask.getReaction(), oldTask.isDone(), tempHour, tempMinute);
                        updatedTask.setId(oldTask.getId());

                        if (updatedTask.getId() == null || updatedTask.getId().isEmpty()) {
                            updatedTask.setId(UUID.randomUUID().toString());
                        }

                        taskList.set(pos, updatedTask);
                        sortTasks();
                        taskAdapter.notifyDataSetChanged();
                        saveTasksDebounced();

                        if (oldTask.getId() != null) {
                            NotificationHelper.cancelNotification(MainActivity.this, oldTask.getId());
                        }

                        if (tempHour >= 0 && tempMinute >= 0 && !updatedTask.isDone()) {
                            try {
                                NotificationHelper.scheduleNotification(MainActivity.this, updatedTask);
                            } catch (Exception e) {
                                Log.e("EditDialog", "scheduleNotification error", e);
                            }
                        }
                        Toast.makeText(MainActivity.this, "Изменено", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Задача не может быть пустой", Toast.LENGTH_SHORT).show();
                    }
                    clearDialogReferences();
                })
                .setNegativeButton("Отмена", (d, w) -> clearDialogReferences())
                .create();

        // Устанавливаем цвет кнопок диалога
        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            if (positiveButton != null) {
                positiveButton.setTextColor(accentColor);
            }
            if (negativeButton != null) {
                negativeButton.setTextColor(accentColor);
            }
        });

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
            // Находим TextView времени и обновляем его цвет, если нужно
            if (currentDialog != null) {
                TextView txtTime = currentDialog.findViewById(R.id.textEditTime);
                if (txtTime != null) {
                    updateTimeDisplay(txtTime);
                }
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker(TextView txtDate, TextView txtTime) {
        Calendar c = Calendar.getInstance();
        int hour = tempHour >= 0 ? tempHour : c.get(Calendar.HOUR_OF_DAY);
        int minute = tempMinute >= 0 ? tempMinute : c.get(Calendar.MINUTE);
        new TimePickerDialog(this, (view1, hourOfDay, minuteOfHour) -> {
            tempHour = hourOfDay;
            tempMinute = minuteOfHour;
            updateDateDisplay(txtDate);
            updateTimeDisplay(txtTime);  // Обновляем отображение времени

            if (tempDate == null) {
                showNotificationTimeToast();
            }
        }, hour, minute, true).show();
    }

    private void updateDateDisplay(TextView txtDate) {
        if (txtDate == null) return;

        // Определяем цвет для даты
        SharedPreferences themePrefs = getSharedPreferences(PREFS_THEME, MODE_PRIVATE);
        boolean isDark = themePrefs.getBoolean(THEME_KEY, false);
        int accentColor;
        if (isDark) {
            accentColor = ContextCompat.getColor(this, R.color.dark_accent);
        } else {
            accentColor = ContextCompat.getColor(this, R.color.light_accent);
        }
        txtDate.setTextColor(accentColor);

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
                    String filePath = task.getFilePath();
                    if (filePath != null) {
                        File file = new File(filePath);
                        if (file.exists()) {
                            file.delete();
                        }
                    }
                    NotificationHelper.cancelNotification(this, task.getId());
                    taskList.remove(pos);
                    if (taskList.isEmpty()) {
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
        saveHandler.postDelayed(saveRunnable, 500);
    }

    private void saveTasksImmediate() {
        synchronized (saveLock) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
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
                if (t.hasFile() && t.getFilePath() != null) {
                    ed.putString("file_" + i, t.getFilePath());
                } else {
                    ed.remove("file_" + i);
                }
                if (t.getReaction() != null && !t.getReaction().isEmpty()) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && alarmManager.canScheduleExactAlarms()) {
                rescheduleAllNotifications();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (rewardManager != null) {
            rewardManager.destroy();
        }
    }
    private void updateTimeDisplay(TextView txtTime) {
        if (txtTime == null) return;

        // Определяем цвет для времени
        SharedPreferences themePrefs = getSharedPreferences(PREFS_THEME, MODE_PRIVATE);
        boolean isDark = themePrefs.getBoolean(THEME_KEY, false);
        int accentColor;
        if (isDark) {
            accentColor = ContextCompat.getColor(this, R.color.dark_accent);
        } else {
            accentColor = ContextCompat.getColor(this, R.color.light_accent);
        }
        txtTime.setTextColor(accentColor);

        if (tempHour >= 0 && tempMinute >= 0) {
            txtTime.setText(String.format("%02d:%02d", tempHour, tempMinute));
        } else {
            txtTime.setText("Время не выбрано");
        }
    }

}