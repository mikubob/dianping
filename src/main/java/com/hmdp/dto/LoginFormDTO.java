package com.hmdp.dto;

import lombok.Data;

/**
 * 登录表单数据传输对象 - 封装用户登录所需的信息
 * 支持手机号+验证码登录或手机号+密码登录两种方式
 */
@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
