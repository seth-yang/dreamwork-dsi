package org.dreamwork.injection.impl;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

@Resource
public class SimpleInjection {
    private static final String JMX_NAME = "SimpleObjectContext";
    private static final String JMX_GROUP = "org.dreamwork.jmx";
    private static final MBeanServer server  = ManagementFactory.getPlatformMBeanServer ();
    private final ObjectName oName;

    @Resource
    private SimpleObjectContext context;

    public SimpleInjection () {
        oName = registerJMXService (JMX_NAME, this);
    }

    @PreDestroy
    public void destroy () {
        if (oName != null) {
            unregisterJMXService (oName);
        }
    }

    /**
     * 将指定的对象 {@code mbean} 注册未指定的名称 {@code name}
     * @param name  mbean 注册名称
     * @param mbean mbean 对象
     * @return mbean名称
     */
    public static ObjectName registerJMXService (String name, Object mbean) {
        try {
            ObjectName oName = new ObjectName (JMX_GROUP, "name", name);
            server.registerMBean (mbean, oName);
            return oName;
        } catch (Exception ex) {
            throw new RuntimeException (ex);
        }
    }

    /**
     * 反注册指定名称 {@code oName} 的mbean
     * @param oName mbean 名称
     */
    public static void unregisterJMXService (ObjectName oName) {
        try {
            server.unregisterMBean (oName);
        } catch (Exception ex) {
            throw new RuntimeException (ex);
        }
    }
}