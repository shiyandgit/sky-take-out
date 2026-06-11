package com.sky.agent;

import com.alibaba.fastjson.JSON;
import com.sky.agent.memory.ConversationMemory;
import com.sky.agent.model.ChatMessage;
import com.sky.agent.model.ToolDefinition;
import com.sky.agent.tools.Tool;
import com.sky.dto.AgentResponse;
import com.sky.dto.QuickAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminAgentService {

    @Autowired
    private QwenClient qwenClient;

    @Autowired
    private ConversationMemory conversationMemory;

    @Autowired
    private List<Tool> tools;

    private static final String SYSTEM_PROMPT = """
        你是苍穹外卖的智能运营助手，帮助商家管理店铺运营。

        ## 你的能力
        1. 查询经营数据：营业额、订单量、客单价、销量排行
        2. 管理订单：搜索订单、接单、拒单、取消、派送、完成
        3. 管理菜品：查询菜品、搜索菜品、起售/停售
        4. 管理店铺：查询营业状态、营业/打烊

        ## 订单状态说明
        - 1: 待付款
        - 2: 待接单
        - 3: 已接单（待派送）
        - 4: 派送中
        - 5: 已完成
        - 6: 已取消

        ## 回复规则
        1. 使用简洁的中文回复
        2. 数据查询结果以清晰的格式展示
        3. 操作确认前要明确告知将执行的操作
        4. 如果信息不足，主动询问补充
        """;

    public AgentResponse chat(Long employeeId, String sessionId, String message) {
        log.info("Admin Agent对话开始，员工ID: {}, 会话ID: {}, 消息: {}", employeeId, sessionId, message);

        // 1. 加载会话历史
        List<ChatMessage> history = conversationMemory.get(sessionId);
        if (history == null) {
            history = new ArrayList<>();
        }

        // 2. 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", SYSTEM_PROMPT, null, null));
        messages.addAll(history);
        messages.add(new ChatMessage("user", message, null, null));

        // 记录本轮新增的消息（用于保存到历史）
        int newMessagesStart = messages.size() - 1;

        // 3. 获取工具定义（只使用admin工具）
        List<ToolDefinition> toolDefinitions = tools.stream()
            .filter(tool -> tool.getClass().getPackage().getName().contains("admin"))
            .map(Tool::getDefinition)
            .collect(Collectors.toList());

        List<Tool> adminTools = tools.stream()
            .filter(tool -> tool.getClass().getPackage().getName().contains("admin"))
            .collect(Collectors.toList());

        // 4. 调用大模型
        QwenClient.ChatCompletionResponse response = qwenClient.chatCompletion(messages, toolDefinitions);

        // 5. 处理工具调用
        String content = response.getContent();
        if (response.getToolCalls() != null && !response.getToolCalls().isEmpty()) {
            content = handleToolCalls(sessionId, messages, toolDefinitions, adminTools, response);
        }

        // 6. 构建响应
        AgentResponse agentResponse = new AgentResponse();
        agentResponse.setContent(content);
        agentResponse.setSessionId(sessionId);
        agentResponse.setQuickActions(generateQuickActions(message, content));

        // 7. 保存会话历史（包含工具调用的完整过程）
        List<ChatMessage> newMessages = new ArrayList<>(messages.subList(newMessagesStart, messages.size()));
        conversationMemory.addMessages(sessionId, newMessages);

        log.info("Admin Agent对话完成，响应: {}", content);
        return agentResponse;
    }

    private String handleToolCalls(String sessionId, List<ChatMessage> messages,
                                   List<ToolDefinition> toolDefinitions,
                                   List<Tool> adminTools,
                                   QwenClient.ChatCompletionResponse response) {
        ChatMessage assistantMsg = new ChatMessage("assistant", null, null, null);
        messages.add(assistantMsg);

        for (QwenClient.ToolCall toolCall : response.getToolCalls()) {
            String toolName = toolCall.getFunction().getName();
            String arguments = toolCall.getFunction().getArguments();

            log.info("执行工具调用: {}, 参数: {}", toolName, arguments);

            Tool tool = adminTools.stream()
                .filter(t -> t.getName().equals(toolName))
                .findFirst()
                .orElse(null);

            String result;
            if (tool != null) {
                try {
                    Map<String, Object> args = JSON.parseObject(arguments, Map.class);
                    result = tool.execute(args);
                } catch (Exception e) {
                    log.error("工具执行失败: {}", toolName, e);
                    result = "工具执行失败: " + e.getMessage();
                }
            } else {
                result = "未知工具: " + toolName;
            }

            messages.add(new ChatMessage("tool", result, toolCall.getId(), toolName));
        }

        QwenClient.ChatCompletionResponse finalResponse = qwenClient.chatCompletion(messages, toolDefinitions);
        return finalResponse.getContent();
    }

    public List<ChatMessage> getHistory(String sessionId) {
        return conversationMemory.get(sessionId);
    }

    public AgentResponse quickAction(Long employeeId, String sessionId,
                                     String action, Map<String, Object> params) {
        String message = buildQuickActionMessage(action, params);
        return chat(employeeId, sessionId, message);
    }

    private String buildQuickActionMessage(String action, Map<String, Object> params) {
        switch (action) {
            case "TODAY_DATA":
                return "查看今日营业数据";
            case "ORDER_STATS":
                return "查看订单统计";
            case "PENDING_ORDERS":
                return "查看待接单的订单";
            case "TOP10":
                return "查看销量前10的菜品";
            case "SHOP_STATUS":
                return "查看店铺营业状态";
            case "OPEN_SHOP":
                return "开始营业";
            case "CLOSE_SHOP":
                return "打烊";
            default:
                return action;
        }
    }

    private List<QuickAction> generateQuickActions(String userMessage, String agentResponse) {
        List<QuickAction> actions = new ArrayList<>();

        if (userMessage.contains("营业额") || userMessage.contains("数据") || agentResponse.contains("营业额")) {
            actions.add(createQuickAction("今日数据", "TODAY_DATA"));
            actions.add(createQuickAction("销量排行", "TOP10"));
        }

        if (userMessage.contains("订单") || agentResponse.contains("订单")) {
            actions.add(createQuickAction("待接单", "PENDING_ORDERS"));
            actions.add(createQuickAction("订单统计", "ORDER_STATS"));
        }

        if (userMessage.contains("店铺") || userMessage.contains("营业") || agentResponse.contains("店铺")) {
            actions.add(createQuickAction("营业状态", "SHOP_STATUS"));
        }

        if (actions.isEmpty()) {
            actions.add(createQuickAction("今日数据", "TODAY_DATA"));
            actions.add(createQuickAction("待接单", "PENDING_ORDERS"));
            actions.add(createQuickAction("订单统计", "ORDER_STATS"));
        }

        return actions;
    }

    private QuickAction createQuickAction(String label, String action) {
        QuickAction quickAction = new QuickAction();
        quickAction.setLabel(label);
        quickAction.setAction(action);
        return quickAction;
    }
}
