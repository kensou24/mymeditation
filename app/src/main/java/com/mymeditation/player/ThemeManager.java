package com.mymeditation.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public class ThemeManager {
    public static final String PREF_NAME = "theme_prefs";
    public static final String KEY_THEME = "selected_theme";

    public static final int THEME_CLAUDE_ORANGE = 0;
    public static final int THEME_CLAUDE_BLUE = 1;
    public static final int THEME_CLAUDE_GREEN = 2;
    public static final int THEME_CLAUDE_DARK = 3;
    public static final int THEME_CLAUDE_WARM = 4;

    private static volatile ThemeManager instance;
    private final Context context;
    private int currentTheme;
    private final SharedPreferences prefs;

    private ThemeManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.currentTheme = prefs.getInt(KEY_THEME, THEME_CLAUDE_ORANGE);
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
            case THEME_CLAUDE_BLUE:
                return new ThemeColors(
                    Color.parseColor("#6A9BCC"),
                    Color.parseColor("#4A7DA8"),
                    Color.parseColor("#94BDD8"),
                    Color.parseColor("#D97757"),
                    Color.parseColor("#B85E3F"),
                    Color.parseColor("#F5F7FA"),
                    Color.parseColor("#1A2332"),
                    Color.parseColor("#2A3A4F"),
                    Color.parseColor("#1A2332"),
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#8A9BB0")
                );
            case THEME_CLAUDE_GREEN:
                return new ThemeColors(
                    Color.parseColor("#788C5D"),
                    Color.parseColor("#5A6B43"),
                    Color.parseColor("#9AAD7E"),
                    Color.parseColor("#6A9BCC"),
                    Color.parseColor("#4A7DA8"),
                    Color.parseColor("#F5F7F2"),
                    Color.parseColor("#1A1E16"),
                    Color.parseColor("#2A3224"),
                    Color.parseColor("#1A1E16"),
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#8A9B80")
                );
            case THEME_CLAUDE_DARK:
                return new ThemeColors(
                    Color.parseColor("#3D3B39"),
                    Color.parseColor("#2A2928"),
                    Color.parseColor("#5A5856"),
                    Color.parseColor("#D97757"),
                    Color.parseColor("#B85E3F"),
                    Color.parseColor("#1E1D1C"),
                    Color.parseColor("#141413"),
                    Color.parseColor("#2A2928"),
                    Color.parseColor("#FAF9F5"),
                    Color.parseColor("#2A2928"),
                    Color.parseColor("#8A8880")
                );
            case THEME_CLAUDE_WARM:
                return new ThemeColors(
                    Color.parseColor("#B0AEA5"),
                    Color.parseColor("#8A8880"),
                    Color.parseColor("#C8C6BE"),
                    Color.parseColor("#D97757"),
                    Color.parseColor("#B85E3F"),
                    Color.parseColor("#F2F0EB"),
                    Color.parseColor("#2A2928"),
                    Color.parseColor("#3D3B39"),
                    Color.parseColor("#3D3835"),
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#9A988F")
                );
            default: // THEME_CLAUDE_ORANGE
                return new ThemeColors(
                    Color.parseColor("#D97757"),
                    Color.parseColor("#B85E3F"),
                    Color.parseColor("#E8A88A"),
                    Color.parseColor("#6A9BCC"),
                    Color.parseColor("#4A7DA8"),
                    Color.parseColor("#FAF9F5"),
                    Color.parseColor("#141413"),
                    Color.parseColor("#2A2928"),
                    Color.parseColor("#3D3835"),
                    Color.parseColor("#FFFFFF"),
                    Color.parseColor("#B0AEA5")
                );
        }
    }

    public String getThemeName() {
        switch (currentTheme) {
            case THEME_CLAUDE_BLUE:
                return "Claude 蓝";
            case THEME_CLAUDE_GREEN:
                return "Claude 绿";
            case THEME_CLAUDE_DARK:
                return "Claude 暗黑";
            case THEME_CLAUDE_WARM:
                return "Claude 暖灰";
            default:
                return "Claude 暖橙";
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
        public int textOnSurface;
        public int surface;
        public int textSecondary;

        public ThemeColors(int primary, int primaryDark, int primaryLight,
                          int accent, int accentDark, int background,
                          int playerBackground, int playerBackgroundLight,
                          int textOnSurface, int surface, int textSecondary) {
            this.primary = primary;
            this.primaryDark = primaryDark;
            this.primaryLight = primaryLight;
            this.accent = accent;
            this.accentDark = accentDark;
            this.background = background;
            this.playerBackground = playerBackground;
            this.playerBackgroundLight = playerBackgroundLight;
            this.textOnSurface = textOnSurface;
            this.surface = surface;
            this.textSecondary = textSecondary;
        }
    }
}
