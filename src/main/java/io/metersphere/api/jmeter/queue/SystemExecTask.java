package io.metersphere.api.jmeter.queue;

import io.metersphere.api.jmeter.ExtendedParameter;
import io.metersphere.api.jmeter.JMeterService;
import io.metersphere.api.jmeter.utils.CommonBeanFactory;
import io.metersphere.api.jmeter.utils.JMeterThreadUtil;
import io.metersphere.api.service.ProducerService;
import io.metersphere.dto.JmeterRunRequestDTO;
import io.metersphere.dto.ResultDTO;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;

import java.util.HashMap;

public class SystemExecTask implements Runnable {
    private JmeterRunRequestDTO request;

    public SystemExecTask(JmeterRunRequestDTO request) {
        this.request = request;
    }

    public JmeterRunRequestDTO getRequest() {
        return this.request;
    }

    @Override
    public void run() {
        LoggerUtil.info("开始执行队列中任务", request.getReportId());
        CommonBeanFactory.getBean(JMeterService.class).addQueue(request);
        if (StringUtils.isNotEmpty(request.getReportId())) {
            Object res = PoolExecBlockingQueueUtil.take(request.getReportId());
            if (res == null && !JMeterThreadUtil.isRunning(request.getReportId(), request.getTestId())) {
                LoggerUtil.info("任务执行超时", request.getReportId());
                ResultDTO dto = new ResultDTO();
                BeanUtils.copyProperties(dto, request);
                if (dto.getArbitraryData() == null || dto.getArbitraryData().isEmpty()) {
                    dto.setArbitraryData(new HashMap<String, Object>() {{
                        this.put(ExtendedParameter.TEST_END, true);
                        this.put("TIMEOUT", true);
                    }});
                } else {
                    dto.getArbitraryData().put(ExtendedParameter.TEST_END, true);
                    dto.getArbitraryData().put("TIMEOUT", true);
                }
                CommonBeanFactory.getBean(ProducerService.class).send(dto, request.getKafkaConfig());
            }
        }
        LoggerUtil.info("任务执行完成", request.getReportId());
    }
}
