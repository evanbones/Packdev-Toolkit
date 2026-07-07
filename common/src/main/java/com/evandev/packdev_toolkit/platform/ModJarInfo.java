package com.evandev.packdev_toolkit.platform;

import java.nio.file.Path;
import java.util.List;

public record ModJarInfo(String id, String name, String version, List<Path> rootPaths) {
}
