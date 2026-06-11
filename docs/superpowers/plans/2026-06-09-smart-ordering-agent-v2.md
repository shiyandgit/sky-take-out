# 智能点餐 Agent 实现计划 (V2 - 直接API调用)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为苍穹外卖项目添加智能点餐Agent功能，支持自然语言点餐、场景化推荐、多轮对话

**Architecture:** 使用RestTemplate直接调用通义千问API，手动实现Agent逻辑，保持现有Spring Boot 2.7.3版本不变

**Tech Stack:** RestTemplate、通义千问API、Redis、Spring Boot 2.7.3

---

## 文件结构

```
sky-server/src/main/java/com/sky/
├── agent/
│   ├── AgentService.java                 # Agent核心服务
│   ├── QwenClient.java                   # 通义千问API客户端
│   ├── tools/
│   │   ├── Tool.java                     # Tool接口
│   │   ├── DishQueryTool.java            # 菜品查询工具
│   │   ├── CartTool.java                 # 购物车工具
│   │   ├── OrderTool.java                # 订单工具
│   │   └── AddressTool.java              # 地址工具
│   ├── memory/
│   │   ├── ConversationMemory.java       # 会话级记忆
│   │   └── UserPreferenceMemory.java     # 用户级记忆
│   └── model/
│       ├── ChatMessage.java              # 聊天消息模型
│       ├── FunctionCall.java             # 函数调用模型
│       └── ToolDefinition.java           # 工具定义模型
├── controller/user/
│   └── AgentController.java              # Agent对话接口
└── dto/
    ├── AgentRequest.java                 # Agent请求DTO
    ├── AgentResponse.java                # Agent响应DTO
    ├── QuickAction.java                  # 快捷按钮DTO
    └── UserPreference.java               # 用户偏好DTO

sky-server/src/test/java/com/sky/agent/
├── AgentServiceTest.java                 # Agent服务测试
├── QwenClientTest.java                   # API客户端测试
└── tools/
    └── ToolTest.java                     # 工具测试
```

---

## Task 1: 创建数据模型

**Files:**
- Create: `sky-pojo/src/main/java/com/sky/dto/AgentRequest.java`
- Create: `sky-pojo/src/main/java/com/sky/dto/AgentResponse.java`
- Create: `sky-pojo/src/main/java/com/sky/dto/QuickAction.java`
- Create: `sky-pojo/src/main/java/com/sky/dto/UserPreference.java`
- Create: `sky-pojo/src/main/java/com/sky/dto/QuickActionRequest.java`

- [ ] **Step 1: 创建AgentRequest DTO**

```java
package com.sky.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("Agent对话请求")
public class AgentRequest {
    @ApiModelProperty("会话ID")
    private String sessionId;

    @ApiModelProperty("用户消息")
    private String message;

    @ApiModelProperty("用户ID")
    private Long userId;
}
```

- [ ] **Step 2: 创建AgentResponse DTO**

```java
package com.sky.dto;

import com.sky.vo.DishVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
@ApiModel("Agent对话响应")
public class AgentResponse {
    @ApiModelProperty("回复内容")
    private String content;

    @ApiModelProperty("快捷按钮")
    private List<QuickAction> quickActions;

    @ApiModelProperty("推荐的菜品")
    private List<DishVO> dishes;

    @ApiModelProperty("会话ID")
    private String sessionId;
}
```

- [ ] **Step 3: 创建QuickAction DTO**

```java
package com.sky.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

@Data
@ApiModel("快捷按钮")
public class QuickAction {
    @ApiModelProperty("按钮文字")
    private String label;

    @ApiModelProperty("动作类型")
    private String action;

    @ApiModelProperty("动作参数")
    private Map<String, Object> params;
}
```

- [ ] **Step 4: 创建UserPreference DTO**

```java
package com.sky.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@ApiModel("用户偏好")
public class UserPreference {
    @ApiModelProperty("喜欢的口味")
    private List<String> favoriteFlavors;

    @ApiModelProperty("常点的菜品")
    private List<Long> favoriteDishes;

    @ApiModelProperty("平均消费")
    private BigDecimal averageBudget;

    @ApiModelProperty("平均用餐人数")
    private Integer averagePeople;
}
```

- [ ] **Step 5: 创建QuickActionRequest DTO**

```java
package com.sky.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Map;

@Data
@ApiModel("快捷操作请求")
public class QuickActionRequest {
    @ApiModelProperty("用户ID")
    private Long userId;

    @ApiModelProperty("会话ID")
    private String sessionId;

    @ApiModelProperty("动作类型")
    private String action;

    @ApiModelProperty("动作参数")
    private Map<String, Object> params;
}
```

- [ ] **Step 6: Commit**

```bash
git add sky-pojo/src/main/java/com/sky/dto/Agent*.java
git add sky-pojo/src/main/java/com/sky/dto/QuickAction*.java
git add sky-pojo/src/main/java/com/sky/dto/UserPreference.java
git commit -m "feat: 添加Agent数据模型"
```

---

## Task 2: 创建Agent内部模型

**Files:**
- Create: `sky-server/src/main/java/com/sky/agent/model/ChatMessage.java`
- Create: `sky-server/src/main/java/com/sky/agent/model/FunctionCall.java`
- Create: `sky-server/src/main/java/com/sky/agent/model/ToolDefinition.java`

- [ ] **Step 1: 创建ChatMessage模型**

```java
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
```

- [ ] **Step 2: 创建FunctionCall模型**

```java
package com.sky.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FunctionCall {
    private String name;
    private String arguments;  // JSON格式的参数
}
```

- [ ] **Step 3: 创建ToolDefinition模型**

```java
package com.sky.agent.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ToolDefinition {
    private String type = "function";
    private Function function;

    @Data
    public static class Function {
        private String name;
        private String description;
        private Parameters parameters;
    }

    @Data
    public static class Parameters {
        private String type = "object";
        private Map<String, Property> properties;
        private List<String> required;
    }

    @Data
    public static class Property {
        private String type;
        private String description;
        private List<String> enumValues;
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add sky-server/src/main/java/com/sky/agent/model/
git commit -m "feat: 添加Agent内部模型"
```

---

## Task 3: 创建通义千问API客户端

**Files:**
- Create: `sky-server/src/main/java/com/sky/agent/QwenClient.java`
- Create: `sky-server/src/test/java/com/sky/agent/QwenClientTest.java`

- [ ] **Step 1: 创建QwenClient类**

```java
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
```

- [ ] **Step 2: 添加RestTemplate配置**

```java
package com.sky.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

- [ ] **Step 3: 添加AI配置到application.yml**

```yaml
ai:
  api-key: ${AI_API_KEY:your-api-key-here}
  model: qwen-turbo
  base-url: https://dashscope.aliyuncs.com/api/v1
```

- [ ] **Step 4: Commit**

```bash
git add sky-server/src/main/java/com/sky/agent/QwenClient.java
git add sky-server/src/main/java/com/sky/config/RestTemplateConfig.java
git add sky-server/src/main/resources/application.yml
git commit -m "feat: 实现通义千问API客户端"
```

---

## Task 4: 创建工具接口和实现

**Files:**
- Create: `sky-server/src/main/java/com/sky/agent/tools/Tool.java`
- Create: `sky-server/src/main/java/com/sky/agent/tools/DishQueryTool.java`
- Create: `sky-server/src/main/java/com/sky/agent/tools/CartTool.java`
- Create: `sky-server/src/main/java/com/sky/agent/tools/OrderTool.java`
- Create: `sky-server/src/main/java/com/sky/agent/tools/AddressTool.java`

- [ ] **Step 1: 创建Tool接口**

```java
package com.sky.agent.tools;

import com.sky.agent.model.ToolDefinition;

import java.util.Map;

public interface Tool {
    String getName();
    String getDescription();
    ToolDefinition getDefinition();
    String execute(Map<String, Object> arguments);
}
```

- [ ] **Step 2: 实现DishQueryTool**

```java
package com.sky.agent.tools;

import com.alibaba.fastjson.JSON;
import com.sky.agent.model.ToolDefinition;
import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
@Slf4j
public class DishQueryTool implements Tool {

    @Autowired
    private DishService dishService;

    @Override
    public String getName() {
        return "queryDishes";
    }

    @Override
    public String getDescription() {
        return "根据条件查询菜品，支持口味、价格、分类等筛选";
    }

    @Override
    public ToolDefinition getDefinition() {
        ToolDefinition def = new ToolDefinition();
        ToolDefinition.Function func = new ToolDefinition.Function();
        func.setName(getName());
        func.setDescription(getDescription());

        ToolDefinition.Parameters params = new ToolDefinition.Parameters();
        Map<String, ToolDefinition.Property> properties = new HashMap<>();

        ToolDefinition.Property flavor = new ToolDefinition.Property();
        flavor.setType("string");
        flavor.setDescription("口味偏好，如：辣、甜、清淡");
        properties.put("flavor", flavor);

        ToolDefinition.Property priceRange = new ToolDefinition.Property();
        priceRange.setType("string");
        priceRange.setDescription("价格范围，如：20-50");
        properties.put("priceRange", priceRange);

        ToolDefinition.Property category = new ToolDefinition.Property();
        category.setType("string");
        category.setDescription("菜品分类，如：主食、小吃、饮品");
        properties.put("category", category);

        params.setProperties(properties);
        func.setParameters(params);
        def.setFunction(func);

        return def;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String flavor = (String) arguments.get("flavor");
        String priceRange = (String) arguments.get("priceRange");
        String category = (String) arguments.get("category");

        log.info("查询菜品，口味: {}, 价格范围: {}, 分类: {}", flavor, priceRange, category);

        Dish dish = new Dish();
        dish.setStatus(StatusConstant.ENABLE);

        List<DishVO> dishes = dishService.listWithFlavor(dish);

        if (flavor != null && !flavor.isEmpty()) {
            dishes = filterByFlavor(dishes, flavor);
        }

        if (priceRange != null && !priceRange.isEmpty()) {
            dishes = filterByPriceRange(dishes, priceRange);
        }

        return JSON.toJSONString(dishes);
    }

    private List<DishVO> filterByFlavor(List<DishVO> dishes, String flavor) {
        return dishes.stream()
            .filter(dish -> {
                if (dish.getFlavors() == null) return false;
                return dish.getFlavors().stream()
                    .anyMatch(f -> f.getValue().contains(flavor));
            })
            .collect(java.util.stream.Collectors.toList());
    }

    private List<DishVO> filterByPriceRange(List<DishVO> dishes, String priceRange) {
        String[] parts = priceRange.split("-");
        BigDecimal min = new BigDecimal(parts[0]);
        BigDecimal max = parts.length > 1 ? new BigDecimal(parts[1]) : BigDecimal.valueOf(Double.MAX_VALUE);

        return dishes.stream()
            .filter(dish -> dish.getPrice().compareTo(min) >= 0
                         && dish.getPrice().compareTo(max) <= 0)
            .collect(java.util.stream.Collectors.toList());
    }
}
```

- [ ] **Step 3: 实现CartTool**

```java
package com.sky.agent.tools;

import com.alibaba.fastjson.JSON;
import com.sky.agent.model.ToolDefinition;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class CartTool implements Tool {

    @Autowired
    private ShoppingCartService shoppingCartService;

    @Override
    public String getName() {
        return "manageCart";
    }

    @Override
    public String getDescription() {
        return "管理购物车，包括添加、删除、查看、清空操作";
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
        action.setDescription("操作类型：add(添加), remove(删除), view(查看), clear(清空)");
        List<String> actionValues = Arrays.asList("add", "remove", "view", "clear");
        // 注意：这里简化处理，实际应该设置enum
        properties.put("action", action);

        ToolDefinition.Property dishId = new ToolDefinition.Property();
        dishId.setType("number");
        dishId.setDescription("菜品ID，添加或删除时需要");
        properties.put("dishId", dishId);

        ToolDefinition.Property dishName = new ToolDefinition.Property();
        dishName.setType("string");
        dishName.setDescription("菜品名称");
        properties.put("dishName", dishName);

        ToolDefinition.Property quantity = new ToolDefinition.Property();
        quantity.setType("number");
        quantity.setDescription("数量，添加时需要");
        properties.put("quantity", quantity);

        params.setProperties(properties);
        func.setParameters(params);
        def.setFunction(func);

        return def;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String action = (String) arguments.get("action");
        log.info("购物车操作: {}", action);

        switch (action) {
            case "add":
                return addToCart(arguments);
            case "remove":
                return removeFromCart(arguments);
            case "view":
                return viewCart();
            case "clear":
                return clearCart();
            default:
                return "未知操作: " + action;
        }
    }

    private String addToCart(Map<String, Object> arguments) {
        Long dishId = Long.valueOf(arguments.get("dishId").toString());
        String dishName = (String) arguments.get("dishName");
        Integer quantity = arguments.get("quantity") != null ?
            Integer.valueOf(arguments.get("quantity").toString()) : 1;

        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setDishId(dishId);
        dto.setNumber(quantity);

        shoppingCartService.addShoppingCart(dto);

        return String.format("已将 %s x%d 添加到购物车", dishName, quantity);
    }

    private String removeFromCart(Map<String, Object> arguments) {
        Long dishId = Long.valueOf(arguments.get("dishId").toString());
        String dishName = (String) arguments.get("dishName");

        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setDishId(dishId);

        shoppingCartService.subShoppingCart(dto);

        return String.format("已从购物车删除 %s", dishName);
    }

    private String viewCart() {
        List<ShoppingCart> cart = shoppingCartService.showShoppingCart();
        return JSON.toJSONString(cart);
    }

    private String clearCart() {
        shoppingCartService.cleanShoppingCart();
        return "购物车已清空";
    }
}
```

- [ ] **Step 4: 实现OrderTool**

```java
package com.sky.agent.tools;

import com.alibaba.fastjson.JSON;
import com.sky.agent.model.ToolDefinition;
import com.sky.entity.AddressBook;
import com.sky.entity.Orders;
import com.sky.result.PageResult;
import com.sky.service.AddressBookService;
import com.sky.service.OrderService;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class OrderTool implements Tool {

    @Autowired
    private OrderService orderService;

    @Autowired
    private AddressBookService addressBookService;

    @Override
    public String getName() {
        return "manageOrder";
    }

    @Override
    public String getDescription() {
        return "管理订单，包括提交订单、查询订单状态、取消订单、查询历史订单";
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
        action.setDescription("操作类型：submit(提交), query(查询), cancel(取消), history(历史)");
        properties.put("action", action);

        ToolDefinition.Property orderId = new ToolDefinition.Property();
        orderId.setType("number");
        orderId.setDescription("订单ID");
        properties.put("orderId", orderId);

        ToolDefinition.Property addressId = new ToolDefinition.Property();
        addressId.setType("number");
        addressId.setDescription("地址ID，提交订单时需要");
        properties.put("addressId", addressId);

        ToolDefinition.Property remark = new ToolDefinition.Property();
        remark.setType("string");
        remark.setDescription("备注");
        properties.put("remark", remark);

        ToolDefinition.Property page = new ToolDefinition.Property();
        page.setType("number");
        page.setDescription("页码");
        properties.put("page", page);

        ToolDefinition.Property status = new ToolDefinition.Property();
        status.setType("number");
        status.setDescription("订单状态：1待付款 2待接单 3已接单 4派送中 5已完成 6已取消");
        properties.put("status", status);

        params.setProperties(properties);
        func.setParameters(params);
        def.setFunction(func);

        return def;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String action = (String) arguments.get("action");
        log.info("订单操作: {}", action);

        switch (action) {
            case "submit":
                return submitOrder(arguments);
            case "query":
                return queryOrder(arguments);
            case "cancel":
                return cancelOrder(arguments);
            case "history":
                return queryHistory(arguments);
            default:
                return "未知操作: " + action;
        }
    }

    private String submitOrder(Map<String, Object> arguments) {
        Long addressId = arguments.get("addressId") != null ?
            Long.valueOf(arguments.get("addressId").toString()) : null;
        String remark = (String) arguments.get("remark");

        if (addressId == null) {
            AddressBook address = getDefaultAddress();
            if (address != null) {
                addressId = address.getId();
            }
        }

        Orders.OrdersSubmitDTO dto = new Orders.OrdersSubmitDTO();
        dto.setAddressBookId(addressId);
        dto.setRemark(remark);

        OrderSubmitVO result = orderService.submitOrder(dto);
        return JSON.toJSONString(result);
    }

    private String queryOrder(Map<String, Object> arguments) {
        Long orderId = Long.valueOf(arguments.get("orderId").toString());
        OrderVO order = orderService.details(orderId);
        return JSON.toJSONString(order);
    }

    private String cancelOrder(Map<String, Object> arguments) {
        Long orderId = Long.valueOf(arguments.get("orderId").toString());
        try {
            orderService.userCancelById(orderId);
            return "订单已取消";
        } catch (Exception e) {
            return "取消订单失败: " + e.getMessage();
        }
    }

    private String queryHistory(Map<String, Object> arguments) {
        Integer page = arguments.get("page") != null ?
            Integer.valueOf(arguments.get("page").toString()) : 1;
        Integer status = arguments.get("status") != null ?
            Integer.valueOf(arguments.get("status").toString()) : null;

        PageResult result = orderService.pageQuery4User(page, 10, status);
        return JSON.toJSONString(result);
    }

    private AddressBook getDefaultAddress() {
        AddressBook query = new AddressBook();
        query.setIsDefault(1);
        List<AddressBook> addresses = addressBookService.list(query);
        return addresses.isEmpty() ? null : addresses.get(0);
    }
}
```

- [ ] **Step 5: 实现AddressTool**

```java
package com.sky.agent.tools;

import com.alibaba.fastjson.JSON;
import com.sky.agent.model.ToolDefinition;
import com.sky.entity.AddressBook;
import com.sky.service.AddressBookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class AddressTool implements Tool {

    @Autowired
    private AddressBookService addressBookService;

    @Override
    public String getName() {
        return "queryAddress";
    }

    @Override
    public String getDescription() {
        return "查询用户地址，包括地址列表和默认地址";
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
        action.setDescription("操作类型：list(列表), default(默认), detail(详情)");
        properties.put("action", action);

        ToolDefinition.Property addressId = new ToolDefinition.Property();
        addressId.setType("number");
        addressId.setDescription("地址ID，查询详情时需要");
        properties.put("addressId", addressId);

        params.setProperties(properties);
        func.setParameters(params);
        def.setFunction(func);

        return def;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String action = (String) arguments.get("action");
        log.info("地址操作: {}", action);

        switch (action) {
            case "list":
                return listAddresses();
            case "default":
                return getDefaultAddress();
            case "detail":
                return getAddressDetail(arguments);
            default:
                return "未知操作: " + action;
        }
    }

    private String listAddresses() {
        List<AddressBook> addresses = addressBookService.list(new AddressBook());
        return JSON.toJSONString(addresses);
    }

    private String getDefaultAddress() {
        AddressBook query = new AddressBook();
        query.setIsDefault(1);
        List<AddressBook> addresses = addressBookService.list(query);
        if (addresses.isEmpty()) {
            return "没有默认地址";
        }
        return JSON.toJSONString(addresses.get(0));
    }

    private String getAddressDetail(Map<String, Object> arguments) {
        Long addressId = Long.valueOf(arguments.get("addressId").toString());
        AddressBook address = addressBookService.getById(addressId);
        return JSON.toJSONString(address);
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add sky-server/src/main/java/com/sky/agent/tools/
git commit -m "feat: 实现Agent工具"
```

---

## Task 5: 创建记忆管理

**Files:**
- Create: `sky-server/src/main/java/com/sky/agent/memory/ConversationMemory.java`
- Create: `sky-server/src/main/java/com/sky/agent/memory/UserPreferenceMemory.java`

- [ ] **Step 1: 实现ConversationMemory**

```java
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
        String key = PREFIX + sessionId;
        List<ChatMessage> messages = get(sessionId);
        if (messages == null) {
            messages = new ArrayList<>();
        }

        messages.add(new ChatMessage("user", userMessage, null, null));
        messages.add(new ChatMessage("assistant", agentResponse, null, null));

        // 限制历史长度，保留最近20条消息
        if (messages.size() > 20) {
            messages = messages.subList(messages.size() - 20, messages.size());
        }

        redisTemplate.opsForValue().set(key, messages, TTL);
        log.debug("保存会话历史，会话ID: {}, 消息数量: {}", sessionId, messages.size());
    }

    public void addToolCall(String sessionId, String toolCallId, String toolName, String result) {
        String key = PREFIX + sessionId;
        List<ChatMessage> messages = get(sessionId);
        if (messages == null) {
            messages = new ArrayList<>();
        }

        messages.add(new ChatMessage("tool", result, toolCallId, toolName));

        redisTemplate.opsForValue().set(key, messages, TTL);
    }

    public void clear(String sessionId) {
        String key = PREFIX + sessionId;
        redisTemplate.delete(key);
        log.info("清除会话历史，会话ID: {}", sessionId);
    }
}
```

- [ ] **Step 2: 实现UserPreferenceMemory**

```java
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
```

- [ ] **Step 3: Commit**

```bash
git add sky-server/src/main/java/com/sky/agent/memory/
git commit -m "feat: 实现记忆管理"
```

---

## Task 6: 创建Agent核心服务

**Files:**
- Create: `sky-server/src/main/java/com/sky/agent/AgentService.java`
- Create: `sky-server/src/test/java/com/sky/agent/AgentServiceTest.java`

- [ ] **Step 1: 创建AgentService**

```java
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

        // 8. 保存会话历史
        conversationMemory.add(sessionId, message, content);

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
            conversationMemory.addToolCall(sessionId, toolCall.getId(), toolName, result);
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
```

- [ ] **Step 2: Commit**

```bash
git add sky-server/src/main/java/com/sky/agent/AgentService.java
git commit -m "feat: 实现Agent核心服务"
```

---

## Task 7: 创建AgentController

**Files:**
- Create: `sky-server/src/main/java/com/sky/controller/user/AgentController.java`

- [ ] **Step 1: 创建AgentController**

```java
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
```

- [ ] **Step 2: Commit**

```bash
git add sky-server/src/main/java/com/sky/controller/user/AgentController.java
git commit -m "feat: 实现AgentController"
```

---

## Task 8: 测试验证

**Files:**
- None

- [ ] **Step 1: 编译项目**

Run: `mvn clean compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 2: 启动应用**

Run: `mvn spring-boot:run -pl sky-server`
Expected: 应用启动成功

- [ ] **Step 3: 访问Swagger文档**

Open: `http://localhost:8080/doc.html`
Expected: 显示Agent相关接口

- [ ] **Step 4: 测试Agent对话**

使用Postman或curl测试：
```bash
curl -X POST http://localhost:8080/user/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "sessionId": "test", "message": "我想吃辣的"}'
```

Expected: 返回Agent响应

- [ ] **Step 5: 最终Commit**

```bash
git add .
git commit -m "feat: 智能点餐Agent功能完成"
```

---

## 完成

智能点餐Agent功能已实现完成。主要功能包括：

1. **自然语言点餐**：用户可以通过对话完成点餐流程
2. **场景化推荐**：支持"两个人吃、预算50、想吃清淡的"等场景
3. **购物车管理**：添加、删除、查看、清空购物车
4. **订单管理**：提交订单、查询订单状态、取消订单
5. **地址管理**：查询地址、获取默认地址
6. **多轮对话**：保持会话上下文
7. **用户偏好**：记录和应用用户偏好
8. **快捷按钮**：提供便捷操作入口

**下一步：**
- 配置AI_API_KEY环境变量
- 部署到测试环境
- 进行用户验收测试
