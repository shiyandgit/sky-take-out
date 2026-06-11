package com.sky.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

@Data
@ApiModel("快捷按钮")
public class QuickAction {
    @ApiModelProperty("按钮文字")
    private String label;

    @ApiModelProperty("动作类型")
    private String action;

    @ApiModelProperty("动作参数")
    private Map<String, Object> params;
}
