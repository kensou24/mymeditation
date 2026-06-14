package com.mymeditation.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;

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

    // 播放模式
    public static final int MODE_SEQUENCE = 0;      // 顺序循环
    public static final int MODE_REPEAT_ONE = 1;    // 单曲循环
    public static final int MODE_SHUFFLE = 2;       // 随机播放

    private MediaPlayer mediaPlayer;
    private List<String> playlist;
    private int currentIndex = 0;
    private int playbackState = STATE_STOPPED;
    private int playMode = MODE_SEQUENCE;
    private String currentDirectoryPath;
    private Handler timerHandler;
    private Handler progressHandler;
    private Runnable timerRunnable;
    private Runnable progressRunnable;
    private long timerEndTime = 0;
    private MediaSessionCompat mediaSession;
    private ProgressCallback progressCallback;
    private TimerCallback timerCallback;
    private TrackChangeListener trackChangeListener;
    private PlaybackStateListener playbackStateListener;
    private boolean stopAfterCurrent = false;

    // 音频焦点
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean hasAudioFocus = false;

    public interface ProgressCallback {
        void onProgressUpdate(int currentPosition, int duration, String currentFileName);
    }

    public interface TimerCallback {
        void onTimerUpdate(long remainingSeconds);
        void onTimerFinished();
    }

    /** A3: 实际播放曲目发生变化（新曲目 onPrepared 时触发） */
    public interface TrackChangeListener {
        void onTrackChanged(String filePath, int index);
    }

    /** C2: 播放状态变化时触发，避免每秒全量刷新 UI */
    public interface PlaybackStateListener {
        void onStateChanged(int state);
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
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
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
        ComponentName mediaButtonReceiver = new ComponentName(this, MediaButtonReceiver.class.getName());
        mediaSession = new MediaSessionCompat(this, "MyMeditationPlayer", mediaButtonReceiver, null);
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                play();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onStop() {
                stop();
            }

            @Override
            public void onSkipToNext() {
                playNext();
            }

            @Override
            public void onSkipToPrevious() {
                playPrevious();
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
            }

            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
                if (Intent.ACTION_MEDIA_BUTTON.equals(mediaButtonEvent.getAction())) {
                    KeyEvent keyEvent = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                    if (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                        switch (keyEvent.getKeyCode()) {
                            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                                if (playbackState == STATE_PLAYING) {
                                    pause();
                                } else {
                                    play();
                                }
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_NEXT:
                                playNext();
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                                playPrevious();
                                return true;
                        }
                    }
                }
                return super.onMediaButtonEvent(mediaButtonEvent);
            }
        });
        mediaSession.setActive(true);
    }

    private void updateMediaSessionState() {
        if (mediaSession == null) return;

        long actions = PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SEEK_TO;

        int state;
        switch (playbackState) {
            case STATE_PLAYING:
                state = PlaybackStateCompat.STATE_PLAYING;
                actions |= PlaybackStateCompat.ACTION_PAUSE;
                break;
            case STATE_PAUSED:
                state = PlaybackStateCompat.STATE_PAUSED;
                actions |= PlaybackStateCompat.ACTION_PLAY;
                break;
            default:
                state = PlaybackStateCompat.STATE_STOPPED;
                actions |= PlaybackStateCompat.ACTION_PLAY;
                break;
        }

        long position = 0;
        if (mediaPlayer != null && (playbackState == STATE_PLAYING || playbackState == STATE_PAUSED)) {
            try {
                position = mediaPlayer.getCurrentPosition();
            } catch (IllegalStateException ignored) {
            }
        }

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();
        stateBuilder.setActions(actions);
        stateBuilder.setState(state, position, 1.0f);
        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void updateMediaSessionMetadata() {
        if (mediaSession == null) return;

        String fileName = getCurrentFileName();
        if (fileName.isEmpty()) {
            fileName = "冥想音乐";
        }
        // 去掉 .mp3 后缀显示
        if (fileName.toLowerCase().endsWith(".mp3")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }

        long duration = 0;
        if (mediaPlayer != null) {
            try {
                duration = mediaPlayer.getDuration();
            } catch (IllegalStateException ignored) {
            }
        }

        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, fileName);
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "冥想播放器");
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
        mediaSession.setMetadata(metadataBuilder.build());
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    public void setTimerCallback(TimerCallback callback) {
        this.timerCallback = callback;
    }

    public void setTrackChangeListener(TrackChangeListener listener) {
        this.trackChangeListener = listener;
    }

    public void setPlaybackStateListener(PlaybackStateListener listener) {
        this.playbackStateListener = listener;
    }

    /** A5: 设置「播完本曲后停止」 */
    public void setStopAfterCurrent(boolean stopAfterCurrent) {
        this.stopAfterCurrent = stopAfterCurrent;
    }

    public boolean isStopAfterCurrent() {
        return stopAfterCurrent;
    }

    private void notifyTrackChanged() {
        if (trackChangeListener != null && playlist != null
                && currentIndex >= 0 && currentIndex < playlist.size()) {
            trackChangeListener.onTrackChanged(playlist.get(currentIndex), currentIndex);
        }
    }

    private void notifyStateChanged() {
        if (playbackStateListener != null) {
            playbackStateListener.onStateChanged(playbackState);
        }
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
                    allFiles.sort(String::compareTo);

                    String selectedFilePath = file.getAbsolutePath();
                    int selectedIndex = 0;
                    for (int i = 0; i < allFiles.size(); i++) {
                        if (allFiles.get(i).equals(selectedFilePath)) {
                            selectedIndex = i;
                            break;
                        }
                    }

                    // T10: 从选中文件开始，到末尾后再从目录开头循环
                    for (int i = selectedIndex; i < allFiles.size(); i++) {
                        playlist.add(allFiles.get(i));
                    }
                    for (int i = 0; i < selectedIndex; i++) {
                        playlist.add(allFiles.get(i));
                    }
                    currentIndex = 0;
                }
            }
        }

        stop();
    }

    public List<String> getPlaylist() {
        return playlist;
    }

    public int getPlaylistSize() {
        return playlist != null ? playlist.size() : 0;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (IllegalStateException ignored) {
            }
            try {
                mediaPlayer.release();
            } catch (IllegalStateException ignored) {
            }
            mediaPlayer = null;
        }
    }

    private void setupMediaPlayer(String filePath) {
        setupMediaPlayer(filePath, 0);
    }

    private void setupMediaPlayer(String filePath, int retryCount) {
        releasePlayer();
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            if (progressCallback != null) {
                progressCallback.onProgressUpdate(0, 0, getCurrentFileName() + " (播放失败)");
            }
            if (playlist != null && playlist.size() > 1 && retryCount + 1 < playlist.size()) {
                currentIndex++;
                if (currentIndex >= playlist.size()) {
                    currentIndex = 0;
                }
                setupMediaPlayer(playlist.get(currentIndex), retryCount + 1);
            } else {
                stop();
            }
            return true;
        });
        mediaPlayer.setOnCompletionListener(mp -> {
            // A5: 播完本曲后停止（优先于下一首/循环逻辑）
            if (stopAfterCurrent) {
                stopAfterCurrent = false;
                stop();
                return;
            }
            playNext();
        });
        try {
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                playbackState = STATE_PLAYING;
                startProgressUpdates();
                updateNotification();
                updateMediaSessionState();
                updateMediaSessionMetadata();
                if (progressCallback != null) {
                    progressCallback.onProgressUpdate(0, mp.getDuration(), getCurrentFileName());
                }
                notifyStateChanged();
                notifyTrackChanged();
            });
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
            if (playlist != null && playlist.size() > 1 && retryCount + 1 < playlist.size()) {
                currentIndex++;
                if (currentIndex >= playlist.size()) {
                    currentIndex = 0;
                }
                setupMediaPlayer(playlist.get(currentIndex), retryCount + 1);
            } else {
                stop();
            }
        }
    }

    @SuppressWarnings("deprecation")
    private boolean requestAudioFocus() {
        if (audioManager == null) return true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener(this::onAudioFocusChange)
                        .build();
            }
            int result = audioManager.requestAudioFocus(audioFocusRequest);
            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        } else {
            int result = audioManager.requestAudioFocus(
                    this::onAudioFocusChange,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
            );
            hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        }
        return hasAudioFocus;
    }

    @SuppressWarnings("deprecation")
    private void abandonAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(this::onAudioFocusChange);
        }
        hasAudioFocus = false;
    }

    private void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                // 永久失去焦点 — 停止播放
                stop();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // 短暂失去焦点（来电等） — 暂停
                if (playbackState == STATE_PLAYING) {
                    pause();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // 可以降低音量播放（如通知提示音）
                if (mediaPlayer != null) {
                    try {
                        mediaPlayer.setVolume(0.3f, 0.3f);
                    } catch (IllegalStateException ignored) {
                    }
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                // 恢复焦点 — 恢复播放
                if (mediaPlayer != null) {
                    try {
                        mediaPlayer.setVolume(1.0f, 1.0f);
                    } catch (IllegalStateException ignored) {
                    }
                }
                break;
        }
    }

    public void play() {
        if (playlist == null || playlist.isEmpty()) {
            return;
        }

        if (playbackState == STATE_PAUSED && mediaPlayer != null) {
            if (requestAudioFocus()) {
                mediaPlayer.start();
                playbackState = STATE_PLAYING;
                startProgressUpdates();
                updateNotification();
                updateMediaSessionState();
                notifyStateChanged();
            }
            return;
        }

        releasePlayer();

        if (currentIndex >= playlist.size()) {
            currentIndex = 0;
        }

        if (!requestAudioFocus()) return;

        startForeground(NOTIFICATION_ID, createNotification());
        setupMediaPlayer(playlist.get(currentIndex));
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            playbackState = STATE_PAUSED;
            stopProgressUpdates();
            updateNotification();
            updateMediaSessionState();
            notifyStateChanged();
        }
    }

    public void stop() {
        releasePlayer();
        playbackState = STATE_STOPPED;
        cancelTimer();
        stopProgressUpdates();
        abandonAudioFocus();
        updateNotification();
        updateMediaSessionState();
        stopForeground(true);
        notifyStateChanged();
    }

    public void playNext() {
        if (playlist == null || playlist.isEmpty()) {
            stop();
            return;
        }

        if (playMode == MODE_REPEAT_ONE) {
            // 单曲循环：重播当前曲目
            currentIndex = currentIndex; // 不变
        } else if (playMode == MODE_SHUFFLE) {
            // 随机播放
            int newIndex;
            if (playlist.size() <= 1) {
                newIndex = 0;
            } else {
                do {
                    newIndex = (int) (Math.random() * playlist.size());
                } while (newIndex == currentIndex);
            }
            currentIndex = newIndex;
        } else {
            // 顺序循环
            currentIndex++;
            if (currentIndex >= playlist.size()) {
                currentIndex = 0;
            }
        }

        setupMediaPlayer(playlist.get(currentIndex));
    }

    public void playPrevious() {
        if (playlist == null || playlist.isEmpty()) {
            return;
        }

        // 如果当前播放超过 3 秒，则重播当前曲目
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.getCurrentPosition() > 3000) {
                    seekTo(0);
                    return;
                }
            } catch (IllegalStateException ignored) {
            }
        }

        if (playMode == MODE_SHUFFLE) {
            int newIndex;
            if (playlist.size() <= 1) {
                newIndex = 0;
            } else {
                do {
                    newIndex = (int) (Math.random() * playlist.size());
                } while (newIndex == currentIndex);
            }
            currentIndex = newIndex;
        } else {
            currentIndex--;
            if (currentIndex < 0) {
                currentIndex = playlist.size() - 1;
            }
        }

        setupMediaPlayer(playlist.get(currentIndex));
    }

    public void setPlayMode(int mode) {
        this.playMode = mode;
    }

    public int getPlayMode() {
        return playMode;
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
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (IllegalStateException e) {
                return 0;
            }
        }
        return 0;
    }

    public int getDuration() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getDuration();
            } catch (IllegalStateException e) {
                return 0;
            }
        }
        return 0;
    }

    public void seekTo(int position) {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying() || playbackState == STATE_PAUSED) {
                    mediaPlayer.seekTo(position);
                    if (progressCallback != null) {
                        int duration = mediaPlayer.getDuration();
                        String fileName = getCurrentFileName();
                        progressCallback.onProgressUpdate(position, duration, fileName);
                    }
                    updateMediaSessionState();
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
                    stop();
                    if (timerCallback != null) {
                        timerCallback.onTimerFinished();
                    }
                } else {
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
        boolean wasActive = isTimerActive();
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
            timerRunnable = null;
        }
        timerEndTime = 0;
        if (wasActive && timerCallback != null) {
            timerCallback.onTimerFinished();
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
        // 去掉 .mp3 后缀显示
        String displayTitle = fileName;
        if (displayTitle.toLowerCase().endsWith(".mp3")) {
            displayTitle = displayTitle.substring(0, displayTitle.length() - 4);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("冥想播放器")
                .setContentText(displayTitle)
                .setContentIntent(pendingIntent)
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(playbackState == STATE_PLAYING);

        // 上一首按钮
        Intent prevIntent = new Intent(this, MusicService.class);
        prevIntent.setAction("PREVIOUS");
        PendingIntent prevPendingIntent = PendingIntent.getService(this, 3, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.addAction(android.R.drawable.ic_media_previous, "上一首", prevPendingIntent);

        // 播放/暂停按钮
        Intent playPauseIntent = new Intent(this, MusicService.class);
        playPauseIntent.setAction(playbackState == STATE_PLAYING ? "PAUSE" : "PLAY");
        PendingIntent playPausePendingIntent = PendingIntent.getService(this, 1, playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (playbackState == STATE_PLAYING) {
            builder.addAction(android.R.drawable.ic_media_pause, "暂停", playPausePendingIntent);
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "播放", playPausePendingIntent);
        }

        // 停止按钮
        Intent stopIntent = new Intent(this, MusicService.class);
        stopIntent.setAction("STOP");
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 2, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.addAction(android.R.drawable.ic_media_ff, "停止", stopPendingIntent);

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
            } else if ("PREVIOUS".equals(action)) {
                playPrevious();
            } else if ("NEXT".equals(action)) {
                playNext();
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
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
    }
}
