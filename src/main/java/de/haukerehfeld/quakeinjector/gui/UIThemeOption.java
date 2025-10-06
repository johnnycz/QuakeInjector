package de.haukerehfeld.quakeinjector.gui;

import com.github.weisj.darklaf.LafManager;
import com.github.weisj.darklaf.theme.DarculaTheme;
import com.github.weisj.darklaf.theme.IntelliJTheme;
import com.github.weisj.darklaf.theme.SolarizedDarkTheme;
import com.github.weisj.darklaf.theme.SolarizedLightTheme;

public enum UIThemeOption {
    SYSTEM("system", "Follow System Preferences") {
        @Override
        public void init() {
            LafManager.installTheme(LafManager.getPreferredThemeStyle());
        }
    },
    LIGHT("light", "Light") {
        @Override
        public void init() {
            LafManager.installTheme(new IntelliJTheme());
        }
    },
    DARCULA("darcula", "Darcula") {
        @Override
        public void init() {
            LafManager.installTheme(new DarculaTheme());
        }
    },
    SOLARIZED_LIGHT("solarizedlight", "Solarized Light") {
        @Override
        public void init() {
            LafManager.installTheme(new SolarizedLightTheme());
        }
    },
    SOLARIZED_DARK("solarizeddark", "Solarized Dark") {
        @Override
        public void init() {
            LafManager.installTheme(new SolarizedDarkTheme());
        }
    },
    LEGACY("legacy", "Legacy") {
        @Override
        public void init() {
            try {
                javax.swing.UIManager.setLookAndFeel(
                        javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    },
    ;

    private final String code;
    private final String title;

    UIThemeOption(String code, String title) {
        this.code = code;
        this.title = title;
    }

    public abstract void init();

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public static UIThemeOption getByCode(String code) {
        for (UIThemeOption t : values()) {
            if (t.getCode().equals(code)) {
                return t;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return title;
    }
}
