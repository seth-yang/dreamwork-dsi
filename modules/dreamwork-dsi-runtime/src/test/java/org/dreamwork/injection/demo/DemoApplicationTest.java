package org.dreamwork.injection.demo;

import org.dreamwork.config.IConfiguration;
import org.dreamwork.injection.AInjectionContext;
import org.dreamwork.injection.IObjectContext;
import org.dreamwork.injection.ObjectContextFactory;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端测试：从一个 {@link AInjectionContext} 标注的类启动整个托管容器。
 */
class DemoApplicationTest {

    @Test
    void startApplicationFromAnnotatedClass () throws Throwable {
        Path config = prepareConfigFile ();

        IObjectContext context = ObjectContextFactory.start (
                DemoApplication.class,
                "-c", config.toString (),
                "without-logs"
        );
        assertNotNull (context);

        try {
            // 扫描器自动注册了 global-config / LazyScanner / 注解本身
            assertInstanceOf (IConfiguration.class, context.getBean ("global-config"));
            assertNotNull (context.getBean ("lazyScanner"));
            assertSame (
                    DemoApplication.class.getAnnotation (AInjectionContext.class),
                    context.getBean (AInjectionContext.class)
            );

            Annotation[] annotations = (Annotation[]) context.getBean (IObjectContext.CONTEXT_ANNOTATION_KEY);
            assertNotNull (annotations);
            assertTrue (annotations.length > 0);

            // 应用自身的受托管对象
            DemoService service = context.getBean (DemoService.class);
            assertNotNull (service);
            assertSame (context.getBean (DemoRepository.class), service.repository ());
            assertEquals ("demo-42", service.repository ().findById ("42"));

            // 配置文件中的 demo.title 被注入
            assertEquals ("hello-from-config", service.title ());
            // @PostConstruct 已被调用
            assertTrue (service.started ());
            assertFalse (service.destroyed ());
        } finally {
            context.dispose ();
        }
    }

    @Test
    void disposeTriggersPreDestroy () throws Throwable {
        Path config = prepareConfigFile ();

        IObjectContext context = ObjectContextFactory.start (
                DemoApplication.class,
                "-c", config.toString (),
                "without-logs"
        );
        DemoService service = context.getBean (DemoService.class);
        assertFalse (service.destroyed ());

        context.dispose ();

        assertTrue (service.destroyed ());
        assertTrue (context.getAllBeanNames ().isEmpty ());
    }

    private Path prepareConfigFile () throws Exception {
        Path dir = Paths.get ("target", "test-config");
        Files.createDirectories (dir);

        Path config = dir.resolve ("object-context.conf");
        Files.writeString (config, "demo.title=hello-from-config" + System.lineSeparator (), StandardCharsets.UTF_8);
        return config;
    }
}
