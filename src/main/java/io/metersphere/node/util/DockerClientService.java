package io.metersphere.node.util;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import io.metersphere.node.controller.request.DockerLoginRequest;
import io.metersphere.node.controller.request.TestRequest;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DockerClientService {

    private static final String jmeterCores;
    private static final String reportCores;

    static {
        String jmeterCpuSet = System.getenv("JMETER_CPU_SET");
        String reportCpuSet = System.getenv("REPORT_CPU_SET");
        boolean reportRealtime = BooleanUtils.toBoolean(System.getenv().getOrDefault("REPORT_REALTIME", "true"));
        boolean reportFinal = BooleanUtils.toBoolean(System.getenv().getOrDefault("REPORT_FINAL", "true"));
        if (StringUtils.isNotBlank(jmeterCpuSet) && StringUtils.isNotBlank(reportCpuSet)) {
            jmeterCores = jmeterCpuSet;
            reportCores = reportCpuSet;
        } else {
            int cores = Runtime.getRuntime().availableProcessors();
            if (cores >= 4) {
                int lastIndex = cores - 1;
                if (reportRealtime && reportFinal) {
                    jmeterCores = "2-" + lastIndex;
                    reportCores = "0-1";
                } else if (reportRealtime) {
                    jmeterCores = "1-" + lastIndex;
                    reportCores = "0";
                } else if (reportFinal) {
                    jmeterCores = "1-" + lastIndex;
                    reportCores = "0";
                } else {
                    jmeterCores = "2-" + lastIndex;
                    reportCores = "0-1";
                }
            } else {
                jmeterCores = "";
                reportCores = "";
            }
        }
    }

    /**
     * 连接docker服务器
     *
     * @return
     */
    public static DockerClient connectDocker() {
        return DockerClientBuilder.getInstance().build();
    }

    public static DockerClient connectDocker(DockerLoginRequest request) {
        if (StringUtils.isBlank(request.getRegistry()) || StringUtils.isBlank(request.getUsername()) || StringUtils.isBlank(request.getPassword())) {
            return connectDocker();
        }
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withRegistryUrl(request.getRegistry())
                .withRegistryUsername(request.getUsername())
                .withRegistryPassword(request.getPassword())
                .build();
        return DockerClientBuilder.getInstance(config).build();
    }

    /**
     * 创建容器
     *
     * @param client
     * @return
     */
    public static CreateContainerResponse createContainers(DockerClient client, TestRequest request, String testId, String imageName) {

        // 创建 hostConfig
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNetworkMode("host")
                .withCpusetCpus(jmeterCores);

        CreateContainerResponse container = client.createContainerCmd(imageName)
                .withName(testId)
                .withHostConfig(hostConfig)
                .withEnv(getEnvs(request))
                .withVolumes(new Volume("/test"), new Volume("/jmeter-log"))
                .exec();
        return container;
    }

    public static CreateContainerResponse createReportContainers(DockerClient client, TestRequest request, String testId, String imageName) {
        // 创建 hostConfig
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNetworkMode("host")
                .withCpusetCpus(reportCores)
                .withVolumesFrom(new VolumesFrom(testId));
        CreateContainerResponse container = client.createContainerCmd(imageName)
                .withName(testId + "_report")
                .withHostConfig(hostConfig)
                .withEntrypoint("/bin/sh", "-c", "/generate-report.sh")
                .withEnv(getEnvs(request))
                .exec();
        return container;
    }


    /**
     * 启动容器
     *
     * @param client
     * @param containerId
     */
    public static void startContainer(DockerClient client, String containerId) {
        client.startContainerCmd(containerId).exec();
    }

    /**
     * 停止容器
     *
     * @param client
     * @param containerId
     */
    public static void stopContainer(DockerClient client, String containerId) {
        client.stopContainerCmd(containerId).exec();
    }

    /**
     * 删除容器
     *
     * @param client
     * @param containerId
     */
    public static void removeContainer(DockerClient client, String containerId) {
        client.removeContainerCmd(containerId)
                .withForce(true)
                .withRemoveVolumes(true)
                .exec();
    }

    /**
     * 容器是否存在
     *
     * @param client
     * @param containerId
     */
    public static int existContainer(DockerClient client, String containerId) {
        List<Container> list = client.listContainersCmd()
                .withShowAll(true)
                .withIdFilter(Collections.singleton(containerId))
                .exec();
        return list.size();
    }

    public static void createVolume(DockerClient dockerClient, String reportId, String name) {
        dockerClient.createVolumeCmd()
                .withName(reportId + "_" + name)
                .withDriverOpts(new HashMap<>() {{
                    put("type", "none");
                    put("device", "/Users/liuruibin/git_workspace/metersphere/node-controller/" + reportId + "/" + name);
                    put("o", "bind");
                }})
                .exec();
    }

    private static String[] getEnvs(TestRequest testRequest) {
        Map<String, String> env = testRequest.getEnv();
        env.put("REPORT_REALTIME", System.getenv().getOrDefault("REPORT_REALTIME", "true"));
        env.put("REPORT_FINAL", System.getenv().getOrDefault("REPORT_FINAL", "true"));
        return env.keySet().stream().map(k -> k + "=" + env.get(k)).toArray(String[]::new);
    }
}
