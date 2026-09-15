package org.dreamwork.dsi.embedded.httpd.support;

public enum SseRole {
    /** SSE 任务数据的生产者 **/
    Producer,
    /** SSE 任务数据的订阅者 **/
    Subscriber
}