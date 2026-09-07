package org.dreamwork.dsi.embedded.httpd.support;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;

public final class Cache {
    public final Collection<Field> fields = new HashSet<> ();
    public final Collection<AccessibleObject> config = new HashSet<> ();
    public final Collection<Method> methods = new HashSet<> ();
    public Method starter, destroyer;
}
