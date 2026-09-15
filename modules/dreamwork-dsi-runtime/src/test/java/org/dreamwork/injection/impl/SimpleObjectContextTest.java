package org.dreamwork.injection.impl;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.config.PropertyConfiguration;
import org.dreamwork.injection.*;
import org.junit.jupiter.api.*;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SimpleObjectContext} 的单元测试.
 *
 * <p>测试直接从同包（{@code org.dreamwork.injection.impl}）内构造容器，因此可以使用包内可见的
 * 构造函数、{@code resolve(Set)} 和 {@code configureFields(...)} 等方法。</p>
 */
class SimpleObjectContextTest {
    private SimpleObjectContext context;

    @BeforeEach
    void setUp () {
        // shutdownPort 传入一个 1~1024 之间的"无效值"，容器不会尝试监听关闭端口
        context = new SimpleObjectContext (1);
    }

    @AfterEach
    void tearDown () {
        context.dispose ();
    }

    // ------------------------------------------------------------------ 注册 / 索引

    @Test
    void register_withName_canBeLookedUpByName () throws Exception {
        HelloService hello = new HelloService ();
        context.register ("hello", hello);

        assertSame (hello, context.getBean ("hello"));
        assertNull (context.getBean ("none"));
    }

    @Test
    void register_withoutName_usesJavaPropertyStyleNameAndExpandsTypeTree () throws Exception {
        HelloService hello = new HelloService ();
        context.register (hello);

        assertSame (hello, context.getBean ("helloService"));
        assertSame (hello, context.getBean (HelloService.class));
        // 继承树上的接口同样可以作为索引
        assertSame (hello, context.getBean (Greeter.class));
    }

    @Test
    void register_singleCharacterClassName_isLowerCased () throws Exception {
        A bean = new A ();
        context.register (bean);

        assertSame (bean, context.getBean ("a"));
    }

    @Test
    void register_duplicatedName_throws () throws Exception {
        context.register ("same", new HelloService ());

        assertThrows (InstanceNotUniqueException.class, () -> context.register ("same", new HiService ()));
    }

    @Test
    void register_null_throws () {
        assertThrows (InstanceNotFoundException.class, () -> context.register ("null-bean", null));
        assertThrows (InstanceNotFoundException.class, () -> context.register ((Object) null));
    }

    @Test
    void getBean_withMultipleCandidates_throws () throws Exception {
        context.register ("hello", new HelloService ());
        context.register ("hi", new HiService ());

        assertThrows (InstanceNotUniqueException.class, () -> context.getBean (Greeter.class));

        Map<String, Greeter> map = context.getBeanMap (Greeter.class);
        assertEquals (2, map.size ());
        assertInstanceOf (HelloService.class, map.get ("hello"));
        assertInstanceOf (HiService.class, map.get ("hi"));
    }

    @Test
    void getBeanMap_withSingleCandidate_containsOnlyThatElement () throws Exception {
        HelloService hello = new HelloService ();
        context.register ("hello", hello);

        Map<String, Greeter> map = context.getBeanMap (Greeter.class);
        assertEquals (1, map.size ());
        assertSame (hello, map.get ("hello"));
    }

    @Test
    void getBeanMap_withoutAnyCandidate_returnsEmptyMap () {
        assertTrue (context.getBeanMap (Greeter.class).isEmpty ());
        assertNull (context.getBean (Greeter.class));
    }

    @Test
    void typeIndex_ignoresJdkCollectionInterfaces () throws Exception {
        List<String> list = new ArrayList<> ();
        context.register ("list", list);

        assertSame (list, context.getBean (ArrayList.class));
        // java.util.* 下的接口不参与类型索引
        assertNull (context.getBean (List.class));
    }

    // ------------------------------------------------------------------ 删除

    @Test
    void remove_byName () throws Exception {
        context.register ("hello", new HelloService ());

        context.remove ("hello");

        assertNull (context.getBean ("hello"));
        assertNull (context.getBean (HelloService.class));
        assertNull (context.getBean (Greeter.class));
    }

    @Test
    void remove_byBeanInstance () throws Exception {
        HiService hi = new HiService ();
        context.register ("hi", hi);

        context.remove (hi);

        assertNull (context.getBean ("hi"));
        assertNull (context.getBean (HiService.class));
    }

    @Test
    void remove_oneOfMany_keepsOthers () throws Exception {
        HelloService hello = new HelloService ();
        HiService hi = new HiService ();
        context.register ("hello", hello);
        context.register ("hi", hi);

        context.remove (hello);

        Map<String, Greeter> map = context.getBeanMap (Greeter.class);
        assertEquals (1, map.size ());
        assertSame (hi, map.get ("hi"));
        // 注意：类型索引一旦退化为列表就不会再还原为单实例，
        // 因此这里 getBean(Class) 仍然抛出 InstanceNotUniqueException（按现有实现的行为断言）
        assertThrows (InstanceNotUniqueException.class, () -> context.getBean (Greeter.class));
    }

    @Test
    void remove_unknownElement_isNoop () throws Exception {
        context.register ("hello", new HelloService ());

        context.remove ("never-registered");
        context.remove (new HiService ());

        assertNotNull (context.getBean ("hello"));
    }

    // ------------------------------------------------------------------ create

    @Test
    void create_registersProducedInstance () {
        context.create (interfaces -> new HelloService (), Greeter.class);

        assertNotNull (context.getBean (Greeter.class));
        assertEquals (1, context.getBeanMap (Greeter.class).size ());
        assertEquals (1, context.getAllBeanNames ().size ());
    }

    @Test
    void create_producesMultipleInstances () {
        IObjectCreateFactory factory = interfaces -> new HelloService ();
        context.create (factory, Greeter.class);
        context.create (factory, Greeter.class);

        assertEquals (2, context.getBeanMap (Greeter.class).size ());
        assertThrows (InstanceNotUniqueException.class, () -> context.getBean (Greeter.class));
    }

    @Test
    void create_withNullResult_registersNothing () {
        context.create (interfaces -> null, Greeter.class);

        assertTrue (context.getAllBeanNames ().isEmpty ());
        assertNull (context.getBean (Greeter.class));
    }

    @Test
    void create_withIncompatibleResult_onlyRegistersName () {
        context.create (interfaces -> new Object (), Greeter.class);

        assertEquals (1, context.getAllBeanNames ().size ());
        assertNull (context.getBean (Greeter.class));
    }

    // ------------------------------------------------------------------ resolve / 生命周期

    @Test
    void resolve_canBeCalledOnlyOnce () {
        context.resolve ();

        assertThrows (IllegalStateException.class, context::resolve);
    }

    @Test
    void resolve_invokesProcessorsInOrderOfGetOrder () throws Exception {
        List<String> order = new ArrayList<> ();
        IInjectResolvedProcessor low = new RecordingProcessor (100, "low", order);
        IInjectResolvedProcessor high = new RecordingProcessor (10, "high", order);

        context.resolve (Set.of (processorWrapper (low), processorWrapper (high)));
        context.resolve ();

        assertEquals (List.of ("high", "low"), order);
    }

    @Test
    void resolve_whenProcessorFails_throwsRuntimeException () throws Exception {
        IInjectResolvedProcessor broken = new IInjectResolvedProcessor () {
            @Override
            public void perform (IObjectContext ignored) throws Exception {
                throw new Exception ("boom");
            }
        };
        context.resolve (Set.of (processorWrapper (broken)));

        RuntimeException ex = assertThrows (RuntimeException.class, context::resolve);
        assertEquals ("boom", ex.getCause ().getMessage ());
    }

    @Test
    void resolve_invokesPostConstructMethod () throws Exception {
        LifecycleBean bean = new LifecycleBean ();
        context.register ("life", bean);

        context.resolve (Set.of (postConstructWrapper (bean, "start")));

        assertEquals (1, bean.started);
    }

    @Test
    void dispose_invokesPreDestroyMethod () throws Exception {
        LifecycleBean bean = new LifecycleBean ();
        context.register ("life", bean);

        context.dispose ();

        assertEquals (1, bean.stopped);
        assertTrue (context.getAllBeanNames ().isEmpty ());
        assertTrue (context.getAllRegisteredBeans ().isEmpty ());
    }

    @Test
    void register_afterResolved_injectsDependenciesImmediately () throws Exception {
        context.resolve ();
        context.register ("greeter", new HelloService ());

        InjectedBean bean = new InjectedBean ();
        context.register ("injected", bean);

        assertNotNull (bean.greeter);
        assertInstanceOf (HelloService.class, bean.greeter);
        assertEquals (1, bean.started);
    }

    @Test
    void register_afterResolved_withMissingDependency_throws () {
        context.resolve ();

        assertThrows (InstanceNotFoundException.class, () -> context.register ("injected", new InjectedBean ()));
    }

    @Test
    void register_afterResolved_withTwoPostConstructMethods_throws () throws Exception {
        context.resolve ();

        assertThrows (
                InvocationTargetException.class,
                () -> context.register ("child", new ChildWithInit ())
        );
    }

    // ------------------------------------------------------------------ 自动注入

    @Test
    void injectField_acceptsTheContextItself () throws Exception {
        context.resolve ();

        ContextAware bean = new ContextAware ();
        context.register ("aware", bean);

        assertSame (context, bean.context);
    }

    @Test
    void injectMethod_byType_and_byName () throws Exception {
        context.resolve ();
        context.register ("named", new HiService ());

        ByTypeBean byType = new ByTypeBean ();
        context.register ("byType", byType);
        assertNotNull (byType.greeter);

        ByNameBean byName = new ByNameBean ();
        context.register ("byName", byName);
        assertInstanceOf (HiService.class, byName.greeter);

        ContextSetterBean contextSetter = new ContextSetterBean ();
        context.register ("contextSetter", contextSetter);
        assertSame (context, contextSetter.context);
    }

    @Test
    void processResourceMethod_getterExposesReturnValue () throws Exception {
        context.resolve ();

        context.register ("exposer", new Exposer ());

        assertInstanceOf (HelloService.class, context.getBean ("greeter"));
        // 显式指定 name 的 getter 使用注解上的名字
        assertInstanceOf (HiService.class, context.getBean ("custom-greeter"));
    }

    @Test
    void processResourceMethod_getterReturningNull_throws () throws Exception {
        context.resolve ();

        assertThrows (InstantiationException.class, () -> context.register ("bad", new NullGetter ()));
    }

    @Test
    void processResourceMethod_invalidGetterOrSetter_throwsIntrospectionException () throws Exception {
        context.resolve ();

        assertThrows (IntrospectionException.class, () -> context.register ("a", new VoidGetter ()));
        assertThrows (IntrospectionException.class, () -> context.register ("b", new GetterWithParameter ()));
        assertThrows (IntrospectionException.class, () -> context.register ("c", new VoidSetter ()));
        assertThrows (IntrospectionException.class, () -> context.register ("d", new TwoParameterSetter ()));
    }

    @Test
    void processResourceMethod_setterWithoutRegisteredResource_throws () throws Exception {
        context.resolve ();

        assertThrows (InstanceNotFoundException.class, () -> context.register ("e", new ByTypeBean ()));
    }

    @Test
    void processResourceMethod_methodWithUnknownPrefix_isIgnored () throws Exception {
        context.resolve ();

        // 既不是 getter 也不是 setter 的 @Resource 方法，容器直接忽略
        assertDoesNotThrow (() -> context.register ("f", new NeitherGetterNorSetter ()));
    }

    // ------------------------------------------------------------------ 配置注入

    @Test
    void configureFields_stringBooleanAndJson () throws Exception {
        Properties props = new Properties ();
        props.setProperty ("app.name", "dreamwork");
        props.setProperty ("app.enabled", "on");
        props.setProperty ("app.pool", "{\"size\":8,\"label\":\"main\"}");
        props.setProperty (ConfigTarget.class.getCanonicalName () + ".byFieldName", "auto-key");
        PropertyConfiguration conf = new PropertyConfiguration (props);

        ConfigTarget target = new ConfigTarget ();
        SimpleObjectContext.configureFields (conf, target, List.of (
                ConfigTarget.class.getDeclaredField ("name"),
                ConfigTarget.class.getDeclaredField ("enabled"),
                ConfigTarget.class.getDeclaredField ("pool"),
                ConfigTarget.class.getDeclaredField ("byFieldName"),
                ConfigTarget.class.getDeclaredField ("constant")
        ));

        assertEquals ("dreamwork", target.name);
        assertTrue (target.enabled);
        assertNotNull (target.pool);
        assertEquals (8, target.pool.size);
        assertEquals ("main", target.pool.label);
        assertEquals ("auto-key", target.byFieldName);
        assertEquals ("plain-text", target.constant);
    }

    @Test
    void configureFields_acceptsCommonBooleanLiteral () throws Exception {
        for (String text : List.of ("true", "T", "1", "on", "ON")) {
            assertTrue (configureBoolean (text));
        }
        for (String text : List.of ("false", "f", "0", "off", "OFF")) {
            assertFalse (configureBoolean (text));
        }
    }

    @Test
    void configureFields_invalidBoolean_throws () throws Exception {
        assertThrows (IllegalArgumentException.class, () -> configureBoolean ("maybe"));
    }

    @Test
    void configureFields_missingRequiredKey_throws () throws Exception {
        ConfigTarget target = new ConfigTarget ();
        Field field = ConfigTarget.class.getDeclaredField ("required");

        assertThrows (
                ConfigurationNotFoundException.class,
                () -> SimpleObjectContext.configureFields (new PropertyConfiguration (new Properties ()), target, List.of (field))
        );
    }

    @Test
    void configureFields_missingNonRequiredKey_keepsDefaultValue () throws Exception {
        ConfigTarget target = new ConfigTarget ();

        SimpleObjectContext.configureFields (
                new PropertyConfiguration (new Properties ()),
                target,
                List.of (ConfigTarget.class.getDeclaredField ("missing"))
        );

        assertEquals ("default-value", target.missing);
    }

    // ------------------------------------------------------------------ 元数据

    @Test
    void getAllBeanNames_and_getAllRegisteredBeans_areUnmodifiable () throws Exception {
        context.register ("hello", new HelloService ());

        assertTrue (context.getAllBeanNames ().contains ("hello"));
        assertEquals (1, context.getAllRegisteredBeans ().size ());
        assertThrows (UnsupportedOperationException.class, () -> context.getAllBeanNames ().add ("x"));
        assertThrows (UnsupportedOperationException.class, () -> context.getAllRegisteredBeans ().clear ());
    }

    @Test
    void getContextAnnotation_returnsRegisteredAnnotations () throws Exception {
        Annotation[] annotations = Annotated.class.getAnnotations ();
        context.register (IObjectContext.CONTEXT_ANNOTATION_KEY, annotations);

        assertArrayEquals (annotations, context.getContextAnnotation ());
    }

    @Test
    void getContextAnnotation_withoutRegistration_returnsNull () {
        assertNull (context.getContextAnnotation ());
    }

    // ------------------------------------------------------------------ 测试用夹具

    private boolean configureBoolean (String text) throws Exception {
        Properties props = new Properties ();
        props.setProperty ("flag", text);
        ConfigTarget target = new ConfigTarget ();

        SimpleObjectContext.configureFields (
                new PropertyConfiguration (props),
                target,
                List.of (ConfigTarget.class.getDeclaredField ("flag"))
        );
        return target.flag;
    }

    private static ClassScanner.Wrapper processorWrapper (IInjectResolvedProcessor processor) {
        ClassScanner.Wrapper w = new ClassScanner.Wrapper ();
        w.bean = processor;
        w.type = processor.getClass ();
        w.isProcessor = true;
        return w;
    }

    private static ClassScanner.Wrapper postConstructWrapper (Object bean, String methodName) throws NoSuchMethodException {
        ClassScanner.Wrapper w = new ClassScanner.Wrapper ();
        w.bean = bean;
        w.type = bean.getClass ();
        w.postConstruct = bean.getClass ().getDeclaredMethod (methodName);
        return w;
    }

    interface Greeter {
        String greet ();
    }

    static class HelloService implements Greeter {
        @Override
        public String greet () {
            return "hello";
        }
    }

    static class HiService implements Greeter {
        @Override
        public String greet () {
            return "hi";
        }
    }

    static class A {}

    static class LifecycleBean {
        int started, stopped;

        @PostConstruct
        public void start () {
            started ++;
        }

        @PreDestroy
        public void stop () {
            stopped ++;
        }
    }

    static class ParentWithInit {
        @PostConstruct
        public void init () {}
    }

    static class ChildWithInit extends ParentWithInit {
        @PostConstruct
        public void initChild () {}
    }

    static class InjectedBean {
        @Resource
        private Greeter greeter;

        int started;

        @PostConstruct
        public void start () {
            started ++;
        }
    }

    static class ContextAware {
        @Resource
        private IObjectContext context;
    }

    static class ByTypeBean {
        Greeter greeter;

        @Resource
        public void setGreeter (Greeter greeter) {
            this.greeter = greeter;
        }
    }

    static class ByNameBean {
        Greeter greeter;

        @Resource (name = "named")
        public void setGreeter (Greeter greeter) {
            this.greeter = greeter;
        }
    }

    static class ContextSetterBean {
        IObjectContext context;

        @Resource
        public void setContext (IObjectContext context) {
            this.context = context;
        }
    }

    static class Exposer {
        @Resource
        public Greeter getGreeter () {
            return new HelloService ();
        }

        @Resource (name = "custom-greeter")
        public Greeter getCustomGreeter () {
            return new HiService ();
        }
    }

    static class NullGetter {
        @Resource
        public Greeter getGreeter () {
            return null;
        }
    }

    static class VoidGetter {
        @Resource
        public void getNothing () {}
    }

    static class GetterWithParameter {
        @Resource
        public Greeter getGreeter (String ignored) {
            return new HelloService ();
        }
    }

    static class VoidSetter {
        @Resource
        public void setNothing () {}
    }

    static class TwoParameterSetter {
        @Resource
        public void setGreeter (Greeter greeter, String ignored) {}
    }

    static class NeitherGetterNorSetter {
        @Resource
        public String greet () {
            return "hello";
        }
    }

    static class Pool {
        public int size;
        public String label;
    }

    static class ConfigTarget {
        @AConfigured ("${app.name}")
        String name;

        @AConfigured ("${app.enabled}")
        boolean enabled;

        @AConfigured ("${app.pool}")
        Pool pool;

        @AConfigured
        String byFieldName;

        @AConfigured ("plain-text")
        String constant;

        @AConfigured ("${missing.key}")
        String missing = "default-value";

        @AConfigured (key = "${required.key}", required = true)
        String required;

        @AConfigured ("${flag}")
        boolean flag;
    }

    static class RecordingProcessor implements IInjectResolvedProcessor {
        private final int order;
        private final String name;
        private final List<String> trace;

        RecordingProcessor (int order, String name, List<String> trace) {
            this.order = order;
            this.name = name;
            this.trace = trace;
        }

        @Override
        public void perform (IObjectContext context) {
            trace.add (name);
        }

        @Override
        public int getOrder () {
            return order;
        }
    }

    @Deprecated
    static class Annotated {}
}
