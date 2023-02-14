package io.metersphere.api.jmeter;

import io.metersphere.api.enums.StorageConstants;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.jmeter.utils.MSException;
import io.metersphere.api.jmeter.utils.URLParserUtil;
import io.metersphere.api.repository.MinIORepositoryImpl;
import io.metersphere.api.service.utils.ZipSpider;
import io.metersphere.dto.JmeterRunRequestDTO;
import io.metersphere.dto.ProjectJarConfig;
import io.metersphere.jmeter.ProjectClassLoader;
import io.metersphere.utils.JarConfigUtils;
import io.metersphere.utils.LocalPathUtil;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MsDriverManager {

    public static void downloadProjectJar(JmeterRunRequestDTO runRequest) {
        if (MapUtils.isNotEmpty(runRequest.getCustomJarInfo())) {
            Map<String, List<ProjectJarConfig>> map = runRequest.getCustomJarInfo();
            //获取项目id
            List<String> projectIds = map.keySet().stream().collect(Collectors.toList());
            //获取需要下载的jar包
            Map<String, List<ProjectJarConfig>> jarConfigs = JarConfigUtils.getJarConfigs(projectIds, map);
            if (MapUtils.isNotEmpty(jarConfigs)) {
                Map<String, List<ProjectJarConfig>> historyDataMap = new HashMap<>();
                Map<String, List<ProjectJarConfig>> gitMap = new HashMap<>();
                Map<String, List<ProjectJarConfig>> minIOMap = new HashMap<>();
                List<String> loaderProjectIds = new ArrayList<>();
                jarConfigs.forEach((key, value) -> {
                    if (CollectionUtils.isNotEmpty(value)) {
                        loaderProjectIds.add(key);
                        //Git下载或历史数据
                        List<ProjectJarConfig> otherRepositoryList = value.stream().distinct().filter(s -> !StringUtils.equals(StorageConstants.MINIO.name(), s.getStorage()) || s.isHasFile()).collect(Collectors.toList());
                        if (CollectionUtils.isNotEmpty(otherRepositoryList)) {
                            historyDataMap.put(key, otherRepositoryList);
                        }
                        //MinIO下载
                        List<ProjectJarConfig> minIOList = value.stream().distinct().filter(s -> StringUtils.equals(StorageConstants.MINIO.name(), s.getStorage())).collect(Collectors.toList());
                        if (CollectionUtils.isNotEmpty(minIOList)) {
                            minIOMap.put(key, minIOList);
                        }
                    }
                });
                //下载MinIO jar包
                if (MapUtils.isNotEmpty(minIOMap)) {
                    MinIORepositoryImpl minIORepository = new MinIORepositoryImpl();
                    minIOMap.forEach((key, value) -> {
                        value.stream().forEach(s -> {
                            try {
                                String path = StringUtils.join(
                                        key, File.separator, s.getName());
                                LoggerUtil.info("开始下载MinIO中的Jar包，文件名：" + s.getName() + "，路径：" + path);
                                byte[] bytes = minIORepository.getFileAsStream(path).readAllBytes();
                                FileUtils.createFile(StringUtils.join(LocalPathUtil.JAR_PATH,
                                        File.separator,
                                        key,
                                        File.separator,
                                        s.getId(),
                                        File.separator,
                                        String.valueOf(s.getUpdateTime()), ".jar"), bytes);
                            } catch (Exception e) {
                                historyDataMap.put(key, value);
                                LoggerUtil.error(e.getMessage(), e);
                                LoggerUtil.error("Jar包下载失败，不存在MinIO中");
                            }
                        });
                    });
                }
                //下载Git或本地jar包
                if (MapUtils.isNotEmpty(historyDataMap)) {
                    try {
                        // 生成附件/JAR文件
                        String jarUrl = URLParserUtil.getJarURL(runRequest.getPlatformUrl());
                        LoggerUtil.info("开始同步上传的JAR：" + jarUrl);
                        //下载历史jar包
                        File file = ZipSpider.downloadJarDb(jarUrl, historyDataMap, LocalPathUtil.JAR_PATH);
                        if (file != null) {
                            ZipSpider.unzip(file.getPath(), LocalPathUtil.JAR_PATH);
                            FileUtils.deleteFile(file.getPath());
                        }
                    } catch (Exception e) {
                        LoggerUtil.error(e.getMessage(), e);
                        MSException.throwException(e.getMessage());
                    }
                }
                if (CollectionUtils.isNotEmpty(loaderProjectIds)) {
                    ProjectClassLoader.initClassLoader(loaderProjectIds);
                }
            }
        }
    }
}