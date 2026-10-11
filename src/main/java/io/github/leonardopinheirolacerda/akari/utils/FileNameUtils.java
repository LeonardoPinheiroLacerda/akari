package io.github.leonardopinheirolacerda.akari.utils;

import java.util.Set;

public final class FileNameUtils {

    private static final Set<String> VIDEO_EXTENSIONS =
            Set.of("mkv", "mp4", "avi", "mov", "wmv", "webm", "ts", "m2ts", "flv");

    private FileNameUtils() {
    }

    public static String extensionOf(String name) {
        final int lastDot = name.lastIndexOf('.');
        return lastDot < 0 ? null : name.substring(lastDot + 1);
    }

    public static boolean isVideoFile(String name) {
        final String extension = extensionOf(name);
        return extension != null && VIDEO_EXTENSIONS.contains(extension.toLowerCase());
    }

}
