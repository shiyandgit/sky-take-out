package com.sky.agent.tools;

import com.alibaba.fastjson.JSON;
import com.sky.agent.model.ToolDefinition;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
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
        if (action == null || action.isEmpty()) {
            return "请指定操作类型";
        }
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

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(addressId);
        dto.setRemark(remark);

        OrderSubmitVO result = orderService.submitOrder(dto);
        return JSON.toJSONString(result);
    }

    private String queryOrder(Map<String, Object> arguments) {
        if (arguments.get("orderId") == null) {
            return "请提供订单ID";
        }
        Long orderId = Long.valueOf(arguments.get("orderId").toString());
        OrderVO order = orderService.details(orderId);
        return JSON.toJSONString(order);
    }

    private String cancelOrder(Map<String, Object> arguments) {
        if (arguments.get("orderId") == null) {
            return "请提供订单ID";
        }
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
