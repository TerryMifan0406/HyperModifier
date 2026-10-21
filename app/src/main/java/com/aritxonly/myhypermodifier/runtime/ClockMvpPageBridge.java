package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Process;
import android.system.Os;
import android.system.OsConstants;
import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Two bounded requests per candidate, never socket/PM work on animation threads. */
final class ClockMvpPageBridge {
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(0, 1, 5, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(4), runnable -> new Thread(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                runnable.run();
            }, "MHMClockPage"));
    private static int launcherUid = -1; // worker only
    static void read(Context context, Consumer<ClockMvpPagePolicy.Snapshot> callback) {
        try { WORKER.execute(() -> {
            ClockMvpPagePolicy.Snapshot snapshot = null;
            try {
                if (launcherUid < 0) launcherUid = context.getPackageManager().getPackageUid("com.miui.home", 0);
                try (LocalSocket socket = new LocalSocket()) {
                    // LocalSocket's timeout-connect overload throws on this framework.
                    // Create its fd, use a nonblocking AF_UNIX connect, then bounded reads.
                    InputStream input = socket.getInputStream();
                    int flags = Os.fcntlInt(socket.getFileDescriptor(), OsConstants.F_GETFL, 0);
                    Os.fcntlInt(socket.getFileDescriptor(), OsConstants.F_SETFL, flags | OsConstants.O_NONBLOCK);
                    socket.connect(new LocalSocketAddress("mhm.widget.status." + Process.myUid() / 100000,
                            LocalSocketAddress.Namespace.ABSTRACT));
                    Os.fcntlInt(socket.getFileDescriptor(), OsConstants.F_SETFL, flags);
                    socket.setSoTimeout(80);
                    if (socket.getPeerCredentials().getUid() != launcherUid) throw new SecurityException("Clock page peer");
                    ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                    byte[] buffer = new byte[512]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > 2048) throw new IllegalStateException("Clock page response");
                        output.write(buffer, 0, count);
                    }
                    snapshot = ClockMvpPagePolicy.parse(output.toString(StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) { /* Unknown page retains stock transition. */ }
            callback.accept(snapshot);
        }); } catch (RejectedExecutionException ignored) { callback.accept(null); }
    }
}
