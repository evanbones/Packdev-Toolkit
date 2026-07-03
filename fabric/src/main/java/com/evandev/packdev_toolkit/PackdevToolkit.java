package com.evandev.packdev_toolkit;

import net.fabricmc.api.ModInitializer;

public class PackdevToolkit implements ModInitializer {

    @Override
    public void onInitialize() {
        CommonClass.init();
    }

}