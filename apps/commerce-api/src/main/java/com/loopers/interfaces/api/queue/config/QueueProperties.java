package com.loopers.interfaces.api.queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "queue")
public class QueueProperties {

    // 대기열
    private int maxQueueSize = 50000;

    // TTL (초 단위)
    private int accessTtlSeconds = 60;
    private int hardTtlSeconds = 300;
    private int activityExtensionSeconds = 60;
    private int maxExtensionsPerMinute = 2;
    private int gracePeriodSeconds = 60;

    // 입장 스케줄러
    private long admissionIntervalMs = 10_000;
    private int admissionBatchSize = 1250;

    // GC 스케줄러
    private long gcIntervalMs = 10_000;

    // 세션 검증
    private boolean sessionValidationEnabled = true;

    // Rate Limit
    private int ipRateLimitPerSecond = 50;
    private int queueEnterLimitPerWindow = 1;
    private int queueEnterWindowSeconds = 10;
    private int positionLimitPerSecond = 2;
    private int orderLimitPerMinute = 3;

    // Getter & Setter

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public int getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public void setAccessTtlSeconds(int accessTtlSeconds) {
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public int getHardTtlSeconds() {
        return hardTtlSeconds;
    }

    public void setHardTtlSeconds(int hardTtlSeconds) {
        this.hardTtlSeconds = hardTtlSeconds;
    }

    public int getActivityExtensionSeconds() {
        return activityExtensionSeconds;
    }

    public void setActivityExtensionSeconds(int activityExtensionSeconds) {
        this.activityExtensionSeconds = activityExtensionSeconds;
    }

    public int getMaxExtensionsPerMinute() {
        return maxExtensionsPerMinute;
    }

    public void setMaxExtensionsPerMinute(int maxExtensionsPerMinute) {
        this.maxExtensionsPerMinute = maxExtensionsPerMinute;
    }

    public int getGracePeriodSeconds() {
        return gracePeriodSeconds;
    }

    public void setGracePeriodSeconds(int gracePeriodSeconds) {
        this.gracePeriodSeconds = gracePeriodSeconds;
    }

    public long getAdmissionIntervalMs() {
        return admissionIntervalMs;
    }

    public void setAdmissionIntervalMs(long admissionIntervalMs) {
        this.admissionIntervalMs = admissionIntervalMs;
    }

    public int getAdmissionBatchSize() {
        return admissionBatchSize;
    }

    public void setAdmissionBatchSize(int admissionBatchSize) {
        this.admissionBatchSize = admissionBatchSize;
    }

    public long getGcIntervalMs() {
        return gcIntervalMs;
    }

    public void setGcIntervalMs(long gcIntervalMs) {
        this.gcIntervalMs = gcIntervalMs;
    }

    public boolean isSessionValidationEnabled() {
        return sessionValidationEnabled;
    }

    public void setSessionValidationEnabled(boolean sessionValidationEnabled) {
        this.sessionValidationEnabled = sessionValidationEnabled;
    }

    public int getIpRateLimitPerSecond() {
        return ipRateLimitPerSecond;
    }

    public void setIpRateLimitPerSecond(int ipRateLimitPerSecond) {
        this.ipRateLimitPerSecond = ipRateLimitPerSecond;
    }

    public int getQueueEnterLimitPerWindow() {
        return queueEnterLimitPerWindow;
    }

    public void setQueueEnterLimitPerWindow(int queueEnterLimitPerWindow) {
        this.queueEnterLimitPerWindow = queueEnterLimitPerWindow;
    }

    public int getQueueEnterWindowSeconds() {
        return queueEnterWindowSeconds;
    }

    public void setQueueEnterWindowSeconds(int queueEnterWindowSeconds) {
        this.queueEnterWindowSeconds = queueEnterWindowSeconds;
    }

    public int getPositionLimitPerSecond() {
        return positionLimitPerSecond;
    }

    public void setPositionLimitPerSecond(int positionLimitPerSecond) {
        this.positionLimitPerSecond = positionLimitPerSecond;
    }

    public int getOrderLimitPerMinute() {
        return orderLimitPerMinute;
    }

    public void setOrderLimitPerMinute(int orderLimitPerMinute) {
        this.orderLimitPerMinute = orderLimitPerMinute;
    }
}
