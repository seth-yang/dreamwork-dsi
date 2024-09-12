package org.dreamwork.injection.impl;

import org.dreamwork.injection.IObjectContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

public class ShutdownHook extends Thread {
    private volatile boolean running = true;

    private final ServerSocket server;
    private final Logger logger = LoggerFactory.getLogger (ShutdownHook.class);

    private IObjectContext context;
    private final Object LOCKER = new byte[0];
    private final AtomicBoolean released = new AtomicBoolean (false);

    private ShutdownHook (int port) throws IOException {
        server = new ServerSocket (port, -1, InetAddress.getByName ("127.0.0.1"));  // 仅监听本地
        String temp = System.getProperty ("java.io.tmpdir");
        Path target = Paths.get (temp, ".shutdown-port");
        Files.write (target, String.valueOf (port).getBytes ());
    }

    @Override
    public void run () {
        while (running && !this.isInterrupted ()) {
            try (Socket socket = server.accept ()) {
                InetSocketAddress address = (InetSocketAddress) socket.getRemoteSocketAddress ();
                if (address.getAddress ().isLoopbackAddress ()) { // 从本地发起的才有用
                    cancel ();
                } else if (logger.isTraceEnabled ()) {
                    logger.trace ("received a connect from {}，ignore this request", address);
                }
            } catch (IOException ex) {
                logger.warn (ex.getMessage (), ex);
            }
        }
    }

    public void cancel () {
        running = false;
        this.interrupt ();
        try {
            server.close ();
        } catch (IOException ignore) {}

        // 异步销毁context，并最长等待 30s
        new Thread (() -> {
            if (context != null) {
                try {
                    context.dispose ();
                    released.set (true);
                    synchronized (LOCKER) {
                        LOCKER.notifyAll ();
                    }
                } catch (Throwable e) {
                    throw new RuntimeException (e);
                }
            }
        }).start ();
        if (context != null && !released.get ()){
            synchronized (LOCKER) {
                try {
                    LOCKER.wait (30000);
                } catch (InterruptedException ignore) {
                }
            }
        }
        System.exit (0);
    }

    public static ShutdownHook bind (IObjectContext context, int port) throws IOException {
        ShutdownHook hook = new ShutdownHook (port);
        hook.context = context;
        hook.start ();
        return hook;
    }

    public static void shutdown () throws IOException {
        String temp = System.getProperty ("java.io.tmpdir");
        Path target = Paths.get (temp, ".shutdown-port");
        if (Files.exists (target)) {
            byte[] buff = Files.readAllBytes (target);
            String s_port = new String (buff);
            int port = Integer.parseInt (s_port);
            try (Socket socket = new Socket ("127.0.0.1", port)) {
                socket.getOutputStream ().write ("Good Bye!".getBytes ());
            }
        }
    }

    public static boolean isShutdownRequest (String... args) {
        for (String arg : args) {
            switch (arg) {
                case "stop":
                case "--stop":
                case "shutdown":
                case "--shutdown":
                    return true;
            }
        }
        return false;
    }
}