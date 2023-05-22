package io.metersphere.api.jmeter;

import com.fasterxml.jackson.core.type.TypeReference;
import io.metersphere.api.jmeter.queue.BlockingQueueUtil;
import io.metersphere.api.jmeter.queue.PoolExecBlockingQueueUtil;
import io.metersphere.api.jmeter.utils.CommonBeanFactory;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.jmeter.utils.FixedCapacityUtil;
import io.metersphere.api.service.JvmService;
import io.metersphere.api.service.ProducerService;
import io.metersphere.api.service.utils.JmxAttachmentFileUtil;
import io.metersphere.constants.BackendListenerConstants;
import io.metersphere.constants.RunModeConstants;
import io.metersphere.dto.MsRegexDTO;
import io.metersphere.dto.ResultDTO;
import io.metersphere.jmeter.JMeterBase;
import io.metersphere.utils.JsonUtils;
import io.metersphere.utils.LoggerUtil;
import io.metersphere.utils.ReportStatusUtil;
import io.metersphere.utils.RetryResultUtil;
import io.metersphere.vo.ResultVO;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.services.FileServer;
import org.apache.jmeter.visualizers.backend.AbstractBackendListenerClient;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;

import java.io.Serializable;
import java.util.*;

public class MsApiBackendListener extends AbstractBackendListenerClient implements Serializable {
    private List<SampleResult> queues;
    private JmxAttachmentFileUtil jmxAttachmentFileUtil;
    private ProducerService producerService;
    // KAFKA 配置信息
    private Map<String, Object> producerProps;
    private ResultDTO dto;

    // 当前场景报告/用例结果状态
    private ResultVO resultVO;

    private static final List<String> apiRunModes = new ArrayList<>() {{
        this.add("DEFINITION");
        this.add("API_PLAN");
        this.add("SCHEDULE_API_PLAN");
        this.add("JENKINS_API_PLAN");

    }};

    @Override
    public void setupTest(BackendListenerContext context) throws Exception {
        queues = new LinkedList<>();
        jmxAttachmentFileUtil = CommonBeanFactory.getBean(JmxAttachmentFileUtil.class);
        producerService = CommonBeanFactory.getBean(ProducerService.class);
        resultVO = new ResultVO();
        this.setParam(context);
        super.setupTest(context);
    }

    @Override
    public void handleSampleResults(List<SampleResult> sampleResults, BackendListenerContext context) {
        LoggerUtil.info("接收到JMETER执行数据【" + sampleResults.size() + " 】", dto.getReportId());
        if (CollectionUtils.isNotEmpty(sampleResults)) {
            RetryResultUtil.clearLoops(sampleResults);
        }
        if (dto.isRetryEnable()) {
            queues.addAll(sampleResults);
        } else {
            JMeterBase.resultFormatting(sampleResults, dto);
            LoggerUtil.info("结果数据处理完成：" + dto.getRequestResults().size(), dto.getReportId());
            if (apiRunModes.contains(dto.getRunMode())) {
                String reportId = dto.getReportId();
                if (StringUtils.equals(dto.getReportType(), RunModeConstants.SET_REPORT.toString())) {
                    reportId = StringUtils.join(dto.getReportId(), "_", dto.getTestId());
                }
                dto.setConsole(FixedCapacityUtil.getJmeterLogger(reportId, true));
            }
            resultVO = ReportStatusUtil.getStatus(dto, resultVO);
            dto.getArbitraryData().put(ReportStatusUtil.LOCAL_STATUS_KEY, resultVO);
            LoggerUtil.info("开始发送单条请求：" + dto.getRequestResults().size(), dto.getReportId());
            producerService.send(dto, producerProps);
            sampleResults.clear();
        }
    }

    @Override
    public void teardownTest(BackendListenerContext context) {
        String reportId = dto.getReportId();
        try {
            super.teardownTest(context);
            PoolExecBlockingQueueUtil.offer(dto.getReportId());
            if (StringUtils.isNotEmpty(dto.getReportId())) {
                BlockingQueueUtil.remove(dto.getReportId());
            }
            // 整理执行结果
            LoggerUtil.info("开始处理数据：" + queues.size(), dto.getReportId());
            JMeterBase.resultFormatting(queues, dto);
            if (dto.isRetryEnable()) {
                LoggerUtil.info("重试结果处理开始", dto.getReportId());
                RetryResultUtil.mergeRetryResults(dto.getRequestResults());
                resultVO = ReportStatusUtil.getStatus(dto, resultVO);
                LoggerUtil.info("重试结果处理结束", dto.getReportId());
            }
            if (StringUtils.equals(dto.getReportType(), RunModeConstants.SET_REPORT.toString())) {
                reportId = StringUtils.join(dto.getReportId(), "_", dto.getTestId());
            }
            dto.setConsole(FixedCapacityUtil.getJmeterLogger(reportId, true));
            dto.setHasEnded(true);
            FileUtils.deleteFile(FileUtils.BODY_FILE_DIR + "/" + dto.getReportId() + "_" + dto.getTestId() + ".jmx");
            LoggerUtil.info("node整体执行完成", dto.getReportId());
            // 存储结果
            dto.getArbitraryData().put(ReportStatusUtil.LOCAL_STATUS_KEY, resultVO);
            producerService.send(dto, producerProps);
            LoggerUtil.info(JvmService.jvmInfo().toString(), dto.getReportId());
        } catch (Exception e) {
            LoggerUtil.error("结果处理异常", dto.getReportId(), e);
        } finally {
            //删除执行过程中的文件和附件
            jmxAttachmentFileUtil.deleteTmpFiles(reportId);

            if (FileServer.getFileServer() != null) {
                LoggerUtil.info("进入监听，开始关闭CSV", dto.getReportId());
                FileServer.getFileServer().closeCsv(dto.getReportId());
            }
            PoolExecBlockingQueueUtil.offer(dto.getReportId());
            queues.clear();
            ApiLocalRunner.clearCache(dto.getReportId());
        }
    }

    /**
     * 初始化参数
     *
     * @param context
     */
    private void setParam(BackendListenerContext context) {
        dto = new ResultDTO();
        dto.setTestId(context.getParameter(BackendListenerConstants.TEST_ID.name()));
        dto.setRunMode(context.getParameter(BackendListenerConstants.RUN_MODE.name()));
        dto.setReportId(context.getParameter(BackendListenerConstants.REPORT_ID.name()));
        dto.setReportType(context.getParameter(BackendListenerConstants.REPORT_TYPE.name()));
        dto.setTestPlanReportId(context.getParameter(BackendListenerConstants.MS_TEST_PLAN_REPORT_ID.name()));
        if (context.getParameter(BackendListenerConstants.RETRY_ENABLE.name()) != null) {
            dto.setRetryEnable(Boolean.parseBoolean(context.getParameter(BackendListenerConstants.RETRY_ENABLE.name())));
        }
        this.producerProps = new HashMap<>();

        if (StringUtils.isNotEmpty(context.getParameter(BackendListenerConstants.KAFKA_CONFIG.name()))) {
            this.producerProps = JsonUtils.parseObject(context.getParameter(BackendListenerConstants.KAFKA_CONFIG.name()), Map.class);
        }
        dto.setQueueId(context.getParameter(BackendListenerConstants.QUEUE_ID.name()));
        dto.setRunType(context.getParameter(BackendListenerConstants.RUN_TYPE.name()));
        if (StringUtils.isNotBlank(context.getParameter(BackendListenerConstants.FAKE_ERROR.name()))) {
            Map<String, List<MsRegexDTO>> fakeErrorMap = JsonUtils.parseObject(
                    context.getParameter(BackendListenerConstants.FAKE_ERROR.name()),
                    new TypeReference<Map<String, List<MsRegexDTO>>>() {
                    });
            dto.setFakeErrorMap(fakeErrorMap);
        }
        String ept = context.getParameter(BackendListenerConstants.EPT.name());
        if (StringUtils.isNotEmpty(ept)) {
            dto.setExtendedParameters(JsonUtils.parseObject(ept, Map.class));
        }
    }
}
