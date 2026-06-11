package com.sky.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sky.agent.model.ChatMessage;
import com.sky.agent.model.FunctionCall;
import com.sky.agent.model.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class QwenClient {

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.model:qwen-turbo}")
    private String model;

    @Value("${ai.base-url:https://dashscope.aliyuncs.com/api/v1}")
    private String baseUrl;

    @Autowired
    private RestTemplate restTemplate;

    public ChatCompletionResponse chatCompletion(List<ChatMessage> messages, List<ToolDefinition> tools) {
        String url = baseUrl + "/services/aigc/text-generation/generation";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        JSONObject request = new JSONObject();
        request.put("model", model);

        // 构建消息
        JSONObject input = new JSONObject();
        JSONArray messageArray = new JSONArray();
        for (ChatMessage msg : messages) {
            JSONObject msgObj = new JSONObject();
            msgObj.put("role", msg.getRole());
            msgObj.put("content", msg.getContent());
            if (msg.getToolCallId() != null) {
                msgObj.put("tool_call_id", msg.getToolCallId());
            }
            if (msg.getName() != null) {
                msgObj.put("name", msg.getName());
            }
            messageArray.add(msgObj);
        }
        input.put("messages", messageArray);

        // 构建工具定义
        if (tools != null && !tools.isEmpty()) {
            JSONArray toolArray = new JSONArray();
            for (ToolDefinition tool : tools) {
                JSONObject toolObj = new JSONObject();
                toolObj.put("type", "function");
                JSONObject function = new JSONObject();
                function.put("name", tool.getFunction().getName());
                function.put("description", tool.getFunction().getDescription());
                function.put("parameters", JSON.parseObject(JSON.toJSONString(tool.getFunction().getParameters())));
                toolObj.put("function", function);
                toolArray.add(toolObj);
            }
            input.put("tools", toolArray);
        }

        request.put("input", input);

        // 参数配置
        JSONObject parameters = new JSONObject();
        parameters.put("result_format", "message");
        request.put("parameters", parameters);

        HttpEntity<String> entity = new HttpEntity<>(request.toJSONString(), headers);

        log.info("调用通义千问API，消息数量: {}, 工具数量: {}", messages.size(), tools != null ? tools.size() : 0);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            JSONObject responseJson = JSON.parseObject(response.getBody());

            return parseResponse(responseJson);
        } catch (Exception e) {
            log.error("调用通义千问API失败", e);
            throw new RuntimeException("调用AI服务失败: " + e.getMessage());
        }
    }

    private ChatCompletionResponse parseResponse(JSONObject response) {
        ChatCompletionResponse result = new ChatCompletionResponse();

        JSONObject output = response.getJSONObject("output");
        JSONArray choices = output.getJSONArray("choices");

        if (choices != null && !choices.isEmpty()) {
            JSONObject choice = choices.getJSONObject(0);
            JSONObject message = choice.getJSONObject("message");

            result.setContent(message.getString("content"));
            result.setRole(message.getString("role"));

            // 解析工具调用
            JSONArray toolCalls = message.getJSONArray("tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                List<ToolCall> calls = new ArrayList<>();
                for (int i = 0; i < toolCalls.size(); i++) {
                    JSONObject toolCall = toolCalls.getJSONObject(i);
                    ToolCall call = new ToolCall();
                    call.setId(toolCall.getString("id"));
                    call.setType(toolCall.getString("type"));

                    JSONObject function = toolCall.getJSONObject("function");
                    FunctionCall funcCall = new FunctionCall();
                    funcCall.setName(function.getString("name"));
                    funcCall.setArguments(function.getString("arguments"));
                    call.setFunction(funcCall);

                    calls.add(call);
                }
                result.setToolCalls(calls);
            }
        }

        return result;
    }

    @lombok.Data
    public static class ChatCompletionResponse {
        private String content;
        private String role;
        private List<ToolCall> toolCalls;
    }

    @lombok.Data
    public static class ToolCall {
        private String id;
        private String type;
        private FunctionCall function;
    }
}
