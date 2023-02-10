package io.metersphere.api.jmeter.utils;

import io.metersphere.api.service.utils.BodyFile;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.config.CSVDataSet;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.protocol.http.util.HTTPFileArg;
import org.apache.jorphan.collections.HashTree;
import org.aspectj.util.FileUtil;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    public static final String BODY_FILE_DIR = "/opt/metersphere/data/body";
    public static final String PROJECT_JAR_FILE_DIR = "/opt/metersphere/data/node/jar";
    public static final String JAR_PLUG_FILE_DIR = "/opt/metersphere/data/node/plug/jar";
    public static final String IS_REF = "isRef";
    public static final String FILE_ID = "fileId";
    public static final String FILENAME = "filename";

    public static final String FILE_PATH = "File.path";
    public static final String BODY_PLUGIN_FILE_DIR = "/opt/metersphere/data/body/plugin/";

    public static void createFiles(MultipartFile[] bodyFiles, String path) {
        if (bodyFiles != null && bodyFiles.length > 0) {
            File testDir = new File(path);
            if (!testDir.exists()) {
                testDir.mkdirs();
            }
            for (int i = 0; i < bodyFiles.length; i++) {
                MultipartFile item = bodyFiles[i];
                File file = new File(path + "/" + item.getOriginalFilename());
                try (InputStream in = item.getInputStream(); OutputStream out = new FileOutputStream(file)) {
                    file.createNewFile();
                    FileUtil.copyStream(in, out);
                } catch (IOException e) {
                    LoggerUtil.error(e);
                    MSException.throwException("文件处理异常");
                }
            }
        }
    }

    public static void deleteFile(String path) {
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
    }

    private static File[] getFiles(File dir) {
        return dir.listFiles((f, name) -> {
            File jar = new File(f, name);
            return jar.isFile() && jar.canRead();
        });
    }


    public static void deletePath(String path) {
        File file = new File(path);
        if (file != null && file.isDirectory()) {// $NON-NLS-1$
            file = new File(path + "/");
        }
        if (file != null) {
            File[] files = getFiles(file);
            if (ArrayUtils.isNotEmpty(files)) {
                for (int i = 0; i < files.length; i++) {
                    if (files[i] != null && files[i].exists()) {
                        files[i].delete();
                    }
                }
            }
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

    public static void getFiles(HashTree tree, List<BodyFile> files) {
        for (Object key : tree.keySet()) {
            HashTree node = tree.get(key);
            if (key instanceof HTTPSamplerProxy) {
                HTTPSamplerProxy source = (HTTPSamplerProxy) key;
                if (source != null && source.getHTTPFiles().length > 0) {
                    for (HTTPFileArg arg : source.getHTTPFiles()) {
                        BodyFile file = new BodyFile();
                        file.setId(arg.getParamName());
                        file.setName(arg.getPath());
                        if (arg.getPropertyAsBoolean(IS_REF)) {
                            file.setRefResourceId(arg.getPropertyAsString(FILE_ID));
                        }
                        files.add(file);
                    }
                }
            } else if (key instanceof CSVDataSet) {
                CSVDataSet source = (CSVDataSet) key;
                if (source != null && StringUtils.isNotEmpty(source.getPropertyAsString(FILENAME))) {
                    BodyFile file = new BodyFile();
                    file.setId(source.getPropertyAsString(FILENAME));
                    file.setName(source.getPropertyAsString(FILENAME));
                    if (source.getPropertyAsBoolean(IS_REF)) {
                        file.setRefResourceId(source.getPropertyAsString(FILE_ID));
                    }
                    files.add(file);
                }
            }
            if (node != null) {
                getFiles(node, files);
            }
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
