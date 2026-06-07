package com.mymeditation.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

public class ThemeManager {
    public static final String PREF_NAME = "theme_prefs";
    public static final String KEY_THEME = "selected_theme";
    
    public static final int THEME_OCEAN_BLUE = 0;  // 海洋蓝（默认）
    public static final int THEME_FOREST_GREEN = 1; // 森林绿
    public static final int THEME_SUNSET_ORANGE = 2; // 日落橙
    public static final int THEME_LAVENDER_PURPLE = 3; // 薰衣草紫
    public static final int THEME_DEEP_BLUE = 4; // 深蓝
    
    private static ThemeManager instance;
    private Context context;
    private int currentTheme;
    private SharedPreferences prefs;
    
    private ThemeManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.currentTheme = prefs.getInt(KEY_THEME, THEME_OCEAN_BLUE);
    }
    
    public static ThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemeManager(context);
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
                    Color.parseColor("#5A8A7A"), // primary
                    Color.parseColor("#3D5F52"), // primary_dark
                    Color.parseColor("#7FA896"), // primary_light
                    Color.parseColor("#8FBC8F"), // accent
                    Color.parseColor("#6B9B6B"), // accent_dark
                    Color.parseColor("#F0F5F2"), // background
                    Color.parseColor("#2D4A3D"), // player_background
                    Color.parseColor("#3A5C4A")  // player_background_light
                );
            case THEME_SUNSET_ORANGE:
                return new ThemeColors(
                    Color.parseColor("#D4A574"), // primary
                    Color.parseColor("#B8875A"), // primary_dark
                    Color.parseColor("#E5C19A"), // primary_light
                    Color.parseColor("#F4A460"), // accent
                    Color.parseColor("#D68910"), // accent_dark
                    Color.parseColor("#FFF8F0"), // background
                    Color.parseColor("#8B4513"), // player_background
                    Color.parseColor("#A0522D")  // player_background_light
                );
            case THEME_LAVENDER_PURPLE:
                return new ThemeColors(
                    Color.parseColor("#9B8FB8"), // primary
                    Color.parseColor("#7A6A95"), // primary_dark
                    Color.parseColor("#B8A8D1"), // primary_light
                    Color.parseColor("#C8A2C8"), // accent
                    Color.parseColor("#A67AA6"), // accent_dark
                    Color.parseColor("#F5F0F8"), // background
                    Color.parseColor("#6B4C7A"), // player_background
                    Color.parseColor("#7D5A8A")  // player_background_light
                );
            case THEME_DEEP_BLUE:
                return new ThemeColors(
                    Color.parseColor("#4A6FA5"), // primary
                    Color.parseColor("#2E4A6F"), // primary_dark
                    Color.parseColor("#6B8FC7"), // primary_light
                    Color.parseColor("#5B9BD5"), // accent
                    Color.parseColor("#3D7AB8"), // accent_dark
                    Color.parseColor("#F0F4F8"), // background
                    Color.parseColor("#1E3A5F"), // player_background
                    Color.parseColor("#2D4A6F")  // player_background_light
                );
            default: // THEME_OCEAN_BLUE
                return new ThemeColors(
                    Color.parseColor("#6B8E9F"), // primary
                    Color.parseColor("#4A6B7A"), // primary_dark
                    Color.parseColor("#9BB5C4"), // primary_light
                    Color.parseColor("#A8D5BA"), // accent
                    Color.parseColor("#7FB896"), // accent_dark
                    Color.parseColor("#F5F7FA"), // background
                    Color.parseColor("#2C3E50"), // player_background
                    Color.parseColor("#34495E")  // player_background_light
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

