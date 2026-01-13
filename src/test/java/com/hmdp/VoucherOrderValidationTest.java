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

/**
 * 专门测试一人一单限制和超卖问题的秒杀场景压力测试类
 * 该测试会验证：
 * 1. 同一用户不能重复下单（一人一单限制）
 * 2. 优惠券不会被超卖（库存控制）
 */
public class VoucherOrderValidationTest {
    private static final int TOTAL_REQUESTS_PER_USER = 10;  // 每个用户的请求数量
    private static final int CONCURRENT_THREADS = 10; // 并发线程数（模拟不同用户）
    private static final String BASE_URL = "http://localhost:8081";
    private static final String BASE_PHONE = "136886689"; // 基础手机号
    private static final Long VOUCHER_ID = 10L; // 要秒杀的券ID
    private static final int INITIAL_STOCK = 5; // 初始库存，设置一个小值便于测试超卖问题
    private static final int TEST_DURATION_SECONDS = 30; // 测试持续时间（秒）
    private static final String PROVIDED_TOKEN = "29a5e192cfa14baea4b7e1c2aea9e099"; // 用户提供的令牌

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
        AtomicInteger duplicateOrderFailures = new AtomicInteger(0); // 重复下单失败计数
        AtomicInteger outOfStockFailures = new AtomicInteger(0); // 库存不足失败计数
        AtomicInteger uniqueUsersAttempted = new AtomicInteger(0); // 尝试的不同用户数
        List<String> successfulOrders = Collections.synchronizedList(new ArrayList<>()); // 成功订单列表
        List<String> failedOrders = Collections.synchronizedList(new ArrayList<>()); // 失败订单列表
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("开始验证一人一单限制和超卖问题的秒杀测试...");
        System.out.println("每个用户的请求数: " + TOTAL_REQUESTS_PER_USER);
        System.out.println("并发用户数: " + CONCURRENT_THREADS);
        System.out.println("目标URL: " + BASE_URL + "/voucher-order/seckill/" + VOUCHER_ID);
        System.out.println("初始库存: " + INITIAL_STOCK);
        
        // 使用提供的令牌进行测试
        String token;
        if (PROVIDED_TOKEN != null && !PROVIDED_TOKEN.isEmpty()) {
            token = PROVIDED_TOKEN;
            System.out.println("使用提供的登录令牌: " + token.substring(0, Math.min(10, token.length())) + "...");
        } else {
            // 获取登录令牌
            String phone = BASE_PHONE + "34"; // 使用基础手机号
            token = getLoginToken(phone);
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

        // 运行"一人一单"测试 - 使用同一用户发起多个请求
        testOneUserMultipleRequests(token);
        
        // 运行多用户测试 - 验证库存控制
        testMultiUserStockControl();
    }

    /**
     * 测试同一用户发起多个请求的"一人一单"限制
     */
    private static void testOneUserMultipleRequests(String token) throws InterruptedException {
        System.out.println("\n==================== 开始一人一单功能测试 ====================");
        System.out.println("使用同一用户令牌发起 " + TOTAL_REQUESTS_PER_USER + " 个并发请求...");
        
        ExecutorService executor = Executors.newFixedThreadPool(TOTAL_REQUESTS_PER_USER);
        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS_PER_USER);
        
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
                
        long start = System.currentTimeMillis();
        
        // 初始化统计变量
        Stats stats = new Stats();

        for (int i = 0; i < TOTAL_REQUESTS_PER_USER; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    // 构建带认证头的请求
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/voucher-order/seckill/" + VOUCHER_ID))
                            .header("Authorization", "Bearer " + token) // 使用同一用户令牌
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
                            stats.successfulOrders.add("Request: " + requestId + ", Response: " + responseBody);
                        } else if (responseBody != null) {
                            // 即使状态码是200，但响应内容可能显示失败
                            if (responseBody.contains("无法重复抢购") || responseBody.contains("已经使用过")) {
                                stats.duplicateOrderFailures.incrementAndGet();
                                stats.seckillFailCount.incrementAndGet();
                                stats.failedOrders.add("Request: " + requestId + ", Reason: 重复下单, Response: " + responseBody);
                            } else if (responseBody.contains("抢空") || responseBody.contains("库存不足")) {
                                stats.outOfStockFailures.incrementAndGet();
                                stats.seckillFailCount.incrementAndGet();
                                stats.failedOrders.add("Request: " + requestId + ", Reason: 库存不足, Response: " + responseBody);
                            } else {
                                stats.seckillFailCount.incrementAndGet();
                                stats.failedOrders.add("Request: " + requestId + ", Reason: 其他原因, Response: " + responseBody);
                            }
                        }
                    } else if (statusCode == 401) {
                        stats.unauthorizedCount.incrementAndGet();
                        stats.errorCount.incrementAndGet();
                        stats.statusCode401.incrementAndGet();
                    } else {
                        stats.errorCount.incrementAndGet();
                        switch (statusCode) {
                            case 400:
                                // 可能是重复下单或其他业务错误
                                if (responseBody != null && (responseBody.contains("无法重复抢购") || responseBody.contains("已经使用过"))) {
                                    stats.duplicateOrderFailures.incrementAndGet();
                                    stats.seckillFailCount.incrementAndGet();
                                    stats.failedOrders.add("Request: " + requestId + ", Reason: 重复下单, Response: " + responseBody);
                                } else if (responseBody != null && (responseBody.contains("抢空") || responseBody.contains("库存不足"))) {
                                    stats.outOfStockFailures.incrementAndGet();
                                    stats.seckillFailCount.incrementAndGet();
                                    stats.failedOrders.add("Request: " + requestId + ", Reason: 库存不足, Response: " + responseBody);
                                }
                                stats.statusCode404.incrementAndGet();
                                break;
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
            if (i % 5 == 0) {
                Thread.sleep(2); // 每发送5个请求暂停2毫秒
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
        printDetailedStats(stats, start, end, "一人一单功能测试");
        
        // 验证一人一单功能
        validateOneUserOneOrderFeature(stats);
    }
    
    /**
     * 测试多用户场景下的库存控制（防超卖）
     */
    private static void testMultiUserStockControl() throws InterruptedException {
        System.out.println("\n==================== 开始多用户库存控制测试 ====================");
        System.out.println("使用 " + CONCURRENT_THREADS + " 个不同用户发起请求，初始库存: " + INITIAL_STOCK);
        
        // 获取多个用户的登录令牌
        String[] tokens = new String[CONCURRENT_THREADS];
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            String phone = BASE_PHONE + String.format("%02d", i + 34); // 使用不同的手机号
            tokens[i] = getLoginToken(phone);
            if (tokens[i] == null || tokens[i].isEmpty()) {
                System.err.println("错误: 无法为用户 " + phone + " 获取登录令牌");
                return;
            }
            System.out.println("用户 " + phone + " 获取到登录令牌: " + tokens[i].substring(0, Math.min(10, tokens[i].length())) + "...");
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_THREADS);
        
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
                
        long start = System.currentTimeMillis();
        
        // 初始化统计变量
        Stats stats = new Stats();

        for (int userIndex = 0; userIndex < CONCURRENT_THREADS; userIndex++) {
            final int userIdx = userIndex;
            final String userToken = tokens[userIdx];
            final String userPhone = BASE_PHONE + String.format("%02d", userIdx + 34);
            
            // 每个用户发送一个请求
            executor.submit(() -> {
                try {
                    // 构建带认证头的请求
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/voucher-order/seckill/" + VOUCHER_ID))
                            .header("Authorization", "Bearer " + userToken) // 不同用户使用不同令牌
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
                            stats.successfulOrders.add("User: " + userPhone + ", Response: " + responseBody);
                        } else if (responseBody != null) {
                            // 即使状态码是200，但响应内容可能显示失败
                            if (responseBody.contains("无法重复抢购") || responseBody.contains("已经使用过")) {
                                stats.duplicateOrderFailures.incrementAndGet();
                                stats.seckillFailCount.incrementAndGet();
                                stats.failedOrders.add("User: " + userPhone + ", Reason: 重复下单, Response: " + responseBody);
                            } else if (responseBody.contains("抢空") || responseBody.contains("库存不足")) {
                                stats.outOfStockFailures.incrementAndGet();
                                stats.seckillFailCount.incrementAndGet();
                                stats.failedOrders.add("User: " + userPhone + ", Reason: 库存不足, Response: " + responseBody);
                            } else {
                                stats.seckillFailCount.incrementAndGet();
                                stats.failedOrders.add("User: " + userPhone + ", Reason: 其他原因, Response: " + responseBody);
                            }
                        }
                    } else if (statusCode == 401) {
                        stats.unauthorizedCount.incrementAndGet();
                        stats.errorCount.incrementAndGet();
                        stats.statusCode401.incrementAndGet();
                    } else {
                        stats.errorCount.incrementAndGet();
                        switch (statusCode) {
                            case 400:
                                // 可能是重复下单或其他业务错误
                                if (responseBody != null && (responseBody.contains("无法重复抢购") || responseBody.contains("已经使用过"))) {
                                    stats.duplicateOrderFailures.incrementAndGet();
                                    stats.seckillFailCount.incrementAndGet();
                                    stats.failedOrders.add("User: " + userPhone + ", Reason: 重复下单, Response: " + responseBody);
                                } else if (responseBody != null && (responseBody.contains("抢空") || responseBody.contains("库存不足"))) {
                                    stats.outOfStockFailures.incrementAndGet();
                                    stats.seckillFailCount.incrementAndGet();
                                    stats.failedOrders.add("User: " + userPhone + ", Reason: 库存不足, Response: " + responseBody);
                                }
                                stats.statusCode404.incrementAndGet();
                                break;
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
                    System.err.println("请求异常 (用户: " + userPhone + "): " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
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
        printDetailedStats(stats, start, end, "多用户库存控制测试");
        
        // 验证库存控制功能
        validateStockControlFeature(stats);
    }
    
    /**
     * 获取登录令牌
     */
    private static String getLoginToken(String phone) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            
            // 先获取验证码
            HttpRequest codeRequest = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/user/code?phone=" + phone))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(10))
                    .build();
            
            HttpResponse<String> codeResponse = client.send(codeRequest, HttpResponse.BodyHandlers.ofString());
            if (codeResponse.statusCode() != 200) {
                System.out.println("获取验证码失败，状态码: " + codeResponse.statusCode() + " for phone: " + phone);
                return null;
            }
            
            // 模拟等待验证码生成
            Thread.sleep(1000); // 等待1秒让验证码生成
            
            // 使用固定验证码登录（通常是6位数字）
            String loginPayload = "{\"phone\":\"" + phone + "\",\"code\":\"123456\",\"nickName\":\"test_user_" + phone.substring(phone.length() - 2) + "\"}";
            HttpRequest loginRequest = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/user/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            
            HttpResponse<String> loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
            if (loginResponse.statusCode() != 200) {
                System.out.println("登录失败，状态码: " + loginResponse.statusCode() + " for phone: " + phone);
                System.out.println("响应内容: " + loginResponse.body());
                return null;
            }
            
            // 解析登录响应以获取令牌
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode responseJson = objectMapper.readTree(loginResponse.body());
            
            if (responseJson.has("data") && responseJson.get("data").isTextual()) {
                return responseJson.get("data").asText();
            } else {
                System.out.println("未能从登录响应中提取令牌 for phone: " + phone);
                System.out.println("响应内容: " + loginResponse.body());
                return null;
            }
        } catch (Exception e) {
            System.out.println("获取登录令牌时发生异常 for phone: " + phone + ": " + e.getMessage());
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
            
            for (int i = 0; i < 5; i++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Authorization", "Bearer " + token)
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
    private static void printDetailedStats(Stats stats, long startTime, long endTime, String testName) {
        DecimalFormat df = new DecimalFormat("#.##");
        
        // 对响应时间排序以计算百分位数
        Collections.sort(stats.responseTimes);
        
        long avgTime = stats.responseTimes.size() > 0 ? stats.totalTime.get() / stats.responseTimes.size() : 0;
        double successRate = (stats.successCount.get() + stats.errorCount.get()) > 0 ? 
                (double) stats.successCount.get() / (stats.successCount.get() + stats.errorCount.get()) * 100 : 0;
        
        // 计算百分位数
        long p95 = stats.responseTimes.size() > 0 ? 
                stats.responseTimes.get(Math.min((int)(stats.responseTimes.size() * 0.95), stats.responseTimes.size() - 1)) : 0;
        long p99 = stats.responseTimes.size() > 0 ? 
                stats.responseTimes.get(Math.min((int)(stats.responseTimes.size() * 0.99), stats.responseTimes.size() - 1)) : 0;
        long p999 = stats.responseTimes.size() > 0 ? 
                stats.responseTimes.get(Math.min((int)(stats.responseTimes.size() * 0.999), stats.responseTimes.size() - 1)) : 0;
        
        // 计算吞吐量 (每秒请求数)
        long testDuration = endTime - startTime;
        double throughput = testDuration > 0 ? (double) (stats.successCount.get() + stats.errorCount.get()) / (testDuration / 1000.0) : 0;
        
        System.out.println("\n==================== " + testName + " ====================");
        System.out.println("测试基本信息:");
        System.out.println("  测试耗时: " + testDuration + "ms (" + df.format(testDuration / 1000.0) + "s)");
        System.out.println("  总请求数: " + (stats.successCount.get() + stats.errorCount.get()));
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
        System.out.println("  重复下单失败数: " + stats.duplicateOrderFailures.get());
        System.out.println("  库存不足失败数: " + stats.outOfStockFailures.get());
        
        System.out.println("\nHTTP状态码分布:");
        System.out.println("  200 OK: " + stats.statusCode200.get());
        System.out.println("  401 Unauthorized: " + stats.statusCode401.get());
        System.out.println("  404 Not Found / 业务错误: " + stats.statusCode404.get());
        System.out.println("  500 Internal Server Error: " + stats.statusCode500.get());
        if (stats.otherStatusCodes.get() > 0) {
            System.out.println("  其他状态码: " + stats.otherStatusCodes.get());
        }
        
        System.out.println("\n=========================================================");
    }
    
    /**
     * 验证一人一单功能
     */
    private static void validateOneUserOneOrderFeature(Stats stats) {
        System.out.println("\n==================== 一人一单功能验证结果 ====================");
        
        int successfulOrders = stats.seckillSuccessCount.get();
        int duplicateFailures = stats.duplicateOrderFailures.get();
        
        if (successfulOrders == 1 && duplicateFailures > 0) {
            System.out.println("✓ 一人一单限制工作正常:");
            System.out.println("  - 成功下单: 1 次");
            System.out.println("  - 重复下单被阻止: " + duplicateFailures + " 次");
            System.out.println("  - 同一用户无法重复下单的限制有效");
        } else if (successfulOrders == 1 && duplicateFailures == 0) {
            System.out.println("? 一人一单限制: 仅成功下单1次，未检测到重复下单尝试（可能第一次请求就成功了）");
        } else if (successfulOrders > 1) {
            System.out.println("✗ 一人一单限制失效:");
            System.out.println("  - 成功下单: " + successfulOrders + " 次");
            System.out.println("  - 同一用户下了多笔订单，违反了一人一单原则");
        } else {
            System.out.println("? 一人一单限制: 未成功下单，可能库存已空或其它原因");
        }
        
        // 显示成功和失败的订单
        if (!stats.successfulOrders.isEmpty()) {
            System.out.println("\n成功订单详情:");
            for (String order : stats.successfulOrders) {
                System.out.println("  " + order);
            }
        }
        
        if (!stats.failedOrders.isEmpty()) {
            System.out.println("\n失败订单详情 (前5个):");
            for (int i = 0; i < Math.min(5, stats.failedOrders.size()); i++) {
                System.out.println("  " + stats.failedOrders.get(i));
            }
        }
        
        System.out.println("\n=================================================");
    }
    
    /**
     * 验证库存控制功能（防超卖）
     */
    private static void validateStockControlFeature(Stats stats) {
        System.out.println("\n==================== 库存控制功能验证结果 ====================");
        
        int successfulOrders = stats.seckillSuccessCount.get();
        int outOfStockFailures = stats.outOfStockFailures.get();
        
        System.out.println("库存控制验证:");
        System.out.println("  初始库存: " + INITIAL_STOCK);
        System.out.println("  成功订单数: " + successfulOrders);
        System.out.println("  库存不足失败数: " + outOfStockFailures);
        
        if (successfulOrders <= INITIAL_STOCK) {
            System.out.println("✓ 库存控制工作正常: 成功订单数(" + successfulOrders + ") <= 初始库存(" + INITIAL_STOCK + ")");
        } else {
            System.out.println("✗ 存在超卖问题: 成功订单数(" + successfulOrders + ") > 初始库存(" + INITIAL_STOCK + ")");
        }
        
        // 分析成功的订单是否超过了库存限制
        if (successfulOrders > INITIAL_STOCK) {
            System.out.println("! 警告: 检测到超卖现象，系统可能存在问题");
        } else if (successfulOrders == INITIAL_STOCK) {
            System.out.println("✓ 所有库存均已售出，没有超卖现象");
        } else {
            System.out.println("? 部分库存售出，没有超卖现象");
        }
        
        // 显示一些成功和失败的订单样例
        if (!stats.successfulOrders.isEmpty()) {
            System.out.println("\n成功订单样例 (前3个):");
            for (int i = 0; i < Math.min(3, stats.successfulOrders.size()); i++) {
                System.out.println("  " + stats.successfulOrders.get(i));
            }
        }
        
        if (!stats.failedOrders.isEmpty()) {
            System.out.println("\n失败订单样例 (前3个):");
            for (int i = 0; i < Math.min(3, stats.failedOrders.size()); i++) {
                System.out.println("  " + stats.failedOrders.get(i));
            }
        }
        
        System.out.println("\n=================================================");
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
                    .header("Authorization", "Bearer " + token)
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