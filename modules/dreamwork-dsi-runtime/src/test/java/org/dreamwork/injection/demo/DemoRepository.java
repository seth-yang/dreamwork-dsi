package org.dreamwork.injection.demo;

import jakarta.annotation.Resource;

/**
 * 端到端测试用的受托管资源。
 */
@Resource
public class DemoRepository {
    public String findById (String id) {
        return "demo-" + id;
    }
}
