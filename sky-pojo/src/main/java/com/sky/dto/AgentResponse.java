package com.sky.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
@ApiModel("Agent对话响应")
public class AgentResponse {
    @ApiModelProperty("回复内容")
    private String content;

    @ApiModelProperty("快捷按钮")
    private List<QuickAction> quickActions;

    @ApiModelProperty("会话ID")
    private String sessionId;
}
