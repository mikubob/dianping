package com.hmdp.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.service.IUploadService;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * <p>
 * 文件上传服务实现类 - 实现文件上传相关的具体业务逻辑
 * 提供图片上传、删除等核心功能的具体实现
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UploadServiceImpl implements IUploadService {

    /**
     * 上传博客图片
     * 此方法用于上传博客相关的图片文件，自动生成唯一的文件名并保存到指定目录
     *
     * @param image 上传的图片文件
     * @return 包含上传成功后的文件路径的结果对象
     */
    @Override
    public Result uploadImage(MultipartFile image) {
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename);
            // 保存文件
            image.transferTo(new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName));
            // 返回结果
            log.debug("文件上传成功，{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 删除博客图片
     * 此方法用于删除指定名称的博客图片文件
     *
     * @param filename 要删除的图片文件名
     * @return 成功或失败的结果
     */
    @Override
    public Result deleteBlogImg(String filename) {
        File file = new File(SystemConstants.IMAGE_UPLOAD_DIR, filename);
        if (file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    /**
     * 创建新的文件名
     * 根据原始文件名生成唯一的文件名，并按哈希值组织目录结构，防止单目录文件过多
     *
     * @param originalFilename 原始文件名
     * @return 生成的新文件路径
     */
    private String createNewFileName(String originalFilename) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        File dir = new File(SystemConstants.IMAGE_UPLOAD_DIR, StrUtil.format("/blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}