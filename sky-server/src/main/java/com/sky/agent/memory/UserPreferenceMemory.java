package com.sky.agent.memory;

import com.sky.dto.UserPreference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserPreferenceMemory {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String PREFIX = "agent:preference:";

    public UserPreference get(Long userId) {
        String key = PREFIX + userId;
        UserPreference preference = (UserPreference) redisTemplate.opsForValue().get(key);
        log.debug("获取用户偏好，用户ID: {}, 偏好: {}", userId, preference);
        return preference;
    }

    public void save(Long userId, UserPreference preference) {
        String key = PREFIX + userId;
        redisTemplate.opsForValue().set(key, preference);
        log.info("保存用户偏好，用户ID: {}, 偏好: {}", userId, preference);
    }

    public void update(Long userId, String message, String agentResponse) {
        // 从对话中提取偏好信息
        // 这里简化处理，实际应该使用大模型提取
        log.info("更新用户偏好，用户ID: {}", userId);
    }
}
