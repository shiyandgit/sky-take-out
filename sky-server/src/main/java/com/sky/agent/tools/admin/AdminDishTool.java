package com.sky.agent.tools.admin;

import com.alibaba.fastjson.JSON;
import com.sky.agent.model.ToolDefinition;
import com.sky.agent.tools.Tool;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Category;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.service.CategoryService;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AdminDishTool implements Tool {

    @Autowired
    private DishService dishService;

    @Autowired
    private CategoryService categoryService;

    @Override
    public String getName() {
        return "manageAdminDish";
    }

    @Override
    public String getDescription() {
        return "管理菜品，包括查询菜品列表、搜索菜品、查看菜品详情、起售停售";
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
        action.setDescription("操作类型：search(搜索), detail(详情), enable(起售), disable(停售), listByCategory(按分类查), listCategories(查分类)");
        properties.put("action", action);

        ToolDefinition.Property dishId = new ToolDefinition.Property();
        dishId.setType("number");
        dishId.setDescription("菜品ID");
        properties.put("dishId", dishId);

        ToolDefinition.Property dishName = new ToolDefinition.Property();
        dishName.setType("string");
        dishName.setDescription("菜品名称（搜索用）");
        properties.put("dishName", dishName);

        ToolDefinition.Property categoryId = new ToolDefinition.Property();
        categoryId.setType("number");
        categoryId.setDescription("分类ID");
        properties.put("categoryId", categoryId);

        ToolDefinition.Property status = new ToolDefinition.Property();
        status.setType("number");
        status.setDescription("状态：1起售 0停售");
        properties.put("status", status);

        ToolDefinition.Property page = new ToolDefinition.Property();
        page.setType("number");
        page.setDescription("页码");
        properties.put("page", page);

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
        log.info("商家菜品操作: {}", action);

        switch (action) {
            case "search":
                return searchDishes(arguments);
            case "detail":
                return getDishDetail(arguments);
            case "enable":
                return enableDish(arguments);
            case "disable":
                return disableDish(arguments);
            case "listByCategory":
                return listByCategory(arguments);
            case "listCategories":
                return listCategories();
            default:
                return "未知操作: " + action;
        }
    }

    private String searchDishes(Map<String, Object> arguments) {
        DishPageQueryDTO dto = new DishPageQueryDTO();
        dto.setPage(arguments.get("page") != null ? Integer.parseInt(arguments.get("page").toString()) : 1);
        dto.setPageSize(10);
        if (arguments.get("dishName") != null) {
            dto.setName((String) arguments.get("dishName"));
        }
        if (arguments.get("categoryId") != null) {
            dto.setCategoryId(Integer.parseInt(arguments.get("categoryId").toString()));
        }
        if (arguments.get("status") != null) {
            dto.setStatus(Integer.parseInt(arguments.get("status").toString()));
        }
        PageResult result = dishService.pageQuery(dto);
        return JSON.toJSONString(result);
    }

    private String getDishDetail(Map<String, Object> arguments) {
        if (arguments.get("dishId") == null) {
            return "请提供菜品ID";
        }
        Long dishId = Long.valueOf(arguments.get("dishId").toString());
        DishVO dish = dishService.getByIdWithFlavor(dishId);
        return JSON.toJSONString(dish);
    }

    private String enableDish(Map<String, Object> arguments) {
        if (arguments.get("dishId") == null) {
            return "请提供菜品ID";
        }
        Long dishId = Long.valueOf(arguments.get("dishId").toString());
        dishService.startOrStop(1, dishId);
        return "菜品 " + dishId + " 已起售";
    }

    private String disableDish(Map<String, Object> arguments) {
        if (arguments.get("dishId") == null) {
            return "请提供菜品ID";
        }
        Long dishId = Long.valueOf(arguments.get("dishId").toString());
        dishService.startOrStop(0, dishId);
        return "菜品 " + dishId + " 已停售";
    }

    private String listByCategory(Map<String, Object> arguments) {
        if (arguments.get("categoryId") == null) {
            return "请提供分类ID";
        }
        Long categoryId = Long.valueOf(arguments.get("categoryId").toString());
        List<Dish> dishes = dishService.list(categoryId);
        return JSON.toJSONString(dishes);
    }

    private String listCategories() {
        List<Category> categories = categoryService.list(null);
        return JSON.toJSONString(categories);
    }
}
