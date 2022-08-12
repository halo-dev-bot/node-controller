package io.metersphere.api.service;

import com.alibaba.fastjson.JSON;
import io.metersphere.api.jmeter.JMeterService;
import io.metersphere.api.jmeter.MsDriverManager;
import io.metersphere.api.jmeter.queue.BlockingQueueUtil;
import io.metersphere.api.jmeter.queue.PoolExecBlockingQueueUtil;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.jmeter.utils.MSException;
import io.metersphere.api.service.utils.ZipSpider;
import io.metersphere.dto.JmeterRunRequestDTO;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jorphan.collections.HashTree;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

@Service
public class JmeterExecuteService {
    @Resource
    private JMeterService jMeterService;
    @Resource
    private ProducerService producerService;

    public String runStart(JmeterRunRequestDTO runRequest) {
        try {
            LoggerUtil.info("进入node执行方法开始处理任务", runRequest.getReportId());
            if (runRequest.getKafkaConfig() == null) {
                LoggerUtil.error("KAFKA配置为空无法执行", runRequest.getReportId());
                return "KAFKA 初始化失败，请检查配置";
            }
            // 生成附件/JAR文件
            URL urlObject = new URL(runRequest.getPlatformUrl());
            String jarUrl = urlObject.getProtocol() + "://" + urlObject.getHost() + (urlObject.getPort() > 0 ? ":" + urlObject.getPort() : "") + "/api/jmeter/download/jar";
            String plugJarUrl = urlObject.getProtocol() + "://" + urlObject.getHost() + (urlObject.getPort() > 0 ? ":" + urlObject.getPort() : "") + "/api/jmeter/download/plug/jar";
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
            File bodyFile = ZipSpider.downloadFile(runRequest.getPlatformUrl(), FileUtils.BODY_FILE_DIR);
            if (bodyFile != null) {
                ZipSpider.unzip(bodyFile.getPath(), FileUtils.BODY_FILE_DIR);
                File jmxFile = new File(FileUtils.BODY_FILE_DIR + "/" + runRequest.getReportId() + "_" + runRequest.getTestId() + ".jmx");
                LoggerUtil.info("下载执行脚本完成：" + jmxFile.getName(), runRequest.getReportId());
                // 生成执行脚本
                HashTree testPlan = SaveService.loadTree(jmxFile);
                TestPlan test = (TestPlan) testPlan.getArray()[0];
                test.setProperty("JAR_PATH", JSON.toJSONString(MsDriverManager.loadJar(runRequest)));
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
            return e.getMessage();
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
}
