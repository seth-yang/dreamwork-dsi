package org.dreamwork.injection.demo;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.dreamwork.injection.AConfigured;

/**
 * 端到端测试用的托管服务：依赖注入 + 配置注入 + 生命周期回调。
 */
@Resource
public class DemoService {
    @Resource
    private DemoRepository repository;

    @AConfigured ("${demo.title}")
    String title = "unset";

    boolean started;

    private boolean destroyed;

    public DemoRepository repository () {
        return repository;
    }

    public String title () {
        return title;
    }

    public boolean started () {
        return started;
    }

    public boolean destroyed () {
        return destroyed;
    }

    @PostConstruct
    public void start () {
        started = true;
    }

    @PreDestroy
    public void stop () {
        destroyed = true;
    }
}
