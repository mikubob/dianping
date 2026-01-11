package com.hmdp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SimpleStressTest {
    private static final int TOTAL_REQUESTS = 1000;  // 总请求数量
    private static final int CONCURRENT_THREADS = 50; // 并发线程数
    private static final String TARGET_URL = "http://localhost:8081/shop/1";

    public static void main(String[] args) throws InterruptedException {
        System.out.println("开始压力测试...");
        System.out.println("总请求数: " + TOTAL_REQUESTS);
        System.out.println("并发线程数: " + CONCURRENT_THREADS);
        System.out.println("目标URL: " + TARGET_URL);
        
        // 检查服务是否可用
        if (!checkServiceAvailable()) {
            System.err.println("错误: 目标服务不可用，请确保应用正在运行在 " + TARGET_URL);
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TARGET_URL))
                .GET()
                .timeout(java.time.Duration.ofSeconds(10)) // 添加请求超时
                .build();

        System.out.println("开始发送 " + TOTAL_REQUESTS + " 个并发请求...");
        long start = System.currentTimeMillis();
        
        // 统计变量
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalTime = new AtomicLong(0);

        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            executor.submit(() -> {
                try {
                    long reqStart = System.currentTimeMillis();
                    // 发送请求
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    long reqEnd = System.currentTimeMillis();
                    
                    int statusCode = response.statusCode();
                    long reqTime = reqEnd - reqStart;
                    
                    totalTime.addAndGet(reqTime);
                    
                    if (statusCode == 200) {
                        successCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                        System.err.println("请求失败: " + statusCode + ", 响应时间: " + reqTime + "ms");
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    System.err.println("请求异常: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
            
            // 控制请求发送频率，避免瞬间冲击
            if (i % 10 == 0) {
                Thread.sleep(10); // 每发送10个请求暂停10毫秒
            }
        }

        // 等待所有请求完成
        latch.await(5, TimeUnit.MINUTES);
        
        // 关闭线程池
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow();
        }

        long end = System.currentTimeMillis();
        
        // 输出统计结果
        long avgTime = TOTAL_REQUESTS > 0 ? totalTime.get() / TOTAL_REQUESTS : 0;
        double successRate = TOTAL_REQUESTS > 0 ? (double) successCount.get() / TOTAL_REQUESTS * 100 : 0;
        
        System.out.println("\n========== 测试结果 ==========");
        System.out.println("测试耗时: " + (end - start) + "ms");
        System.out.println("总请求数: " + TOTAL_REQUESTS);
        System.out.println("成功请求数: " + successCount.get());
        System.out.println("失败请求数: " + errorCount.get());
        System.out.println("成功率: " + String.format("%.2f%%", successRate));
        System.out.println("平均响应时间: " + avgTime + "ms");
        System.out.println("=============================");
    }
    
    /**
     * 检查目标服务是否可用
     */
    private static boolean checkServiceAvailable() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TARGET_URL))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET()
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 || response.statusCode() == 404; // 200正常返回或404(资源不存在但服务正常)都认为服务可用
        } catch (Exception e) {
            return false;
        }
    }
}