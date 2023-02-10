package io.metersphere.api.service;

import io.metersphere.api.jmeter.JMeterService;
import io.metersphere.api.jmeter.MsDriverManager;
import io.metersphere.api.jmeter.queue.BlockingQueueUtil;
import io.metersphere.api.jmeter.queue.PoolExecBlockingQueueUtil;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.jmeter.utils.MSException;
import io.metersphere.api.jmeter.utils.URLParserUtil;
import io.metersphere.api.service.utils.JmxAttachmentFileUtil;
import io.metersphere.api.service.utils.ZipSpider;
import io.metersphere.api.vo.ScriptData;
import io.metersphere.dto.JmeterRunRequestDTO;
import io.metersphere.utils.LoggerUtil;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.save.SaveService;
import org.apache.jorphan.collections.HashTree;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Service
public class JMeterExecuteService {
    @Resource
    private JMeterService jMeterService;
    @Resource
    private ProducerService producerService;
    @Resource
    private RestTemplate restTemplate;
    @Resource
    private JmxAttachmentFileUtil jmxAttachmentFileUtil;
    @Resource
    private DownloadPluginJarService downloadPluginJarService;


    public String runStart(JmeterRunRequestDTO runRequest) {
        try {
            LoggerUtil.info("进入node执行方法开始处理任务", runRequest.getReportId());
            if (runRequest.getKafkaConfig() == null) {
                LoggerUtil.error("KAFKA配置为空无法执行", runRequest.getReportId());
                return "KAFKA 初始化失败，请检查配置";
            }
            // 下载系统插件
            downloadPluginJarService.downloadPlugin(runRequest);
            // 下载项目插件
            MsDriverManager.downloadProjectJar(runRequest);
            JMeterRunContext.getContext().setEnable(runRequest.isEnable());
            LoggerUtil.info("开始拉取脚本和脚本附件：" + runRequest.getPlatformUrl(), runRequest.getReportId());
            if (runRequest.getHashTree() != null) {
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

                //检查是否需要附件进行下载，并替换JMX里的文件路径
                jmxAttachmentFileUtil.parseJmxAttachmentFile(testPlan, runRequest.getReportId(), runRequest.getPlatformUrl());

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

            //检查是否需要附件进行下载，并替换JMX里的文件路径
            jmxAttachmentFileUtil.parseJmxAttachmentFile(runRequest.getHashTree(), runRequest.getReportId(), runRequest.getPlatformUrl());
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
