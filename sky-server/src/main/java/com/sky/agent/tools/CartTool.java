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
        if (action == null || action.isEmpty()) {
            return "请指定操作类型";
        }
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
        if (arguments.get("dishId") == null) {
            return "请提供菜品ID";
        }
        Long dishId = Long.valueOf(arguments.get("dishId").toString());
        String dishName = (String) arguments.get("dishName");
        Integer quantity = arguments.get("quantity") != null ?
            Integer.valueOf(arguments.get("quantity").toString()) : 1;

        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setDishId(dishId);

        for (int i = 0; i < quantity; i++) {
            shoppingCartService.addShoppingCart(dto);
        }

        return String.format("已将 %s x%d 添加到购物车", dishName, quantity);
    }

    private String removeFromCart(Map<String, Object> arguments) {
        if (arguments.get("dishId") == null) {
            return "请提供菜品ID";
        }
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
