package com.sky.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessage {
    private String role;  // system, user, assistant, tool
    private String content;
    private String toolCallId;  // 用于tool角色
    private String name;  // 用于tool角色
}
