/*
 * Copyright 2026- the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mammb.code.filetree.watch;

import com.mammb.code.filetree.watch.impl.FlatWatch;
import com.mammb.code.filetree.watch.impl.PollingWatch;
import com.mammb.code.filetree.watch.impl.RecursiveWatch;
import com.mammb.code.filetree.watch.impl.SyncAction;
import java.io.Closeable;
import java.nio.file.Path;
import java.time.Duration;

/**
 * The FileTreeWatch.
 * @author Naotsugu Kobayashi
 */
public interface FileTreeWatch extends Closeable {

    System.Logger log = System.getLogger(FileTreeWatch.class.getName());

    @Override
    void close();

    static FileTreeWatch run(Path path, Event.Listener listener) {
        var os = System.getProperty("os.name", "other").toLowerCase();
        if (os.startsWith("windows")) {
            return spawn(() -> FlatWatch.run(path, true, listener));
        } else if (os.startsWith("mac")) {
            return spawn(() -> PollingWatch.run(path, Duration.ofSeconds(2), listener));
        } else {
            return spawn(() -> RecursiveWatch.run(path, listener));
        }
    }

    static FileTreeWatch run(Path path, Duration interval, Event.Listener listener, String... globs) {
        return spawn(() -> PollingWatch.run(path, interval, listener, globs));
    }

    static FileTreeWatch sync(Path source, Path target, String... globs) {
        return run(source, new SyncAction(source, target, globs));
    }

    static FileTreeWatch sync(Path source, Path target, Duration interval, String... globs) {
        return run(source, interval, new SyncAction(source, target, globs));
    }

    private static FileTreeWatch spawn(Runnable runnable) {
        final var thread = new Thread(runnable);
        thread.start();
        return thread::interrupt;
    }

}
