package com.sky.agent.tools.admin;

import com.alibaba.fastjson.JSON;
import com.sky.agent.model.ToolDefinition;
import com.sky.agent.tools.Tool;
import com.sky.constant.StatusConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class ShopManageTool implements Tool {

    public static final String SHOP_STATUS_KEY = "SHOP_STATUS";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public String getName() {
        return "manageShop";
    }

    @Override
    public String getDescription() {
        return "管理店铺状态，查询或设置营业状态";
    }

    @Override
    public ToolDefinition getDefinition() {
        ToolDefinition def = new ToolDefinition();
        ToolDefinition.Function func = new ToolDefinition.Function();
        func.setName(getName());
        func.setDescription(getDescription());

        ToolDefinition.Parameters params = new ToolDefinition.Parameters();
        Map<String, ToolDefinition.Property> properties = new HashMap<>();

        ToolDefinition.Property action = new ToolDefinition.Property();
        action.setType("string");
        action.setDescription("操作类型：getStatus(查询状态), open(营业), close(打烊)");
        properties.put("action", action);

        params.setProperties(properties);
        func.setParameters(params);
        def.setFunction(func);

        return def;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String action = (String) arguments.get("action");
        if (action == null || action.isEmpty()) {
            return "请指定操作类型";
        }
        log.info("店铺管理操作: {}", action);

        switch (action) {
            case "getStatus":
                return getStatus();
            case "open":
                return setStatus(StatusConstant.ENABLE);
            case "close":
                return setStatus(StatusConstant.DISABLE);
            default:
                return "未知操作: " + action;
        }
    }

    private String getStatus() {
        Integer status = (Integer) redisTemplate.opsForValue().get(SHOP_STATUS_KEY);
        if (status == null) {
            status = 0;
        }
        String statusText = status == 1 ? "营业中" : "已打烊";
        Map<String, Object> result = new HashMap<>();
        result.put("status", status);
        result.put("statusText", statusText);
        return JSON.toJSONString(result);
    }

    private String setStatus(Integer status) {
        redisTemplate.opsForValue().set(SHOP_STATUS_KEY, status);
        String statusText = status == 1 ? "已开始营业" : "已打烊";
        return statusText;
    }
}
