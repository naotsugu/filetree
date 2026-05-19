# file-tree

## Watch

This library allows you to watch a file tree, including all subdirectories.

To watch a specific directory:

```java
Path path = Path.of("dir/");
try (var ignored = FileTreeWatch.run(path, System.out::println)) {
    IO.readln();
}
```

To synchronize changes from a source directory to a target directory:

```java
Path source = Path.of("src/main");
Path target = Path.of("src/dest");
try (var watch = FileTreeWatch.sync(source, target, "**/*.java", "**/*.html")) {
    IO.readln();
}
```

## `java.nio.file.WatchService`

The standard `java.nio.file.WatchService` can only watch a single directory. The implementation of this watching mechanism varies by platform:

- **Linux**: Uses `inotify` for OS-level monitoring.
- **Windows**: Uses the `ReadDirectoryChangesW` function for OS-level monitoring.
- **Other OS**: Uses periodic polling (every 2 seconds) implemented in Java.

To watch an entire directory tree, including subdirectories, you would typically need to recursively register each subdirectory with the `WatchService`. This approach has several drawbacks:

- **Windows**: Directories being watched with `ReadDirectoryChangesW` cannot be deleted.
- **Other OS**: Polling each subdirectory individually leads to performance degradation.

This library addresses these issues with the following platform-specific implementations:

- **Linux**: Recursively registers each subdirectory with the `WatchService`.
- **Windows**: Uses the `bWatchSubtree` option of `ReadDirectoryChangesW` to watch the entire subtree.
- **Other OS**: Polls only the top-level parent directory to detect changes in any subdirectory.
