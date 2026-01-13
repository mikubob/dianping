package com.hmdp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class VoucherSeckillStressTest {
    private static final int TOTAL_REQUESTS = 100;  // 总请求数量
    private static final int CONCURRENT_THREADS = 1; // 并发线程数
    private static final String BASE_URL = "http://localhost:8081";
    private static final String LOGIN_PHONE = "13688668934"; // 使用数据库中存在的手机号
    private static final Long VOUCHER_ID = 10L; // 要秒杀的券ID
    private static final String PROVIDED_TOKEN = "8c42d735109248d995cf531f3d32ded6"; // 用户提供的令牌
    private static final int WARMUP_REQUESTS = 50; // 预热请求数
    
    // 统计类用于收集详细的测试数据
    static class Stats {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger unauthorizedCount = new AtomicInteger(0); // 未授权计数
        AtomicLong totalTime = new AtomicLong(0);
        List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger statusCode200 = new AtomicInteger(0);
        AtomicInteger statusCode401 = new AtomicInteger(0); // 未授权状态码
        AtomicInteger statusCode404 = new AtomicInteger(0);
        AtomicInteger statusCode500 = new AtomicInteger(0);
        AtomicInteger otherStatusCodes = new AtomicInteger(0);
        AtomicLong maxResponseTime = new AtomicLong(0);
        AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
        AtomicInteger seckillSuccessCount = new AtomicInteger(0); // 秒杀成功计数
        AtomicInteger seckillFailCount = new AtomicInteger(0); // 秒杀失败计数
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("开始秒杀券压力测试...");
        System.out.println("总请求数: " + TOTAL_REQUESTS);
        System.out.println("并发线程数: " + CONCURRENT_THREADS);
        System.out.println("目标URL: " + BASE_URL + "/voucher-order/seckill/" + VOUCHER_ID);
        System.out.println("预热请求数: " + WARMUP_REQUESTS);
        
        String token;
        if (PROVIDED_TOKEN != null && !PROVIDED_TOKEN.isEmpty()) {
            token = PROVIDED_TOKEN;
            System.out.println("使用提供的登录令牌: " + token.substring(0, Math.min(10, token.length())) + "...");
        } else {
            // 获取登录令牌
            token = getLoginToken();
            if (token == null || token.isEmpty()) {
                System.err.println("错误: 无法获取登录令牌，请确保用户存在且服务正常运行");
                return;
            }
            System.out.println("获取到登录令牌: " + token.substring(0, Math.min(10, token.length())) + "...");
        }
        
        // 检查服务是否可用
        if (!checkServiceAvailable(BASE_URL + "/voucher-order/seckill/" + VOUCHER_ID, token)) {
            System.err.println("错误: 目标服务不可用，请确保应用正在运行");
            return;
        }

        // 执行预热请求
        System.out.println("\n正在进行预热请求...");
        warmup(BASE_URL + "/voucher-order/seckill/" + VOUCHER_ID, token);
        
        // 等待一段时间让系统稳定
        Thread.sleep(2000);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);
        
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
                
        System.out.println("\n开始发送 " + TOTAL_REQUESTS + " 个并发秒杀请求...");
        long start = System.currentTimeMillis();
        
        // 初始化统计变量
        Stats stats = new Stats();

        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    // 构建带认证头的请求
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/voucher-order/seckill/" + VOUCHER_ID))
                            .header("Authorization", token) // 设置认证头，不带Bearer前缀
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .timeout(Duration.ofSeconds(10)) // 添加请求超时
                            .build();

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
                    
                    // 解析响应内容以区分秒杀成功还是失败
                    String responseBody = response.body();
                    if (statusCode == 200) {
                        stats.successCount.incrementAndGet();
                        stats.statusCode200.incrementAndGet();
                        // 检查响应内容判断秒杀是否真正成功
                        if (responseBody != null && responseBody.contains("\"success\":true")) {
                            stats.seckillSuccessCount.incrementAndGet();
                        } else if (responseBody != null && responseBody.contains("\"success\":false")) {
                            stats.seckillFailCount.incrementAndGet();
                        }
                    } else if (statusCode == 401) {
                        stats.unauthorizedCount.incrementAndGet();
                        stats.errorCount.incrementAndGet();
                        stats.statusCode401.incrementAndGet();
                    } else {
                        stats.errorCount.incrementAndGet();
                        switch (statusCode) {
                            case 404:
                                stats.statusCode404.incrementAndGet();
                                break;
                            case 500:
                                stats.statusCode500.incrementAndGet();
                                break;
                            default:
                                stats.otherStatusCodes.incrementAndGet();
                                break;
                        }
                    }
                } catch (Exception e) {
                    stats.errorCount.incrementAndGet();
                    System.err.println("请求异常 (请求ID: " + requestId + "): " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
            
            // 控制请求发送频率，避免瞬间冲击
            if (i % 10 == 0) {
                Thread.sleep(5); // 每发送10个请求暂停5毫秒
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
     * 获取登录令牌
     */
    private static String getLoginToken() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            
            // 先获取验证码
            HttpRequest codeRequest = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/user/code?phone=" + LOGIN_PHONE))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(10))
                    .build();
            
            HttpResponse<String> codeResponse = client.send(codeRequest, HttpResponse.BodyHandlers.ofString());
            if (codeResponse.statusCode() != 200) {
                System.out.println("获取验证码失败，状态码: " + codeResponse.statusCode());
                return null;
            }
            
            // 模拟等待验证码生成
            Thread.sleep(1000); // 等待1秒让验证码生成
            
            // 使用固定验证码登录（通常是6位数字）
            String loginPayload = "{\"phone\":\"" + LOGIN_PHONE + "\",\"code\":\"123456\",\"nickName\":\"stress_test\"}";
            HttpRequest loginRequest = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/user/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            
            HttpResponse<String> loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
            if (loginResponse.statusCode() != 200) {
                System.out.println("登录失败，状态码: " + loginResponse.statusCode());
                System.out.println("响应内容: " + loginResponse.body());
                return null;
            }
            
            // 解析登录响应以获取令牌
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode responseJson = objectMapper.readTree(loginResponse.body());
            
            if (responseJson.has("data") && responseJson.get("data").isTextual()) {
                return responseJson.get("data").asText();
            } else {
                System.out.println("未能从登录响应中提取令牌");
                System.out.println("响应内容: " + loginResponse.body());
                return null;
            }
        } catch (Exception e) {
            System.out.println("获取登录令牌时发生异常: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 执行预热请求
     */
    private static void warmup(String url, String token) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            
            for (int i = 0; i < WARMUP_REQUESTS; i++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Authorization", token)
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .timeout(Duration.ofSeconds(5))
                            .build();
                    
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
        long p95 = stats.responseTimes.size() > 0 && stats.responseTimes.size() >= 95 ? 
                stats.responseTimes.get((int)(stats.responseTimes.size() * 0.95) - 1) : 0;
        long p99 = stats.responseTimes.size() > 0 && stats.responseTimes.size() >= 99 ? 
                stats.responseTimes.get((int)(stats.responseTimes.size() * 0.99) - 1) : 0;
        long p999 = stats.responseTimes.size() > 0 && stats.responseTimes.size() >= 999 ? 
                stats.responseTimes.get((int)(stats.responseTimes.size() * 0.999) - 1) : 0;
        
        // 计算吞吐量 (每秒请求数)
        long testDuration = endTime - startTime;
        double throughput = testDuration > 0 ? (double) (stats.successCount.get() + stats.errorCount.get()) / (testDuration / 1000.0) : 0;
        
        System.out.println("\n==================== 秒杀券压力测试详细报告 ====================");
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
        System.out.println("  未授权请求数: " + stats.unauthorizedCount.get());
        
        System.out.println("\n秒杀结果统计:");
        System.out.println("  秒杀成功数: " + stats.seckillSuccessCount.get());
        System.out.println("  秒杀失败数: " + stats.seckillFailCount.get());
        
        System.out.println("\nHTTP状态码分布:");
        System.out.println("  200 OK: " + stats.statusCode200.get());
        System.out.println("  401 Unauthorized: " + stats.statusCode401.get());
        System.out.println("  404 Not Found: " + stats.statusCode404.get());
        System.out.println("  500 Internal Server Error: " + stats.statusCode500.get());
        if (stats.otherStatusCodes.get() > 0) {
            System.out.println("  其他状态码: " + stats.otherStatusCodes.get());
        }
        
        System.out.println("\n=========================================================");
    }
    
    /**
     * 检查目标服务是否可用
     */
    private static boolean checkServiceAvailable(String url, String token) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", token)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 || response.statusCode() == 401 || response.statusCode() == 400; // 200正常返回或401(需要登录)或400(参数错误)都认为服务可用
        } catch (Exception e) {
            return false;
        }
    }
}