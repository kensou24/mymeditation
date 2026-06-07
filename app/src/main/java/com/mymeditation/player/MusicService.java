package com.mymeditation.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MusicService extends Service {
    public static final int STATE_STOPPED = 0;
    public static final int STATE_PLAYING = 1;
    public static final int STATE_PAUSED = 2;

    private static final String CHANNEL_ID = "music_channel";
    private static final int NOTIFICATION_ID = 1;

    private MediaPlayer mediaPlayer;
    private List<String> playlist;
    private int currentIndex = 0;
    private int playbackState = STATE_STOPPED;
    private String currentDirectoryPath;
    private Handler timerHandler;
    private Handler progressHandler;
    private Runnable timerRunnable;
    private Runnable progressRunnable;
    private long timerEndTime = 0;
    private Object mediaSession; // MediaSessionCompat placeholder
    private ProgressCallback progressCallback;
    private TimerCallback timerCallback;

    public interface ProgressCallback {
        void onProgressUpdate(int currentPosition, int duration, String currentFileName);
    }
    
    public interface TimerCallback {
        void onTimerUpdate(long remainingSeconds);
        void onTimerFinished();
    }

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        timerHandler = new Handler(Looper.getMainLooper());
        progressHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        initializeMediaSession();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "音乐播放",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("音乐播放通知");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void initializeMediaSession() {
        // MediaSession initialization removed - using basic notification instead
        mediaSession = null;
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }
    
    public void setTimerCallback(TimerCallback callback) {
        this.timerCallback = callback;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setPlaylist(String directoryPath) {
        currentDirectoryPath = directoryPath;
        playlist = new ArrayList<>();
        File directory = new File(directoryPath);
        
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().toLowerCase().endsWith(".mp3")) {
                        playlist.add(file.getAbsolutePath());
                    }
                }
                // 按文件名排序
                playlist.sort(String::compareTo);
            }
        }
        
        stop();
        currentIndex = 0;
    }
    
    public void setPlaylistFromFile(String filePath) {
        playlist = new ArrayList<>();
        File file = new File(filePath);
        
        if (file.exists() && file.isFile() && file.getName().toLowerCase().endsWith(".mp3")) {
            currentDirectoryPath = file.getParent();
            
            // 加载同目录下的所有MP3文件
            File directory = new File(currentDirectoryPath);
            if (directory.exists() && directory.isDirectory()) {
                File[] files = directory.listFiles();
                if (files != null) {
                    List<String> allFiles = new ArrayList<>();
                    for (File f : files) {
                        if (f.isFile() && f.getName().toLowerCase().endsWith(".mp3")) {
                            allFiles.add(f.getAbsolutePath());
                        }
                    }
                    // 按文件名排序
                    allFiles.sort(String::compareTo);
                    
                    // 找到选中文件在列表中的位置
                    String selectedFilePath = file.getAbsolutePath();
                    int selectedIndex = 0;
                    for (int i = 0; i < allFiles.size(); i++) {
                        if (allFiles.get(i).equals(selectedFilePath)) {
                            selectedIndex = i;
                            break;
                        }
                    }
                    
                    // 只将选中文件及其后面的文件加入播放列表
                    for (int i = selectedIndex; i < allFiles.size(); i++) {
                        playlist.add(allFiles.get(i));
                    }
                    currentIndex = 0; // 从列表的第一个（即选中的文件）开始播放
                }
            }
        }
        
        stop();
    }
    
    public List<String> getPlaylist() {
        return playlist;
    }

    public void play() {
        if (playlist == null || playlist.isEmpty()) {
            return;
        }

        if (playbackState == STATE_PAUSED && mediaPlayer != null) {
            mediaPlayer.start();
            playbackState = STATE_PLAYING;
            startProgressUpdates();
            updateNotification();
            return;
        }

        stop();
        
        if (currentIndex >= playlist.size()) {
            currentIndex = 0;
        }

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(playlist.get(currentIndex));
            mediaPlayer.prepare();
            mediaPlayer.setOnCompletionListener(mp -> {
                playNext();
            });
            mediaPlayer.start();
            playbackState = STATE_PLAYING;
            startProgressUpdates();
            updateNotification();
            startForeground(NOTIFICATION_ID, createNotification());
        } catch (IOException e) {
            e.printStackTrace();
            playbackState = STATE_STOPPED;
        }
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            playbackState = STATE_PAUSED;
            stopProgressUpdates();
            updateNotification();
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        playbackState = STATE_STOPPED;
        cancelTimer();
        stopProgressUpdates();
        updateNotification();
        stopForeground(true);
    }

    private void playNext() {
        if (playlist == null || playlist.isEmpty()) {
            stop();
            return;
        }

        currentIndex++;
        if (currentIndex >= playlist.size()) {
            currentIndex = 0; // 循环播放
        }

        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(playlist.get(currentIndex));
            mediaPlayer.prepare();
            mediaPlayer.setOnCompletionListener(mp -> {
                playNext();
            });
            mediaPlayer.start();
            playbackState = STATE_PLAYING;
            startProgressUpdates();
            updateNotification();
        } catch (IOException e) {
            e.printStackTrace();
            stop();
        }
    }

    private void startProgressUpdates() {
        stopProgressUpdates();
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    int currentPosition = mediaPlayer.getCurrentPosition();
                    int duration = mediaPlayer.getDuration();
                    String fileName = getCurrentFileName();
                    if (progressCallback != null) {
                        progressCallback.onProgressUpdate(currentPosition, duration, fileName);
                    }
                    progressHandler.postDelayed(this, 1000);
                }
            }
        };
        progressHandler.post(progressRunnable);
    }

    private void stopProgressUpdates() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
    }

    private String getCurrentFileName() {
        if (playlist != null && currentIndex >= 0 && currentIndex < playlist.size()) {
            String path = playlist.get(currentIndex);
            File file = new File(path);
            return file.getName();
        }
        return "";
    }

    public int getCurrentPosition() {
        if (mediaPlayer != null) {
            return mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public int getDuration() {
        if (mediaPlayer != null) {
            return mediaPlayer.getDuration();
        }
        return 0;
    }

    public void seekTo(int position) {
        if (mediaPlayer != null) {
            try {
                // 确保MediaPlayer已准备好
                if (mediaPlayer.isPlaying() || playbackState == STATE_PAUSED) {
                    mediaPlayer.seekTo(position);
                    // 立即更新进度回调，确保UI同步
                    if (progressCallback != null) {
                        int duration = mediaPlayer.getDuration();
                        String fileName = getCurrentFileName();
                        progressCallback.onProgressUpdate(position, duration, fileName);
                    }
                }
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }
    }

    public void setTimer(int minutes) {
        cancelTimer();
        timerEndTime = System.currentTimeMillis() + (minutes * 60 * 1000L);
        
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                if (currentTime >= timerEndTime) {
                    // 定时结束，停止播放并取消定时
                    stop();
                    cancelTimer();
                    if (timerCallback != null) {
                        timerCallback.onTimerFinished();
                    }
                } else {
                    // 计算剩余秒数并更新回调
                    long remainingMillis = timerEndTime - currentTime;
                    long remainingSeconds = remainingMillis / 1000;
                    if (timerCallback != null) {
                        timerCallback.onTimerUpdate(remainingSeconds);
                    }
                    timerHandler.postDelayed(this, 1000);
                }
            }
        };
        timerHandler.post(timerRunnable);
    }
    
    public void cancelTimer() {
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
            timerRunnable = null;
        }
        timerEndTime = 0;
        if (timerCallback != null) {
            timerCallback.onTimerFinished(); // 通知UI隐藏倒计时
        }
    }
    
    public boolean isTimerActive() {
        return timerEndTime > 0 && System.currentTimeMillis() < timerEndTime;
    }
    
    public long getRemainingSeconds() {
        if (timerEndTime > 0) {
            long remaining = (timerEndTime - System.currentTimeMillis()) / 1000;
            return remaining > 0 ? remaining : 0;
        }
        return 0;
    }

    public int getPlaybackState() {
        return playbackState;
    }

    private void updateNotification() {
        if (playbackState == STATE_PLAYING || playbackState == STATE_PAUSED) {
            Notification notification = createNotification();
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, notification);
            }
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String fileName = getCurrentFileName();
        if (fileName.isEmpty()) {
            fileName = "冥想音乐";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("冥想播放器")
                .setContentText(fileName)
                .setContentIntent(pendingIntent)
                .setStyle(new MediaStyle()
                        .setShowActionsInCompactView(0, 1))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(playbackState == STATE_PLAYING);

        // 添加播放/暂停按钮
        Intent playPauseIntent = new Intent(this, MusicService.class);
        playPauseIntent.setAction(playbackState == STATE_PLAYING ? "PAUSE" : "PLAY");
        PendingIntent playPausePendingIntent = PendingIntent.getService(this, 0, playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (playbackState == STATE_PLAYING) {
            builder.addAction(android.R.drawable.ic_media_pause, "暂停", playPausePendingIntent);
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "播放", playPausePendingIntent);
        }

        // 添加停止按钮
        Intent stopIntent = new Intent(this, MusicService.class);
        stopIntent.setAction("STOP");
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.addAction(android.R.drawable.ic_media_ff, "停止", stopPendingIntent);

        // MediaSession metadata and state updates removed - using basic notification

        return builder.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("PLAY".equals(action)) {
                play();
            } else if ("PAUSE".equals(action)) {
                pause();
            } else if ("STOP".equals(action)) {
                stop();
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stop();
        if (timerHandler != null) {
            timerHandler.removeCallbacksAndMessages(null);
        }
        if (progressHandler != null) {
            progressHandler.removeCallbacksAndMessages(null);
        }
        // MediaSession cleanup removed
    }
}

