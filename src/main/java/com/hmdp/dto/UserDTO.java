package com.hmdp.dto;

import lombok.Data;

/**
 * 用户数据传输对象 - 用于在系统间传递用户基本信息
 * 只包含用户的ID、昵称和头像等公开信息，不包含敏感信息
 */
@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
}
