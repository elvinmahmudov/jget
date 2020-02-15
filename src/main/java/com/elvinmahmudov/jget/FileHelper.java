package com.elvinmahmudov.jget;

import java.io.File;
import java.io.IOException;

public class FileHelper {
    private FileHelper() {

    }

    public static String getSafeDirPath(String dirPath) {
        if (dirPath.lastIndexOf(File.separator) == dirPath.length() - 1) {
            dirPath = dirPath.substring(0, dirPath.length() - 1);
        }
        return dirPath;
    }

    public static File getSafeFile(String dirPath, String fileName)
            throws IOException {
        dirPath = getSafeDirPath(dirPath);
        File dir = new File(dirPath);
        dir.mkdirs();
        File newFile = new File(dirPath + File.separator + fileName);
        if (!newFile.exists()) {
            newFile.createNewFile();
        }
        return newFile;
    }
}
