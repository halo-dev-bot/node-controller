package io.metersphere.api.service;

import io.metersphere.api.jmeter.ExtendedParameter;
import io.metersphere.api.jmeter.JMeterService;
import io.metersphere.api.jmeter.MsDriverManager;
import io.metersphere.api.jmeter.queue.BlockingQueueUtil;
import io.metersphere.api.jmeter.queue.PoolExecBlockingQueueUtil;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.jmeter.utils.MSException;
import io.metersphere.api.jmeter.utils.URLParserUtil;
import io.metersphere.api.service.utils.BodyFile;
import io.metersphere.api.service.utils.BodyFileRequest;
import io.metersphere.api.service.utils.ZipSpider;
import io.metersphere.api.vo.ScriptData;
import io.metersphere.dto.JmeterRunRequestDTO;
import io.metersphere.utils.JsonUtils;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jorphan.collections.HashTree;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

@Service
public class JMeterExecuteService {
    @Resource
    private JMeterService jMeterService;
    @Resource
    private ProducerService producerService;
    @Resource
    private RestTemplate restTemplate;

    public String runStart(JmeterRunRequestDTO runRequest) {
        try {
            LoggerUtil.info("进入node执行方法开始处理任务", runRequest.getReportId());
            if (runRequest.getKafkaConfig() == null) {
                LoggerUtil.error("KAFKA配置为空无法执行", runRequest.getReportId());
                return "KAFKA 初始化失败，请检查配置";
            }
            // 生成附件/JAR文件
            String jarUrl = URLParserUtil.getJarURL(runRequest.getPlatformUrl());
            String plugJarUrl = URLParserUtil.getPluginURL(runRequest.getPlatformUrl());
            LoggerUtil.info("开始同步上传的JAR：" + jarUrl);
            MsDriverManager.downloadJar(runRequest, jarUrl);

            if (StringUtils.isEmpty(JMeterRunContext.getContext().getPlugUrl())) {
                LoggerUtil.info("开始同步插件JAR：" + plugJarUrl, runRequest.getReportId());
                File plugFile = ZipSpider.downloadFile(plugJarUrl, FileUtils.JAR_PLUG_FILE_DIR);
                if (plugFile != null) {
                    ZipSpider.unzip(plugFile.getPath(), FileUtils.JAR_PLUG_FILE_DIR);
                    this.loadPlugJar(FileUtils.JAR_PLUG_FILE_DIR);
                }
            }
            JMeterRunContext.getContext().setPlugUrl(plugJarUrl);
            JMeterRunContext.getContext().setEnable(runRequest.isEnable());

            LoggerUtil.info("开始拉取脚本和脚本附件：" + runRequest.getPlatformUrl(), runRequest.getReportId());
            if (runRequest.getHashTree() != null) {
                TestPlan test = (TestPlan) runRequest.getHashTree().getArray()[0];
                test.setProperty(ExtendedParameter.JAR_PATH, JsonUtils.toJSONString(MsDriverManager.loadJar(runRequest)));
                jMeterService.run(runRequest);
                return "SUCCESS";
            }
            File bodyFile = ZipSpider.downloadFile(runRequest.getPlatformUrl(), FileUtils.BODY_FILE_DIR);
            if (bodyFile != null) {
                ZipSpider.unzip(bodyFile.getPath(), FileUtils.BODY_FILE_DIR);
                File jmxFile = new File(FileUtils.BODY_FILE_DIR + "/" + runRequest.getReportId() + "_" + runRequest.getTestId() + ".jmx");
                LoggerUtil.info("下载执行脚本完成：" + jmxFile.getName(), runRequest.getReportId());
                // 生成执行脚本
                HashTree testPlan = SaveService.loadTree(jmxFile);
                TestPlan test = (TestPlan) testPlan.getArray()[0];
                test.setProperty(ExtendedParameter.JAR_PATH, JsonUtils.toJSONString(MsDriverManager.loadJar(runRequest)));
                // 开始执行
                runRequest.setHashTree(testPlan);
                LoggerUtil.info("开始加入队列执行", runRequest.getReportId());
                jMeterService.run(runRequest);
                FileUtils.deleteFile(bodyFile.getPath());
            } else {
                PoolExecBlockingQueueUtil.offer(runRequest.getReportId());
                MSException.throwException("未找到执行的JMX文件");
            }
        } catch (Exception e) {
            LoggerUtil.error("node处理任务异常", runRequest.getReportId(), e);
            BlockingQueueUtil.remove(runRequest.getReportId());
            PoolExecBlockingQueueUtil.offer(runRequest.getReportId());
            LoggerUtil.info("node处理任务异常，补偿一条失败消息", runRequest.getReportId(), e);
            producerService.send(runRequest, "node处理任务异常：" + e.getMessage());
            LoggerUtil.error("处理脚本异常" + runRequest.getReportId(), e);
        }
        return "SUCCESS";
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

    private void loadPlugJar(String jarPath) {
        File file = new File(jarPath);
        if (file.isDirectory() && !jarPath.endsWith("/")) {// $NON-NLS-1$
            file = new File(jarPath + "/");// $NON-NLS-1$
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

    public String debug(JmeterRunRequestDTO runRequest) {
        try {
            runRequest.getExtendedParameters().put(LoggerUtil.DEBUG, true);
            Map<String, String> params = new HashMap<>();
            params.put("reportId", runRequest.getReportId());
            params.put("testId", runRequest.getTestId());
            String script = this.getForObject(URLParserUtil.getScriptURL(runRequest.getPlatformUrl()), params);
            InputStream inputSource = getStrToStream(script);
            runRequest.setHashTree(JMeterService.getHashTree(SaveService.loadElement(inputSource)));
            runRequest.setDebug(true);
            List<BodyFile> files = new ArrayList<>();
            FileUtils.getFiles(runRequest.getHashTree(), files);
            if (CollectionUtils.isNotEmpty(files)) {
                LoggerUtil.info("获取到附件文件", JsonUtils.toJSONString(files));
                String uri = URLParserUtil.getDownFileURL(runRequest.getPlatformUrl());
                BodyFileRequest request = new BodyFileRequest(runRequest.getReportId(), files);
                File bodyFile = ZipSpider.downloadFile(uri, request, FileUtils.BODY_FILE_DIR);
                if (bodyFile != null) {
                    runRequest.getExtendedParameters().put(ExtendedParameter.JMX_FILES, files);
                    ZipSpider.unzip(bodyFile.getPath(), FileUtils.BODY_FILE_DIR);
                    FileUtils.deleteFile(bodyFile.getPath());
                }
            }
            return this.runStart(runRequest);
        } catch (Exception e) {
            LoggerUtil.error(e);
            return e.getMessage();
        }
    }

    private String getForObject(String url, Object object) {
        StringBuffer stringBuffer = new StringBuffer(url);
        if (object instanceof Map) {
            Iterator iterator = ((Map) object).entrySet().iterator();
            if (iterator.hasNext()) {
                stringBuffer.append("?");
                Object element;
                while (iterator.hasNext()) {
                    element = iterator.next();
                    Map.Entry<String, Object> entry = (Map.Entry) element;
                    if (entry.getValue() != null) {
                        stringBuffer.append(element).append("&");
                    }
                    url = stringBuffer.substring(0, stringBuffer.length() - 1);
                }
            }
        }
        return restTemplate.getForObject(url, ScriptData.class).getData();
    }

    @Scheduled(cron = "0 0/5 * * * ?")
    public void execute() {
        if (JMeterRunContext.getContext().isEnable() && MapUtils.isNotEmpty(JMeterRunContext.getContext().getProjectUrls())) {
            MsDriverManager.downloadJar(JMeterRunContext.getContext().getProjectUrls());
            // 清理历史jar
            FileUtils.deletePath(FileUtils.JAR_PLUG_FILE_DIR);
            LoggerUtil.info("开始同步插件JAR：" + JMeterRunContext.getContext().getPlugUrl());
            File plugFile = ZipSpider.downloadFile(JMeterRunContext.getContext().getPlugUrl(), FileUtils.JAR_PLUG_FILE_DIR);
            if (plugFile != null) {
                ZipSpider.unzip(plugFile.getPath(), FileUtils.JAR_PLUG_FILE_DIR);
                FileUtils.deleteFile(plugFile.getPath());
                this.loadPlugJar(FileUtils.JAR_PLUG_FILE_DIR);
            }
        }
    }

    private static InputStream getStrToStream(String sInputString) {
        if (StringUtils.isNotEmpty(sInputString)) {
            try {
                ByteArrayInputStream tInputStringStream = new ByteArrayInputStream(sInputString.getBytes());
                return tInputStringStream;
            } catch (Exception ex) {
                LoggerUtil.error(ex);
                MSException.throwException("生成脚本异常");
            }
        }
        return null;
    }
}
