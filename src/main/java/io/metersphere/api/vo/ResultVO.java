package io.metersphere.api.vo;

import lombok.Data;

import java.text.DecimalFormat;

@Data
public class ResultVO {
    private String status;
    private long scenarioSuccess;
    private int scenarioTotal;

}



