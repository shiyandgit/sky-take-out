package com.sky.agent.tools.admin;

import com.alibaba.fastjson.JSON;
import com.sky.agent.model.ToolDefinition;
import com.sky.agent.tools.Tool;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@Slf4j
public class BusinessReportTool implements Tool {

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private ReportService reportService;

    @Override
    public String getName() {
        return "queryBusinessData";
    }

    @Override
    public String getDescription() {
        return "查询经营数据，包括营业额、订单量、客单价、销量排名等";
    }

    @Override
    public ToolDefinition getDefinition() {
        ToolDefinition def = new ToolDefinition();
        ToolDefinition.Function func = new ToolDefinition.Function();
        func.setName(getName());
        func.setDescription(getDescription());

        ToolDefinition.Parameters params = new ToolDefinition.Parameters();
        Map<String, ToolDefinition.Property> properties = new HashMap<>();

        ToolDefinition.Property dataType = new ToolDefinition.Property();
        dataType.setType("string");
        dataType.setDescription("数据类型：today(今日数据), turnover(营业额趋势), orderStats(订单统计), top10(销量排行), overview(总览)");
        properties.put("dataType", dataType);

        ToolDefinition.Property beginDate = new ToolDefinition.Property();
        beginDate.setType("string");
        beginDate.setDescription("开始日期，格式：yyyy-MM-dd");
        properties.put("beginDate", beginDate);

        ToolDefinition.Property endDate = new ToolDefinition.Property();
        endDate.setType("string");
        endDate.setDescription("结束日期，格式：yyyy-MM-dd");
        properties.put("endDate", endDate);

        params.setProperties(properties);
        func.setParameters(params);
        def.setFunction(func);

        return def;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String dataType = (String) arguments.get("dataType");
        if (dataType == null || dataType.isEmpty()) {
            return "请指定数据类型，可选：today、turnover、orderStats、top10、overview";
        }
        log.info("查询经营数据，类型: {}", dataType);

        switch (dataType) {
            case "today":
                return getTodayData();
            case "turnover":
                return getTurnover(arguments);
            case "orderStats":
                return getOrderStats(arguments);
            case "top10":
                return getTop10(arguments);
            case "overview":
                return getOverview();
            default:
                return getTodayData();
        }
    }

    private String getTodayData() {
        LocalDateTime begin = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        BusinessDataVO data = workspaceService.getBusinessData(begin, end);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("日期", LocalDate.now().toString());
        result.put("营业额", data.getTurnover());
        result.put("有效订单数", data.getValidOrderCount());
        result.put("订单完成率", data.getOrderCompletionRate());
        result.put("平均客单价", data.getUnitPrice());
        result.put("新增用户数", data.getNewUsers());
        return JSON.toJSONString(result);
    }

    private String getTurnover(Map<String, Object> arguments) {
        String beginStr = (String) arguments.get("beginDate");
        String endStr = (String) arguments.get("endDate");
        LocalDate begin = beginStr != null ? LocalDate.parse(beginStr) : LocalDate.now().minusDays(7);
        LocalDate end = endStr != null ? LocalDate.parse(endStr) : LocalDate.now();
        TurnoverReportVO vo = reportService.getTurnoverStatistics(begin, end);
        return JSON.toJSONString(vo);
    }

    private String getOrderStats(Map<String, Object> arguments) {
        String beginStr = (String) arguments.get("beginDate");
        String endStr = (String) arguments.get("endDate");
        LocalDate begin = beginStr != null ? LocalDate.parse(beginStr) : LocalDate.now().minusDays(7);
        LocalDate end = endStr != null ? LocalDate.parse(endStr) : LocalDate.now();
        return JSON.toJSONString(reportService.getOrderStatistics(begin, end));
    }

    private String getTop10(Map<String, Object> arguments) {
        String beginStr = (String) arguments.get("beginDate");
        String endStr = (String) arguments.get("endDate");
        LocalDate begin = beginStr != null ? LocalDate.parse(beginStr) : LocalDate.now().minusDays(30);
        LocalDate end = endStr != null ? LocalDate.parse(endStr) : LocalDate.now();
        SalesTop10ReportVO vo = reportService.getSalesTop10(begin, end);

        // 解析为可读格式
        String[] names = vo.getNameList() != null ? vo.getNameList().split(",") : new String[0];
        String[] numbers = vo.getNumberList() != null ? vo.getNumberList().split(",") : new String[0];

        List<Map<String, Object>> topList = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("排名", i + 1);
            item.put("菜品", names[i].trim());
            item.put("销量", i < numbers.length ? numbers[i].trim() : "0");
            topList.add(item);
        }
        return JSON.toJSONString(topList);
    }

    private String getOverview() {
        OrderOverViewVO orderView = workspaceService.getOrderOverView();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("待接单", orderView.getWaitingOrders());
        result.put("待派送", orderView.getDeliveredOrders());
        result.put("已完成", orderView.getCompletedOrders());
        result.put("已取消", orderView.getCancelledOrders());
        result.put("全部订单", orderView.getAllOrders());
        return JSON.toJSONString(result);
    }
}
