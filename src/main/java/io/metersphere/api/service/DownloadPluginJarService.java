package io.metersphere.api.service;

import io.metersphere.api.jmeter.utils.CommonBeanFactory;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.jmeter.utils.URLParserUtil;
import io.metersphere.api.repository.MinIORepositoryImpl;
import io.metersphere.api.service.utils.ZipSpider;
import io.metersphere.dto.AttachmentBodyFile;
import io.metersphere.dto.JmeterRunRequestDTO;
import io.metersphere.dto.PluginConfigDTO;
import io.metersphere.dto.PluginInfoDTO;
import io.metersphere.utils.LocalPathUtil;
import io.metersphere.utils.LoggerUtil;
import io.metersphere.utils.TemporaryFileUtil;
import jakarta.annotation.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DownloadPluginJarService {

    @Resource
    private MinIORepositoryImpl minIOConfigService;

    private TemporaryFileUtil temporaryFileUtil;

    public void downloadPlugin(JmeterRunRequestDTO runRequest) {
        if (temporaryFileUtil == null) {
            temporaryFileUtil = CommonBeanFactory.getBean(TemporaryFileUtil.class);
        }

        //Minio 初始化
        minIOConfigService.initMinioClient(runRequest.getPluginConfigDTO().getConfig());
        LoggerUtil.info("开始同步插件JAR：", runRequest.getReportId());

        if (CollectionUtils.isEmpty(runRequest.getPluginConfigDTO().getPluginDTOS())) {
            return;
        }
        List<String> jarPluginIds = new ArrayList<>();
        try {
            //获取本地已存在的jar信息
            List<String> nodeFiles = FileUtils.getFileNames(LocalPathUtil.PLUGIN_PATH, FileUtils.BODY_PLUGIN_FILE_DIR);
            //获取所有插件信息
            PluginConfigDTO pluginConfigDTO = runRequest.getPluginConfigDTO();

            List<PluginInfoDTO> pluginList = pluginConfigDTO.getPluginDTOS();
            //获取主服务插件jar信息
            List<String> dbJars = pluginList
                    .stream()
                    .map(PluginInfoDTO::getSourcePath)
                    .toList();
            if (CollectionUtils.isNotEmpty(nodeFiles)) {
                //node文件和主服务文件的差集，删除无用jar包
                List<String> expiredJar = nodeFiles
                        .stream().filter(plugin -> !dbJars.contains(plugin)).collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(expiredJar)) {
                    expiredJar.forEach(expired -> {
                        FileUtils.deleteFile(StringUtils.join(LocalPathUtil.PLUGIN_PATH, File.separator, StringUtils.substringAfter(expired, FileUtils.BODY_PLUGIN_FILE_DIR)));
                    });
                }
            }

            //需要从MinIO或者主服务工程获取的jar (优先从MinIO下载)
            pluginList = pluginList.stream().filter(plugin -> !nodeFiles.contains(plugin.getSourcePath())).collect(Collectors.toList());
            pluginList.forEach(plugin -> {
                try {
                    AttachmentBodyFile fileRequest = new AttachmentBodyFile();
                    fileRequest.setRemotePath(StringUtils.join(LocalPathUtil.PLUGIN_PATH, plugin.getPluginId(), File.separator, plugin.getPluginId()));
                    File file = minIOConfigService.getFile(fileRequest);
                    if (file != null && file.exists()) {
                        FileUtils.createFile(StringUtils.join(LocalPathUtil.PLUGIN_PATH, File.separator, StringUtils.substringAfter(plugin.getSourcePath(), FileUtils.BODY_PLUGIN_FILE_DIR)), temporaryFileUtil.fileToByte(file));
                    }
                } catch (Exception e) {
                    jarPluginIds.add(plugin.getPluginId());
                }
            });
            //兼容历史数据
            if (CollectionUtils.isNotEmpty(jarPluginIds)) {
                String plugJarUrl = URLParserUtil.getPluginURL(runRequest.getPlatformUrl());
                LoggerUtil.info("下载插件jar:", plugJarUrl);
                File plugFile = ZipSpider.downloadJarHistory(plugJarUrl, jarPluginIds, LocalPathUtil.PLUGIN_PATH);
                if (plugFile != null) {
                    ZipSpider.unzip(plugFile.getPath(), LocalPathUtil.PLUGIN_PATH);
                    FileUtils.deleteFile(plugFile.getPath());
                }
            }
            //load所有jar
            if (CollectionUtils.isNotEmpty(pluginList) || CollectionUtils.isNotEmpty(jarPluginIds)) {
                this.loadPlugJar(LocalPathUtil.PLUGIN_PATH);
            }
        } catch (Exception e) {
            LoggerUtil.error("node处理插件异常", runRequest.getReportId(), e);
        }
    }

    public void loadPlugJar(String jarPath) {
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
