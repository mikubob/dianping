package com.hmdp.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScrollResult {

    private List<?> list;// 数据
    private Long minTime;//时间戳
    private Integer offset;//游标
}
