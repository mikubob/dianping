package com.hmdp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 高级压力测试工具类 - 提供全面的性能测试和统计分析功能
 * 包含详细的性能指标、多维度统计、实时监控等功能
 */
public class AdvancedStressTest {
    private static final DecimalFormat DF = new DecimalFormat("#.##");

    // 测试配置参数
    public static class TestConfig {
        public final String url;
        public final int totalRequests;
        public final int concurrentThreads;
        public final int rampUpPeriod; // 预热时间（秒）
        public final int maxRps; // 最大请求速率（requests per second）
        public final boolean enableRealtimeMonitor; // 是否启用实时监控
        public final String requestMethod;
        public final Map<String, String> headers;
        public final String requestBody;

        public TestConfig(String url, int totalRequests, int concurrentThreads, int rampUpPeriod, int maxRps, 
                         boolean enableRealtimeMonitor, String requestMethod, Map<String, String> headers, String requestBody) {
            this.url = url;
            this.totalRequests = totalRequests;
            this.concurrentThreads = concurrentThreads;
            this.rampUpPeriod = rampUpPeriod;
            this.maxRps = maxRps;
            this.enableRealtimeMonitor = enableRealtimeMonitor;
            this.requestMethod = requestMethod;
            this.headers = headers;
            this.requestBody = requestBody;
        }
        
        public static class Builder {
            private String url = "http://localhost:8081/shop/1";
            private int totalRequests = 1000;
            private int concurrentThreads = 50;
            private int rampUpPeriod = 5;
            private int maxRps = 0; // 0表示无限制
            private boolean enableRealtimeMonitor = true;
            private String requestMethod = "GET";
            private Map<String, String> headers = Map.of();
            private String requestBody = "";

            public Builder url(String url) { this.url = url; return this; }
            public Builder totalRequests(int totalRequests) { this.totalRequests = totalRequests; return this; }
            public Builder concurrentThreads(int concurrentThreads) { this.concurrentThreads = concurrentThreads; return this; }
            public Builder rampUpPeriod(int rampUpPeriod) { this.rampUpPeriod = rampUpPeriod; return this; }
            public Builder maxRps(int maxRps) { this.maxRps = maxRps; return this; }
            public Builder enableRealtimeMonitor(boolean enable) { this.enableRealtimeMonitor = enable; return this; }
            public Builder requestMethod(String method) { this.requestMethod = method; return this; }
            public Builder headers(Map<String, String> headers) { this.headers = headers; return this; }
            public Builder requestBody(String body) { this.requestBody = body; return this; }
            
            public TestConfig build() {
                return new TestConfig(url, totalRequests, concurrentThreads, rampUpPeriod, maxRps, 
                                    enableRealtimeMonitor, requestMethod, headers, requestBody);
            }
        }
    }

    // 统计数据类
    public static class TestStats {
        public final AtomicInteger successCount = new AtomicInteger(0);
        public final AtomicInteger errorCount = new AtomicInteger(0);
        public final AtomicLong totalTime = new AtomicLong(0);
        public final List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        public final Map<Integer, AtomicInteger> statusCodeCounts = 
            Map.of(200, new AtomicInteger(0), 404, new AtomicInteger(0), 500, new AtomicInteger(0));
        public final AtomicLong maxResponseTime = new AtomicLong(0);
        public final AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
        public final AtomicLong totalBytesReceived = new AtomicLong(0);
        public final AtomicInteger timeoutCount = new AtomicInteger(0);
        public final AtomicInteger connectionErrorCount = new AtomicInteger(0);
        public final List<Long> timestamps = Collections.synchronizedList(new ArrayList<>()); // 记录每个请求的时间戳
    }

    // 实时监控数据
    public static class RealtimeStats {
        public volatile int activeConnections = 0;
        public volatile double currentRps = 0.0;
        public volatile int lastSecondRequests = 0;
        public volatile long lastUpdateTimestamp = System.currentTimeMillis();
    }

    public static void main(String[] args) throws InterruptedException {
        // 创建测试配置
        TestConfig config = new TestConfig.Builder()
                .url("http://localhost:8081/shop/1")
                .totalRequests(2000)
                .concurrentThreads(100)
                .rampUpPeriod(10)
                .maxRps(200)
                .enableRealtimeMonitor(true)
                .build();

        runStressTest(config);
    }

    /**
     * 运行压力测试
     */
    public static void runStressTest(TestConfig config) throws InterruptedException {
        System.out.println("=".repeat(80));
        System.out.println("高级压力测试开始");
        System.out.println("=".repeat(80));
        System.out.printf("目标URL: %s%n", config.url);
        System.out.printf("总请求数: %d%n", config.totalRequests);
        System.out.printf("并发线程数: %d%n", config.concurrentThreads);
        System.out.printf("预热时间: %d秒%n", config.rampUpPeriod);
        System.out.printf("最大RPS: %s%n", config.maxRps > 0 ? config.maxRps : "无限制");
        System.out.printf("请求方法: %s%n", config.requestMethod);
        System.out.println("=".repeat(80));

        // 检查服务是否可用
        if (!checkServiceAvailable(config.url)) {
            System.err.println("错误: 目标服务不可用，请确保应用正在运行在 " + config.url);
            return;
        }

        // 预热阶段
        System.out.println("\n正在进行预热请求...");
        warmup(config);

        // 等待系统稳定
        Thread.sleep(2000);

        // 初始化统计
        TestStats stats = new TestStats();
        RealtimeStats realtimeStats = new RealtimeStats();

        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(config.concurrentThreads);
        CountDownLatch latch = new CountDownLatch(config.totalRequests);

        // 创建HTTP客户端
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

        System.out.println("\n开始发送压力测试请求...");
        long start = System.currentTimeMillis();

        // 如果启用了实时监控，则启动监控线程
        ScheduledExecutorService monitorExecutor = null;
        if (config.enableRealtimeMonitor) {
            monitorExecutor = Executors.newSingleThreadScheduledExecutor();
            monitorExecutor.scheduleAtFixedRate(() -> printRealtimeStats(realtimeStats), 1, 1, TimeUnit.SECONDS);
        }

        // 计算每个请求之间的延迟（如果设置了最大RPS）
        long delayBetweenRequests = 0;
        if (config.maxRps > 0) {
            delayBetweenRequests = Math.max(0, 1000 / config.maxRps);
        }

        // 发送请求
        for (int i = 0; i < config.totalRequests; i++) {
            // 控制请求发送速率
            if (delayBetweenRequests > 0) {
                Thread.sleep(delayBetweenRequests);
            }

            executor.submit(() -> {
                try {
                    realtimeStats.activeConnections++;
                    
                    long reqStart = System.currentTimeMillis();
                    stats.timestamps.add(reqStart);
                    
                    // 构建请求
                    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                            .uri(URI.create(config.url))
                            .timeout(java.time.Duration.ofSeconds(10));
                    
                    // 设置请求头
                    for (Map.Entry<String, String> header : config.headers.entrySet()) {
                        requestBuilder.header(header.getKey(), header.getValue());
                    }
                    
                    // 设置请求体（如果是POST等方法）
                    if ("POST".equalsIgnoreCase(config.requestMethod) || 
                        "PUT".equalsIgnoreCase(config.requestMethod) ||
                        "PATCH".equalsIgnoreCase(config.requestMethod)) {
                        requestBuilder.method(config.requestMethod, HttpRequest.BodyPublishers.ofString(config.requestBody));
                    } else {
                        requestBuilder.GET();
                    }
                    
                    HttpRequest request = requestBuilder.build();
                    
                    // 发送请求
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    long reqEnd = System.currentTimeMillis();
                    
                    // 更新实时统计
                    updateRealtimeStats(realtimeStats, reqStart);
                    
                    int statusCode = response.statusCode();
                    long reqTime = reqEnd - reqStart;
                    long bytesReceived = response.body().length();
                    
                    // 更新统计信息
                    updateStats(stats, reqTime, statusCode, bytesReceived);
                    
                } catch (java.net.http.HttpTimeoutException e) {
                    stats.timeoutCount.incrementAndGet();
                    stats.errorCount.incrementAndGet();
                    System.err.println("请求超时: " + e.getMessage());
                } catch (Exception e) {
                    stats.connectionErrorCount.incrementAndGet();
                    stats.errorCount.incrementAndGet();
                    System.err.println("请求异常: " + e.getMessage());
                } finally {
                    realtimeStats.activeConnections--;
                    latch.countDown();
                }
            });
        }

        // 等待所有请求完成
        long awaitStart = System.currentTimeMillis();
        boolean completed = latch.await(5, TimeUnit.MINUTES);
        long awaitEnd = System.currentTimeMillis();

        if (!completed) {
            System.out.println("\n警告: 测试未在预期时间内完成，可能存在挂起的请求");
        }

        // 停止监控
        if (monitorExecutor != null) {
            monitorExecutor.shutdown();
        }

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
        
        // 输出详细统计报告
        printDetailedReport(stats, start, end, config);
    }

    /**
     * 执行预热请求
     */
    private static void warmup(TestConfig config) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.url))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();

            int warmupRequests = Math.min(100, config.totalRequests / 10); // 预热请求数为总数的10%，最多100个
            for (int i = 0; i < warmupRequests; i++) {
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
     * 更新统计信息
     */
    private static void updateStats(TestStats stats, long responseTime, int statusCode, long bytesReceived) {
        stats.responseTimes.add(responseTime);
        stats.totalTime.addAndGet(responseTime);
        stats.totalBytesReceived.addAndGet(bytesReceived);

        // 记录最大最小响应时间
        stats.maxResponseTime.set(Math.max(stats.maxResponseTime.get(), responseTime));
        if (responseTime < stats.minResponseTime.get()) {
            stats.minResponseTime.set(responseTime);
        }

        // 统计状态码
        if (stats.statusCodeCounts.containsKey(statusCode)) {
            stats.statusCodeCounts.get(statusCode).incrementAndGet();
        }

        // 分类成功/失败
        if (statusCode >= 200 && statusCode < 300) {
            stats.successCount.incrementAndGet();
        } else {
            stats.errorCount.incrementAndGet();
        }
    }

    /**
     * 更新实时统计
     */
    private static void updateRealtimeStats(RealtimeStats stats, long requestStartTime) {
        long currentTime = System.currentTimeMillis();
        long timeDiff = currentTime - stats.lastUpdateTimestamp;
        
        if (timeDiff >= 1000) { // 每秒更新一次RPS
            stats.currentRps = (stats.lastSecondRequests * 1000.0) / timeDiff;
            stats.lastSecondRequests = 1;
            stats.lastUpdateTimestamp = currentTime;
        } else {
            stats.lastSecondRequests++;
        }
    }

    /**
     * 打印实时统计信息
     */
    private static void printRealtimeStats(RealtimeStats stats) {
        System.out.printf("[实时监控] 活跃连接: %d, 当前RPS: %.2f%n", 
                         stats.activeConnections, stats.currentRps);
    }

    /**
     * 输出详细统计报告
     */
    private static void printDetailedReport(TestStats stats, long startTime, long endTime, TestConfig config) {
        // 对响应时间排序以计算百分位数
        Collections.sort(stats.responseTimes);
        
        long totalRequests = stats.successCount.get() + stats.errorCount.get();
        long avgTime = stats.responseTimes.size() > 0 ? stats.totalTime.get() / stats.responseTimes.size() : 0;
        double successRate = totalRequests > 0 ? (double) stats.successCount.get() / totalRequests * 100 : 0;
        
        // 计算百分位数
        long p50 = getPercentile(stats.responseTimes, 0.50);
        long p90 = getPercentile(stats.responseTimes, 0.90);
        long p95 = getPercentile(stats.responseTimes, 0.95);
        long p99 = getPercentile(stats.responseTimes, 0.99);
        long p999 = getPercentile(stats.responseTimes, 0.999);
        
        // 计算吞吐量 (每秒请求数)
        long testDuration = endTime - startTime;
        double throughput = testDuration > 0 ? (double) totalRequests / (testDuration / 1000.0) : 0;
        
        // 计算平均每秒接收字节数
        double avgBytesPerSecond = testDuration > 0 ? (double) stats.totalBytesReceived.get() / (testDuration / 1000.0) : 0;
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("压力测试详细报告");
        System.out.println("=".repeat(80));
        
        System.out.println("\n📊 基础统计信息:");
        System.out.println("  ".concat("测试耗时: ").concat(formatTime(testDuration)));
        System.out.println("  ".concat("总请求数: ").concat(String.valueOf(totalRequests)));
        System.out.println("  ".concat("成功请求数: ").concat(String.valueOf(stats.successCount.get()))
                  .concat(" (").concat(DF.format(successRate)).concat("%)"));
        System.out.println("  ".concat("失败请求数: ").concat(String.valueOf(stats.errorCount.get()))
                  .concat(" (").concat(DF.format(100 - successRate)).concat("%)"));
        System.out.println("  ".concat("吞吐量: ").concat(DF.format(throughput)).concat(" req/s"));
        System.out.println("  ".concat("平均吞吐量: ").concat(DF.format(avgBytesPerSecond)).concat(" bytes/s"));

        System.out.println("\n⏱️  响应时间统计:");
        System.out.println("  ".concat("平均响应时间: ").concat(String.valueOf(avgTime)).concat("ms"));
        System.out.println("  ".concat("最小响应时间: ").concat(String.valueOf(
                stats.minResponseTime.get() == Long.MAX_VALUE ? 0 : stats.minResponseTime.get())).concat("ms"));
        System.out.println("  ".concat("最大响应时间: ").concat(String.valueOf(stats.maxResponseTime.get())).concat("ms"));
        System.out.println("  ".concat("中位数(P50): ").concat(String.valueOf(p50)).concat("ms"));
        System.out.println("  ".concat("90th percentile: ").concat(String.valueOf(p90)).concat("ms"));
        System.out.println("  ".concat("95th percentile: ").concat(String.valueOf(p95)).concat("ms"));
        System.out.println("  ".concat("99th percentile: ").concat(String.valueOf(p99)).concat("ms"));
        System.out.println("  ".concat("99.9th percentile: ").concat(String.valueOf(p999)).concat("ms"));

        System.out.println("\n❌ 错误分析:");
        System.out.println("  ".concat("超时错误: ").concat(String.valueOf(stats.timeoutCount.get())));
        System.out.println("  ".concat("连接错误: ").concat(String.valueOf(stats.connectionErrorCount.get())));

        System.out.println("\n🌐 HTTP状态码分布:");
        stats.statusCodeCounts.forEach((code, count) -> {
            if (count.get() > 0) {
                System.out.println("  ".concat(String.valueOf(code)).concat(": ").concat(String.valueOf(count.get())));
            }
        });

        // 计算错误率
        double errorRate = totalRequests > 0 ? (double) stats.errorCount.get() / totalRequests * 100 : 0;
        System.out.println("\n📈 性能评估:");
        System.out.println("  ".concat("错误率: ").concat(DF.format(errorRate)).concat("%"));
        
        // 性能评级
        String performanceGrade = evaluatePerformance(throughput, avgTime, errorRate);
        System.out.println("  ".concat("性能评级: ").concat(performanceGrade));
        
        // 服务器响应能力评估
        System.out.println("\n🎯 服务器响应能力评估:");
        if (avgTime < 50) {
            System.out.println("  响应速度: 极快 (<50ms)");
        } else if (avgTime < 100) {
            System.out.println("  响应速度: 很快 (50-100ms)");
        } else if (avgTime < 200) {
            System.out.println("  响应速度: 较快 (100-200ms)");
        } else if (avgTime < 500) {
            System.out.println("  响应速度: 一般 (200-500ms)");
        } else {
            System.out.println("  响应速度: 较慢 (>500ms)");
        }
        
        if (errorRate < 1) {
            System.out.println("  稳定性: 极高 (<1%)");
        } else if (errorRate < 5) {
            System.out.println("  稳定性: 高 (1-5%)");
        } else if (errorRate < 10) {
            System.out.println("  稳定性: 中等 (5-10%)");
        } else {
            System.out.println("  稳定性: 差 (>10%)");
        }

        System.out.println("\n💡 建议:");
        if (errorRate > 5 || avgTime > 500) {
            System.out.println("  - 考虑增加服务器资源或优化代码逻辑");
            System.out.println("  - 检查数据库查询性能，考虑添加索引");
            System.out.println("  - 评估缓存策略的有效性");
        } else if (throughput < 100) {
            System.out.println("  - 可以尝试更高并发的测试以确定系统极限");
            System.out.println("  - 评估服务器资源配置是否合理");
        } else {
            System.out.println("  - 系统表现良好，可以考虑更高负载的测试");
        }
        
        System.out.println("=".repeat(80));
    }

    /**
     * 计算百分位数值
     */
    private static long getPercentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    /**
     * 格式化时间显示
     */
    private static String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long mins = seconds / 60;
        seconds = seconds % 60;
        return mins + "m " + seconds + "s";
    }

    /**
     * 评估性能等级
     */
    private static String evaluatePerformance(double throughput, long avgResponseTime, double errorRate) {
        if (errorRate > 10) return "差 (需要立即优化)";
        if (errorRate > 5) return "合格 (需关注)";
        if (throughput > 200 && avgResponseTime < 100) return "优秀";
        if (throughput > 100 && avgResponseTime < 200) return "良好";
        if (throughput > 50 && avgResponseTime < 500) return "一般";
        return "较差 (需优化)";
    }

    /**
     * 检查目标服务是否可用
     */
    private static boolean checkServiceAvailable(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
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