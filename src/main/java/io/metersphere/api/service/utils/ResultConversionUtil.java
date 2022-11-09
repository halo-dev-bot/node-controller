package io.metersphere.api.service.utils;

import io.metersphere.api.vo.*;
import io.metersphere.dto.RequestResult;
import io.metersphere.dto.ResultDTO;
import io.metersphere.utils.LoggerUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedList;
import java.util.List;

public class ResultConversionUtil {

    public static ResultVO getStatus(ResultDTO dto) {
        List<ApiScenarioReportVo> requestResults = getApiScenarioReportResults(dto.getReportId(), dto.getRequestResults());
        ResultVO resultVO = new ResultVO();
        resultVO.setScenarioSuccess(requestResults.stream().filter(requestResult -> StringUtils.equalsIgnoreCase(requestResult.getStatus(),ScenarioStatusEnum.Success.name())).count());
        resultVO.setScenarioTotal(requestResults.size());
        long errorSize = requestResults.stream().filter(requestResult ->
                StringUtils.equalsIgnoreCase(requestResult.getStatus(), ScenarioStatusEnum.Error.name())).count();

        long errorReportResultSize = requestResults.stream().filter(requestResult ->
                StringUtils.equalsIgnoreCase(requestResult.getStatus(), ExecuteResultEnum.ERROR_REPORT_RESULT.toString())).count();

        String status = requestResults.isEmpty() ? ExecuteResultEnum.UN_EXECUTE.toString() : ScenarioStatusEnum.Success.name();
        if (errorSize > 0) {
            status = ScenarioStatusEnum.Error.name();
        } else if (errorReportResultSize > 0) {
            status = ExecuteResultEnum.ERROR_REPORT_RESULT.toString();
        }
        // 超时状态
        if (dto != null && dto.getArbitraryData() != null && dto.getArbitraryData().containsKey("TIMEOUT") && (Boolean) dto.getArbitraryData().get("TIMEOUT")) {
            LoggerUtil.info("资源 " + dto.getTestId() + " 执行超时", dto.getReportId());
            status = ScenarioStatusEnum.Timeout.name();
        }
        resultVO.setStatus(status);
        return resultVO;
    }

    public static List<ApiScenarioReportVo> getApiScenarioReportResults(String reportId, List<RequestResult> requestResults) {
        //解析误报内容
        List<ApiScenarioReportVo> list = new LinkedList<>();
        if (CollectionUtils.isEmpty(requestResults)) {
            return list;
        }
        requestResults.forEach(item -> {
            list.add(getApiScenarioReportResult(reportId, item));
        });
        return list;
    }

    public static ApiScenarioReportVo getApiScenarioReportResult(String reportId, RequestResult requestResult) {
        //解析误报内容
        ErrorReportLibraryParseVo errorCodeDTO = ErrorReportLibraryUtil.parseAssertions(requestResult);
        RequestResult result = errorCodeDTO.getResult();
        String resourceId = result.getResourceId();

        ApiScenarioReportVo report = new ApiScenarioReportVo(reportId, resourceId);
        String status = result.getError() == 0 ? ExecuteResultEnum.SCENARIO_SUCCESS.toString() : ExecuteResultEnum.SCENARIO_ERROR.toString();
        if (CollectionUtils.isNotEmpty(errorCodeDTO.getErrorCodeList())) {
            report.setErrorCode(errorCodeDTO.getErrorCodeStr());
        }
        if (StringUtils.equalsIgnoreCase(errorCodeDTO.getRequestStatus(), ExecuteResultEnum.ERROR_REPORT_RESULT.toString())) {
            status = errorCodeDTO.getRequestStatus();
        }
        report.setStatus(status);
        LoggerUtil.info("报告ID [ " + reportId + " ] 执行请求：【 " + requestResult.getName() + "】 入库存储");
        return report;
    }
}
