package org.dreamwork.injection;

/**
 * <p>当调用 {@link IObjectContext#register(String, Object)} 向容器中注册实例时，若名称已存在时，抛出这个异常</p>
 * <p>当调用 {@link IObjectContext#getBean(Class)} 时，容器中有多个实例时抛出这个异常</p>
 */
public class InstanceNotUniqueException extends RuntimeException {
    public InstanceNotUniqueException () {
        super ();
    }

    public InstanceNotUniqueException (String message) {
        super (message);
    }

    public InstanceNotUniqueException (String message, Throwable cause) {
        super (message, cause);
    }

    public InstanceNotUniqueException (Throwable cause) {
        super (cause);
    }

    protected InstanceNotUniqueException (String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super (message, cause, enableSuppression, writableStackTrace);
    }
}
