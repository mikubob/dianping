package com.hmdp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SimpleStressTest {
    public static void main(String[] args) throws InterruptedException {
        // 1. 模拟 JMeter 的 1000 个线程
        int threadCount = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // HTTP 客户端
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8081/shop/1"))
                .GET()
                .build();

        System.out.println("开始模拟 " + threadCount + " 个并发请求...");
        long start = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // 发送请求
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    // 打印非 200 的错误（避免刷屏）
                    if (response.statusCode() != 200) {
                        System.err.println("请求失败: " + response.statusCode());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        // 关闭线程池并等待所有任务完成
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        long end = System.currentTimeMillis();
        System.out.println("测试结束，耗时: " + (end - start) + "ms");
    }
}