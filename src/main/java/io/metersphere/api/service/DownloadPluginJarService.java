package io.metersphere.api.service;

import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.jmeter.utils.URLParserUtil;
import io.metersphere.api.service.utils.ZipSpider;
import io.metersphere.dto.JmeterRunRequestDTO;
import io.metersphere.dto.PluginConfigDTO;
import io.metersphere.dto.PluginInfoDTO;
import io.metersphere.utils.LoggerUtil;
import jakarta.annotation.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DownloadPluginJarService {

    @Resource
    private MinIOConfigService minIOConfigService;

    public void downloadPlugin(JmeterRunRequestDTO runRequest) {
        LoggerUtil.info("开始同步插件JAR：", runRequest.getReportId());
        List<String> jarPaths = new ArrayList<>();
        try {
            //获取本地已存在的jar信息
            List<String> nodeFiles = FileUtils.getFileNames(FileUtils.JAR_PLUG_FILE_DIR);
            //获取所有插件信息
            PluginConfigDTO pluginConfigDTO = runRequest.getPluginConfigDTO();
            Map<String, Object> minioConfig = pluginConfigDTO.getConfig();
            List<PluginInfoDTO> pluginList = pluginConfigDTO.getPluginDTOS();
            //获取主服务插件jar信息
            List<String> dbJars = pluginList
                    .stream()
                    .map(PluginInfoDTO::getSourcePath)
                    .collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(nodeFiles)) {
                //node文件和主服务文件的差集，删除无用jar包
                List<String> expiredJar = nodeFiles
                        .stream().filter(plugin -> !dbJars.contains(plugin)).collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(expiredJar)) {
                    expiredJar.stream().forEach(expired -> {
                        FileUtils.deleteFile(StringUtils.join(FileUtils.JAR_PLUG_FILE_DIR, File.separator, StringUtils.substringAfter(expired, FileUtils.BODY_PLUGIN_FILE_DIR)));
                    });
                }
            }
            //需要从MinIO或者主服务工程获取的jar (优先从MinIO下载)
            pluginList = pluginList.stream().filter(plugin -> !nodeFiles.contains(plugin.getSourcePath())).collect(Collectors.toList());
            pluginList.stream().forEach(plugin -> {
                try {
                    byte[] file = minIOConfigService.getFile(minioConfig, plugin.getPluginId());
                    if (ArrayUtils.isNotEmpty(file)) {
                        FileUtils.createFile(StringUtils.join(FileUtils.JAR_PLUG_FILE_DIR, File.separator, StringUtils.substringAfter(plugin.getSourcePath(), FileUtils.BODY_PLUGIN_FILE_DIR)), file);
                    }
                } catch (Exception e) {
                    jarPaths.add(plugin.getSourcePath());
                }
            });
            //兼容历史数据
            if (CollectionUtils.isNotEmpty(jarPaths)) {
                String plugJarUrl = URLParserUtil.getPluginURL(runRequest.getPlatformUrl());
                LoggerUtil.info("下载插件jar:", plugJarUrl);
                File plugFile = ZipSpider.downloadJarHistory(plugJarUrl, jarPaths, FileUtils.JAR_PLUG_FILE_DIR);
                if (plugFile != null) {
                    ZipSpider.unzip(plugFile.getPath(), FileUtils.JAR_PLUG_FILE_DIR);
                    FileUtils.deleteFile(plugFile.getPath());
                }
            }
            //load所有jar
            this.loadPlugJar(FileUtils.JAR_PLUG_FILE_DIR);
        } catch (Exception e) {
            LoggerUtil.error("node处理插件异常", runRequest.getReportId(), e);
        }
    }

    private void loadPlugJar(String jarPath) {
        File file = new File(jarPath);
        if (file.isDirectory() && !jarPath.endsWith("/")) {
            file = new File(jarPath + "/");
        }

        File[] jars = listJars(file);
        for (File jarFile : jars) {
            try {
                ClassLoader classLoader = ClassLoader.getSystemClassLoader();
                try {
                    // 从URLClassLoader类中获取类所在文件夹的方法，jar也可以认为是一个文件夹
                    Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                    method.setAccessible(true);
                    // 获取系统类加载器
                    method.invoke(classLoader, jarFile.toURI().toURL());
                } catch (Exception e) {
                    Method method = classLoader.getClass()
                            .getDeclaredMethod("appendToClassPathForInstrumentation", String.class);
                    method.setAccessible(true);
                    method.invoke(classLoader, jarFile.getPath());
                }
            } catch (Exception e) {
                LoggerUtil.error(e);
            }
        }
    }

    private static File[] listJars(File dir) {
        if (dir.isDirectory()) {
            return dir.listFiles((f, name) -> {
                if (name.endsWith(".jar")) {// $NON-NLS-1$
                    File jar = new File(f, name);
                    return jar.isFile() && jar.canRead();
                }
                return false;
            });
        }
        return new File[0];
    }

}
