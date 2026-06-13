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
import android.os.IBinder;
import android.provider.Settings;
import android.view.View;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
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
    private List<DirectoryItem> directoryList;
    private List<FileItem> fileList;
    private MusicService musicService;
    private boolean isServiceBound = false;

    private ImageButton buttonPlay, buttonPause, buttonStop;
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
    private String selectedDirectoryPath;
    private String selectedFilePath;
    private boolean isSeekBarUserSeeking = false;
    private ThemeManager themeManager;
    private int currentSortMode = SORT_NAME;
    private SharedPreferences prefs;
    private boolean playerBarAnimated = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.LocalBinder binder = (MusicService.LocalBinder) service;
            musicService = binder.getService();
            isServiceBound = true;
            musicService.setProgressCallback((currentPosition, duration, fileName) -> {
                runOnUiThread(() -> {
                    updateProgress(currentPosition, duration, fileName);
                    // 确保 async prepare 完成后 UI 状态同步更新
                    updateUI();
                });
            });
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
            updateUI();
            updatePlayModeButton();
            if (musicService.isTimerActive()) {
                updateTimerDisplay(musicService.getRemainingSeconds());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
        }
    };

    // ===== T11: 配置变更处理 =====

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
        // 恢复文件列表（如果之前有打开的目录）
        if (selectedDirectoryPath != null) {
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
                // T16: 恢复上次打开的目录
                String lastDir = prefs.getString(KEY_LAST_DIRECTORY, null);
                if (lastDir != null && new File(lastDir).exists()) {
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
        buttonPlay = findViewById(R.id.buttonPlay);
        buttonPause = findViewById(R.id.buttonPause);
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

        // Click listeners
        buttonPlay.setOnClickListener(v -> playMusic());
        buttonPause.setOnClickListener(v -> pauseMusic());
        buttonStop.setOnClickListener(v -> stopMusic());
        buttonPrevious.setOnClickListener(v -> playPrevious());
        buttonNext.setOnClickListener(v -> playNext());
        buttonPlayMode.setOnClickListener(v -> cyclePlayMode());
        buttonSetTimer.setOnClickListener(v -> showTimerDialog());
        buttonSort.setOnClickListener(v -> cycleSortMode());
        if (buttonSettings != null) {
            buttonSettings.setOnClickListener(v -> showThemeDialog());
        }

        // Add spacing decorations to RecyclerViews
        int itemSpacing = (int) getResources().getDimension(R.dimen.item_spacing);
        recyclerViewDirectories.addItemDecoration(new SpacingItemDecoration(itemSpacing));
        recyclerViewFiles.addItemDecoration(new SpacingItemDecoration(itemSpacing));

        updatePlayModeButton();
        updateSortButton();

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

        fileList = new ArrayList<>();
        recyclerViewFiles.setLayoutManager(new LinearLayoutManager(this));
    }

    // ===== 排序 T18 =====

    private void cycleSortMode() {
        currentSortMode = (currentSortMode + 1) % 3;
        prefs.edit().putInt(KEY_SORT_MODE, currentSortMode).apply();
        updateSortButton();
        if (selectedDirectoryPath != null) {
            loadFiles(selectedDirectoryPath);
        }
    }

    private void updateSortButton() {
        if (buttonSort == null) return;
        switch (currentSortMode) {
            case SORT_NAME:
                buttonSort.setText(R.string.sort_by_name);
                break;
            case SORT_SIZE:
                buttonSort.setText(R.string.sort_by_size);
                break;
            case SORT_DATE:
                buttonSort.setText(R.string.sort_by_date);
                break;
        }
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
            updateUI();
        }
    }

    private void playNext() {
        if (isServiceBound && musicService != null) {
            musicService.playNext();
            updateUI();
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

        // Section title text color (dark theme needs light text)
        int sectionTextColor = colors.textOnSurface;
        if (textViewDirectoryTitle != null) {
            textViewDirectoryTitle.setTextColor(sectionTextColor);
        }
        if (textViewFileTitle != null) {
            textViewFileTitle.setTextColor(sectionTextColor);
        }

        // Play/pause button circle backgrounds
        if (buttonPlay != null) {
            ((GradientDrawable) buttonPlay.getBackground()).setColor(colors.accent);
            buttonPlay.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        }
        if (buttonPause != null) {
            ((GradientDrawable) buttonPause.getBackground()).setColor(colors.primary);
            buttonPause.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
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
        // T12: Android 11+ 使用 MANAGE_EXTERNAL_STORAGE
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

    // ===== 目录/文件浏览 =====

    private void loadDirectories() {
        if (recyclerViewDirectories == null) return;

        // T17: 显示加载状态
        showLoading(true);

        directoryList = new ArrayList<>();

        if (!hasStoragePermission()) {
            showLoading(false);
            return;
        }

        try {
            File[] possiblePaths = {
                    new File("/sdcard/播放文件"),
                    new File("/storage/emulated/0/播放文件"),
                    new File(getExternalFilesDir(null), "播放文件"),
                    new File(getFilesDir(), "播放文件"),
            };

            File baseDir = null;
            for (File path : possiblePaths) {
                try {
                    if (path.exists() && path.isDirectory()) {
                        baseDir = path;
                        break;
                    }
                } catch (SecurityException e) {
                    continue;
                }
            }

            if (baseDir != null && baseDir.exists() && baseDir.isDirectory()) {
                try {
                    File[] dirs = baseDir.listFiles(File::isDirectory);
                    if (dirs != null) {
                        for (File dir : dirs) {
                            try {
                                int mp3Count = getMp3Count(dir);
                                if (mp3Count > 0) {
                                    directoryList.add(new DirectoryItem(dir.getName(), dir.getAbsolutePath(), mp3Count));
                                }
                            } catch (SecurityException e) {
                                continue;
                            }
                        }
                    }
                } catch (SecurityException e) {
                    Toast.makeText(this, getString(R.string.directory_access_error, e.getMessage()), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, R.string.directory_not_found, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.directory_load_error, e.getMessage()), Toast.LENGTH_SHORT).show();
        }

        // T15: 创建 adapter 时传入主题颜色
        if (directoryAdapter == null) {
            directoryAdapter = new DirectoryAdapter(directoryList, directory -> {
                selectedDirectoryPath = directory.getPath();
                selectedFilePath = null;
                // T16: 记住上次打开的目录
                prefs.edit().putString(KEY_LAST_DIRECTORY, directory.getPath()).apply();
                Toast.makeText(this, getString(R.string.directory_selected, directory.getName()), Toast.LENGTH_SHORT).show();
                loadFiles(directory.getPath());
            });
            directoryAdapter.setThemeColors(themeManager.getThemeColors());
        } else {
            directoryAdapter.notifyDataSetChanged();
        }

        if (recyclerViewDirectories.getLayoutManager() == null) {
            recyclerViewDirectories.setLayoutManager(new LinearLayoutManager(this));
        }
        recyclerViewDirectories.setAdapter(directoryAdapter);

        showLoading(false);
    }

    private void loadFiles(String directoryPath) {
        if (recyclerViewFiles == null) return;

        fileList.clear();

        try {
            File directory = new File(directoryPath);
            if (directory.exists() && directory.isDirectory()) {
                File[] files = directory.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && file.getName().toLowerCase().endsWith(".mp3")) {
                            fileList.add(new FileItem(file.getName(), file.getAbsolutePath(), file.length(), file.lastModified()));
                        }
                    }
                    sortFileList();
                }
            }
        } catch (SecurityException e) {
            Toast.makeText(this, getString(R.string.file_access_error, e.getMessage()), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.file_load_error, e.getMessage()), Toast.LENGTH_SHORT).show();
        }

        if (fileAdapter == null) {
            fileAdapter = new FileAdapter(fileList, (file, position) -> {
                selectedFilePath = file.getPath();
                // T16: 记住上次选中的文件
                prefs.edit().putString(KEY_LAST_FILE, file.getPath()).apply();
                Toast.makeText(this, getString(R.string.file_selected, file.getName()), Toast.LENGTH_SHORT).show();
                if (isServiceBound && musicService != null) {
                    musicService.setPlaylistFromFile(selectedFilePath);
                    updateUI();
                }
            });
            fileAdapter.setThemeColors(themeManager.getThemeColors());
            recyclerViewFiles.setAdapter(fileAdapter);
        } else {
            fileAdapter.resetAnimation();
            fileAdapter.notifyDataSetChanged();
        }

        if (fileList.size() > 0) {
            textViewFileTitle.setVisibility(View.VISIBLE);
            recyclerViewFiles.setVisibility(View.VISIBLE);
            textViewFileTitle.setText(getString(R.string.file_count_label, fileList.size()));
            if (buttonSort != null) buttonSort.setVisibility(View.VISIBLE);
        } else {
            textViewFileTitle.setVisibility(View.GONE);
            recyclerViewFiles.setVisibility(View.GONE);
            if (buttonSort != null) buttonSort.setVisibility(View.GONE);
        }
    }

    // T18: 文件排序
    private void sortFileList() {
        switch (currentSortMode) {
            case SORT_SIZE:
                Collections.sort(fileList, (f1, f2) -> Long.compare(f2.getSize(), f1.getSize()));
                break;
            case SORT_DATE:
                Collections.sort(fileList, (f1, f2) -> Long.compare(f2.getLastModified(), f1.getLastModified()));
                break;
            default: // SORT_NAME
                Collections.sort(fileList, (f1, f2) -> f1.getName().compareTo(f2.getName()));
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

    // T17: 加载状态
    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (recyclerViewDirectories != null && show) {
            recyclerViewDirectories.setVisibility(View.GONE);
        } else if (recyclerViewDirectories != null && !show) {
            recyclerViewDirectories.setVisibility(View.VISIBLE);
        }
    }

    // ===== 服务绑定 =====

    private void bindMusicService() {
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        startService(intent);
    }

    // ===== 播放控制 =====

    private void playMusic() {
        if (isServiceBound && musicService != null) {
            if (selectedFilePath != null && !selectedFilePath.isEmpty()) {
                musicService.play();
            } else if (selectedDirectoryPath != null && !selectedDirectoryPath.isEmpty()) {
                if (musicService.getPlaylist() == null || musicService.getPlaylist().isEmpty()) {
                    musicService.setPlaylist(selectedDirectoryPath);
                }
                musicService.play();
            } else {
                Toast.makeText(this, R.string.select_directory_or_file, Toast.LENGTH_SHORT).show();
                return;
            }
            updateUI();
        }
    }

    private void pauseMusic() {
        if (isServiceBound && musicService != null) {
            musicService.pause();
            updateUI();
        }
    }

    private void stopMusic() {
        if (isServiceBound && musicService != null) {
            musicService.stop();
            updateUI();
        }
    }

    // ===== 定时器 =====

    private void showTimerDialog() {
        // 如果已有定时器，显示取消选项
        boolean timerActive = isServiceBound && musicService != null && musicService.isTimerActive();

        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(48, 32, 48, 16);

        EditText input = new EditText(this);
        input.setHint(R.string.timer_hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setGravity(android.view.Gravity.CENTER);
        if (timerActive) {
            input.setEnabled(false);
            input.setAlpha(0.4f);
        }
        dialogLayout.addView(input);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.timer_hint);
        builder.setView(dialogLayout);

        if (timerActive) {
            builder.setPositiveButton(R.string.cancel_timer, (dialog, which) -> {
                cancelTimer();
            });
            builder.setNegativeButton(R.string.dialog_cancel, null);
        } else {
            builder.setPositiveButton(R.string.set_timer, (dialog, which) -> {
                String minutesStr = input.getText().toString().trim();
                if (minutesStr.isEmpty()) {
                    Toast.makeText(this, R.string.timer_input_hint, Toast.LENGTH_SHORT).show();
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
            });
            builder.setNegativeButton(R.string.dialog_cancel, null);
        }

        builder.show();
    }

    private void cancelTimer() {
        if (isServiceBound && musicService != null) {
            musicService.cancelTimer();
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

    // ===== UI 状态更新 =====

    private void updateUI() {
        if (!isServiceBound || musicService == null) return;
        if (textViewStatus == null || buttonPlay == null) return;

        int state = musicService.getPlaybackState();
        switch (state) {
            case MusicService.STATE_PLAYING:
                textViewStatus.setText(R.string.status_playing);
                animatePlayPauseTransition(buttonPlay, buttonPause);
                buttonStop.setEnabled(true);
                buttonPrevious.setEnabled(true);
                buttonNext.setEnabled(true);
                setSecondaryAlpha(buttonStop, true);
                setSecondaryAlpha(buttonPrevious, true);
                setSecondaryAlpha(buttonNext, true);
                break;
            case MusicService.STATE_PAUSED:
                textViewStatus.setText(R.string.status_paused);
                animatePlayPauseTransition(buttonPause, buttonPlay);
                buttonStop.setEnabled(true);
                buttonPrevious.setEnabled(true);
                buttonNext.setEnabled(true);
                setSecondaryAlpha(buttonStop, true);
                setSecondaryAlpha(buttonPrevious, true);
                setSecondaryAlpha(buttonNext, true);
                break;
            case MusicService.STATE_STOPPED:
                textViewStatus.setText(R.string.status_stopped);
                animatePlayPauseTransition(buttonPause, buttonPlay);
                buttonStop.setEnabled(false);
                buttonPrevious.setEnabled(false);
                buttonNext.setEnabled(false);
                setSecondaryAlpha(buttonStop, false);
                setSecondaryAlpha(buttonPrevious, false);
                setSecondaryAlpha(buttonNext, false);
                if (textViewCurrentFile != null) textViewCurrentFile.setText("");
                if (textViewCurrentTime != null) textViewCurrentTime.setText("00:00");
                if (textViewTotalTime != null) textViewTotalTime.setText("00:00");
                if (seekBarProgress != null) seekBarProgress.setProgress(0);
                break;
        }
    }

    /**
     * Animated crossfade between play and pause buttons.
     */
    private void animatePlayPauseTransition(View outgoing, View incoming) {
        if (outgoing.getVisibility() == View.GONE && incoming.getVisibility() == View.VISIBLE) {
            // Already in the correct state, skip animation
            return;
        }
        // Animate outgoing view out
        outgoing.animate()
                .alpha(0f)
                .scaleX(0.7f)
                .scaleY(0.7f)
                .setDuration(150)
                .withEndAction(() -> {
                    outgoing.setVisibility(View.GONE);
                    outgoing.setScaleX(1f);
                    outgoing.setScaleY(1f);
                    outgoing.setAlpha(1f);
                });

        // Animate incoming view in
        incoming.setAlpha(0f);
        incoming.setScaleX(0.7f);
        incoming.setScaleY(0.7f);
        incoming.setVisibility(View.VISIBLE);
        incoming.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .start();
    }

    private void setSecondaryAlpha(ImageButton button, boolean enabled) {
        if (button == null) return;
        button.setImageAlpha(enabled ? 255 : 100);
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

    // T14: 安全解绑
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (isServiceBound) {
                unbindService(serviceConnection);
                isServiceBound = false;
            }
        } catch (IllegalArgumentException e) {
            // Service already unbound
            isServiceBound = false;
        }
    }
}
