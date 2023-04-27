package io.metersphere.api.jmeter;

import io.metersphere.jmeter.LocalRunner;
import io.metersphere.utils.LoggerUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.engine.JMeterEngineException;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jorphan.collections.HashTree;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@NoArgsConstructor
public class ApiLocalRunner extends LocalRunner {
    private final static Map<String, StandardJMeterEngine> runningTasks = new ConcurrentHashMap<>();
    private boolean isRunning = true;
    private HashTree jmxTree;

    public ApiLocalRunner(HashTree jmxTree) {
        this.jmxTree = jmxTree;
    }


    public void run(String report, String runMode, String triggerMode) {
        StandardJMeterEngine engine = new StandardJMeterEngine();
        engine.configure(this.jmxTree);
        List<String> runModeList = List.of("SCENARIO_PLAN",
                "SCHEDULE_SCENARIO_PLAN",
                "SCHEDULE_SCENARIO",
                "JENKINS_SCENARIO_PLAN",
                "API_PLAN", "SCHEDULE_API_PLAN",
                "JENKINS_API_PLAN");
        List<String> triggerModeList = List.of("BATCH", "MANUAL");
        if (triggerModeList.contains(triggerMode) &&
                !runModeList.contains(runMode)) {
            runningTasks.put(report, engine);
        }
        try {
            LoggerUtil.info("LocalRunner 开始执行报告", report);
            engine.runTest();
        } catch (JMeterEngineException var4) {
            engine.stopTest(true);
        }
    }

    public static void stop(String report) {
        if (MapUtils.isNotEmpty(runningTasks)) {
            StandardJMeterEngine engine = runningTasks.get(report);
            if (engine != null) {
                engine.stopTest(true);
                runningTasks.remove(report);
            }
        }
    }

    public static void clearCache(String report) {
        if (StringUtils.isNotEmpty(report)) {
            runningTasks.remove(report);
        }
    }

    @PostConstruct
    public void checkRunningTasks() {
        new Thread(() -> {
            while (isRunning) {
                try {
                    Thread.sleep(1000 * 60);
                    if (MapUtils.isNotEmpty(runningTasks)) {
                        runningTasks.keySet().stream()
                                .filter(reportId -> !runningTasks.get(reportId).isActive())
                                .forEach(runningTasks::remove);
                    }
                } catch (Exception e) {
                    LoggerUtil.error("检查运行中的任务异常：", e);
                }
            }
        }).start();
    }

    @PreDestroy
    public void destroy() {
        isRunning = false;
    }
}
