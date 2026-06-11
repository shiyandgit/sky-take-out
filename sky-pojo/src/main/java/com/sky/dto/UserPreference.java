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
