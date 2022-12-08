package io.metersphere.api.jmeter;

import io.metersphere.api.jmeter.queue.BlockingQueueUtil;
import io.metersphere.api.jmeter.queue.PoolExecBlockingQueueUtil;
import io.metersphere.api.jmeter.utils.CommonBeanFactory;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.jmeter.utils.FixedCapacityUtil;
import io.metersphere.api.service.JvmService;
import io.metersphere.api.service.ProducerService;
import io.metersphere.constants.BackendListenerConstants;
import io.metersphere.constants.RunModeConstants;
import io.metersphere.dto.ResultDTO;
import io.metersphere.jmeter.JMeterBase;
import io.metersphere.utils.JsonUtils;
import io.metersphere.utils.LoggerUtil;
import io.metersphere.utils.RetryResultUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.services.FileServer;
import org.apache.jmeter.visualizers.backend.AbstractBackendListenerClient;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MsApiBackendListener extends AbstractBackendListenerClient implements Serializable {
    private List<SampleResult> queues;
    // KAFKA 配置信息
    private Map<String, Object> producerProps;
    private ResultDTO dto;

    @Override
    public void setupTest(BackendListenerContext context) throws Exception {
        queues = new LinkedList<>();
        this.setParam(context);
        super.setupTest(context);
    }

    @Override
    public void handleSampleResults(List<SampleResult> sampleResults, BackendListenerContext context) {
        LoggerUtil.info("开始处理单条执行结果报告", dto.getReportId());
        if (CollectionUtils.isNotEmpty(sampleResults)) {
            RetryResultUtil.clearLoops(sampleResults);
            queues.addAll(sampleResults);
        }
    }

    @Override
    public void teardownTest(BackendListenerContext context) {
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
                LoggerUtil.info("重试结果处理结束", dto.getReportId());
            }
            String reportId = dto.getReportId();
            if (StringUtils.equals(dto.getReportType(), RunModeConstants.SET_REPORT.toString())) {
                reportId = StringUtils.join(dto.getReportId(), "_", dto.getTestId());
            }
            dto.setConsole(FixedCapacityUtil.getJmeterLogger(reportId,true));
            if (dto.getArbitraryData() == null || dto.getArbitraryData().isEmpty()) {
                dto.setArbitraryData(new HashMap<String, Object>() {{
                    this.put(ExtendedParameter.TEST_END, true);
                }});
            } else {
                dto.getArbitraryData().put(ExtendedParameter.TEST_END, true);
            }
            FileUtils.deleteFile(FileUtils.BODY_FILE_DIR + "/" + dto.getReportId() + "_" + dto.getTestId() + ".jmx");
            LoggerUtil.info("node整体执行完成", dto.getReportId());
            // 存储结果
            CommonBeanFactory.getBean(ProducerService.class).send(dto, producerProps);
            LoggerUtil.info(JvmService.jvmInfo().toString(), dto.getReportId());
        } catch (Exception e) {
            LoggerUtil.error("结果处理异常", dto.getReportId(), e);
        } finally {
            if (FileServer.getFileServer() != null) {
                LoggerUtil.info("进入监听，开始关闭CSV", dto.getReportId());
                FileServer.getFileServer().closeCsv(dto.getReportId());
            }
            PoolExecBlockingQueueUtil.offer(dto.getReportId());
            queues.clear();
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

        String ept = context.getParameter(BackendListenerConstants.EPT.name());
        if (StringUtils.isNotEmpty(ept)) {
            dto.setExtendedParameters(JsonUtils.parseObject(ept, Map.class));
        }
    }
}
