package io.metersphere.api.jmeter.utils;

import io.metersphere.api.jmeter.dto.RequestResultExpandDTO;
import io.metersphere.api.service.utils.ErrorReportLibraryUtil;
import io.metersphere.api.vo.ErrorReportLibraryParseVo;
import io.metersphere.api.vo.ExecuteResultEnum;
import io.metersphere.dto.RequestResult;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 请求返回解析工具
 */
public class ResponseUtil {

    public static RequestResultExpandDTO parseByRequestResult(RequestResult baseResult) {
        //解析是否含有误报库信息
        ErrorReportLibraryParseVo errorCodeDTO = ErrorReportLibraryUtil.parseAssertions(baseResult);
        RequestResult requestResult = errorCodeDTO.getResult();
        RequestResultExpandDTO expandDTO = new RequestResultExpandDTO();
        BeanUtils.copyProperties(requestResult, expandDTO);

        if (CollectionUtils.isNotEmpty(errorCodeDTO.getErrorCodeList())) {
            Map<String, String> expandMap = new HashMap<>();
            expandMap.put(ExecuteResultEnum.ERROR_REPORT_RESULT.toString(), errorCodeDTO.getErrorCodeStr());
            if (StringUtils.equalsIgnoreCase(errorCodeDTO.getRequestStatus(), ExecuteResultEnum.ERROR_REPORT_RESULT.toString())) {
                expandMap.put("status", ExecuteResultEnum.ERROR_REPORT_RESULT.toString());
            }
            expandDTO.setAttachInfoMap(expandMap);
        }
        if (StringUtils.equalsIgnoreCase(errorCodeDTO.getRequestStatus(), ExecuteResultEnum.ERROR_REPORT_RESULT.toString())) {
            expandDTO.setStatus(errorCodeDTO.getRequestStatus());
        }
        return expandDTO;
    }
}
