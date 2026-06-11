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
        if (action == null || action.isEmpty()) {
            return "请指定操作类型";
        }
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
        if (arguments.get("addressId") == null) {
            return "请提供地址ID";
        }
        Long addressId = Long.valueOf(arguments.get("addressId").toString());
        AddressBook address = addressBookService.getById(addressId);
        return JSON.toJSONString(address);
    }
}
