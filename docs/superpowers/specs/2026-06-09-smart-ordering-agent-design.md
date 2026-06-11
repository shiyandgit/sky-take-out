# 智能点餐 Agent 设计文档

## 1. 项目概述

### 1.1 背景
苍穹外卖项目是一个典型的 Spring Boot 外卖点餐系统，包含菜品管理、购物车、订单等核心功能。为了提升用户体验并展示 AI Agent 能力，我们计划为其添加智能点餐 Agent 功能。

### 1.2 目标
- 实现自然语言点餐，用户可以通过对话完成点餐流程
- 支持场景化推荐，如"两个人吃、预算50、想吃清淡的"
- 提供个性化推荐，基于用户历史订单和偏好
- 支持多轮对话，保持上下文连贯性

### 1.3 技术选型
- **LLM 服务**：国产大模型（通义千问/智谱清言）
- **Agent 框架**：Spring AI
- **记忆存储**：Redis
- **交互方式**：混合模式（文本 + 快捷按钮）

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      用户端 (微信小程序/APP)                   │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    AgentController (对话入口)                  │
│  - POST /agent/chat (文本对话)                               │
│  - GET  /agent/history (对话历史)                            │
│  - POST /agent/quick (快捷按钮)                              │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      ChatAgent (核心引擎)                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ IntentParser│  │ ToolExecutor│  │ ResponseGen │         │
│  │  意图解析    │  │  工具执行    │  │  响应生成    │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Tools (Function Calling)                │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │DishQuery │ │CartTool  │ │OrderTool │ │AddrTool  │       │
│  │菜品查询   │ │购物车    │ │订单管理   │ │地址管理   │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Memory (记忆管理)                          │
│  ┌─────────────────┐  ┌─────────────────┐                  │
│  │ConversationMem  │  │UserPreferenceMem│                  │
│  │ 会话级记忆(Redis)│  │ 用户级记忆(Redis)│                  │
│  └─────────────────┘  └─────────────────┘                  │
└─────────────────────────────┬───────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              Spring AI + 国产大模型 (通义千问/智谱清言)         │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 模块职责

#### AgentController
- 接收用户请求，路由到 ChatAgent
- 提供 RESTful API 接口
- 处理请求参数校验

#### ChatAgent
- 核心对话引擎
- 意图解析和工具调用
- 响应生成和格式化

#### Tools
- **DishQueryTool**：菜品查询和推荐
- **CartTool**：购物车管理
- **OrderTool**：订单管理
- **AddressTool**：地址管理

#### Memory
- **ConversationMemory**：会话级记忆，存储单次对话历史
- **UserPreferenceMemory**：用户级记忆，存储用户偏好

## 3. 核心模块设计

### 3.1 AgentController

```java
@RestController
@RequestMapping("/agent")
@Api(tags = "智能点餐Agent接口")
public class AgentController {

    @Autowired
    private ChatAgent chatAgent;

    @PostMapping("/chat")
    @ApiOperation("智能对话")
    public Result<AgentResponse> chat(@RequestBody AgentRequest request) {
        AgentResponse response = chatAgent.chat(
            request.getUserId(),
            request.getSessionId(),
            request.getMessage()
        );
        return Result.success(response);
    }

    @GetMapping("/history")
    @ApiOperation("获取对话历史")
    public Result<List<ChatMessage>> history(@RequestParam String sessionId) {
        List<ChatMessage> history = chatAgent.getHistory(sessionId);
        return Result.success(history);
    }

    @PostMapping("/quick")
    @ApiOperation("快捷操作")
    public Result<AgentResponse> quickAction(@RequestBody QuickActionRequest request) {
        AgentResponse response = chatAgent.quickAction(
            request.getUserId(),
            request.getSessionId(),
            request.getAction(),
            request.getParams()
        );
        return Result.success(response);
    }
}
```

### 3.2 ChatAgent 核心引擎

```java
@Component
@Slf4j
public class ChatAgent {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ToolExecutor toolExecutor;

    @Autowired
    private ConversationMemory conversationMemory;

    @Autowired
    private UserPreferenceMemory userPreferenceMemory;

    @Autowired
    private PromptTemplate promptTemplate;

    public AgentResponse chat(Long userId, String sessionId, String message) {
        log.info("Agent对话开始，用户ID: {}, 会话ID: {}, 消息: {}", userId, sessionId, message);

        // 1. 加载会话历史
        List<ChatMessage> history = conversationMemory.get(sessionId);
        if (history == null) {
            history = new ArrayList<>();
        }

        // 2. 加载用户偏好
        UserPreference preference = userPreferenceMemory.get(userId);
        if (preference == null) {
            preference = new UserPreference();
        }

        // 3. 构建系统提示词
        String systemPrompt = promptTemplate.buildSystemPrompt(preference);

        // 4. 调用大模型
        ChatResponse response = chatClient.prompt()
            .system(systemPrompt)
            .messages(history)
            .user(message)
            .functions("dishQuery", "cartAdd", "orderSubmit", "addressQuery")
            .call();

        // 5. 解析响应，执行工具调用
        AgentResponse agentResponse = toolExecutor.execute(response);

        // 6. 保存会话历史
        conversationMemory.add(sessionId, message, agentResponse.getContent());

        // 7. 更新用户偏好
        userPreferenceMemory.update(userId, message, agentResponse);

        log.info("Agent对话完成，响应: {}", agentResponse.getContent());
        return agentResponse;
    }

    public List<ChatMessage> getHistory(String sessionId) {
        return conversationMemory.get(sessionId);
    }

    public AgentResponse quickAction(Long userId, String sessionId, 
                                     String action, Map<String, Object> params) {
        // 处理快捷按钮操作
        String message = buildQuickActionMessage(action, params);
        return chat(userId, sessionId, message);
    }

    private String buildQuickActionMessage(String action, Map<String, Object> params) {
        // 根据动作类型构建消息
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
            default:
                return action;
        }
    }
}
```

### 3.3 Tools (Function Calling)

#### DishQueryTool

```java
@Component
@Slf4j
public class DishQueryTool {

    @Autowired
    private DishService dishService;

    @Autowired
    private CategoryService categoryService;

    @Tool(description = "根据条件查询菜品，支持口味、价格、分类等筛选")
    public List<DishVO> queryDishes(
        @ToolParam(description = "口味偏好，如：辣、甜、清淡") String flavor,
        @ToolParam(description = "价格范围，如：20-50") String priceRange,
        @ToolParam(description = "菜品分类，如：主食、小吃、饮品") String category
    ) {
        log.info("查询菜品，口味: {}, 价格范围: {}, 分类: {}", flavor, priceRange, category);

        // 构建查询条件
        Dish dish = new Dish();
        dish.setStatus(StatusConstant.ENABLE);

        // 查询所有菜品
        List<DishVO> dishes = dishService.listWithFlavor(dish);

        // 应用筛选条件
        if (flavor != null && !flavor.isEmpty()) {
            dishes = filterByFlavor(dishes, flavor);
        }

        if (priceRange != null && !priceRange.isEmpty()) {
            dishes = filterByPriceRange(dishes, priceRange);
        }

        if (category != null && !category.isEmpty()) {
            dishes = filterByCategory(dishes, category);
        }

        return dishes;
    }

    @Tool(description = "根据场景推荐菜品，如：两个人吃、预算50、想吃清淡的")
    public List<DishVO> recommendDishes(
        @ToolParam(description = "用餐人数") Integer peopleCount,
        @ToolParam(description = "预算金额") BigDecimal budget,
        @ToolParam(description = "口味偏好") String flavor,
        @ToolParam(description = "特殊需求") String special需求
    ) {
        log.info("场景化推荐，人数: {}, 预算: {}, 口味: {}, 特殊需求: {}", 
                 peopleCount, budget, flavor, special需求);

        // 查询所有可用菜品
        List<DishVO> allDishes = dishService.listWithFlavor(new Dish());

        // 根据场景推荐
        List<DishVO> recommended = new ArrayList<>();

        // 1. 根据口味筛选
        if (flavor != null && !flavor.isEmpty()) {
            allDishes = filterByFlavor(allDishes, flavor);
        }

        // 2. 根据预算筛选
        if (budget != null) {
            BigDecimal maxPricePerDish = budget.divide(
                BigDecimal.valueOf(peopleCount != null ? peopleCount : 1), 
                2, RoundingMode.HALF_UP
            );
            allDishes = filterByMaxPrice(allDishes, maxPricePerDish);
        }

        // 3. 根据人数推荐数量
        int recommendCount = peopleCount != null ? peopleCount + 1 : 3;
        if (allDishes.size() > recommendCount) {
            // 随机推荐
            Collections.shuffle(allDishes);
            recommended = allDishes.subList(0, recommendCount);
        } else {
            recommended = allDishes;
        }

        return recommended;
    }

    private List<DishVO> filterByFlavor(List<DishVO> dishes, String flavor) {
        return dishes.stream()
            .filter(dish -> {
                if (dish.getFlavors() == null) return false;
                return dish.getFlavors().stream()
                    .anyMatch(f -> f.getValue().contains(flavor));
            })
            .collect(Collectors.toList());
    }

    private List<DishVO> filterByPriceRange(List<DishVO> dishes, String priceRange) {
        String[] parts = priceRange.split("-");
        BigDecimal min = new BigDecimal(parts[0]);
        BigDecimal max = parts.length > 1 ? new BigDecimal(parts[1]) : BigDecimal.valueOf(Double.MAX_VALUE);

        return dishes.stream()
            .filter(dish -> dish.getPrice().compareTo(min) >= 0 
                         && dish.getPrice().compareTo(max) <= 0)
            .collect(Collectors.toList());
    }

    private List<DishVO> filterByCategory(List<DishVO> dishes, String category) {
        // 根据分类名称筛选
        return dishes.stream()
            .filter(dish -> {
                // 这里需要根据实际的分类名称进行匹配
                return true; // 简化处理
            })
            .collect(Collectors.toList());
    }

    private List<DishVO> filterByMaxPrice(List<DishVO> dishes, BigDecimal maxPrice) {
        return dishes.stream()
            .filter(dish -> dish.getPrice().compareTo(maxPrice) <= 0)
            .collect(Collectors.toList());
    }
}
```

#### CartTool

```java
@Component
@Slf4j
public class CartTool {

    @Autowired
    private ShoppingCartService shoppingCartService;

    @Tool(description = "添加菜品到购物车")
    public String addToCart(
        @ToolParam(description = "菜品ID") Long dishId,
        @ToolParam(description = "菜品名称") String dishName,
        @ToolParam(description = "数量") Integer quantity
    ) {
        log.info("添加购物车，菜品ID: {}, 名称: {}, 数量: {}", dishId, dishName, quantity);

        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setDishId(dishId);
        dto.setNumber(quantity != null ? quantity : 1);

        shoppingCartService.addShoppingCart(dto);

        return String.format("已将 %s x%d 添加到购物车", dishName, quantity != null ? quantity : 1);
    }

    @Tool(description = "查看购物车内容")
    public List<ShoppingCart> viewCart() {
        log.info("查看购物车");
        return shoppingCartService.showShoppingCart();
    }

    @Tool(description = "清空购物车")
    public String clearCart() {
        log.info("清空购物车");
        shoppingCartService.cleanShoppingCart();
        return "购物车已清空";
    }

    @Tool(description = "从购物车删除一个菜品")
    public String removeFromCart(
        @ToolParam(description = "菜品ID") Long dishId,
        @ToolParam(description = "菜品名称") String dishName
    ) {
        log.info("从购物车删除，菜品ID: {}, 名称: {}", dishId, dishName);

        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setDishId(dishId);

        shoppingCartService.subShoppingCart(dto);

        return String.format("已从购物车删除 %s", dishName);
    }
}
```

#### OrderTool

```java
@Component
@Slf4j
public class OrderTool {

    @Autowired
    private OrderService orderService;

    @Autowired
    private AddressBookService addressBookService;

    @Tool(description = "提交订单")
    public OrderSubmitVO submitOrder(
        @ToolParam(description = "地址ID") Long addressId,
        @ToolParam(description = "备注") String remark
    ) {
        log.info("提交订单，地址ID: {}, 备注: {}", addressId, remark);

        // 获取用户默认地址
        if (addressId == null) {
            AddressBook address = getDefaultAddress();
            if (address != null) {
                addressId = address.getId();
            }
        }

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(addressId);
        dto.setRemark(remark);

        return orderService.submitOrder(dto);
    }

    @Tool(description = "查询订单状态")
    public OrderVO queryOrderStatus(
        @ToolParam(description = "订单ID") Long orderId
    ) {
        log.info("查询订单状态，订单ID: {}", orderId);
        return orderService.details(orderId);
    }

    @Tool(description = "取消订单")
    public String cancelOrder(
        @ToolParam(description = "订单ID") Long orderId
    ) {
        log.info("取消订单，订单ID: {}", orderId);
        try {
            orderService.userCancelById(orderId);
            return "订单已取消";
        } catch (Exception e) {
            return "取消订单失败: " + e.getMessage();
        }
    }

    @Tool(description = "查询历史订单")
    public PageResult queryOrderHistory(
        @ToolParam(description = "页码") Integer page,
        @ToolParam(description = "每页数量") Integer pageSize,
        @ToolParam(description = "订单状态") Integer status
    ) {
        log.info("查询历史订单，页码: {}, 每页数量: {}, 状态: {}", page, pageSize, status);
        return orderService.pageQuery4User(
            page != null ? page : 1,
            pageSize != null ? pageSize : 10,
            status
        );
    }

    @Tool(description = "再来一单")
    public String reorder(
        @ToolParam(description = "订单ID") Long orderId
    ) {
        log.info("再来一单，订单ID: {}", orderId);
        orderService.repetition(orderId);
        return "已将订单中的菜品添加到购物车";
    }

    private AddressBook getDefaultAddress() {
        AddressBook query = new AddressBook();
        query.setIsDefault(1);
        List<AddressBook> addresses = addressBookService.list(query);
        return addresses.isEmpty() ? null : addresses.get(0);
    }
}
```

#### AddressTool

```java
@Component
@Slf4j
public class AddressTool {

    @Autowired
    private AddressBookService addressBookService;

    @Tool(description = "查询用户地址列表")
    public List<AddressBook> queryAddresses() {
        log.info("查询用户地址列表");
        return addressBookService.list(new AddressBook());
    }

    @Tool(description = "获取默认地址")
    public AddressBook getDefaultAddress() {
        log.info("获取默认地址");
        AddressBook query = new AddressBook();
        query.setIsDefault(1);
        List<AddressBook> addresses = addressBookService.list(query);
        return addresses.isEmpty() ? null : addresses.get(0);
    }

    @Tool(description = "获取地址详情")
    public AddressBook getAddressDetail(
        @ToolParam(description = "地址ID") Long addressId
    ) {
        log.info("获取地址详情，地址ID: {}", addressId);
        return addressBookService.getById(addressId);
    }
}
```

### 3.4 Memory (记忆管理)

#### ConversationMemory

```java
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

        messages.add(new ChatMessage("user", userMessage));
        messages.add(new ChatMessage("assistant", agentResponse));

        // 限制历史长度，保留最近20条消息
        if (messages.size() > 20) {
            messages = messages.subList(messages.size() - 20, messages.size());
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
```

#### UserPreferenceMemory

```java
@Component
@Slf4j
public class UserPreferenceMemory {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ChatClient chatClient;

    private static final String PREFIX = "agent:preference:";

    public UserPreference get(Long userId) {
        String key = PREFIX + userId;
        UserPreference preference = (UserPreference) redisTemplate.opsForValue().get(key);
        log.debug("获取用户偏好，用户ID: {}, 偏好: {}", userId, preference);
        return preference;
    }

    public void update(Long userId, String message, AgentResponse response) {
        log.info("更新用户偏好，用户ID: {}", userId);

        // 使用大模型从对话中提取用户偏好
        String extractPrompt = String.format("""
            从以下对话中提取用户的点餐偏好，返回JSON格式：
            {
                "favoriteFlavors": ["口味1", "口味2"],
                "favoriteDishes": ["菜品1", "菜品2"],
                "averageBudget": 50,
                "averagePeople": 2
            }
            
            用户消息: %s
            Agent响应: %s
            """, message, response.getContent());

        try {
            ChatResponse chatResponse = chatClient.prompt()
                .user(extractPrompt)
                .call();

            String jsonResponse = chatResponse.getResult().getOutput().getContent();
            UserPreference preference = parsePreference(jsonResponse);

            String key = PREFIX + userId;
            redisTemplate.opsForValue().set(key, preference);
            log.info("用户偏好已更新，用户ID: {}, 偏好: {}", userId, preference);
        } catch (Exception e) {
            log.error("更新用户偏好失败，用户ID: {}", userId, e);
        }
    }

    private UserPreference parsePreference(String json) {
        // 解析JSON为UserPreference对象
        // 这里简化处理，实际应该使用JSON库
        return new UserPreference();
    }
}
```

### 3.5 PromptTemplate

```java
@Component
public class PromptTemplate {

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
        3. 提供快捷按钮，方便用户操作
        4. 如果用户需求不明确，主动询问澄清
        5. 保持回复简洁，避免过长

        ## 快捷按钮
        当用户询问推荐时，提供"推荐菜品"按钮
        当用户询问已选菜品时，提供"查看购物车"按钮
        当购物车有内容时，提供"提交订单"按钮
        当用户询问订单状态时，提供"查询订单"按钮
        """;

    public String buildSystemPrompt(UserPreference preference) {
        String preferenceStr = formatPreference(preference);
        return SYSTEM_PROMPT.replace("{userPreference}", preferenceStr);
    }

    private String formatPreference(UserPreference preference) {
        if (preference == null) {
            return "暂无历史偏好";
        }

        StringBuilder sb = new StringBuilder();
        if (preference.getFavoriteFlavors() != null && !preference.getFavoriteFlavors().isEmpty()) {
            sb.append("喜欢的口味：").append(String.join("、", preference.getFavoriteFlavors())).append("\n");
        }
        if (preference.getFavoriteDishes() != null && !preference.getFavoriteDishes().isEmpty()) {
            sb.append("常点的菜品：").append(String.join("、", preference.getFavoriteDishes())).append("\n");
        }
        if (preference.getAverageBudget() != null) {
            sb.append("平均预算：").append(preference.getAverageBudget()).append("元\n");
        }
        if (preference.getAveragePeople() != null) {
            sb.append("平均用餐人数：").append(preference.getAveragePeople()).append("人\n");
        }

        return sb.length() > 0 ? sb.toString() : "暂无历史偏好";
    }
}
```

## 4. 数据模型设计

### 4.1 请求/响应模型

```java
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

### 4.2 快捷按钮类型

```java
public enum QuickActionType {
    RECOMMEND("推荐菜品", "RECOMMEND"),
    VIEW_CART("查看购物车", "VIEW_CART"),
    SUBMIT_ORDER("提交订单", "SUBMIT_ORDER"),
    QUERY_ORDER("查询订单", "QUERY_ORDER"),
    CLEAR_CART("清空购物车", "CLEAR_CART"),
    REORDER("再来一单", "REORDER");

    private final String label;
    private final String action;

    QuickActionType(String label, String action) {
        this.label = label;
        this.action = action;
    }

    public String getLabel() {
        return label;
    }

    public String getAction() {
        return action;
    }
}
```

## 5. 配置说明

### 5.1 依赖配置

在 `sky-server/pom.xml` 中添加：

```xml
<!-- Spring AI -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 通义千问 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-tongyi-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 5.2 应用配置

在 `application.yml` 中添加：

```yaml
spring:
  ai:
    tongyi:
      api-key: ${AI_API_KEY}
      chat:
        options:
          model: qwen-turbo
          temperature: 0.7
          max-tokens: 1000
```

### 5.3 环境变量

需要配置以下环境变量：

```bash
export AI_API_KEY=your_api_key_here
```

## 6. 使用示例

### 6.1 基本对话

**用户**：我想吃点辣的，两个人吃

**Agent**：好的，为您推荐以下辣味菜品：
1. 麻辣香锅 - ¥38（微辣/中辣/特辣）
2. 水煮鱼 - ¥45（麻辣鲜香）
3. 辣子鸡 - ¥32（香辣可口）

您想点哪些呢？

**快捷按钮**：[查看购物车] [提交订单]

### 6.2 场景化推荐

**用户**：两个人吃，预算50，想吃清淡的

**Agent**：根据您的需求，为您推荐：
1. 清炒时蔬 - ¥18（清淡爽口）
2. 白切鸡 - ¥28（原汁原味）
3. 冬瓜排骨汤 - ¥22（清淡滋养）

总价约¥48，符合您的预算。

**快捷按钮**：[查看购物车] [提交订单]

### 6.3 购物车管理

**用户**：把麻辣香锅加到购物车

**Agent**：已将麻辣香锅 x1 添加到购物车。

**快捷按钮**：[查看购物车] [提交订单]

### 6.4 订单管理

**用户**：提交订单

**Agent**：订单已提交成功！
- 订单号：202606090001
- 金额：¥38
- 预计送达时间：30分钟

**快捷按钮**：[查询订单] [再来一单]

## 7. 测试策略

### 7.1 单元测试

- 测试各个Tool的功能
- 测试Memory的读写
- 测试PromptTemplate的生成

### 7.2 集成测试

- 测试完整的对话流程
- 测试多轮对话的上下文保持
- 测试用户偏好的学习和应用

### 7.3 端到端测试

- 模拟用户完整点餐流程
- 测试异常场景的处理
- 测试性能和响应时间

## 8. 部署说明

### 8.1 开发环境

1. 配置AI_API_KEY环境变量
2. 启动Redis服务
3. 运行Spring Boot应用

### 8.2 生产环境

1. 配置生产环境的AI_API_KEY
2. 确保Redis服务可用
3. 配置日志级别
4. 监控Agent调用性能

## 9. 后续优化

### 9.1 功能优化

- 支持语音输入
- 支持图片识别（拍照点餐）
- 支持多轮对话的上下文理解
- 支持用户画像的深度学习

### 9.2 性能优化

- 缓存常用菜品推荐结果
- 优化大模型调用延迟
- 实现异步处理机制

### 9.3 用户体验优化

- 个性化推荐算法优化
- 快捷按钮的智能推荐
- 对话流程的自然度提升
