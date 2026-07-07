package com.evandev.packdev_toolkit.platform;

import com.evandev.packdev_toolkit.platform.services.IPlatformHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isPhysicalClient() {
        return FMLLoader.getDist() == Dist.CLIENT;
    }

    @Override
    public List<ModJarInfo> getLoadedMods() {
        List<ModJarInfo> mods = new ArrayList<>();
        for (var mi : ModList.get().getMods()) {
            var fileInfo = mi.getOwningFile();
            List<Path> rootPaths = new ArrayList<>();
            if (fileInfo != null) {
                var file = fileInfo.getFile();
                if (file != null) {
                    rootPaths.add(file.findResource(""));
                }
            }
            mods.add(new ModJarInfo(
                    mi.getModId(),
                    mi.getDisplayName(),
                    mi.getVersion().toString(),
                    rootPaths
            ));
        }
        mods.sort(Comparator.comparing(m -> m.name().toLowerCase(Locale.ROOT)));
        return mods;
    }
}