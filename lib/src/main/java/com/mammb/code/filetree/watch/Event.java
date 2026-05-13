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

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The file watch event.
 * @author Naotsugu Kobayashi
 */
public interface Event {

    /**
     * Retrieves the path associated with the event.
     * @return the path associated with this event.
     */
    Path path();

    /**
     * Directory changed Event.
     * @param path The directory created.
     */
    record DirectoryCreate(Path path) implements Event { }

    /**
     * Directory deleted Event.
     * @param path The directory deleted.
     */
    record DirectoryDelete(Path path) implements Event { }

    /**
     * File changed Event.
     * @param path The file changed.
     */
    record FileChange(Path path) implements Event { }

    /**
     * File created Event.
     * @param path The file created.
     */
    record FileCreate(Path path) implements Event { }

    /**
     * File deleted Event.
     * @param path The file deleted.
     */
    record FileDelete(Path path) implements Event { }

    interface Listener extends Consumer<Event> {
        @Override
        void accept(Event event);
    }

}
