package org.dreamwork.dsi.embedded.httpd.support;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;

public final class Cache {
    public Collection<Field> fields = new HashSet<> ();
    public Collection<Field> config = new HashSet<> ();
    public Collection<Method> methods = new HashSet<> ();
    public Method starter, destroyer;
}
