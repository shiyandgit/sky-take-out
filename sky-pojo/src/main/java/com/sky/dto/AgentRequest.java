package com.sky.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("Agent对话请求")
public class AgentRequest {
    @ApiModelProperty("会话ID")
    private String sessionId;

    @ApiModelProperty("用户消息")
    private String message;

    @ApiModelProperty("用户ID")
    private Long userId;
}
