package com.evandev.packdev_toolkit.platform.services;

import com.evandev.packdev_toolkit.platform.ModJarInfo;

import java.nio.file.Path;
import java.util.List;

public interface IPlatformHelper {

    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }

    Path getConfigDirectory();

    boolean isPhysicalClient();

    List<ModJarInfo> getLoadedMods();
}