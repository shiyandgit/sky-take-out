package com.sky.agent.tools;

import com.sky.agent.model.ToolDefinition;

import java.util.Map;

public interface Tool {
    String getName();
    String getDescription();
    ToolDefinition getDefinition();
    String execute(Map<String, Object> arguments);
}
