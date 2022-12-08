package io.metersphere.api.jmeter.utils;

import io.metersphere.api.jmeter.queue.ExecThreadPoolExecutor;
import io.metersphere.api.jmeter.queue.PoolExecBlockingQueueUtil;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class JMeterThreadUtil {
    public static String stop(List<String> names) {
        ThreadGroup currentGroup = Thread.currentThread().getThreadGroup();
        int noThreads = currentGroup.activeCount();
        Thread[] lstThreads = new Thread[noThreads];
        currentGroup.enumerate(lstThreads);
        StringBuilder threadNames = new StringBuilder();
        for (String name : names) {
            for (int i = 0; i < noThreads; i++) {
                if (lstThreads[i] != null && StringUtils.isNotEmpty(lstThreads[i].getName()) && lstThreads[i].getName().startsWith(name)) {
                    LoggerUtil.error("异常强制处理线程编号：" + i + " = " + lstThreads[i].getName());
                    threadNames.append(lstThreads[i].getName()).append("；");
                    lstThreads[i].interrupt();
                }
            }
        }
        return threadNames.toString();
    }

    public static boolean isRunning(String reportId, String testId) {
        if (StringUtils.isEmpty(reportId)) {
            return false;
        }
        if (PoolExecBlockingQueueUtil.queue.containsKey(reportId)) {
            return true;
        }
        if (CommonBeanFactory.getBean(ExecThreadPoolExecutor.class).check(reportId)) {
            return true;
        }
        ThreadGroup currentGroup = Thread.currentThread().getThreadGroup();
        int noThreads = currentGroup.activeCount();
        Thread[] lstThreads = new Thread[noThreads];
        currentGroup.enumerate(lstThreads);
        for (int i = 0; i < noThreads; i++) {
            if (StringUtils.isNotEmpty(reportId) && StringUtils.isNotEmpty(lstThreads[i].getName()) && lstThreads[i].getName().startsWith(reportId)) {
                return true;
            } else if (StringUtils.isNotEmpty(testId) && StringUtils.isNotEmpty(lstThreads[i].getName()) && lstThreads[i].getName().startsWith(testId)) {
                return true;
            }
        }
        return false;
    }

    public static Integer activeCount() {
        ThreadGroup currentGroup = Thread.currentThread().getThreadGroup();
        return currentGroup.activeCount();
    }
}
