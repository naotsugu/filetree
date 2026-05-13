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
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.mammb.code.filetree.watch.Event.*;
import static java.lang.System.Logger.Level.*;
import static java.util.function.Predicate.not;

/**
 * The SyncAction.
 * @author Naotsugu Kobayashi
 */
public class SyncAction implements Listener {

    private static final System.Logger log = System.getLogger(SyncAction.class.getName());

    private final Path source;
    private final Path target;
    private final List<PathMatcher> pathMatchers;

    public SyncAction(Path source, Path target, List<PathMatcher> pathMatchers) {
        this.source = Objects.requireNonNull(source).toAbsolutePath().normalize();
        this.target = Objects.requireNonNull(target).toAbsolutePath().normalize();
        this.pathMatchers = (pathMatchers == null || pathMatchers.isEmpty())
                ? List.of(FileSystems.getDefault().getPathMatcher("glob:**"))
                : pathMatchers;
    }


    public SyncAction(Path source, Path target, PathMatcher... pathMatchers) {
        this(source, target, (pathMatchers == null || pathMatchers.length == 0) ? List.of() : List.of(pathMatchers));
    }

    public SyncAction(Path source, Path target, String... globs) {
        this(source, target, Arrays.stream(globs)
                .filter(not(String::isBlank))
                .map(str -> str.startsWith("glob:") ? str : "glob:"+ str)
                .map(str -> FileSystems.getDefault().getPathMatcher(str))
                .toList());
    }

    @Override
    public void accept(Event event) {

        log.log(DEBUG, "Accept : {0}", event);

        switch (event) {
            case DirectoryDelete(Path p) ->
                deleteDirectory(target.resolve(source.relativize(p)));
            case FileDelete(Path p) ->
                deleteFile(target.resolve(source.relativize(p)));
            case FileCreate(Path p)
                    when pathMatchers.stream().anyMatch(m -> m.matches(source.relativize(p))) ->
                copyFile(p, target.resolve(source.relativize(p)));
            case FileChange(Path p)
                    when pathMatchers.stream().anyMatch(m -> m.matches(source.relativize(p))) ->
                copyFile(p, target.resolve(source.relativize(p)));
            case null, default -> {}
        }
    }

    private void deleteFile(Path path) {
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return;
        }
        try {
            Files.deleteIfExists(path);
            log.log(DEBUG, "delete : {0}", path);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void copyFile(Path from, Path to) {
        if (!Files.isRegularFile(from)) {
            return;
        }
        try {
            Files.createDirectories(to.getParent());
            Files.copy(from, to,
                    StandardCopyOption.COPY_ATTRIBUTES,
                    StandardCopyOption.REPLACE_EXISTING);
            log.log(DEBUG, "copy : {0}", to);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteDirectory(Path path) {
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            return;
        }
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            log.log(DEBUG, "delete : {0}", path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
