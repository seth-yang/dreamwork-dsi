package org.dreamwork.dsi.embedded.httpd;

import org.dreamwork.injection.IObjectCreateFactory;
import org.dreamwork.injection.IObjectContext;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 单元测试用的 {@link IObjectContext} 桩实现。
 * <p>仅支持按名称 / 按类型读取已注册的 bean，其余操作一律抛出 {@link UnsupportedOperationException}。</p>
 */
public class StubObjectContext implements IObjectContext {
    private final Map<String, Object> beans = new LinkedHashMap<> ();

    public StubObjectContext () {
    }

    public StubObjectContext (String name, Object bean) {
        beans.put (name, bean);
    }

    public StubObjectContext add (String name, Object bean) {
        beans.put (name, bean);
        return this;
    }

    @Override
    public Object getBean (String name) {
        return beans.get (name);
    }

    @Override
    @SuppressWarnings ("unchecked")
    public <T> T getBean (Class<T> type) {
        for (Object bean : beans.values ()) {
            if (type.isInstance (bean)) {
                return (T) bean;
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings ("unchecked")
    public <T> Map<String, T> getBeanMap (Class<T> type) {
        Map<String, T> result = new HashMap<> ();
        beans.forEach ((name, bean) -> {
            if (type.isInstance (bean)) {
                result.put (name, (T) bean);
            }
        });
        return result;
    }

    @Override
    public void remove (Object bean) {
        beans.entrySet ().removeIf (e -> e.getValue () == bean);
    }

    @Override
    public void remove (String name) {
        beans.remove (name);
    }

    @Override
    public void register (String name, Object bean) {
        throw new UnsupportedOperationException ();
    }

    @Override
    public void register (Object bean) {
        throw new UnsupportedOperationException ();
    }

    @Override
    public void create (IObjectCreateFactory factory, Class<?>... interfaces) {
        throw new UnsupportedOperationException ();
    }

    @Override
    public void resolve () {
        throw new UnsupportedOperationException ();
    }

    @Override
    public Set<String> getAllBeanNames () {
        return Collections.unmodifiableSet (beans.keySet ());
    }

    @Override
    public Set<Object> getAllRegisteredBeans () {
        return Set.copyOf (beans.values ());
    }

    @Override
    public Annotation[] getContextAnnotation () {
        return new Annotation[0];
    }

    @Override
    public void dispose () {
        beans.clear ();
    }
}
