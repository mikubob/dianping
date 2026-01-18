package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {

    @Resource
    private IUploadService uploadService;

    /**
     * 上传博客图片
     * 此接口用于上传博客相关的图片文件，自动生成唯一的文件名并保存到指定目录
     * @param image 上传的图片文件
     * @return 包含上传成功后的文件路径的结果对象
     */
    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        return uploadService.uploadImage(image);
    }

    /**
     * 删除博客图片
     * 此接口用于删除指定名称的博客图片文件
     * @param filename 要删除的图片文件名
     * @return 成功或失败的结果
     */
    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        return uploadService.deleteBlogImg(filename);
    }
}
