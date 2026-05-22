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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    private Map<Path, CacheEntry> entries;
    private long tickCount = -1;

    PollingWatch(Path path, List<PathMatcher> pathMatchers) {
        this.path = path.toAbsolutePath().normalize();
        this.pathMatchers = (pathMatchers == null || pathMatchers.isEmpty())
            ? List.of(FileSystems.getDefault().getPathMatcher("glob:**"))
            : pathMatchers;
    }

    public static void run(Path watchPath, Duration interval, Event.Listener listener, String... globs) {
        run(watchPath, interval, listener, Arrays.stream(globs)
                .filter(not(String::isBlank))
                .map(str -> str.startsWith("glob:") ? str : "glob:"+ str)
                .map(str -> FileSystems.getDefault().getPathMatcher(str))
                .toList());
    }

    public static void run(Path watchPath, Duration interval, Event.Listener listener, List<PathMatcher> pathMatchers) {

        Objects.requireNonNull(watchPath);
        Objects.requireNonNull(interval);
        Objects.requireNonNull(pathMatchers);
        Objects.requireNonNull(listener);

        log.log(INFO, "watchPath : {0}", watchPath);

        // create the periodic task to poll directories
        try (var scheduledExecutor = Executors.newSingleThreadScheduledExecutor()) {

            final var watch = new PollingWatch(watchPath, pathMatchers);
            final Runnable thunk = () -> watch.poll().forEach(listener);
            scheduledExecutor.scheduleAtFixedRate(thunk, 0, interval.getSeconds(), TimeUnit.SECONDS).get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.log(INFO, "interrupted");
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    synchronized void reset() {
        this.entries = null;
        this.tickCount = -1;
    }

    synchronized List<Event> poll() {
        try {

            if (tickCount < 0) {
                entries = buildCacheEntries(tickCount = 0);
                return List.of();
            }

            tickCount++; // update tick
            final List<Event> events = new ArrayList<>();

            // iterate over all entries in directory
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attr) {
                    if (pathMatchers.stream().anyMatch(m -> m.matches(dir))) {
                        CacheEntry e = entries.get(dir);
                        if (e == null) {
                            // new directory found
                            log.log(DEBUG, "CREATE : {0}", dir);
                            entries.put(dir, new CacheEntry(attr, tickCount));
                            events.add(new Event.DirectoryCreate(dir));
                        } else {
                            if (!e.attr().isDirectory()) {
                                // file -> directory
                                log.log(DEBUG, "DELETE : {0}", dir);
                                log.log(DEBUG, "CREATE : {0}", dir);
                                events.add(new Event.FileDelete(dir));
                                events.add(new Event.DirectoryCreate(dir));
                            }
                            // entry in cache so update poll time
                            e.update(attr, tickCount);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
                    if (!(path.getFileName().toString().endsWith("~") ||
                          path.getFileName().toString().endsWith(".swp")
                         ) && pathMatchers.stream().anyMatch(m -> m.matches(file))) {
                        CacheEntry e = entries.get(file);
                        if (e == null) {
                            // new file found
                            log.log(DEBUG, "CREATE : {0}", file);
                            entries.put(file, new CacheEntry(attr, tickCount));
                            events.add(new Event.FileCreate(file));
                        } else {
                            if (e.attr().isDirectory()) {
                                // directory -> file
                                log.log(DEBUG, "DELETE : {0}", file);
                                log.log(DEBUG, "CREATE : {0}", file);
                                events.add(new Event.DirectoryDelete(file));
                                events.add(new Event.FileCreate(file));
                            } else {
                                if (!(Objects.equals(e.attr().lastModifiedTime(), attr.lastModifiedTime()) &&
                                    Objects.equals(e.attr().size(), attr.size()))) {
                                    // file has changed
                                    log.log(DEBUG, "MODIFY : {0}", file);
                                    events.add(new Event.FileChange(file));
                                }
                            }
                            // entry in cache so update poll time
                            e.update(attr, tickCount);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            // iterate over cache to detect entries that have been deleted
            Iterator<Map.Entry<Path, CacheEntry>> i = entries.entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry<Path, CacheEntry> mapEntry = i.next();
                CacheEntry entry = mapEntry.getValue();
                if (entry.lastTickCount() != tickCount) {
                    Path path = mapEntry.getKey();
                    // remove from map and queue delete event
                    i.remove();
                    log.log(DEBUG, "DELETE : {0}", path);
                    events.add(entry.attr().isDirectory()
                        ? new Event.DirectoryDelete(path)
                        : new Event.FileDelete(path));
                }
            }

            log.log(DEBUG, "events: {0}", events.size());
            return events;

        } catch (IOException e) {
            reset();
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the initial entries in the directory.
     * @param tickCount initial tick-count
     * @return the initial entries
     * @throws IOException io error
     */
    private Map<Path, CacheEntry> buildCacheEntries(long tickCount) throws IOException {
        Map<Path, CacheEntry> map = new HashMap<>();
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attr) {
                if (pathMatchers.stream().anyMatch(m -> m.matches(dir))) {
                    map.put(dir, new CacheEntry(attr, tickCount));
                }
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
                if (!(path.getFileName().toString().endsWith("~") ||
                      path.getFileName().toString().endsWith(".swp")
                     ) && pathMatchers.stream().anyMatch(m -> m.matches(file))) {
                    map.put(file, new CacheEntry(attr, tickCount));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        log.log(DEBUG, "cacheEntry size: {0}", map.size());
        return map;
    }

    /**
     * Entry in cache to record file attributes and tick-count
     */
    private static class CacheEntry {
        private BasicFileAttributes attr;
        private long lastTickCount;
        CacheEntry(BasicFileAttributes attr, long lastTickCount) {
            this.attr = attr;
            this.lastTickCount = lastTickCount;
        }
        long lastTickCount() { return lastTickCount; }
        BasicFileAttributes attr() { return attr; }
        void update(BasicFileAttributes attr, long tickCount) {
            this.attr = attr;
            this.lastTickCount = tickCount;
        }
    }

}
