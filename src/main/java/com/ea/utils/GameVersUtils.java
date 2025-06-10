package com.ea.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class GameVersUtils {

    public static final String PSP_MOHGPS071 = "PSP/MOHGPS071";
    public static final String PSP_MOH07 = "PSP/MOH07";
    public static final String PSP_MOH08 = "PSP/MOH08";
    public static final String WII_MOH08 = "WII/MOH08";

    public static String getGameNameByVersion(String gameVersion) {
        switch (gameVersion) {
            case PSP_MOHGPS071:
                return "psp/mohh (host)";
            case PSP_MOH07:
                return "psp/mohh";
            case PSP_MOH08:
                return "psp/mohh2";
            case WII_MOH08:
                return "wii/mohh2";
            default:
                log.warn("Unknown game version: {}", gameVersion);
                return "unknown Game";
        }
    }


}
