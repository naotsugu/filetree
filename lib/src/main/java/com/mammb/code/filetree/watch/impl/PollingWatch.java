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

import com.mammb.code.filetree.watch.Event;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.lang.System.Logger.Level.*;
import static java.util.function.Predicate.not;

/**
 * The PollingWatch.
 * @author Naotsugu Kobayashi
 */
public class PollingWatch {

    private static final System.Logger log = System.getLogger(PollingWatch.class.getName());

    private final Path path;

    private final List<PathMatcher> pathMatchers;

    private Map<Path, FileEntry> baseLine;

    PollingWatch(Path path, List<PathMatcher> pathMatchers) {
        this.path = Objects.requireNonNull(path);
        this.pathMatchers = (pathMatchers == null || pathMatchers.isEmpty())
                ? List.of(FileSystems.getDefault().getPathMatcher("glob:**"))
                : pathMatchers;
    }

    void reset() {
        baseLine = list(path);
    }

    public static void run(Path watchPath, Event.Listener listener) {
        run(watchPath, Duration.ofSeconds(10), listener, "");
    }

    public static void run(Path watchPath, Duration interval, Event.Listener listener, String... globs) {
        run(watchPath, interval, listener,
                Arrays.stream(globs)
                        .filter(not(String::isBlank))
                        .map(str -> str.startsWith("glob:") ? str : "glob:"+ str)
                        .map(str -> FileSystems.getDefault().getPathMatcher(str))
                        .toArray(PathMatcher[]::new));
    }

    public static void run(Path watchPath, Duration interval, Event.Listener listener, PathMatcher... pathMatchers) {

        Objects.requireNonNull(watchPath);
        Objects.requireNonNull(interval);
        Objects.requireNonNull(pathMatchers);
        Objects.requireNonNull(listener);

        final var watch = new PollingWatch(watchPath, List.of(pathMatchers));
        log.log(INFO, "watchPath : {0}", watchPath);

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(interval);
                watch.execute().forEach(listener);
            } catch (final InterruptedException ignored) {
                log.log(INFO, "interrupted");
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.log(INFO, "closed watchService");

    }

    private List<Event> execute() {

        if (baseLine == null || baseLine.isEmpty()) {
            reset();
            return List.of();
        }

        log.log(INFO, "start polling check... [{0}]", baseLine.size());

        final List<Event> events = new ArrayList<>();
        final Map<Path, FileEntry> currMap = list(path);
        final Map<Path, FileEntry> prevMap = baseLine;
        baseLine = null;

        for (Map.Entry<Path, FileEntry> currEntry : currMap.entrySet()) {
            final Path path = currEntry.getKey();
            final FileEntry curr = currEntry.getValue();
            final FileEntry prev = prevMap.remove(path);

            if (prev == null) {
                // new file
                log.log(DEBUG, "CREATE : {0}", curr.path());
                events.add(curr.attr().isDirectory()
                        ? new Event.DirectoryCreate(curr.path())
                        : new Event.FileCreate(curr.path()));
            } else {
                // existing file, check for changes
                if (prev.attr().isDirectory() && curr.attr().isDirectory()) {
                    // same directory, do nothing
                } else if (prev.attr().isDirectory()) {
                    log.log(DEBUG, "DELETE : {0}", prev.path());
                    log.log(DEBUG, "CREATE : {0}", curr.path());
                    events.add(new Event.DirectoryDelete(prev.path()));
                    events.add(new Event.FileCreate(curr.path()));
                } else if (curr.attr().isDirectory()) {
                    log.log(DEBUG, "DELETE : {0}", prev.path());
                    log.log(DEBUG, "CREATE : {0}", curr.path());
                    events.add(new Event.FileDelete(prev.path()));
                    events.add(new Event.DirectoryCreate(curr.path()));
                } else if (Objects.equals(prev.attr().lastModifiedTime(), curr.attr().lastModifiedTime()) &&
                           Objects.equals(prev.attr().size(), curr.attr().size())) {
                    // same file, do nothing
                } else {
                    log.log(DEBUG, "MODIFY : {0}", prev.path());
                    events.add(new Event.FileChange(prev.path()));
                }
            }
        }

        // remaining entries in prevMap are deleted files
        for (FileEntry prev : prevMap.values()) {
            log.log(DEBUG, "DELETE : {0}", prev.path());
            events.add(prev.attr().isDirectory()
                    ? new Event.DirectoryDelete(prev.path())
                    : new Event.FileDelete(prev.path()));
        }

        log.log(DEBUG, "events: {0}", events.size());
        baseLine = currMap;
        return events;
    }

    private Map<Path, FileEntry> list(Path path) {
        final Map<Path, FileEntry> entries = new HashMap<>();
        try {
            Files.walkFileTree(path, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (pathMatchers.stream().anyMatch(m -> m.matches(dir))) {
                        entries.put(dir, new FileEntry(dir, attrs));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (pathMatchers.stream().anyMatch(m -> m.matches(file))) {
                        entries.put(file, new FileEntry(file, attrs));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return entries;
    }

    record FileEntry(Path path, BasicFileAttributes attr) { }

}
