package com.sky.controller.admin;

import com.sky.agent.AdminAgentService;
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

@RestController("adminAgentController")
@RequestMapping("/admin/agent")
@Api(tags = "商家端-智能运营Agent接口")
@Slf4j
public class AdminAgentController {

    @Autowired
    private AdminAgentService adminAgentService;

    @PostMapping("/chat")
    @ApiOperation("智能对话")
    public Result<AgentResponse> chat(@RequestBody AgentRequest request) {
        log.info("Admin Agent对话请求，员工ID: {}, 会话ID: {}, 消息: {}",
                 request.getUserId(), request.getSessionId(), request.getMessage());

        AgentResponse response = adminAgentService.chat(
            request.getUserId(),
            request.getSessionId(),
            request.getMessage()
        );
        return Result.success(response);
    }

    @GetMapping("/history")
    @ApiOperation("获取对话历史")
    public Result<List<ChatMessage>> history(@RequestParam String sessionId) {
        List<ChatMessage> history = adminAgentService.getHistory(sessionId);
        return Result.success(history);
    }

    @PostMapping("/quick")
    @ApiOperation("快捷操作")
    public Result<AgentResponse> quickAction(@RequestBody QuickActionRequest request) {
        log.info("Admin Agent快捷操作，员工ID: {}, 会话ID: {}, 动作: {}",
                 request.getUserId(), request.getSessionId(), request.getAction());

        AgentResponse response = adminAgentService.quickAction(
            request.getUserId(),
            request.getSessionId(),
            request.getAction(),
            request.getParams()
        );
        return Result.success(response);
    }
}
