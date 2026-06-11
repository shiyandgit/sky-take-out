package com.sky.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

@Data
@ApiModel("快捷操作请求")
public class QuickActionRequest {
    @ApiModelProperty("用户ID")
    private Long userId;

    @ApiModelProperty("会话ID")
    private String sessionId;

    @ApiModelProperty("动作类型")
    private String action;

    @ApiModelProperty("动作参数")
    private Map<String, Object> params;
}
