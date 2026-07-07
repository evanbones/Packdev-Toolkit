package com.evandev.packdev_toolkit.platform;

import com.evandev.packdev_toolkit.platform.services.IPlatformHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FabricPlatformHelper implements IPlatformHelper {
    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isPhysicalClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public List<ModJarInfo> getLoadedMods() {
        List<ModJarInfo> mods = new ArrayList<>();
        for (var mc : FabricLoader.getInstance().getAllMods()) {
            var meta = mc.getMetadata();
            mods.add(new ModJarInfo(
                meta.getId(),
                meta.getName(),
                meta.getVersion().getFriendlyString(),
                mc.getRootPaths()
            ));
        }
        mods.sort(Comparator.comparing(m -> m.name().toLowerCase(Locale.ROOT)));
        return mods;
    }
}