package io.metersphere.config;

import io.metersphere.api.jmeter.JMeterService;
import io.metersphere.api.jmeter.utils.FileUtils;
import io.metersphere.api.service.DownloadPluginJarService;
import io.metersphere.jmeter.ProjectClassLoader;
import io.metersphere.utils.LocalPathUtil;
import io.metersphere.utils.LoggerUtil;
import jakarta.annotation.Resource;
import org.python.core.Options;
import org.python.util.PythonInterpreter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class AppStartListener implements ApplicationListener<ApplicationReadyEvent> {

    @Resource
    private JMeterService jMeterService;
    @Resource
    private DownloadPluginJarService downloadPluginJarService;

    @Value("${jmeter.home}")
    private String jmeterHome;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        System.out.println("================= NODE 应用启动 START =================");
        LoggerUtil.info("jmeter.home", jmeterHome);
        initPythonEnv();

        ProjectClassLoader.initClassLoader();

        LoggerUtil.info("init plugin", FileUtils.JAR_PLUG_FILE_DIR);
        downloadPluginJarService.loadPlugJar(FileUtils.JAR_PLUG_FILE_DIR);
        downloadPluginJarService.loadPlugJar(LocalPathUtil.PLUGIN_PATH);
    }

    /**
     * 解决接口测试-无法导入内置python包
     */
    private void initPythonEnv() {
        //解决无法加载 PyScriptEngineFactory
        Options.importSite = false;
        try {
            PythonInterpreter interp = new PythonInterpreter();
            String path = jMeterService.getJmeterHome();
            System.out.println("sys.path: " + path);
            path += "/lib/ext/jython-standalone.jar/Lib";
            interp.exec("import sys");
            interp.exec("sys.path.append(\"" + path + "\")");
        } catch (Exception e) {
            e.printStackTrace();
            LoggerUtil.error(e.getMessage(), e);
        }
    }
}
