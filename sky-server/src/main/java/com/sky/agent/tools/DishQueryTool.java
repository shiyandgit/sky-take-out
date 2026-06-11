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
import java.util.*;
import java.util.stream.Collectors;

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

        if (category != null && !category.isEmpty()) {
            dishes = filterByCategory(dishes, category);
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
            .collect(Collectors.toList());
    }

    private List<DishVO> filterByCategory(List<DishVO> dishes, String category) {
        return dishes.stream()
            .filter(dish -> dish.getCategoryName() != null
                         && dish.getCategoryName().contains(category))
            .collect(Collectors.toList());
    }

    private List<DishVO> filterByPriceRange(List<DishVO> dishes, String priceRange) {
        try {
            String[] parts = priceRange.split("-");
            BigDecimal min = new BigDecimal(parts[0].replaceAll("[^0-9.]", ""));
            BigDecimal max = parts.length > 1 ? new BigDecimal(parts[1].replaceAll("[^0-9.]", "")) : BigDecimal.valueOf(Double.MAX_VALUE);
            return dishes.stream()
                .filter(dish -> dish.getPrice().compareTo(min) >= 0
                             && dish.getPrice().compareTo(max) <= 0)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("价格范围解析失败: {}, 返回未过滤结果", priceRange);
            return dishes;
        }
    }
}
