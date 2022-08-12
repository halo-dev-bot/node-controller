package io.metersphere.api.jmeter;

import com.alibaba.fastjson.JSON;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.jmeter.utils.MSException;
import io.metersphere.api.service.JMeterRunContext;
import io.metersphere.api.service.utils.ZipSpider;
import io.metersphere.dto.JmeterRunRequestDTO;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MsDriverManager {
    private static final String PROJECT_ID = "projectId";

    public static List<String> loadJar(JmeterRunRequestDTO runRequest) {
        List<String> jarPaths = new ArrayList<>();
        try {
            if (runRequest.getExtendedParameters() != null && runRequest.getExtendedParameters().containsKey(PROJECT_ID)) {
                List<String> projectIds = JSON.parseObject(runRequest.getExtendedParameters().get(PROJECT_ID).toString(), List.class);
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
            List<String> projectIds = JSON.parseObject(runRequest.getExtendedParameters().get(PROJECT_ID).toString(), List.class);
            for (String projectId : projectIds) {
                if (JMeterRunContext.getContext().isEnable() && JMeterRunContext.getContext().getProjectUrls().containsKey(projectId)) {
                    continue;
                }
                jarUrl += "/" + projectId;
                download(projectId, jarUrl);
                JMeterRunContext.getContext().getProjectUrls().put(projectId, jarUrl);
            }
        }
    }

    public static void downloadJar(Map<String, String> projectUrls) {
        projectUrls.forEach((k, v) -> {
            download(k, v);
        });
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