package de.haukerehfeld.quakeinjector.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Heuristic check whether an executable is a Quake (1) engine, and how modern it is.
 *
 * Quake engines descend from id Software's GPL source and keep its asset paths,
 * network message names and error strings. Newer engines add strings for the
 * features they support (BSP2 maps, CSQC, QuakeC extensions), which we use to
 * tell the generations apart.
 */
public final class QuakeEngineDetector {

    public enum EngineType {
        /** Not a Quake engine. */
        NOT_ENGINE,
        /** Original id engines: WinQuake, GLQuake, QuakeWorld clients. */
        LEGACY,
        /** Limit-removing ports without QSS features: Quakespasm, JoeQuake. */
        ENHANCED,
        /** Engines with QSS features (CSQC huds, QC extensions): Quakespasm-Spiked, vkQuake, Ironwail. */
        MODERN
    }

    /** Strings from the original id source, present in every Quake engine. */
    private static final String[] ENGINE_MARKERS = {
            "gfx/palette.lmp",
            "gfx/colormap.lmp",
            "gfx.wad",
            "quake.rc",
            "maps/%s.bsp",
            "svc_serverinfo",
            "svc_stufftext",
            "svc_intermission",
            "Host_Error",
            "PR_ExecuteProgram",
            "ED_Alloc",
            "SV_StartSound",
            "CL_ParseServerMessage",
            "Mod_LoadAliasModel",
            "SZ_GetSpace",
    };

    /**
     * How many engine markers must be found. QuakeWorld clients have no game
     * server code and match only 11 of 15; non-engines matched at most 1.
     */
    private static final int ENGINE_THRESHOLD = 8;

    /** BSP2 map format identifiers: support for maps beyond the original limits. */
    private static final String[] EXTENDED_LIMITS_MARKERS = {
            "BSP2",
            "2PSB",
    };

    /** CSQC (custom huds) and QuakeC extensions: the QSS feature set. */
    private static final String[] MODERN_MARKERS = {
            "csprogs.dat",
            "CSQC_DrawHud",
            "CSQC_DrawScores",
            "checkextension",
            "DP_QC_SPRINTF",
            "FTE_STRINGS",
            "FRIK_FILE",
            "traileffectnum",
            "emiteffectnum",
            "autocvar_",
            "SendEntity",
    };

    private static final int MODERN_THRESHOLD = 8;

    /** Skip anything larger than this; engines are a few MB at most. */
    private static final long MAX_SIZE = 64L * 1024 * 1024;

    private QuakeEngineDetector() {
    }

    public static boolean isQuakeEngine(File file) {
        return classify(file) != EngineType.NOT_ENGINE;
    }

    public static EngineType classify(File file) {
        String content = readExecutable(file);
        if (content == null || countMarkers(content, ENGINE_MARKERS) < ENGINE_THRESHOLD) {
            return EngineType.NOT_ENGINE;
        }
        if (countMarkers(content, MODERN_MARKERS) >= MODERN_THRESHOLD) {
            return EngineType.MODERN;
        }
        if (countMarkers(content, EXTENDED_LIMITS_MARKERS) == EXTENDED_LIMITS_MARKERS.length) {
            return EngineType.ENHANCED;
        }
        return EngineType.LEGACY;
    }

    /** Returns the file's bytes as a string, or null if it is not a readable Windows executable. */
    private static String readExecutable(File file) {
        if (file == null || !file.isFile() || file.length() < 2 || file.length() > MAX_SIZE) {
            return null;
        }
        byte[] data;
        try {
            data = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            return null;
        }
        // Windows executables start with "MZ".
        if (data[0] != 'M' || data[1] != 'Z') {
            return null;
        }
        // ISO-8859-1 maps every byte to one char, so contains() works as a byte search.
        return new String(data, StandardCharsets.ISO_8859_1);
    }

    private static int countMarkers(String content, String[] markers) {
        int found = 0;
        for (String marker : markers) {
            if (content.contains(marker)) {
                found++;
            }
        }
        return found;
    }

    public static void main(String[] args) {
        for (String arg : args) {
            System.out.printf("%-10s %s%n", classify(new File(arg)), arg);
        }
    }
}
