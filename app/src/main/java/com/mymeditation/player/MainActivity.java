package com.mymeditation.player;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private RecyclerView recyclerViewDirectories;
    private RecyclerView recyclerViewFiles;
    private DirectoryAdapter directoryAdapter;
    private FileAdapter fileAdapter;
    private List<DirectoryItem> directoryList;
    private List<FileItem> fileList;
    private MusicService musicService;
    private boolean isServiceBound = false;
    private Button buttonPlay, buttonPause, buttonStop, buttonSetTimer, buttonCancelTimer;
    private TextView textViewStatus, textViewTimer, textViewDirectoryTitle, textViewFileTitle;
    private TextView textViewCurrentFile, textViewCurrentTime, textViewTotalTime;
    private EditText editTextTimer;
    private SeekBar seekBarProgress;
    private String selectedDirectoryPath;
    private String selectedFilePath;
    private boolean isSeekBarUserSeeking = false;
    private ThemeManager themeManager;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.LocalBinder binder = (MusicService.LocalBinder) service;
            musicService = binder.getService();
            isServiceBound = true;
            // 设置进度回调
            musicService.setProgressCallback((currentPosition, duration, fileName) -> {
                runOnUiThread(() -> {
                    updateProgress(currentPosition, duration, fileName);
                });
            });
            // 设置定时回调
            musicService.setTimerCallback(new MusicService.TimerCallback() {
                @Override
                public void onTimerUpdate(long remainingSeconds) {
                    runOnUiThread(() -> {
                        updateTimerDisplay(remainingSeconds);
                    });
                }
                
                @Override
                public void onTimerFinished() {
                    runOnUiThread(() -> {
                        hideTimerDisplay();
                    });
                }
            });
            updateUI();
            // 检查定时状态并更新显示
            if (musicService.isTimerActive()) {
                long remainingSeconds = musicService.getRemainingSeconds();
                updateTimerDisplay(remainingSeconds);
            } else {
                hideTimerDisplay();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            // 初始化主题管理器
            themeManager = ThemeManager.getInstance(this);
            
            setContentView(R.layout.activity_main);

            initViews();
            applyTheme(); // 应用主题
            checkPermissions();
            // 延迟加载目录，等待权限检查完成
            if (hasStoragePermission()) {
                loadDirectories();
            }
            bindMusicService();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "应用启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void initViews() {
        recyclerViewDirectories = findViewById(R.id.recyclerViewDirectories);
        recyclerViewFiles = findViewById(R.id.recyclerViewFiles);
        buttonPlay = findViewById(R.id.buttonPlay);
        buttonPause = findViewById(R.id.buttonPause);
        buttonStop = findViewById(R.id.buttonStop);
        buttonSetTimer = findViewById(R.id.buttonSetTimer);
        buttonCancelTimer = findViewById(R.id.buttonCancelTimer);
        textViewStatus = findViewById(R.id.textViewStatus);
        textViewTimer = findViewById(R.id.textViewTimer);
        textViewDirectoryTitle = findViewById(R.id.textViewDirectoryTitle);
        textViewFileTitle = findViewById(R.id.textViewFileTitle);
        textViewCurrentFile = findViewById(R.id.textViewCurrentFile);
        textViewCurrentTime = findViewById(R.id.textViewCurrentTime);
        textViewTotalTime = findViewById(R.id.textViewTotalTime);
        editTextTimer = findViewById(R.id.editTextTimer);
        seekBarProgress = findViewById(R.id.seekBarProgress);

        Button buttonSettings = findViewById(R.id.buttonSettings);
        
        buttonPlay.setOnClickListener(v -> playMusic());
        buttonPause.setOnClickListener(v -> pauseMusic());
        buttonStop.setOnClickListener(v -> stopMusic());
        buttonSetTimer.setOnClickListener(v -> setTimer());
        buttonCancelTimer.setOnClickListener(v -> cancelTimer());
        if (buttonSettings != null) {
            buttonSettings.setOnClickListener(v -> showThemeDialog());
        }
        
        // 设置按钮图标
        setButtonIcon(buttonPlay, R.drawable.ic_play);
        setButtonIcon(buttonPause, R.drawable.ic_pause);
        setButtonIcon(buttonStop, R.drawable.ic_stop);
        setButtonIcon(buttonSetTimer, R.drawable.ic_timer);
        setButtonIcon(buttonCancelTimer, R.drawable.ic_cancel);
        
        // 统一设置按钮高度和内边距，确保高度一致
        setupButtonLayout(buttonPlay);
        setupButtonLayout(buttonPause);
        setupButtonLayout(buttonStop);
        setupButtonLayout(buttonSetTimer);
        setupButtonLayout(buttonCancelTimer);
        
        // 设置标题栏设置按钮的高度
        if (buttonSettings != null) {
            setupButtonLayout(buttonSettings);
        }
        
        // 设置进度条监听
        seekBarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 不在拖动过程中更新，只在停止拖动时更新
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeekBarUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isSeekBarUserSeeking = false;
                // 在停止拖动时执行跳转
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
        
        // 初始化文件列表
        fileList = new ArrayList<>();
        recyclerViewFiles.setLayoutManager(new LinearLayoutManager(this));
    }
    
    private void setButtonIcon(Button button, int drawableResId) {
        Drawable drawable = ContextCompat.getDrawable(this, drawableResId);
        if (drawable != null) {
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            // 使用 setCompoundDrawablesRelative 以支持 RTL 布局
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                button.setCompoundDrawablesRelative(drawable, null, null, null);
            } else {
                button.setCompoundDrawables(drawable, null, null, null);
            }
            // 减小图标和文字之间的间距到最小
            int paddingDp = 1;
            int paddingPx = (int) (paddingDp * getResources().getDisplayMetrics().density);
            button.setCompoundDrawablePadding(paddingPx);
        }
    }
    
    private void setupButtonLayout(Button button) {
        if (button != null) {
            // 设置固定高度为56dp
            button.getLayoutParams().height = (int) (56 * getResources().getDisplayMetrics().density);
            // 确保最小高度为0，避免默认padding影响
            button.setMinHeight(0);
            button.setMinWidth(0);
            // 对于标题栏设置按钮，保持0dp padding（已在XML中设置）
            // 对于其他按钮，如果XML中已经设置了padding="0dp"，则不需要额外设置
            // 这样可以确保所有按钮高度一致，不受padding影响
        }
    }
    
    private void showThemeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择皮肤主题");
        
        // 创建主题选择视图
        RadioGroup radioGroup = new RadioGroup(this);
        radioGroup.setOrientation(LinearLayout.VERTICAL);
        radioGroup.setPadding(50, 30, 50, 30);
        
        String[] themeNames = {"海洋蓝", "森林绿", "日落橙", "薰衣草紫", "深蓝"};
        int[] themeValues = {
            ThemeManager.THEME_OCEAN_BLUE,
            ThemeManager.THEME_FOREST_GREEN,
            ThemeManager.THEME_SUNSET_ORANGE,
            ThemeManager.THEME_LAVENDER_PURPLE,
            ThemeManager.THEME_DEEP_BLUE
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
        builder.setPositiveButton("确定", (dialog, which) -> {
            int selectedId = radioGroup.getCheckedRadioButtonId();
            if (selectedId != -1) {
                themeManager.setTheme(selectedId);
                applyTheme();
                Toast.makeText(this, "已切换到：" + themeManager.getThemeName(), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    private void applyTheme() {
        ThemeManager.ThemeColors colors = themeManager.getThemeColors();
        
        // 应用主题颜色到各个视图
        View headerLayout = findViewById(R.id.headerLayout);
        if (headerLayout != null) {
            headerLayout.setBackgroundColor(colors.primary);
        }
        
        View playerLayout = findViewById(R.id.playerLayout);
        if (playerLayout != null) {
            GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{colors.playerBackground, colors.playerBackgroundLight}
            );
            playerLayout.setBackground(gradient);
        }
        
        // 更新按钮背景
        updateButtonColors(colors);
        
        // 更新进度条颜色
        if (seekBarProgress != null) {
            seekBarProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(colors.accent));
            seekBarProgress.setThumbTintList(android.content.res.ColorStateList.valueOf(colors.accent));
        }
        
        // 更新状态栏颜色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(colors.primaryDark);
            getWindow().setNavigationBarColor(colors.primaryDark);
        }
    }
    
    private void updateButtonColors(ThemeManager.ThemeColors colors) {
        // 更新播放按钮（accent颜色）
        if (buttonPlay != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(colors.accent);
            bg.setCornerRadius(8);
            // 确保背景不影响按钮高度
            buttonPlay.setBackground(bg);
            // 重新应用高度设置，确保主题切换后高度不变
            setupButtonLayout(buttonPlay);
        }
        
        // 更新其他按钮（primary颜色）
        GradientDrawable primaryBg = new GradientDrawable();
        primaryBg.setColor(colors.primary);
        primaryBg.setCornerRadius(8);
        
        if (buttonPause != null) {
            buttonPause.setBackground(primaryBg);
            setupButtonLayout(buttonPause);
        }
        if (buttonStop != null) {
            buttonStop.setBackground(primaryBg);
            setupButtonLayout(buttonStop);
        }
        if (buttonSetTimer != null) {
            buttonSetTimer.setBackground(primaryBg);
            setupButtonLayout(buttonSetTimer);
        }
        if (buttonCancelTimer != null) {
            buttonCancelTimer.setBackground(primaryBg);
            setupButtonLayout(buttonCancelTimer);
        }
    }

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

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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

    private void loadDirectories() {
        if (recyclerViewDirectories == null) {
            return;
        }
        
        directoryList = new ArrayList<>();
        
        // 检查权限
        boolean hasPermission = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        
        if (!hasPermission) {
            // 权限未授予，不加载目录
            return;
        }
        
        try {
            // 尝试多个可能的路径
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
                    // 忽略权限异常，继续尝试下一个路径
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
                                // 忽略无法访问的目录
                                continue;
                            }
                        }
                    }
                } catch (SecurityException e) {
                    Toast.makeText(this, "无法访问目录: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "未找到\"播放文件\"目录，请确保目录存在并包含MP3文件", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "加载目录时出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        
        if (directoryAdapter == null) {
            directoryAdapter = new DirectoryAdapter(directoryList, directory -> {
                selectedDirectoryPath = directory.getPath();
                selectedFilePath = null; // 清除文件选择
                Toast.makeText(this, "已选择目录: " + directory.getName(), Toast.LENGTH_SHORT).show();
                loadFiles(directory.getPath());
            });
        } else {
            directoryAdapter.notifyDataSetChanged();
        }

        if (recyclerViewDirectories.getLayoutManager() == null) {
            recyclerViewDirectories.setLayoutManager(new LinearLayoutManager(this));
        }
        recyclerViewDirectories.setAdapter(directoryAdapter);
    }
    
    private void loadFiles(String directoryPath) {
        if (recyclerViewFiles == null) {
            return;
        }
        
        fileList.clear();
        
        try {
            File directory = new File(directoryPath);
            if (directory.exists() && directory.isDirectory()) {
                File[] files = directory.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && file.getName().toLowerCase().endsWith(".mp3")) {
                            fileList.add(new FileItem(file.getName(), file.getAbsolutePath(), file.length()));
                        }
                    }
                    // 按文件名排序
                    fileList.sort((f1, f2) -> f1.getName().compareTo(f2.getName()));
                }
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "无法访问文件: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "加载文件时出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        
        if (fileAdapter == null) {
            fileAdapter = new FileAdapter(fileList, (file, position) -> {
                selectedFilePath = file.getPath();
                Toast.makeText(this, "已选择文件: " + file.getName(), Toast.LENGTH_SHORT).show();
                if (isServiceBound && musicService != null) {
                    // 播放选中的单个文件
                    musicService.setPlaylistFromFile(selectedFilePath);
                    updateUI();
                }
            });
            recyclerViewFiles.setAdapter(fileAdapter);
        } else {
            fileAdapter.notifyDataSetChanged();
        }
        
        // 显示文件列表
        if (fileList.size() > 0) {
            textViewFileTitle.setVisibility(View.VISIBLE);
            recyclerViewFiles.setVisibility(View.VISIBLE);
            textViewFileTitle.setText("选择文件 (" + fileList.size() + " 个文件)");
        } else {
            textViewFileTitle.setVisibility(View.GONE);
            recyclerViewFiles.setVisibility(View.GONE);
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
            // 权限不足，返回0
            return 0;
        }
        return count;
    }

    private void bindMusicService() {
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        startService(intent);
    }

    private void playMusic() {
        if (isServiceBound && musicService != null) {
            // 如果选择了单个文件，播放该文件；否则播放整个目录
            if (selectedFilePath != null && !selectedFilePath.isEmpty()) {
                musicService.play();
            } else if (selectedDirectoryPath != null && !selectedDirectoryPath.isEmpty()) {
                // 如果没有选择文件，但选择了目录，则播放整个目录
                if (musicService.getPlaylist() == null || musicService.getPlaylist().isEmpty()) {
                    musicService.setPlaylist(selectedDirectoryPath);
                }
                musicService.play();
            } else {
                Toast.makeText(this, "请先选择目录或文件", Toast.LENGTH_SHORT).show();
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

    private void setTimer() {
        String minutesStr = editTextTimer.getText().toString();
        if (minutesStr.isEmpty()) {
            Toast.makeText(this, "请输入分钟数", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            int minutes = Integer.parseInt(minutesStr);
            if (minutes <= 0) {
                Toast.makeText(this, "请输入大于0的分钟数", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isServiceBound && musicService != null) {
                musicService.setTimer(minutes);
                // 立即显示倒计时
                long remainingSeconds = musicService.getRemainingSeconds();
                updateTimerDisplay(remainingSeconds);
                editTextTimer.setText("");
                Toast.makeText(this, "定时已设置：" + minutes + " 分钟", Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void cancelTimer() {
        if (isServiceBound && musicService != null) {
            musicService.cancelTimer();
            hideTimerDisplay();
            Toast.makeText(this, "定时已取消", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void updateTimerDisplay(long remainingSeconds) {
        if (textViewTimer != null && remainingSeconds > 0) {
            long hours = remainingSeconds / 3600;
            long minutes = (remainingSeconds % 3600) / 60;
            long seconds = remainingSeconds % 60;
            
            String timeText;
            if (hours > 0) {
                timeText = String.format("定时剩余: %02d:%02d:%02d", hours, minutes, seconds);
            } else {
                timeText = String.format("定时剩余: %02d:%02d", minutes, seconds);
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

    private void updateUI() {
        if (isServiceBound && musicService != null && 
            textViewStatus != null && buttonPlay != null && 
            buttonPause != null && buttonStop != null) {
            int state = musicService.getPlaybackState();
            switch (state) {
                case MusicService.STATE_PLAYING:
                    textViewStatus.setText("正在播放");
                    buttonPlay.setEnabled(false);
                    buttonPause.setEnabled(true);
                    buttonStop.setEnabled(true);
                    break;
                case MusicService.STATE_PAUSED:
                    textViewStatus.setText("已暂停");
                    buttonPlay.setEnabled(true);
                    buttonPause.setEnabled(false);
                    buttonStop.setEnabled(true);
                    break;
                case MusicService.STATE_STOPPED:
                    textViewStatus.setText("已停止");
                    buttonPlay.setEnabled(true);
                    buttonPause.setEnabled(false);
                    buttonStop.setEnabled(false);
                    if (textViewCurrentFile != null) {
                        textViewCurrentFile.setText("");
                    }
                    if (textViewCurrentTime != null) {
                        textViewCurrentTime.setText("00:00");
                    }
                    if (textViewTotalTime != null) {
                        textViewTotalTime.setText("00:00");
                    }
                    if (seekBarProgress != null) {
                        seekBarProgress.setProgress(0);
                    }
                    break;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadDirectories();
            } else {
                Toast.makeText(this, "需要存储权限才能访问MP3文件", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }
}
