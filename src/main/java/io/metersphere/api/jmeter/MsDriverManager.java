package io.metersphere.api.jmeter;

import com.fasterxml.jackson.core.type.TypeReference;
import io.metersphere.api.enums.StorageConstants;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.jmeter.utils.MSException;
import io.metersphere.api.repository.GitRepositoryImpl;
import io.metersphere.api.repository.MinIORepositoryImpl;
import io.metersphere.api.service.JMeterRunContext;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MsDriverManager {
    private static final String PROJECT_ID = "projectId";

    private static final String PROJECT_JAR_MAP = "PROJECT_JAR_MAP";

    public static List<String> loadJar(JmeterRunRequestDTO runRequest) {
        List<String> jarPaths = new ArrayList<>();
        try {
            if (runRequest.getExtendedParameters() != null && runRequest.getExtendedParameters().containsKey(PROJECT_ID)) {
                List<String> projectIds = JsonUtils.parseObject(runRequest.getExtendedParameters().get(PROJECT_ID).toString(), List.class);
                projectIds.forEach(projectId -> {
                    File file = new File(StringUtils.join(FileUtils.PROJECT_JAR_FILE_DIR, "/", projectId + "/"));
                    if (file.isFile()) {
                        jarPaths.add(file.getPath());
                    } else {
                        File[] files = file.listFiles();
                        if (files != null && files.length > 0) {
                            for (File f : files) {
                                if (!f.exists() || !f.getPath().endsWith(".jar")) {
                                    continue;
                                }
                                jarPaths.add(f.getPath());
                            }
                        }
                    }
                });
            }
        } catch (Exception e) {
            LoggerUtil.error(e.getMessage(), e);
            MSException.throwException(e.getMessage());
        }
        return jarPaths;
    }

    public static void downloadJar(JmeterRunRequestDTO runRequest, String jarUrl) {
        if (runRequest.getExtendedParameters() != null && runRequest.getExtendedParameters().containsKey(PROJECT_ID)) {
            List<String> projectIds = JsonUtils.parseObject(runRequest.getExtendedParameters().get(PROJECT_ID).toString(), List.class);
            for (String projectId : projectIds) {
                if (JMeterRunContext.getContext().isEnable() && JMeterRunContext.getContext().getProjectUrls().containsKey(projectId)) {
                    continue;
                }
                String url = StringUtils.join(jarUrl, File.separator, projectId);
                download(projectId, url);
                JMeterRunContext.getContext().getProjectUrls().put(projectId, url);
            }
        }
    }

    public static void downloadProjectJar(JmeterRunRequestDTO runRequest, String jarUrl) {
        if (runRequest.getExtendedParameters() != null && runRequest.getExtendedParameters().containsKey(PROJECT_JAR_MAP)) {
            Map<String, List<ProjectJarConfig>> map = JsonUtils.parseObject(runRequest.getExtendedParameters().get(PROJECT_JAR_MAP).toString(), new TypeReference<Map<String, List<ProjectJarConfig>>>() {
            });
            //获取项目id
            List<String> projectIds = map.keySet().stream().collect(Collectors.toList());
            //获取需要下载的jar包
            Map<String, List<ProjectJarConfig>> jarConfigs = JarConfigUtils.getJarConfigs(projectIds, map, FileUtils.PROJECT_JAR_FILE_DIR);
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
                //下载历史jar包
                File file = ZipSpider.downloadJarDb(jarUrl, historyDataMap, FileUtils.PROJECT_JAR_FILE_DIR);
                if (file != null) {
                    ZipSpider.unzip(file.getPath(), FileUtils.PROJECT_JAR_FILE_DIR);
                    FileUtils.deleteFile(file.getPath());
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
                                        s.getRefId(),
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
                                        s.getRefId(),
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

    private static void download(String projectId, String url) {
        String path = StringUtils.join(FileUtils.PROJECT_JAR_FILE_DIR, "/", projectId, "/");
        // 先清理历史遗留
        FileUtils.deletePath(path);

        File file = ZipSpider.downloadFile(url, path);
        if (file != null) {
            ZipSpider.unzip(file.getPath(), path);
            FileUtils.deleteFile(file.getPath());
        }
    }
}