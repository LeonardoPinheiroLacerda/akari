package io.github.leonardopinheirolacerda.akari.utils;

public final class DirectoryUtils {

    private DirectoryUtils() {
    }

    public static String parentOf(String path) {
        if (path == null) {
            return "";
        }

        final int lastSlash = path.lastIndexOf('/');

        if (lastSlash < 0) {
            return "";
        }

        return path.substring(0, lastSlash);
    }

    public static String join(String parentPath, String name) {
        return parentPath + "/" + name;
    }

    public static String lastSegmentOf(String path) {
        final int lastSlash = path.lastIndexOf('/');
        return lastSlash < 0 ? path : path.substring(lastSlash + 1);
    }

    public static int depthOf(String path) {
        return path.isEmpty() ? 0 : path.split("/").length;
    }

}
