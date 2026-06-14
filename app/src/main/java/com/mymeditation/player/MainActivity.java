package com.mymeditation.player;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 101;
    private static final int MAX_TIMER_MINUTES = 300;
    private static final String PREF_NAME = "meditation_prefs";
    private static final String KEY_LAST_DIRECTORY = "last_directory";
    private static final String KEY_LAST_FILE = "last_file";
    private static final String KEY_SORT_MODE = "sort_mode";

    // 排序模式
    public static final int SORT_NAME = 0;
    public static final int SORT_SIZE = 1;
    public static final int SORT_DATE = 2;

    private RecyclerView recyclerViewDirectories;
    private RecyclerView recyclerViewFiles;
    private DirectoryAdapter directoryAdapter;
    private FileAdapter fileAdapter;
    private final List<DirectoryItem> directoryList = new ArrayList<>();
    private final List<FileItem> fileList = new ArrayList<>();
    private MusicService musicService;
    private boolean isServiceBound = false;

    private ImageButton buttonPlayPause, buttonStop;
    private ImageButton buttonPrevious, buttonNext;
    private ImageButton buttonPlayMode, buttonSetTimer;
    private MaterialButton buttonSort;
    private ImageButton buttonSettings;
    private TextView textViewStatus, textViewTimer, textViewDirectoryTitle, textViewFileTitle;
    private TextView textViewCurrentFile, textViewCurrentTime, textViewTotalTime;
    private SeekBar seekBarProgress;
    private ProgressBar progressBar;
    private MaterialToolbar toolbar;
    private View playerLayout;
    // A7: 空状态
    private View emptyStateView;
    private ImageView imageViewEmptyIcon;
    private TextView textViewEmptyTitle, textViewEmptyMessage;

    private String selectedDirectoryPath;
    private String selectedFilePath;
    private boolean isSeekBarUserSeeking = false;
    private ThemeManager themeManager;
    private int currentSortMode = SORT_NAME;
    private SharedPreferences prefs;

    // C1: 后台扫描目录/文件，避免大量文件时主线程卡顿
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int loadToken = 0;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.LocalBinder binder = (MusicService.LocalBinder) service;
            musicService = binder.getService();
            isServiceBound = true;
            // C2: 进度回调只更新进度条/时间文本，不再每秒全量刷新 UI
            musicService.setProgressCallback((currentPosition, duration, fileName) ->
                    runOnUiThread(() -> updateProgress(currentPosition, duration, fileName)));
            musicService.setTimerCallback(new MusicService.TimerCallback() {
                @Override
                public void onTimerUpdate(long remainingSeconds) {
                    runOnUiThread(() -> updateTimerDisplay(remainingSeconds));
                }

                @Override
                public void onTimerFinished() {
                    runOnUiThread(() -> {
                        hideTimerDisplay();
                        updateUI();
                    });
                }
            });
            // C2: 仅在播放状态真正变化时刷新 UI
            musicService.setPlaybackStateListener(state -> runOnUiThread(() -> updateUI()));
            // A3: 实际播放曲目变化时，同步文件列表高亮与滚动
            musicService.setTrackChangeListener((filePath, index) -> runOnUiThread(() -> onTrackChanged(filePath)));
            updateUI();
            updatePlayModeButton();
            if (musicService.isTimerActive()) {
                updateTimerDisplay(musicService.getRemainingSeconds());
            }
            // 重绑后，若服务已在播放某曲目，同步一次高亮
            List<String> pl = musicService.getPlaylist();
            int idx = musicService.getCurrentIndex();
            if (pl != null && !pl.isEmpty() && idx >= 0 && idx < pl.size()) {
                onTrackChanged(pl.get(idx));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
        }
    };

    // ===== 配置变更处理 =====

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("selectedDirectoryPath", selectedDirectoryPath);
        outState.putString("selectedFilePath", selectedFilePath);
        outState.putInt("sortMode", currentSortMode);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        selectedDirectoryPath = savedInstanceState.getString("selectedDirectoryPath");
        selectedFilePath = savedInstanceState.getString("selectedFilePath");
        currentSortMode = savedInstanceState.getInt("sortMode", SORT_NAME);
        if (selectedDirectoryPath != null) {
            updateBreadcrumb(new File(selectedDirectoryPath).getName());
            loadFiles(selectedDirectoryPath);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            themeManager = ThemeManager.getInstance(this);
            prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            currentSortMode = prefs.getInt(KEY_SORT_MODE, SORT_NAME);

            setContentView(R.layout.activity_main);
            initViews();
            applyTheme();
            checkPermissions();

            if (hasStoragePermission()) {
                loadDirectories();
                // 恢复上次打开的目录
                String lastDir = prefs.getString(KEY_LAST_DIRECTORY, null);
                if (lastDir != null && new File(lastDir).exists()) {
                    updateBreadcrumb(new File(lastDir).getName());
                    loadFiles(lastDir);
                }
            }
            bindMusicService();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, R.string.app_launch_error, Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        recyclerViewDirectories = findViewById(R.id.recyclerViewDirectories);
        recyclerViewFiles = findViewById(R.id.recyclerViewFiles);
        buttonPlayPause = findViewById(R.id.buttonPlayPause);
        buttonStop = findViewById(R.id.buttonStop);
        buttonPrevious = findViewById(R.id.buttonPrevious);
        buttonNext = findViewById(R.id.buttonNext);
        buttonPlayMode = findViewById(R.id.buttonPlayMode);
        buttonSetTimer = findViewById(R.id.buttonSetTimer);
        buttonSort = findViewById(R.id.buttonSort);
        buttonSettings = findViewById(R.id.buttonSettings);
        textViewStatus = findViewById(R.id.textViewStatus);
        textViewTimer = findViewById(R.id.textViewTimer);
        textViewDirectoryTitle = findViewById(R.id.textViewDirectoryTitle);
        textViewFileTitle = findViewById(R.id.textViewFileTitle);
        textViewCurrentFile = findViewById(R.id.textViewCurrentFile);
        textViewCurrentTime = findViewById(R.id.textViewCurrentTime);
        textViewTotalTime = findViewById(R.id.textViewTotalTime);
        seekBarProgress = findViewById(R.id.seekBarProgress);
        progressBar = findViewById(R.id.progressBar);
        playerLayout = findViewById(R.id.playerLayout);
        emptyStateView = findViewById(R.id.emptyStateView);
        imageViewEmptyIcon = findViewById(R.id.imageViewEmptyIcon);
        textViewEmptyTitle = findViewById(R.id.textViewEmptyTitle);
        textViewEmptyMessage = findViewById(R.id.textViewEmptyMessage);

        // A2: 单按钮播放/暂停切换
        buttonPlayPause.setOnClickListener(v -> togglePlayPause());
        buttonStop.setOnClickListener(v -> stopMusic());
        buttonPrevious.setOnClickListener(v -> playPrevious());
        buttonNext.setOnClickListener(v -> playNext());
        buttonPlayMode.setOnClickListener(v -> cyclePlayMode());
        buttonSetTimer.setOnClickListener(v -> showTimerDialog());
        // A4: 排序改为下拉菜单
        buttonSort.setText(R.string.sort_button_label);
        buttonSort.setOnClickListener(v -> showSortMenu());
        if (buttonSettings != null) {
            buttonSettings.setOnClickListener(v -> showThemeDialog());
        }
        // A7: 点击空状态重新扫描
        if (emptyStateView != null) {
            emptyStateView.setOnClickListener(v -> {
                if (hasStoragePermission()) {
                    loadDirectories();
                } else {
                    checkPermissions();
                }
            });
        }

        // Add spacing decorations to RecyclerViews
        int itemSpacing = (int) getResources().getDimension(R.dimen.item_spacing);
        recyclerViewDirectories.addItemDecoration(new SpacingItemDecoration(itemSpacing));
        recyclerViewFiles.addItemDecoration(new SpacingItemDecoration(itemSpacing));

        updatePlayModeButton();

        recyclerViewFiles.setLayoutManager(new LinearLayoutManager(this));

        seekBarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeekBarUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isSeekBarUserSeeking = false;
                if (isServiceBound && musicService != null) {
                    int duration = musicService.getDuration();
                    if (duration > 0) {
                        int progress = seekBar.getProgress();
                        int position = (int) ((long) progress * duration / 100);
                        musicService.seekTo(position);
                    }
                }
            }
        });
    }

    // ===== 排序（A4 下拉菜单）=====

    private void showSortMenu() {
        PopupMenu popup = new PopupMenu(this, buttonSort);
        popup.getMenu().add(0, SORT_NAME, 0, R.string.sort_by_name).setChecked(currentSortMode == SORT_NAME);
        popup.getMenu().add(0, SORT_SIZE, 1, R.string.sort_by_size).setChecked(currentSortMode == SORT_SIZE);
        popup.getMenu().add(0, SORT_DATE, 2, R.string.sort_by_date).setChecked(currentSortMode == SORT_DATE);
        popup.getMenu().setGroupCheckable(0, true, true);
        popup.setOnMenuItemClickListener(item -> {
            int newMode = item.getItemId();
            if (newMode == currentSortMode) return true;
            currentSortMode = newMode;
            prefs.edit().putInt(KEY_SORT_MODE, currentSortMode).apply();
            if (selectedDirectoryPath != null) {
                loadFiles(selectedDirectoryPath);
            }
            return true;
        });
        popup.show();
    }

    // ===== 播放模式 =====

    private void cyclePlayMode() {
        if (!isServiceBound || musicService == null) return;
        int currentMode = musicService.getPlayMode();
        int nextMode;
        switch (currentMode) {
            case MusicService.MODE_SEQUENCE:
                nextMode = MusicService.MODE_REPEAT_ONE;
                break;
            case MusicService.MODE_REPEAT_ONE:
                nextMode = MusicService.MODE_SHUFFLE;
                break;
            default:
                nextMode = MusicService.MODE_SEQUENCE;
                break;
        }
        musicService.setPlayMode(nextMode);
        updatePlayModeButton();
    }

    private void updatePlayModeButton() {
        if (buttonPlayMode == null) return;
        int mode = (isServiceBound && musicService != null) ? musicService.getPlayMode() : MusicService.MODE_SEQUENCE;
        switch (mode) {
            case MusicService.MODE_SEQUENCE:
                buttonPlayMode.setImageResource(R.drawable.ic_repeat);
                buttonPlayMode.setContentDescription(getString(R.string.mode_sequence));
                break;
            case MusicService.MODE_REPEAT_ONE:
                buttonPlayMode.setImageResource(R.drawable.ic_repeat_one);
                buttonPlayMode.setContentDescription(getString(R.string.mode_repeat_one));
                break;
            case MusicService.MODE_SHUFFLE:
                buttonPlayMode.setImageResource(R.drawable.ic_shuffle);
                buttonPlayMode.setContentDescription(getString(R.string.mode_shuffle));
                break;
        }
    }

    // ===== 上一首 / 下一首 =====

    private void playPrevious() {
        if (isServiceBound && musicService != null) {
            musicService.playPrevious();
        }
    }

    private void playNext() {
        if (isServiceBound && musicService != null) {
            musicService.playNext();
        }
    }

    // ===== 主题 =====

    private void showThemeDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.theme_dialog_title);

        RadioGroup radioGroup = new RadioGroup(this);
        radioGroup.setOrientation(LinearLayout.VERTICAL);
        radioGroup.setPadding(50, 30, 50, 30);

        String[] themeNames = {
                getString(R.string.theme_ocean_blue),
                getString(R.string.theme_forest_green),
                getString(R.string.theme_sunset_orange),
                getString(R.string.theme_lavender_purple),
                getString(R.string.theme_deep_blue)
        };
        int[] themeValues = {
                ThemeManager.THEME_CLAUDE_ORANGE,
                ThemeManager.THEME_CLAUDE_BLUE,
                ThemeManager.THEME_CLAUDE_GREEN,
                ThemeManager.THEME_CLAUDE_DARK,
                ThemeManager.THEME_CLAUDE_WARM
        };

        int currentTheme = themeManager.getCurrentTheme();
        for (int i = 0; i < themeNames.length; i++) {
            RadioButton radioButton = new RadioButton(this);
            radioButton.setText(themeNames[i]);
            radioButton.setId(themeValues[i]);
            radioButton.setPadding(20, 20, 20, 20);
            if (themeValues[i] == currentTheme) {
                radioButton.setChecked(true);
            }
            radioGroup.addView(radioButton);
        }

        builder.setView(radioGroup);
        builder.setPositiveButton(R.string.dialog_confirm, (dialog, which) -> {
            int selectedId = radioGroup.getCheckedRadioButtonId();
            if (selectedId != -1) {
                themeManager.setTheme(selectedId);
                applyTheme();
                Toast.makeText(this, getString(R.string.theme_changed, themeManager.getThemeName()), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, null);
        builder.show();
    }

    private void applyTheme() {
        ThemeManager.ThemeColors colors = themeManager.getThemeColors();

        // Update root background
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            rootView.setBackgroundColor(colors.background);
        }

        // Toolbar
        if (toolbar != null) {
            toolbar.setBackgroundColor(colors.primary);
        }

        // Player background gradient
        if (playerLayout != null) {
            GradientDrawable gradient = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{colors.playerBackground, colors.playerBackgroundLight}
            );
            float cornerRadius = getResources().getDimension(R.dimen.player_corner_radius);
            float[] radii = new float[]{
                    cornerRadius, cornerRadius,  // top-left
                    cornerRadius, cornerRadius,  // top-right
                    0, 0,                         // bottom-right
                    0, 0                          // bottom-left
            };
            gradient.setCornerRadii(radii);
            playerLayout.setBackground(gradient);
        }

        // Section title text color
        int sectionTextColor = colors.textOnSurface;
        if (textViewDirectoryTitle != null) {
            textViewDirectoryTitle.setTextColor(sectionTextColor);
        }
        if (textViewFileTitle != null) {
            textViewFileTitle.setTextColor(sectionTextColor);
        }

        // A2: 单播放/暂停按钮圆形背景
        if (buttonPlayPause != null) {
            ((GradientDrawable) buttonPlayPause.getBackground()).setColor(colors.accent);
            buttonPlayPause.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        }

        // Icon tinting for secondary buttons
        android.content.res.ColorStateList whiteTint =
                android.content.res.ColorStateList.valueOf(Color.WHITE);
        android.content.res.ColorStateList disabledTint =
                android.content.res.ColorStateList.valueOf(0xFF666666);
        android.content.res.ColorStateList secondaryTint =
                android.content.res.ColorStateList.valueOf(colors.primaryLight);

        tintImageButton(buttonPrevious, whiteTint, disabledTint);
        tintImageButton(buttonNext, whiteTint, disabledTint);
        tintImageButton(buttonStop, whiteTint, disabledTint);
        tintImageButton(buttonPlayMode, secondaryTint, disabledTint);
        tintImageButton(buttonSetTimer, secondaryTint, disabledTint);
        tintImageButton(buttonSettings, whiteTint, disabledTint);

        // Seekbar tinting
        if (seekBarProgress != null) {
            seekBarProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(colors.accent));
            seekBarProgress.setThumbTintList(android.content.res.ColorStateList.valueOf(colors.accent));
        }

        // Sort button theming
        if (buttonSort != null) {
            buttonSort.setBackgroundColor(colors.primaryLight);
            buttonSort.setTextColor(Color.WHITE);
        }

        // Status bar & nav bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(colors.primaryDark);
            getWindow().setNavigationBarColor(colors.primaryDark);
        }

        // Timer text color
        if (textViewTimer != null) {
            textViewTimer.setTextColor(colors.accent);
        }

        // A7: 空状态配色
        if (imageViewEmptyIcon != null) {
            imageViewEmptyIcon.setColorFilter(colors.textSecondary);
        }
        if (textViewEmptyTitle != null) {
            textViewEmptyTitle.setTextColor(colors.textOnSurface);
        }
        if (textViewEmptyMessage != null) {
            textViewEmptyMessage.setTextColor(colors.textSecondary);
        }

        // Update adapters
        if (directoryAdapter != null) {
            directoryAdapter.setThemeColors(colors);
        }
        if (fileAdapter != null) {
            fileAdapter.setThemeColors(colors);
        }
    }

    private void tintImageButton(ImageButton button,
                                  android.content.res.ColorStateList enabledTint,
                                  android.content.res.ColorStateList disabledTint) {
        if (button == null) return;
        android.content.res.ColorStateList tintList = new android.content.res.ColorStateList(
                new int[][]{
                        new int[]{-android.R.attr.state_enabled},
                        new int[]{}
                },
                new int[]{disabledTint.getDefaultColor(), enabledTint.getDefaultColor()}
        );
        button.setImageTintList(tintList);
    }

    // ===== 进度 =====

    private void updateProgress(int currentPosition, int duration, String fileName) {
        if (textViewCurrentFile != null) {
            textViewCurrentFile.setText(fileName);
        }
        if (textViewCurrentTime != null) {
            textViewCurrentTime.setText(formatTime(currentPosition));
        }
        if (textViewTotalTime != null) {
            textViewTotalTime.setText(formatTime(duration));
        }
        if (seekBarProgress != null && !isSeekBarUserSeeking && duration > 0) {
            int progress = (int) ((long) currentPosition * 100 / duration);
            seekBarProgress.setProgress(progress);
        }
    }

    private String formatTime(int milliseconds) {
        int totalSeconds = milliseconds / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // ===== 权限 =====

    private void checkPermissions() {
        // Android 11+ 使用 MANAGE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.permission_title)
                        .setMessage(R.string.permission_manage_storage_message)
                        .setPositiveButton(R.string.dialog_confirm, (dialog, which) -> {
                            try {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                            } catch (Exception e) {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                            }
                        })
                        .setNegativeButton(R.string.dialog_cancel, null)
                        .setCancelable(false)
                        .show();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_AUDIO},
                        PERMISSION_REQUEST_CODE);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                loadDirectories();
                String lastDir = prefs.getString(KEY_LAST_DIRECTORY, null);
                if (lastDir != null && new File(lastDir).exists()) {
                    loadFiles(lastDir);
                }
            } else {
                Toast.makeText(this, R.string.permission_denied_storage, Toast.LENGTH_LONG).show();
            }
        }
    }

    // ===== 目录/文件浏览（C1 后台线程）=====

    private void loadDirectories() {
        if (recyclerViewDirectories == null) return;
        if (!hasStoragePermission()) {
            showLoading(false);
            showEmptyState(true, null, null);
            return;
        }
        showLoading(true);
        showEmptyState(false, null, null);
        final int token = ++loadToken;
        ioExecutor.execute(() -> {
            List<DirectoryItem> result = scanDirectories();
            mainHandler.post(() -> {
                if (token != loadToken) return; // 已有更新的加载请求，丢弃旧结果
                directoryList.clear();
                directoryList.addAll(result);
                if (directoryAdapter == null) {
                    directoryAdapter = new DirectoryAdapter(directoryList, directory -> {
                        selectedDirectoryPath = directory.getPath();
                        selectedFilePath = null;
                        prefs.edit().putString(KEY_LAST_DIRECTORY, directory.getPath()).apply();
                        updateBreadcrumb(directory.getName());
                        loadFiles(directory.getPath());
                    });
                    directoryAdapter.setThemeColors(themeManager.getThemeColors());
                    if (recyclerViewDirectories.getLayoutManager() == null) {
                        recyclerViewDirectories.setLayoutManager(new LinearLayoutManager(this));
                    }
                    recyclerViewDirectories.setAdapter(directoryAdapter);
                } else {
                    directoryAdapter.notifyDataSetChanged();
                }
                showLoading(false);
                showEmptyState(directoryList.isEmpty(), null, null);
            });
        });
    }

    private List<DirectoryItem> scanDirectories() {
        List<DirectoryItem> list = new ArrayList<>();
        File baseDir = findBaseDir();
        if (baseDir == null) return list;
        try {
            File[] dirs = baseDir.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File dir : dirs) {
                    try {
                        int mp3Count = getMp3Count(dir);
                        if (mp3Count > 0) {
                            list.add(new DirectoryItem(dir.getName(), dir.getAbsolutePath(), mp3Count));
                        }
                    } catch (SecurityException e) {
                        continue;
                    }
                }
            }
        } catch (SecurityException e) {
            mainHandler.post(() -> Toast.makeText(this,
                    getString(R.string.directory_access_error, e.getMessage()), Toast.LENGTH_SHORT).show());
        }
        Collections.sort(list, (a, b) -> a.getName().compareTo(b.getName()));
        return list;
    }

    private File findBaseDir() {
        File[] possiblePaths = {
                new File("/sdcard/播放文件"),
                new File("/storage/emulated/0/播放文件"),
                new File(getExternalFilesDir(null), "播放文件"),
                new File(getFilesDir(), "播放文件"),
        };
        for (File path : possiblePaths) {
            try {
                if (path.exists() && path.isDirectory()) {
                    return path;
                }
            } catch (SecurityException e) {
                continue;
            }
        }
        return null;
    }

    private void loadFiles(String directoryPath) {
        if (recyclerViewFiles == null) return;
        final int token = ++loadToken;
        ioExecutor.execute(() -> {
            List<FileItem> result = scanFiles(directoryPath);
            mainHandler.post(() -> {
                if (token != loadToken) return;
                fileList.clear();
                fileList.addAll(result);
                if (fileAdapter == null) {
                    fileAdapter = new FileAdapter(fileList, (file, position) -> {
                        // A1: 点击即播放
                        selectedFilePath = file.getPath();
                        prefs.edit().putString(KEY_LAST_FILE, file.getPath()).apply();
                        playFile(file.getPath());
                    });
                    fileAdapter.setThemeColors(themeManager.getThemeColors());
                    recyclerViewFiles.setAdapter(fileAdapter);
                } else {
                    fileAdapter.resetAnimation();
                    fileAdapter.notifyDataSetChanged();
                }

                // 恢复高亮（旋转/重绑后）
                if (selectedFilePath != null && fileAdapter != null) {
                    fileAdapter.highlightByPath(selectedFilePath);
                }

                if (!result.isEmpty()) {
                    textViewFileTitle.setVisibility(View.VISIBLE);
                    textViewFileTitle.setText(getString(R.string.file_count_label, result.size()));
                    recyclerViewFiles.setVisibility(View.VISIBLE);
                    if (buttonSort != null) buttonSort.setVisibility(View.VISIBLE);
                } else {
                    // A7: 目录无文件的内联提示
                    textViewFileTitle.setVisibility(View.VISIBLE);
                    textViewFileTitle.setText(R.string.empty_state_no_files_title);
                    recyclerViewFiles.setVisibility(View.GONE);
                    if (buttonSort != null) buttonSort.setVisibility(View.GONE);
                }
            });
        });
    }

    private List<FileItem> scanFiles(String directoryPath) {
        List<FileItem> list = new ArrayList<>();
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) return list;
        try {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().toLowerCase().endsWith(".mp3")) {
                        list.add(new FileItem(file.getName(), file.getAbsolutePath(), file.length(), file.lastModified()));
                    }
                }
                sortFileList(list);
            }
        } catch (SecurityException e) {
            mainHandler.post(() -> Toast.makeText(this,
                    getString(R.string.file_access_error, e.getMessage()), Toast.LENGTH_SHORT).show());
        }
        return list;
    }

    private void sortFileList(List<FileItem> list) {
        switch (currentSortMode) {
            case SORT_SIZE:
                Collections.sort(list, (f1, f2) -> Long.compare(f2.getSize(), f1.getSize()));
                break;
            case SORT_DATE:
                Collections.sort(list, (f1, f2) -> Long.compare(f2.getLastModified(), f1.getLastModified()));
                break;
            default: // SORT_NAME
                Collections.sort(list, (f1, f2) -> f1.getName().compareTo(f2.getName()));
                break;
        }
    }

    private int getMp3Count(File directory) {
        int count = 0;
        try {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().toLowerCase().endsWith(".mp3")) {
                        count++;
                    }
                }
            }
        } catch (SecurityException e) {
            return 0;
        }
        return count;
    }

    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (recyclerViewDirectories != null) {
            recyclerViewDirectories.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    /** A7: 控制「找不到目录」空状态 */
    private void showEmptyState(boolean show, String titleOverride, String msgOverride) {
        if (emptyStateView == null) return;
        emptyStateView.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            if (textViewEmptyTitle != null) {
                textViewEmptyTitle.setText(titleOverride != null ? titleOverride
                        : getString(R.string.empty_state_title));
            }
            if (textViewEmptyMessage != null) {
                textViewEmptyMessage.setText(msgOverride != null ? msgOverride
                        : getString(R.string.empty_state_message));
            }
        }
    }

    // ===== 服务绑定 =====

    private void bindMusicService() {
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        startService(intent);
    }

    // ===== 播放控制 =====

    /** A1: 点击文件直接播放 */
    private void playFile(String path) {
        if (!isServiceBound || musicService == null) return;
        musicService.setPlaylistFromFile(path);
        musicService.play();
    }

    /** A2: 单按钮播放/暂停切换 */
    private void togglePlayPause() {
        if (!isServiceBound || musicService == null) return;
        if (musicService.getPlaybackState() == MusicService.STATE_PLAYING) {
            musicService.pause();
            return;
        }
        // 需要播放列表才能启动
        if (musicService.getPlaylistSize() == 0) {
            if (selectedFilePath != null && !selectedFilePath.isEmpty()) {
                musicService.setPlaylistFromFile(selectedFilePath);
            } else if (selectedDirectoryPath != null && !selectedDirectoryPath.isEmpty()) {
                musicService.setPlaylist(selectedDirectoryPath);
            } else {
                Toast.makeText(this, R.string.select_directory_or_file, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        musicService.play();
    }

    private void stopMusic() {
        if (isServiceBound && musicService != null) {
            musicService.stop();
        }
    }

    // ===== 面包屑 =====

    private void updateBreadcrumb(String dirName) {
        if (toolbar == null) return;
        toolbar.setSubtitle(dirName == null ? null : getString(R.string.breadcrumb_format, dirName));
    }

    // ===== 定时器（A5 预设 + 播完即停）=====

    private void showTimerDialog() {
        final boolean timerActive = isServiceBound && musicService != null && musicService.isTimerActive();
        boolean finishCurrent = isServiceBound && musicService != null && musicService.isStopAfterCurrent();

        final Context ctx = this;
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(8));

        if (!timerActive) {
            // 快捷预设
            TextView presetsLabel = new TextView(ctx);
            presetsLabel.setText(R.string.timer_hint);
            presetsLabel.setPadding(0, 0, 0, dp(8));
            root.addView(presetsLabel);

            LinearLayout chips = new LinearLayout(ctx);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            int[] presets = {15, 30, 45, 60};
            for (int mins : presets) {
                Button chip = new Button(ctx);
                chip.setText(getString(R.string.timer_preset_minutes, mins));
                chip.setAllCaps(false);
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                chip.setTag(mins);
                chips.addView(chip, chipLp);
            }
            root.addView(chips);

            // 自定义分钟输入
            final EditText input = new EditText(ctx);
            input.setHint(R.string.timer_input_hint);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setGravity(Gravity.CENTER);
            input.setId(View.generateViewId());
            root.addView(input);

            // 播完本曲后停止
            final CheckBox finishBox = new CheckBox(ctx);
            finishBox.setText(R.string.timer_finish_current);
            finishBox.setChecked(finishCurrent);
            root.addView(finishBox);

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.timer_hint)
                    .setView(root)
                    .setPositiveButton(R.string.set_timer, (dialog, which) -> {
                        boolean finish = finishBox.isChecked();
                        if (isServiceBound && musicService != null) {
                            musicService.setStopAfterCurrent(finish);
                        }
                        String minutesStr = input.getText().toString().trim();
                        if (minutesStr.isEmpty()) {
                            if (finish) {
                                Toast.makeText(this, R.string.timer_finish_current_set, Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, R.string.timer_input_hint, Toast.LENGTH_SHORT).show();
                            }
                            return;
                        }
                        try {
                            int minutes = Integer.parseInt(minutesStr);
                            if (minutes <= 0) {
                                Toast.makeText(this, R.string.timer_invalid, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (minutes > MAX_TIMER_MINUTES) {
                                Toast.makeText(this, getString(R.string.timer_max_exceeded, MAX_TIMER_MINUTES), Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (isServiceBound && musicService != null) {
                                musicService.setTimer(minutes);
                                updateTimerDisplay(musicService.getRemainingSeconds());
                                Toast.makeText(this, getString(R.string.timer_set, minutes), Toast.LENGTH_SHORT).show();
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, R.string.timer_invalid_number, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(R.string.dialog_cancel, null);

            final AlertDialog dialog = builder.create();
            // 预设按钮点击即设即关
            for (int i = 0; i < chips.getChildCount(); i++) {
                Button chip = (Button) chips.getChildAt(i);
                final int mins = (int) chip.getTag();
                chip.setOnClickListener(v -> {
                    if (isServiceBound && musicService != null) {
                        musicService.setStopAfterCurrent(finishBox.isChecked());
                        musicService.setTimer(mins);
                        updateTimerDisplay(musicService.getRemainingSeconds());
                        Toast.makeText(this, getString(R.string.timer_set, mins), Toast.LENGTH_SHORT).show();
                    }
                    dialog.dismiss();
                });
            }
            dialog.show();
        } else {
            // 定时器激活中：显示剩余 + 取消
            TextView info = new TextView(ctx);
            info.setPadding(dp(4), dp(4), dp(4), dp(4));
            info.setText(getString(R.string.timer_remaining_ms,
                    musicService.getRemainingSeconds() / 60,
                    musicService.getRemainingSeconds() % 60));
            info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            root.addView(info);

            new MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.timer_hint)
                    .setView(root)
                    .setPositiveButton(R.string.cancel_timer, (d, w) -> cancelTimer())
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show();
        }
    }

    private void cancelTimer() {
        if (isServiceBound && musicService != null) {
            musicService.cancelTimer();
            musicService.setStopAfterCurrent(false);
            hideTimerDisplay();
            Toast.makeText(this, R.string.timer_cancelled, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateTimerDisplay(long remainingSeconds) {
        if (textViewTimer != null && remainingSeconds > 0) {
            long hours = remainingSeconds / 3600;
            long minutes = (remainingSeconds % 3600) / 60;
            long seconds = remainingSeconds % 60;

            String timeText;
            if (hours > 0) {
                timeText = String.format(getString(R.string.timer_remaining_hms), hours, minutes, seconds);
            } else {
                timeText = String.format(getString(R.string.timer_remaining_ms), minutes, seconds);
            }
            textViewTimer.setText(timeText);
            textViewTimer.setVisibility(View.VISIBLE);
        } else {
            hideTimerDisplay();
        }
    }

    private void hideTimerDisplay() {
        if (textViewTimer != null) {
            textViewTimer.setVisibility(View.GONE);
            textViewTimer.setText("");
        }
    }

    // ===== UI 状态更新（C2 由状态回调驱动）=====

    private void updateUI() {
        if (!isServiceBound || musicService == null) return;
        if (textViewStatus == null || buttonPlayPause == null) return;

        int state = musicService.getPlaybackState();
        boolean active = (state == MusicService.STATE_PLAYING || state == MusicService.STATE_PAUSED);
        boolean canControl = active && musicService.getPlaylistSize() > 0;

        switch (state) {
            case MusicService.STATE_PLAYING:
                textViewStatus.setText(R.string.status_playing);
                setPlayPauseIcon(true);
                break;
            case MusicService.STATE_PAUSED:
                textViewStatus.setText(R.string.status_paused);
                setPlayPauseIcon(false);
                break;
            default: // STATE_STOPPED
                textViewStatus.setText(R.string.status_stopped);
                setPlayPauseIcon(false);
                if (textViewCurrentFile != null) textViewCurrentFile.setText("");
                if (textViewCurrentTime != null) textViewCurrentTime.setText("00:00");
                if (textViewTotalTime != null) textViewTotalTime.setText("00:00");
                if (seekBarProgress != null) seekBarProgress.setProgress(0);
                break;
        }

        buttonStop.setEnabled(active);
        buttonPrevious.setEnabled(canControl);
        buttonNext.setEnabled(canControl);
        setSecondaryAlpha(buttonStop, active);
        setSecondaryAlpha(buttonPrevious, canControl);
        setSecondaryAlpha(buttonNext, canControl);

        // B2: 同步文件列表的脉冲动画（仅正在播放的那一行）
        if (fileAdapter != null) {
            fileAdapter.setPlaying(state == MusicService.STATE_PLAYING);
        }
    }

    /** A2: 切换播放/暂停图标，附带轻微弹跳 */
    private void setPlayPauseIcon(boolean playing) {
        if (buttonPlayPause == null) return;
        buttonPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        buttonPlayPause.setContentDescription(getString(playing ? R.string.pause : R.string.play));
        buttonPlayPause.setScaleX(0.85f);
        buttonPlayPause.setScaleY(0.85f);
        buttonPlayPause.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
    }

    /** A3: 服务侧曲目变化时同步列表高亮 + 滚动 */
    private void onTrackChanged(String filePath) {
        if (filePath == null) return;
        selectedFilePath = filePath;
        prefs.edit().putString(KEY_LAST_FILE, filePath).apply();
        if (fileAdapter == null) return;
        fileAdapter.highlightByPath(filePath);
        for (int i = 0; i < fileList.size(); i++) {
            if (filePath.equals(fileList.get(i).getPath())) {
                recyclerViewFiles.smoothScrollToPosition(i);
                break;
            }
        }
    }

    private void setSecondaryAlpha(ImageButton button, boolean enabled) {
        if (button == null) return;
        button.setImageAlpha(enabled ? 255 : 100);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    // ===== 权限回调 =====

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadDirectories();
                String lastDir = prefs.getString(KEY_LAST_DIRECTORY, null);
                if (lastDir != null && new File(lastDir).exists()) {
                    loadFiles(lastDir);
                }
            } else {
                Toast.makeText(this, R.string.permission_denied_audio, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (isServiceBound && musicService != null) {
                musicService.setPlaybackStateListener(null);
                musicService.setTrackChangeListener(null);
                musicService.setProgressCallback(null);
                musicService.setTimerCallback(null);
            }
            if (isServiceBound) {
                unbindService(serviceConnection);
                isServiceBound = false;
            }
        } catch (IllegalArgumentException e) {
            isServiceBound = false;
        }
        ioExecutor.shutdown();
    }
}
