package com.mammb.code.filetree.watch.impl;

import com.mammb.code.filetree.watch.Event;
import java.io.Closeable;

public interface Watch extends Closeable {

    @Override
    default void close() {
        Thread.currentThread().interrupt();
    }

    void run(Event.Listener listener);

}
