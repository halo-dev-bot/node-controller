package io.metersphere.api.jmeter.utils;

import io.metersphere.api.service.utils.BodyFile;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.config.CSVDataSet;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.protocol.http.util.HTTPFileArg;
import org.apache.jorphan.collections.HashTree;
import org.aspectj.util.FileUtil;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.List;

public class FileUtils {
    public static final String BODY_FILE_DIR = "/opt/metersphere/data/body";
    public static final String JAR_FILE_DIR = "/opt/metersphere/data/node/jar";
    public static final String JAR_PLUG_FILE_DIR = "/opt/metersphere/data/node/plug/jar";

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
        if (file.isDirectory()) {// $NON-NLS-1$
            file = new File(path + "/");
        }

        File[] files = getFiles(file);
        for (int i = 0; i < files.length; i++) {
            files[i].delete();
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
                        files.add(file);
                    }
                }
            } else if (key instanceof CSVDataSet) {
                CSVDataSet source = (CSVDataSet) key;
                if (source != null && StringUtils.isNotEmpty(source.getPropertyAsString("filename"))) {
                    BodyFile file = new BodyFile();
                    file.setId(source.getPropertyAsString("filename"));
                    file.setName(source.getPropertyAsString("filename"));
                    files.add(file);
                }
            }
            if (node != null) {
                getFiles(node, files);
            }
        }
    }
}
