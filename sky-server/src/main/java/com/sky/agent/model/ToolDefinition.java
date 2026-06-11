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
