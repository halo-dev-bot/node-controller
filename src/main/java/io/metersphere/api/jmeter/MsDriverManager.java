package io.metersphere.api.jmeter;

import com.fasterxml.jackson.core.type.TypeReference;
import io.metersphere.api.enums.StorageConstants;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.jmeter.utils.MSException;
import io.metersphere.api.jmeter.utils.URLParserUtil;
import io.metersphere.api.repository.GitRepositoryImpl;
import io.metersphere.api.repository.MinIORepositoryImpl;
import io.metersphere.api.service.utils.ZipSpider;
import io.metersphere.dto.AttachmentBodyFile;
import io.metersphere.dto.JmeterRunRequestDTO;
import io.metersphere.dto.ProjectJarConfig;
import io.metersphere.utils.JarConfigUtils;
import io.metersphere.utils.JsonUtils;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MsDriverManager {
    private static final String PROJECT_JAR_MAP = "PROJECT_JAR_MAP";

    public static void downloadProjectJar(JmeterRunRequestDTO runRequest) {
        if (runRequest.getExtendedParameters() != null && runRequest.getExtendedParameters().containsKey(PROJECT_JAR_MAP)) {
            Map<String, List<ProjectJarConfig>> map = JsonUtils.parseObject(runRequest.getExtendedParameters().get(PROJECT_JAR_MAP).toString(), new TypeReference<Map<String, List<ProjectJarConfig>>>() {
            });
            //获取项目id
            List<String> projectIds = map.keySet().stream().collect(Collectors.toList());
            //获取需要下载的jar包
            Map<String, List<ProjectJarConfig>> jarConfigs = JarConfigUtils.getJarConfigs(projectIds, map);
            if (MapUtils.isNotEmpty(jarConfigs)) {
                Map<String, List<ProjectJarConfig>> historyDataMap = new HashMap<>();
                Map<String, List<ProjectJarConfig>> gitMap = new HashMap<>();
                Map<String, List<ProjectJarConfig>> minIOMap = new HashMap<>();
                jarConfigs.forEach((key, value) -> {
                    //历史数据
                    historyDataMap.put(key, value.stream().filter(s -> s.isHasFile()).collect(Collectors.toList()));
                    //Git下载
                    gitMap.put(key, value.stream().filter(s -> StringUtils.equals(StorageConstants.GIT.name(), s.getStorage())).collect(Collectors.toList()));
                    //MinIO下载
                    minIOMap.put(key, value.stream().filter(s -> StringUtils.equals(StorageConstants.MINIO.name(), s.getStorage())).collect(Collectors.toList()));
                });
                try {
                    // 生成附件/JAR文件
                    String jarUrl = URLParserUtil.getJarURL(runRequest.getPlatformUrl());
                    LoggerUtil.info("开始同步上传的JAR：" + jarUrl);
                    //下载历史jar包
                    File file = ZipSpider.downloadJarDb(jarUrl, historyDataMap, FileUtils.PROJECT_JAR_FILE_DIR);
                    if (file != null) {
                        ZipSpider.unzip(file.getPath(), FileUtils.PROJECT_JAR_FILE_DIR);
                        FileUtils.deleteFile(file.getPath());
                    }
                } catch (Exception e) {
                    LoggerUtil.error(e.getMessage(), e);
                    MSException.throwException(e.getMessage());
                }
                //下载Git jar包
                if (MapUtils.isNotEmpty(gitMap)) {
                    GitRepositoryImpl gitRepository = new GitRepositoryImpl();
                    gitMap.forEach((key, value) -> {
                        value.stream().forEach(s -> {
                            try {
                                AttachmentBodyFile attachmentBodyFile = new AttachmentBodyFile();
                                attachmentBodyFile.setFileAttachInfoJson(s.getAttachInfo());
                                byte[] gitFiles = gitRepository.getFile(attachmentBodyFile);
                                FileUtils.createFile(StringUtils.join(FileUtils.PROJECT_JAR_FILE_DIR,
                                        File.separator,
                                        key,
                                        File.separator,
                                        s.getId(),
                                        File.separator,
                                        String.valueOf(s.getUpdateTime()), ".jar"), gitFiles);
                            } catch (Exception e) {
                                LoggerUtil.error(e.getMessage(), e);
                                LoggerUtil.error("Jar包下载失败，不存在Git仓库中");
                            }
                        });
                    });
                }
                //下载MinIO jar包
                if (MapUtils.isNotEmpty(minIOMap)) {
                    MinIORepositoryImpl minIORepository = new MinIORepositoryImpl();
                    minIOMap.forEach((key, value) -> {
                        value.stream().forEach(s -> {
                            try {
                                String path = StringUtils.join(
                                        File.separator,
                                        key, File.separator, s.getName());
                                byte[] bytes = minIORepository.getFileAsStream(path).readAllBytes();
                                FileUtils.createFile(StringUtils.join(FileUtils.PROJECT_JAR_FILE_DIR,
                                        File.separator,
                                        key,
                                        File.separator,
                                        s.getId(),
                                        File.separator,
                                        String.valueOf(s.getUpdateTime()), ".jar"), bytes);
                            } catch (Exception e) {
                                LoggerUtil.error(e.getMessage(), e);
                                LoggerUtil.error("Jar包下载失败，不存在MinIO中");
                            }
                        });
                    });
                }
            }
        }
    }
}