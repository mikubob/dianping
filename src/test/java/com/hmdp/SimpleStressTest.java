package com.hmdp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SimpleStressTest {
    private static final int TOTAL_REQUESTS = 1000;  // 总请求数量
    private static final int CONCURRENT_THREADS = 50; // 并发线程数
    private static final String TARGET_URL = "http://localhost:8081/shop/1";
    private static final int WARMUP_REQUESTS = 100; // 预热请求数
    
    // 统计类用于收集详细的测试数据
    static class Stats {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalTime = new AtomicLong(0);
        List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger statusCode200 = new AtomicInteger(0);
        AtomicInteger statusCode404 = new AtomicInteger(0);
        AtomicInteger statusCode500 = new AtomicInteger(0);
        AtomicInteger otherStatusCodes = new AtomicInteger(0);
        AtomicLong maxResponseTime = new AtomicLong(0);
        AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("开始压力测试...");
        System.out.println("总请求数: " + TOTAL_REQUESTS);
        System.out.println("并发线程数: " + CONCURRENT_THREADS);
        System.out.println("目标URL: " + TARGET_URL);
        System.out.println("预热请求数: " + WARMUP_REQUESTS);
        
        // 检查服务是否可用
        if (!checkServiceAvailable()) {
            System.err.println("错误: 目标服务不可用，请确保应用正在运行在 " + TARGET_URL);
            return;
        }

        // 执行预热请求
        System.out.println("\n正在进行预热请求...");
        warmup();
        
        // 等待一段时间让系统稳定
        Thread.sleep(2000);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);
        
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
                
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TARGET_URL))
                .GET()
                .timeout(java.time.Duration.ofSeconds(10)) // 添加请求超时
                .build();

        System.out.println("\n开始发送 " + TOTAL_REQUESTS + " 个并发请求...");
        long start = System.currentTimeMillis();
        
        // 初始化统计变量
        Stats stats = new Stats();

        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            executor.submit(() -> {
                try {
                    long reqStart = System.currentTimeMillis();
                    // 发送请求
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    long reqEnd = System.currentTimeMillis();
                    
                    int statusCode = response.statusCode();
                    long reqTime = reqEnd - reqStart;
                    
                    // 更新统计信息
                    stats.responseTimes.add(reqTime);
                    stats.totalTime.addAndGet(reqTime);
                    
                    // 记录最大最小响应时间
                    stats.maxResponseTime.set(Math.max(stats.maxResponseTime.get(), reqTime));
                    if (reqTime < stats.minResponseTime.get()) {
                        stats.minResponseTime.set(reqTime);
                    }
                    
                    switch (statusCode) {
                        case 200:
                            stats.successCount.incrementAndGet();
                            stats.statusCode200.incrementAndGet();
                            break;
                        case 404:
                            stats.errorCount.incrementAndGet();
                            stats.statusCode404.incrementAndGet();
                            break;
                        case 500:
                            stats.errorCount.incrementAndGet();
                            stats.statusCode500.incrementAndGet();
                            break;
                        default:
                            stats.errorCount.incrementAndGet();
                            stats.otherStatusCodes.incrementAndGet();
                            break;
                    }
                } catch (Exception e) {
                    stats.errorCount.incrementAndGet();
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
        
        // 输出详细统计结果
        printDetailedStats(stats, start, end);
    }
    
    /**
     * 执行预热请求
     */
    private static void warmup() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TARGET_URL))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();

            for (int i = 0; i < WARMUP_REQUESTS; i++) {
                try {
                    client.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {
                    // 忽略预热过程中的异常
                }
            }
        } catch (Exception e) {
            System.out.println("预热请求出现异常: " + e.getMessage());
        }
    }
    
    /**
     * 输出详细统计结果
     */
    private static void printDetailedStats(Stats stats, long startTime, long endTime) {
        DecimalFormat df = new DecimalFormat("#.##");
        
        // 对响应时间排序以计算百分位数
        Collections.sort(stats.responseTimes);
        
        long avgTime = stats.responseTimes.size() > 0 ? stats.totalTime.get() / stats.responseTimes.size() : 0;
        double successRate = (stats.successCount.get() + stats.errorCount.get()) > 0 ? 
                (double) stats.successCount.get() / (stats.successCount.get() + stats.errorCount.get()) * 100 : 0;
        
        // 计算百分位数
        long p95 = stats.responseTimes.size() > 0 ? stats.responseTimes.get((int)(stats.responseTimes.size() * 0.95)) : 0;
        long p99 = stats.responseTimes.size() > 0 ? stats.responseTimes.get((int)(stats.responseTimes.size() * 0.99)) : 0;
        long p999 = stats.responseTimes.size() > 0 ? stats.responseTimes.get((int)(stats.responseTimes.size() * 0.999)) : 0;
        
        // 计算吞吐量 (每秒请求数)
        long testDuration = endTime - startTime;
        double throughput = testDuration > 0 ? (double) (stats.successCount.get() + stats.errorCount.get()) / (testDuration / 1000.0) : 0;
        
        System.out.println("\n==================== 压力测试详细报告 ====================");
        System.out.println("测试基本信息:");
        System.out.println("  测试耗时: " + testDuration + "ms (" + df.format(testDuration / 1000.0) + "s)");
        System.out.println("  总请求数: " + (stats.successCount.get() + stats.errorCount.get()));
        System.out.println("  并发线程数: " + CONCURRENT_THREADS);
        System.out.println("  吞吐量: " + df.format(throughput) + " req/s");
        
        System.out.println("\n响应时间统计:");
        System.out.println("  平均响应时间: " + avgTime + "ms");
        System.out.println("  最小响应时间: " + (stats.minResponseTime.get() == Long.MAX_VALUE ? 0 : stats.minResponseTime.get()) + "ms");
        System.out.println("  最大响应时间: " + stats.maxResponseTime.get() + "ms");
        System.out.println("  95th percentile: " + p95 + "ms");
        System.out.println("  99th percentile: " + p99 + "ms");
        System.out.println("  99.9th percentile: " + p999 + "ms");
        
        System.out.println("\n请求结果统计:");
        System.out.println("  成功请求数: " + stats.successCount.get() + " (" + df.format(successRate) + "%)");
        System.out.println("  失败请求数: " + stats.errorCount.get() + " (" + df.format(100 - successRate) + "%)");
        
        System.out.println("\nHTTP状态码分布:");
        System.out.println("  200 OK: " + stats.statusCode200.get());
        System.out.println("  404 Not Found: " + stats.statusCode404.get());
        System.out.println("  500 Internal Server Error: " + stats.statusCode500.get());
        if (stats.otherStatusCodes.get() > 0) {
            System.out.println("  其他状态码: " + stats.otherStatusCodes.get());
        }
        
        System.out.println("\n=======================================================");
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