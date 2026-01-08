package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * 滚动查询结果类 - 用于滚动分页查询的返回结果
 * 包含数据列表、最小时间戳和偏移量，适用于时间轴滚动加载场景
 */
@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
