package io.metersphere.api.jmeter;

import io.metersphere.api.jmeter.queue.ExecThreadPoolExecutor;
import io.metersphere.api.jmeter.utils.FixedCapacityUtil;
import io.metersphere.api.jmeter.utils.JMeterProperties;
import io.metersphere.api.jmeter.utils.MSException;
import io.metersphere.constants.BackendListenerConstants;
import io.metersphere.constants.RunModeConstants;
import io.metersphere.dto.JmeterRunRequestDTO;
import io.metersphere.jmeter.JMeterBase;
import io.metersphere.jmeter.LocalRunner;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.File;
import java.lang.reflect.Field;

@Service
public class JMeterService {

    @Resource
    private JMeterProperties jmeterProperties;
    @Resource
    private ExecThreadPoolExecutor execThreadPoolExecutor;

    @PostConstruct
    public void init() {
        String JMETER_HOME = getJmeterHome();
        String JMETER_PROPERTIES = JMETER_HOME + "/bin/jmeter.properties";
        JMeterUtils.loadJMeterProperties(JMETER_PROPERTIES);
        JMeterUtils.setJMeterHome(JMETER_HOME);
        JMeterUtils.setLocale(LocaleContextHolder.getLocale());
    }

    public String getJmeterHome() {
        String home = getClass().getResource("/").getPath() + "jmeter";
        try {
            File file = new File(home);
            if (file.exists()) {
                return home;
            } else {
                return jmeterProperties.getHome();
            }
        } catch (Exception e) {
            return jmeterProperties.getHome();
        }
    }

    private String getReportId(HashTree tree, JmeterRunRequestDTO runRequest) {
        if (StringUtils.equals(runRequest.getReportType(), RunModeConstants.SET_REPORT.toString())) {
            String reportId = StringUtils.join(runRequest.getReportId(), "_", runRequest.getTestId());
            for (Object key : tree.keySet()) {
                HashTree node = tree.get(key);
                if (key instanceof ThreadGroup) {
                    ((ThreadGroup) key).setName(reportId);
                    break;
                } else {
                    getReportId(node, runRequest);
                }
            }
            return reportId;
        } else {
            return runRequest.getReportId();
        }
    }

    public void runLocal(JmeterRunRequestDTO runRequest, HashTree testPlan) {
        try {
            init();
            String reportId = getReportId(runRequest.getHashTree(), runRequest);
            if (!FixedCapacityUtil.containsKey(reportId)) {
                FixedCapacityUtil.put(reportId, new StringBuffer(""));
            }
            runRequest.setHashTree(testPlan);
            // 调试
            if (runRequest.isDebug()) {
                addDebugListener(runRequest);
            }
            if ((runRequest.getExtendedParameters().containsKey(ExtendedParameter.SAVE_RESULT)
                    && Boolean.valueOf(runRequest.getExtendedParameters().get(ExtendedParameter.SAVE_RESULT).toString()))
                    || !runRequest.isDebug()) {
                JMeterBase.addBackendListener(runRequest, runRequest.getHashTree(),
                        MsApiBackendListener.class.getCanonicalName());
            }
            LocalRunner runner = new LocalRunner(testPlan);
            runner.run(runRequest.getReportId());
        } catch (Exception e) {
            LoggerUtil.error("Local执行异常", runRequest.getReportId(), e);
            MSException.throwException("读取脚本失败");
        }
    }

    /**
     * 添加调试监听
     */
    private void addDebugListener(JmeterRunRequestDTO request) {
        MsDebugListener resultCollector = new MsDebugListener();
        resultCollector.setName(request.getReportId());
        resultCollector.setTestId(request.getTestId());
        resultCollector.setReportId(request.getReportId());
        resultCollector.setKafkaConfig(request.getKafkaConfig());
        resultCollector.setProperty(TestElement.TEST_CLASS, MsDebugListener.class.getName());
        resultCollector.setProperty(TestElement.GUI_CLASS, SaveService.aliasToClass("ViewResultsFullVisualizer"));
        resultCollector.setEnabled(true);
        resultCollector.setClearLog(true);
        if ((request.getExtendedParameters().containsKey(ExtendedParameter.SAVE_RESULT)
                && Boolean.valueOf(request.getExtendedParameters().get(ExtendedParameter.SAVE_RESULT).toString()))) {
            resultCollector.setClearLog(false);
        }
        // 添加DEBUG标示
        HashTree test = ArrayUtils.isNotEmpty(request.getHashTree().getArray()) ? request.getHashTree().getTree(request.getHashTree().getArray()[0]) : null;
        if (test != null && ArrayUtils.isNotEmpty(test.getArray()) && test.getArray()[0] instanceof ThreadGroup) {
            ThreadGroup group = (ThreadGroup) test.getArray()[0];
            group.setProperty(BackendListenerConstants.MS_DEBUG.name(), true);
        }
        request.getHashTree().add(request.getHashTree().getArray()[0], resultCollector);
    }

    public void run(JmeterRunRequestDTO request) {
        if (request.getCorePoolSize() > 0) {
            execThreadPoolExecutor.setCorePoolSize(request.getCorePoolSize());
        }
        execThreadPoolExecutor.addTask(request);
    }

    public void addQueue(JmeterRunRequestDTO request) {
        this.runLocal(request, request.getHashTree());
    }

    public static HashTree getHashTree(Object scriptWrapper) throws Exception {
        Field field = scriptWrapper.getClass().getDeclaredField("testPlan");
        field.setAccessible(true);
        return (HashTree) field.get(scriptWrapper);
    }
}
