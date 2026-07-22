package com.deveopsj.assetplan.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssetPlanSaveRequest {

    private Long goalId;
    private String assetType;
    private Long monthlyAmount;
    private String memo;
}
