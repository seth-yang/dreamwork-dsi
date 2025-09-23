package org.dreamwork.dsi.embedded.httpd.support.websocket;

import org.dreamwork.dsi.embedded.httpd.annotation.AWebsocketPackages;
import org.dreamwork.injection.AInjectionContext;
import org.dreamwork.injection.IObjectContext;
import org.dreamwork.util.CollectionCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.Endpoint;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.dreamwork.util.CollectionHelper.isEmpty;
import static org.dreamwork.util.CollectionHelper.isNotEmpty;

/**
 * 自动扫描指定的包来发现 Websocket 组件，并注册
 * @since 2.1.2
 */
public class InternalWebsocketScanner extends AbstractWebSocketScanner {
    private static final Logger logger = LoggerFactory.getLogger (InternalWebsocketScanner.class);
    private static IObjectContext context;

    public static void setContext (IObjectContext context) {
        InternalWebsocketScanner.context = context;
    }

    private volatile Set<Class<? extends Endpoint>> computed = null;

    @Override
    protected Set<Class<? extends Endpoint>> getSupportedEndpointClasses () {
        if (computed == null) {
            if (logger.isTraceEnabled ()) {
                logger.trace ("trying to scanning websocket component ...");
            }
            Set<String> names = new HashSet<> ();
            AInjectionContext ic = (AInjectionContext) context.getBean (IObjectContext.CONTEXT_DESCRIBER);
            if (isNotEmpty (ic.websocketPackages ())) {
                names.addAll (CollectionCreator.asSet (ic.websocketPackages ()));
                if (logger.isTraceEnabled () && isNotEmpty (names)) {
                    logger.trace ("got packages: {}", names);
                }
            }

            Annotation[] annotations = context.getContextAnnotation ();
            if (isNotEmpty (annotations)) {
                for (Annotation annotation : annotations) {
                    if (annotation instanceof AWebsocketPackages) {
                        AWebsocketPackages awp = (AWebsocketPackages) annotation;
                        String[] array = awp.packageNames ();
                        if (isEmpty (array)) {
                            array = awp.value ();
                        }

                        if (isNotEmpty (array)) {
                            names.addAll (CollectionCreator.asSet (array));
                        }
                    }
                }
            }

            if (isNotEmpty (names)) {
                if (logger.isTraceEnabled ()) {
                    logger.trace ("trying to scan websocket component from packages: {}", names);
                }
                computed = new HashSet<> ();
                WebsocketComponentScanner scanner = new WebsocketComponentScanner (computed);
                try {
                    scanner.scan (names.toArray (new String[0]));
                } catch (Exception ex) {
                    logger.warn (ex.getMessage (), ex);
                }
            } else {
                computed = Collections.emptySet ();
            }
        }
        return computed;
    }
}