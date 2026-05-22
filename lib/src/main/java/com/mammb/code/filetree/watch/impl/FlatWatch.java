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
package com.mammb.code.filetree.watch.impl;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.sun.nio.file.ExtendedWatchEventModifier;
import com.mammb.code.filetree.watch.Event;

import static java.lang.System.Logger.Level.*;

/**
 * The FileTreeWatch.
 * @author Naotsugu Kobayashi
 */
public class FlatWatch {

    private static final System.Logger log = System.getLogger(FlatWatch.class.getName());

    private static final WatchEvent.Kind<?>[] kinds = new WatchEvent.Kind<?>[] {
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY
    };

    public static void run(Path watchPath, boolean fileTree, Event.Listener listener) {

        Objects.requireNonNull(watchPath);
        Objects.requireNonNull(listener);

        watchPath = watchPath.toAbsolutePath().normalize();

        if (fileTree && !System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            log.log(WARNING, "fileTree not supported");
        }

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {

            WatchEvent.Modifier[] modifiers = fileTree
                    ? new WatchEvent.Modifier[] { ExtendedWatchEventModifier.FILE_TREE }
                    : new WatchEvent.Modifier[0] ;
            watchPath.register(watchService, kinds, modifiers);
            log.log(INFO, "watchPath : {0}", watchPath);

            while (!Thread.currentThread().isInterrupted()) {

                WatchKey watchKey = watchService.take();
                Path dir = (Path) watchKey.watchable();

                List<Event> events = new ArrayList<>();

                for (WatchEvent<?> event : watchKey.pollEvents()) {

                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        log.log(WARNING, "OVERFLOW");
                        continue;
                    }

                    Path path = dir.resolve((Path) event.context());
                    boolean directory = Files.isDirectory(path);
                    if (!directory && (path.getFileName().toString().endsWith("~") ||
                                       path.getFileName().toString().endsWith(".swp"))) {
                        continue;
                    }

                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        log.log(DEBUG, "CREATE : {0}", path);
                        events.add(directory ? new Event.DirectoryCreate(path) : new Event.FileCreate(path));
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        log.log(DEBUG, "DELETE : {0}", path);
                        events.add(directory ? new Event.DirectoryDelete(path) : new Event.FileDelete(path));
                    } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        log.log(DEBUG, "MODIFY : {0}", path);
                        if (!directory) {
                            events.add(new Event.FileChange(path));
                        }
                    } else {
                        log.log(WARNING, "unknown kind: {0}", kind);
                    }
                }

                boolean valid = watchKey.reset();
                events.forEach(listener);
                if (!valid) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            log.log(INFO, "interrupted");
            Thread.currentThread().interrupt();
        }
        log.log(INFO, "closed watchService");
    }

}
