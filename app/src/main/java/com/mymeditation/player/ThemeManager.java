package com.mymeditation.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public class ThemeManager {
    public static final String PREF_NAME = "theme_prefs";
    public static final String KEY_THEME = "selected_theme";

    public static final int THEME_OCEAN_BLUE = 0;
    public static final int THEME_FOREST_GREEN = 1;
    public static final int THEME_SUNSET_ORANGE = 2;
    public static final int THEME_LAVENDER_PURPLE = 3;
    public static final int THEME_DEEP_BLUE = 4;

    private static volatile ThemeManager instance;
    private final Context context;
    private int currentTheme;
    private final SharedPreferences prefs;

    private ThemeManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.currentTheme = prefs.getInt(KEY_THEME, THEME_OCEAN_BLUE);
    }

    // T22: 双重检查锁 + volatile 保证线程安全
    public static ThemeManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ThemeManager.class) {
                if (instance == null) {
                    instance = new ThemeManager(context);
                }
            }
        }
        return instance;
    }

    public int getCurrentTheme() {
        return currentTheme;
    }

    public void setTheme(int theme) {
        this.currentTheme = theme;
        prefs.edit().putInt(KEY_THEME, theme).apply();
    }

    public ThemeColors getThemeColors() {
        switch (currentTheme) {
            case THEME_FOREST_GREEN:
                return new ThemeColors(
                    Color.parseColor("#5A8A7A"),
                    Color.parseColor("#3D5F52"),
                    Color.parseColor("#7FA896"),
                    Color.parseColor("#8FBC8F"),
                    Color.parseColor("#6B9B6B"),
                    Color.parseColor("#F0F5F2"),
                    Color.parseColor("#2D4A3D"),
                    Color.parseColor("#3A5C4A")
                );
            case THEME_SUNSET_ORANGE:
                return new ThemeColors(
                    Color.parseColor("#D4A574"),
                    Color.parseColor("#B8875A"),
                    Color.parseColor("#E5C19A"),
                    Color.parseColor("#F4A460"),
                    Color.parseColor("#D68910"),
                    Color.parseColor("#FFF8F0"),
                    Color.parseColor("#8B4513"),
                    Color.parseColor("#A0522D")
                );
            case THEME_LAVENDER_PURPLE:
                return new ThemeColors(
                    Color.parseColor("#9B8FB8"),
                    Color.parseColor("#7A6A95"),
                    Color.parseColor("#B8A8D1"),
                    Color.parseColor("#C8A2C8"),
                    Color.parseColor("#A67AA6"),
                    Color.parseColor("#F5F0F8"),
                    Color.parseColor("#6B4C7A"),
                    Color.parseColor("#7D5A8A")
                );
            case THEME_DEEP_BLUE:
                return new ThemeColors(
                    Color.parseColor("#4A6FA5"),
                    Color.parseColor("#2E4A6F"),
                    Color.parseColor("#6B8FC7"),
                    Color.parseColor("#5B9BD5"),
                    Color.parseColor("#3D7AB8"),
                    Color.parseColor("#F0F4F8"),
                    Color.parseColor("#1E3A5F"),
                    Color.parseColor("#2D4A6F")
                );
            default: // THEME_OCEAN_BLUE
                return new ThemeColors(
                    Color.parseColor("#6B8E9F"),
                    Color.parseColor("#4A6B7A"),
                    Color.parseColor("#9BB5C4"),
                    Color.parseColor("#A8D5BA"),
                    Color.parseColor("#7FB896"),
                    Color.parseColor("#F5F7FA"),
                    Color.parseColor("#2C3E50"),
                    Color.parseColor("#34495E")
                );
        }
    }

    public String getThemeName() {
        switch (currentTheme) {
            case THEME_FOREST_GREEN:
                return "森林绿";
            case THEME_SUNSET_ORANGE:
                return "日落橙";
            case THEME_LAVENDER_PURPLE:
                return "薰衣草紫";
            case THEME_DEEP_BLUE:
                return "深蓝";
            default:
                return "海洋蓝";
        }
    }

    public static class ThemeColors {
        public int primary;
        public int primaryDark;
        public int primaryLight;
        public int accent;
        public int accentDark;
        public int background;
        public int playerBackground;
        public int playerBackgroundLight;

        public ThemeColors(int primary, int primaryDark, int primaryLight,
                          int accent, int accentDark, int background,
                          int playerBackground, int playerBackgroundLight) {
            this.primary = primary;
            this.primaryDark = primaryDark;
            this.primaryLight = primaryLight;
            this.accent = accent;
            this.accentDark = accentDark;
            this.background = background;
            this.playerBackground = playerBackground;
            this.playerBackgroundLight = playerBackgroundLight;
        }
    }
}
