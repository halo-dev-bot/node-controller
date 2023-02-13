package io.metersphere.api.jmeter.utils;

import io.metersphere.utils.LoggerUtil;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    public static final String BODY_FILE_DIR = "/opt/metersphere/data/body";
    public static final String JAR_PLUG_FILE_DIR = "/opt/metersphere/data/node/plug/jar";
    public static final String IS_REF = "isRef";
    public static final String FILE_ID = "fileId";
    public static final String FILENAME = "filename";

    public static final String FILE_PATH = "File.path";

    public static final String KEYSTORE_FILE_PATH = "MS-KEYSTORE-FILE-PATH";
    public static final String BODY_PLUGIN_FILE_DIR = "/opt/metersphere/data/body/plugin/";

    public static void deleteFile(String path) {
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
    }

    public static void deleteDir(String path) {
        try {
            File file = new File(path);
            if (file.isDirectory()) {
                org.apache.commons.io.FileUtils.deleteDirectory(file);
            }
        } catch (Exception e) {
            LoggerUtil.error(e);
        }
    }

    public static List<String> getFileNames(String path, String targetPath) {
        File f = new File(path);
        if (!f.exists()) {
            f.mkdirs();
        }
        List<String> fileNames = new ArrayList<>();
        File fa[] = f.listFiles();
        for (int i = 0; i < fa.length; i++) {
            File fs = fa[i];
            if (fs.exists()) {
                fileNames.add(StringUtils.join(targetPath, fs.getName()));
            }
        }
        return fileNames;
    }

    public static void createFile(String filePath, byte[] fileBytes) {
        File file = new File(filePath);
        if (file.exists()) {
            file.delete();
        }
        try {
            File dir = file.getParentFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            file.createNewFile();
        } catch (Exception e) {
            LoggerUtil.error(e);
        }

        try (InputStream in = new ByteArrayInputStream(fileBytes); OutputStream out = new FileOutputStream(file)) {
            final int MAX = 4096;
            byte[] buf = new byte[MAX];
            for (int bytesRead = in.read(buf, 0, MAX); bytesRead != -1; bytesRead = in.read(buf, 0, MAX)) {
                out.write(buf, 0, bytesRead);
            }
        } catch (IOException e) {
            LoggerUtil.error(e);
        }
    }
}
