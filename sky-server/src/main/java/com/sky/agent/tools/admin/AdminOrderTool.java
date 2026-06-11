package com.sky.agent.tools.admin;

import com.alibaba.fastjson.JSON;
import com.sky.agent.model.ToolDefinition;
import com.sky.agent.tools.Tool;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class AdminOrderTool implements Tool {

    @Autowired
    private OrderService orderService;

    @Override
    public String getName() {
        return "manageAdminOrder";
    }

    @Override
    public String getDescription() {
        return "管理订单，包括搜索订单、接单、拒单、取消、派送、完成、统计";
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
        action.setDescription("操作类型：search(搜索), statistics(统计), detail(详情), confirm(接单), reject(拒单), cancel(取消), deliver(派送), complete(完成)");
        properties.put("action", action);

        ToolDefinition.Property orderId = new ToolDefinition.Property();
        orderId.setType("number");
        orderId.setDescription("订单ID");
        properties.put("orderId", orderId);

        ToolDefinition.Property status = new ToolDefinition.Property();
        status.setType("number");
        status.setDescription("订单状态：1待付款 2待接单 3已接单 4派送中 5已完成 6已取消");
        properties.put("status", status);

        ToolDefinition.Property reason = new ToolDefinition.Property();
        reason.setType("string");
        reason.setDescription("拒单或取消原因");
        properties.put("reason", reason);

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
        log.info("商家订单操作: {}", action);

        switch (action) {
            case "search":
                return searchOrders(arguments);
            case "statistics":
                return getStatistics();
            case "detail":
                return getOrderDetail(arguments);
            case "confirm":
                return confirmOrder(arguments);
            case "reject":
                return rejectOrder(arguments);
            case "cancel":
                return cancelOrder(arguments);
            case "deliver":
                return deliverOrder(arguments);
            case "complete":
                return completeOrder(arguments);
            default:
                return "未知操作: " + action;
        }
    }

    private String searchOrders(Map<String, Object> arguments) {
        OrdersPageQueryDTO dto = new OrdersPageQueryDTO();
        dto.setPage(arguments.get("page") != null ? Integer.parseInt(arguments.get("page").toString()) : 1);
        dto.setPageSize(10);
        if (arguments.get("status") != null) {
            dto.setStatus(Integer.parseInt(arguments.get("status").toString()));
        }
        PageResult result = orderService.conditionSearch(dto);
        return JSON.toJSONString(result);
    }

    private String getStatistics() {
        OrderStatisticsVO stats = orderService.statistics();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("待接单", stats.getToBeConfirmed());
        result.put("待派送", stats.getConfirmed());
        result.put("派送中", stats.getDeliveryInProgress());
        return JSON.toJSONString(result);
    }

    private String getOrderDetail(Map<String, Object> arguments) {
        if (arguments.get("orderId") == null) {
            return "请提供订单ID";
        }
        Long orderId = Long.valueOf(arguments.get("orderId").toString());
        OrderVO order = orderService.details(orderId);
        return JSON.toJSONString(order);
    }

    private String confirmOrder(Map<String, Object> arguments) {
        if (arguments.get("orderId") == null) {
            return "请提供订单ID";
        }
        Long orderId = Long.valueOf(arguments.get("orderId").toString());
        OrdersConfirmDTO dto = new OrdersConfirmDTO();
        dto.setId(orderId);
        try {
            orderService.confirm(dto);
            return "订单 " + orderId + " 已接单";
        } catch (Exception e) {
            return "接单失败: " + e.getMessage();
        }
    }

    private String rejectOrder(Map<String, Object> arguments) {
        if (arguments.get("orderId") == null) {
            return "请提供订单ID";
        }
        Long orderId = Long.valueOf(arguments.get("orderId").toString());
        String reason = (String) arguments.getOrDefault("reason", "商家拒单");
        OrdersRejectionDTO dto = new OrdersRejectionDTO();
        dto.setId(orderId);
        dto.setRejectionReason(reason);
        try {
            orderService.rejection(dto);
            return "订单 " + orderId + " 已拒单，原因: " + reason;
        } catch (Exception e) {
            return "拒单失败: " + e.getMessage();
        }
    }

    private String cancelOrder(Map<String, Object> arguments) {
        if (arguments.get("orderId") == null) {
            return "请提供订单ID";
        }
        Long orderId = Long.valueOf(arguments.get("orderId").toString());
        String reason = (String) arguments.getOrDefault("reason", "商家取消");
        OrdersCancelDTO dto = new OrdersCancelDTO();
        dto.setId(orderId);
        dto.setCancelReason(reason);
        try {
            orderService.cancel(dto);
            return "订单 " + orderId + " 已取消";
        } catch (Exception e) {
            return "取消失败: " + e.getMessage();
        }
    }

    private String deliverOrder(Map<String, Object> arguments) {
        if (arguments.get("orderId") == null) {
            return "请提供订单ID";
        }
        Long orderId = Long.valueOf(arguments.get("orderId").toString());
        try {
            orderService.delivery(orderId);
            return "订单 " + orderId + " 已派送";
        } catch (Exception e) {
            return "派送失败: " + e.getMessage();
        }
    }

    private String completeOrder(Map<String, Object> arguments) {
        if (arguments.get("orderId") == null) {
            return "请提供订单ID";
        }
        Long orderId = Long.valueOf(arguments.get("orderId").toString());
        try {
            orderService.complete(orderId);
            return "订单 " + orderId + " 已完成";
        } catch (Exception e) {
            return "完成失败: " + e.getMessage();
        }
    }
}
