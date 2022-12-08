package io.metersphere.api.controller;

import io.metersphere.api.jmeter.JMeterLoggerAppender;
import io.metersphere.api.jmeter.queue.BlockingQueueUtil;
import io.metersphere.api.jmeter.utils.JMeterThreadUtil;
import io.metersphere.api.service.JMeterExecuteService;
import io.metersphere.api.service.JvmService;
import io.metersphere.api.vo.JvmInfo;
import io.metersphere.constants.RunModeConstants;
import io.metersphere.dto.JmeterRunRequestDTO;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/jmeter")
public class JmeterExecuteController {

    @Resource
    private JMeterExecuteService jmeterExecuteService;

    @PostMapping(value = "/api/start")
    public String apiStartRun(@RequestBody JmeterRunRequestDTO runRequest) {
        if (StringUtils.equals(runRequest.getReportType(), RunModeConstants.SET_REPORT.toString())) {
            return jmeterExecuteService.runStart(runRequest);
        } else if (BlockingQueueUtil.add(runRequest.getReportId())) {
            return jmeterExecuteService.runStart(runRequest);
        }
        return "当前报告 " + runRequest.getReportId() + " 正在执行中";
    }

    @PostMapping(value = "/debug")
    public String apiDebug(@RequestBody JmeterRunRequestDTO runRequest) {
        LoggerUtil.info("接收到测试请求 start ");
        return jmeterExecuteService.debug(runRequest);
    }


    @GetMapping("/get/running/queue/{reportId}")
    public boolean getRunningQueue(@PathVariable String reportId) {
        return JMeterThreadUtil.isRunning(reportId, null);
    }

    @GetMapping("/status")
    public String getStatus() {
        return "OK";
    }

    @GetMapping("/get-jvm-info")
    public JvmInfo getJvmInfo() {
        return JvmService.jvmInfo();
    }

    @GetMapping("/stop")
    public void stop(@RequestBody List<String> keys) {
        JMeterThreadUtil.stop(keys);
    }

    @GetMapping("/log/debug/{enable}")
    public boolean debug(@PathVariable boolean enable) {
        return JMeterLoggerAppender.enable = enable;
    }
}
