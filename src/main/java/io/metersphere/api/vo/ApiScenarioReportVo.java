package io.metersphere.api.vo;

import lombok.Data;

@Data
public class ApiScenarioReportVo {
    private String reportId;
    private String resourceId;
    private String status;
    private String errorCode;

    public ApiScenarioReportVo(String reportId, String resourceId) {
        this.reportId = reportId;
        this.resourceId = resourceId;
    }
}
