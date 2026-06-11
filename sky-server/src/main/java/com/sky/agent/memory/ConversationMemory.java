package com.sky.agent.memory;

import com.sky.agent.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class ConversationMemory {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String PREFIX = "agent:conversation:";
    private static final Duration TTL = Duration.ofHours(2);

    public List<ChatMessage> get(String sessionId) {
        String key = PREFIX + sessionId;
        List<ChatMessage> messages = (List<ChatMessage>) redisTemplate.opsForValue().get(key);
        log.debug("获取会话历史，会话ID: {}, 消息数量: {}", sessionId,
                  messages != null ? messages.size() : 0);
        return messages;
    }

    public void add(String sessionId, String userMessage, String agentResponse) {
        List<ChatMessage> newMessages = new ArrayList<>();
        newMessages.add(new ChatMessage("user", userMessage, null, null));
        newMessages.add(new ChatMessage("assistant", agentResponse, null, null));
        addMessages(sessionId, newMessages);
    }

    public void addMessages(String sessionId, List<ChatMessage> newMessages) {
        String key = PREFIX + sessionId;
        List<ChatMessage> messages = get(sessionId);
        if (messages == null) {
            messages = new ArrayList<>();
        }

        messages.addAll(newMessages);

        // 限制历史长度，保留最近20条消息
        if (messages.size() > 20) {
            messages = new ArrayList<>(messages.subList(messages.size() - 20, messages.size()));
        }

        redisTemplate.opsForValue().set(key, messages, TTL);
        log.debug("保存会话历史，会话ID: {}, 消息数量: {}", sessionId, messages.size());
    }

    public void clear(String sessionId) {
        String key = PREFIX + sessionId;
        redisTemplate.delete(key);
        log.info("清除会话历史，会话ID: {}", sessionId);
    }
}
