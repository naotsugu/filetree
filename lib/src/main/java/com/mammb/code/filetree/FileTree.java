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
package com.mammb.code.filetree;

import com.mammb.code.filetree.watch.Event;
import com.mammb.code.filetree.watch.FileTreeWatch;
import com.mammb.code.filetree.watch.impl.FlatWatch;
import com.mammb.code.filetree.watch.impl.PollingWatch;
import com.mammb.code.filetree.watch.impl.RecursiveWatch;
import com.mammb.code.filetree.watch.impl.SyncAction;
import java.nio.file.Path;
import java.time.Duration;

/**
 * The FileTree.
 * @author Naotsugu Kobayashi
 */
public interface FileTree {

    static FileTreeWatch watch(Path path, Event.Listener listener) {
        return FileTreeWatch.run(path, listener);
    }

    static FileTreeWatch watch(Path path, Duration interval, Event.Listener listener, String... globs) {
        return FileTreeWatch.run(path, interval, listener, globs);
    }

    static FileTreeWatch sync(Path source, Path target, String... globs) {
        return FileTreeWatch.sync(source, target, globs);
    }

    static FileTreeWatch sync(Path source, Path target, Duration interval, String... globs) {
        return FileTreeWatch.sync(source, target, interval, globs);
    }

}
