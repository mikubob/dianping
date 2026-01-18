package com.hmdp.service;

import com.hmdp.dto.Result;
import org.springframework.web.multipart.MultipartFile;

/**
 * <p>
 * 文件上传服务接口 - 定义文件上传相关的业务操作方法
 * 提供图片上传、删除等核心功能
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUploadService {

    /**
     * 上传博客图片
     * 此方法用于上传博客相关的图片文件，自动生成唯一的文件名并保存到指定目录
     *
     * @param image 上传的图片文件
     * @return 包含上传成功后的文件路径的结果对象
     */
    Result uploadImage(MultipartFile image);

    /**
     * 删除博客图片
     * 此方法用于删除指定名称的博客图片文件
     *
     * @param filename 要删除的图片文件名
     * @return 成功或失败的结果
     */
    Result deleteBlogImg(String filename);
}