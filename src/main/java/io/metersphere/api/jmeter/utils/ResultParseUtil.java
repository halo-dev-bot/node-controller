package io.metersphere.api.jmeter.utils;

import io.metersphere.dto.RequestResult;
import io.metersphere.dto.ResultDTO;
import io.metersphere.jmeter.JMeterBase;
import io.metersphere.utils.ListenerUtil;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.samplers.SampleResult;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ResultParseUtil {

    public static final String PRE_PROCESS_SCRIPT = "PRE_PROCESSOR_ENV_";
    public static final String POST_PROCESS_SCRIPT = "POST_PROCESSOR_ENV_";
    private final static String TRANSACTION = "Transaction=";

    public static boolean isNotAutoGenerateSampler(RequestResult result) {
        return !(StringUtils.equals(result.getMethod(), "Request") && StringUtils.startsWithAny(result.getName(), PRE_PROCESS_SCRIPT, POST_PROCESS_SCRIPT));
    }

    /**
     * 执行结果数据转化
     *
     * @param sampleResults
     * @param dto
     */
    public static void resultFormatting(List<SampleResult> sampleResults, ResultDTO dto) {
        try {
            List<RequestResult> requestResults = new LinkedList<>();
            List<String> environmentList = new ArrayList<>();
            LoggerUtil.info("接收到执行结果" + sampleResults.size(), dto.getReportId());
            sampleResults.forEach(result -> {
                ListenerUtil.setVars(result);
                RequestResult requestResult = JMeterBase.getRequestResult(result, dto.getFakeErrorMap());
                LoggerUtil.info("接收到执行结果：", requestResult);
                if (StringUtils.equals(result.getSampleLabel(), ListenerUtil.RUNNING_DEBUG_SAMPLER_NAME)) {
                    String evnStr = result.getResponseDataAsString();
                    environmentList.add(evnStr);
                } else {
                    //检查是否有关系到最终执行结果的全局前后置脚本。
                    boolean resultNotFilterOut = ListenerUtil.checkResultIsNotFilterOut(requestResult);
                    LoggerUtil.info("检查是否有关系到最终执行结果的全局前后置脚本：" + resultNotFilterOut, dto.getReportId());
                    if (resultNotFilterOut) {
                        if (StringUtils.isNotEmpty(requestResult.getName()) && requestResult.getName().startsWith(TRANSACTION)) {
                            transactionFormat(requestResult.getSubRequestResults(), requestResults);
                        } else {
                            requestResults.add(requestResult);
                        }
                    }
                }
            });
            LoggerUtil.info("接收到处理后执行结果：" + requestResults.size(), dto.getReportId());
            dto.setRequestResults(requestResults);
            ListenerUtil.setEev(dto, environmentList);
        } catch (Exception e) {
            LoggerUtil.error("JMETER-调用存储方法失败", dto.getReportId(), e);
        }
    }

    private static void transactionFormat(List<RequestResult> requestResults, List<RequestResult> refRes) {
        for (RequestResult requestResult : requestResults) {
            if (CollectionUtils.isEmpty(requestResult.getSubRequestResults())) {
                refRes.add(requestResult);
            } else {
                transactionFormat(requestResult.getSubRequestResults(), refRes);
            }
        }
    }
}
