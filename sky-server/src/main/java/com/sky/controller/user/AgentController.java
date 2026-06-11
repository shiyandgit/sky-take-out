package com.sky.controller.user;

import com.sky.agent.AgentService;
import com.sky.agent.model.ChatMessage;
import com.sky.dto.AgentRequest;
import com.sky.dto.AgentResponse;
import com.sky.dto.QuickActionRequest;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("userAgentController")
@RequestMapping("/user/agent")
@Api(tags = "C端-智能点餐Agent接口")
@Slf4j
public class AgentController {

    @Autowired
    private AgentService agentService;

    @PostMapping("/chat")
    @ApiOperation("智能对话")
    public Result<AgentResponse> chat(@RequestBody AgentRequest request) {
        log.info("Agent对话请求，用户ID: {}, 会话ID: {}, 消息: {}",
                 request.getUserId(), request.getSessionId(), request.getMessage());

        AgentResponse response = agentService.chat(
            request.getUserId(),
            request.getSessionId(),
            request.getMessage()
        );
        return Result.success(response);
    }

    @GetMapping("/history")
    @ApiOperation("获取对话历史")
    public Result<List<ChatMessage>> history(@RequestParam String sessionId) {
        List<ChatMessage> history = agentService.getHistory(sessionId);
        return Result.success(history);
    }

    @PostMapping("/quick")
    @ApiOperation("快捷操作")
    public Result<AgentResponse> quickAction(@RequestBody QuickActionRequest request) {
        log.info("Agent快捷操作，用户ID: {}, 会话ID: {}, 动作: {}",
                 request.getUserId(), request.getSessionId(), request.getAction());

        AgentResponse response = agentService.quickAction(
            request.getUserId(),
            request.getSessionId(),
            request.getAction(),
            request.getParams()
        );
        return Result.success(response);
    }
}
