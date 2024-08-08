package org.dreamwork.injection;

import java.lang.annotation.*;

@Target ({ElementType.FIELD, ElementType.METHOD})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface ALazy {
    boolean value() default true;
}
