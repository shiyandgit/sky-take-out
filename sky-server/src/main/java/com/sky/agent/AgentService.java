package com.sky.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.agent.memory.ConversationMemory;
import com.sky.agent.memory.UserPreferenceMemory;
import com.sky.agent.model.ChatMessage;
import com.sky.agent.model.ToolDefinition;
import com.sky.agent.tools.Tool;
import com.sky.dto.AgentResponse;
import com.sky.dto.QuickAction;
import com.sky.dto.UserPreference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AgentService {

    @Autowired
    private QwenClient qwenClient;

    @Autowired
    private ConversationMemory conversationMemory;

    @Autowired
    private UserPreferenceMemory userPreferenceMemory;

    @Autowired
    private List<Tool> tools;

    private static final String SYSTEM_PROMPT = """
        你是一个智能点餐助手，帮助用户完成点餐流程。

        ## 你的能力
        1. 根据用户描述推荐菜品（如：两个人吃、预算50、想吃清淡的）
        2. 管理购物车（添加、删除、查看、清空）
        3. 提交订单、查询订单状态、取消订单
        4. 查询用户地址、管理地址

        ## 用户偏好
        {userPreference}

        ## 回复规则
        1. 使用友好、自然的中文回复
        2. 推荐菜品时，说明推荐理由
        3. 如果用户需求不明确，主动询问澄清
        4. 保持回复简洁，避免过长
        """;

    public AgentResponse chat(Long userId, String sessionId, String message) {
        log.info("Agent对话开始，用户ID: {}, 会话ID: {}, 消息: {}", userId, sessionId, message);

        // 1. 加载会话历史
        List<ChatMessage> history = conversationMemory.get(sessionId);
        if (history == null) {
            history = new ArrayList<>();
        }

        // 2. 加载用户偏好
        UserPreference preference = userPreferenceMemory.get(userId);
        String systemPrompt = SYSTEM_PROMPT.replace("{userPreference}", formatPreference(preference));

        // 3. 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", systemPrompt, null, null));
        messages.addAll(history);
        messages.add(new ChatMessage("user", message, null, null));

        // 记录本轮新增的消息（用于保存到历史）
        int newMessagesStart = messages.size() - 1; // user消息的位置

        // 4. 获取工具定义
        List<ToolDefinition> toolDefinitions = tools.stream()
            .map(Tool::getDefinition)
            .collect(Collectors.toList());

        // 5. 调用大模型
        QwenClient.ChatCompletionResponse response = qwenClient.chatCompletion(messages, toolDefinitions);

        // 6. 处理工具调用
        String content = response.getContent();
        if (response.getToolCalls() != null && !response.getToolCalls().isEmpty()) {
            content = handleToolCalls(sessionId, messages, toolDefinitions, response);
        }

        // 7. 构建响应
        AgentResponse agentResponse = new AgentResponse();
        agentResponse.setContent(content);
        agentResponse.setSessionId(sessionId);
        agentResponse.setQuickActions(generateQuickActions(message, content));

        // 8. 保存会话历史（包含工具调用的完整过程）
        List<ChatMessage> newMessages = new ArrayList<>(messages.subList(newMessagesStart, messages.size()));
        conversationMemory.addMessages(sessionId, newMessages);

        // 9. 更新用户偏好
        userPreferenceMemory.update(userId, message, content);

        log.info("Agent对话完成，响应: {}", content);
        return agentResponse;
    }

    private String handleToolCalls(String sessionId, List<ChatMessage> messages,
                                   List<ToolDefinition> toolDefinitions,
                                   QwenClient.ChatCompletionResponse response) {
        // 添加assistant消息（包含tool_calls）
        ChatMessage assistantMsg = new ChatMessage("assistant", null, null, null);
        messages.add(assistantMsg);

        // 执行每个工具调用
        for (QwenClient.ToolCall toolCall : response.getToolCalls()) {
            String toolName = toolCall.getFunction().getName();
            String arguments = toolCall.getFunction().getArguments();

            log.info("执行工具调用: {}, 参数: {}", toolName, arguments);

            // 查找并执行工具
            Tool tool = tools.stream()
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

            // 添加工具结果到消息
            messages.add(new ChatMessage("tool", result, toolCall.getId(), toolName));
        }

        // 再次调用大模型获取最终回复
        QwenClient.ChatCompletionResponse finalResponse = qwenClient.chatCompletion(messages, toolDefinitions);
        return finalResponse.getContent();
    }

    public List<ChatMessage> getHistory(String sessionId) {
        return conversationMemory.get(sessionId);
    }

    public AgentResponse quickAction(Long userId, String sessionId,
                                     String action, Map<String, Object> params) {
        String message = buildQuickActionMessage(action, params);
        return chat(userId, sessionId, message);
    }

    private String buildQuickActionMessage(String action, Map<String, Object> params) {
        switch (action) {
            case "RECOMMEND":
                return "推荐一些菜品";
            case "VIEW_CART":
                return "查看购物车";
            case "SUBMIT_ORDER":
                return "提交订单";
            case "QUERY_ORDER":
                return "查询订单状态";
            case "CLEAR_CART":
                return "清空购物车";
            case "REORDER":
                return "再来一单";
            default:
                return action;
        }
    }

    private String formatPreference(UserPreference preference) {
        if (preference == null) {
            return "暂无历史偏好";
        }

        StringBuilder sb = new StringBuilder();
        if (preference.getFavoriteFlavors() != null && !preference.getFavoriteFlavors().isEmpty()) {
            sb.append("喜欢的口味：").append(String.join("、", preference.getFavoriteFlavors())).append("\n");
        }
        if (preference.getAverageBudget() != null) {
            sb.append("平均预算：").append(preference.getAverageBudget()).append("元\n");
        }
        if (preference.getAveragePeople() != null) {
            sb.append("平均用餐人数：").append(preference.getAveragePeople()).append("人\n");
        }

        return sb.length() > 0 ? sb.toString() : "暂无历史偏好";
    }

    private List<QuickAction> generateQuickActions(String userMessage, String agentResponse) {
        List<QuickAction> actions = new ArrayList<>();

        if (userMessage.contains("推荐") || userMessage.contains("吃什么")) {
            actions.add(createQuickAction("推荐菜品", "RECOMMEND"));
        }

        if (agentResponse.contains("购物车") || userMessage.contains("购物车")) {
            actions.add(createQuickAction("查看购物车", "VIEW_CART"));
        }

        if (agentResponse.contains("已添加") || agentResponse.contains("已将")) {
            actions.add(createQuickAction("提交订单", "SUBMIT_ORDER"));
            actions.add(createQuickAction("查看购物车", "VIEW_CART"));
        }

        if (userMessage.contains("订单") || agentResponse.contains("订单")) {
            actions.add(createQuickAction("查询订单", "QUERY_ORDER"));
        }

        if (actions.isEmpty()) {
            actions.add(createQuickAction("推荐菜品", "RECOMMEND"));
            actions.add(createQuickAction("查看购物车", "VIEW_CART"));
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
