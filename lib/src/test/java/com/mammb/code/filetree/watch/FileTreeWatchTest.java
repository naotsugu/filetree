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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The unit test of {@link FileTreeWatch}.
 * @author Naotsugu Kobayashi
 */
class FileTreeWatchTest {

    @Test
    void sync(@TempDir Path source, @TempDir Path target) throws Exception {
        try (var watch = FileTreeWatch.sync(source, target, "**/*.java", "**/*.html")) {
            Path path = source.resolve("l1/l2");
            Files.createDirectories(path);
            Thread.sleep(Duration.ofMillis(2500));
            Files.write(path.resolve("foo.java"), "some content".getBytes());
            Files.write(path.resolve("bar.html"), "some content".getBytes());
            Files.write(path.resolve("baz.xml"), "some content".getBytes());
            Thread.sleep(Duration.ofMillis(2500));
            try (Stream<Path> stream = Files.list(target.resolve("l1/l2"))) {
                var ls = stream.sorted().toList();
                assertEquals(2, ls.size());
                assertEquals(Path.of("l1/l2/bar.html"), target.relativize(ls.get(0)));
                assertEquals(Path.of("l1/l2/foo.java"), target.relativize(ls.get(1)));
            }
        }
    }

    @Test
    void syncs() throws Exception {
        Path source = Path.of("src/main");
        Path target = Path.of("src/dest");
        try (var watch = FileTreeWatch.sync(source, target, "**/*.java", "**/*.html")) {
            IO.readln();
        }
    }

}
