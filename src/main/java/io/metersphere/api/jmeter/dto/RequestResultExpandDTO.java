package io.metersphere.api.jmeter.dto;
import io.metersphere.dto.RequestResult;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class RequestResultExpandDTO extends RequestResult {
    private String status;
    private String uiImg;
    private String reportId;
    private long time;
    private Map<String, String> attachInfoMap;
}
