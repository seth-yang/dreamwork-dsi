package org.dreamwork.injection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ObjectContextFactory} 的单元测试。
 */
class ObjectContextFactoryTest {

    @Test
    void start_withoutAInjectionContext_throws () {
        assertThrows (
                TypeNotPresentException.class,
                () -> ObjectContextFactory.start (Object.class)
        );
    }

    @Test
    void start_withoutAInjectionContext_andArguments_throws () {
        assertThrows (
                TypeNotPresentException.class,
                () -> ObjectContextFactory.start (Object.class, "-c", "whatever.conf", "without-logs")
        );
    }
}
