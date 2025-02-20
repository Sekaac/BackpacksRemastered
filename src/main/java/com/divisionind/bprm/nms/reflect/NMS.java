/*
 * BackpacksRemastered - remastered version of the popular Backpacks plugin
 * Copyright (C) 2019 - 2020, Andrew Howard, <divisionind.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.divisionind.bprm.nms.reflect;

import com.divisionind.bprm.FakeBackpackViewer;
import com.divisionind.bprm.nms.KnownVersion;
import com.divisionind.bprm.nms.reflect.ex.NMSLoadException;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class NMS {

    public static final String VERSION = getVersion();
    public static final String SERVER = "net.minecraft.server." + VERSION + ".";
    public static final String CRAFT = "org.bukkit.craftbukkit." + VERSION + ".";

    public static HumanEntity FAKE_VIEWER;
    public static Field TileEntity_world;
    // TODO add NMSField and NMSConstructor managers

    private static Object DIMENSION_MANAGER_OVERWORLD;

    public static List<Exception> initialize() {
        List<Exception> exceptions = new ArrayList<>();

        // initialize classes
        for (NMSClass nmsClass : NMSClass.values()) {
            try {
                nmsClass.init();
            } catch (Exception e) {
                exceptions.add(new NMSLoadException("NMSClass: " + nmsClass.name(), e));
            }
        }

        // ensure fuzzy-lookup table gets cleaned
        NMSClass.cleanup();

        // init all getters and setters for the various NBTTag data values
        NBTType.COMPOUND.setClassType(NMSClass.NBTBase.getClazz());
        for (NBTType type : NBTType.values()) {
            try {
                type.init(NMSClass.NBTTagCompound.getClazz());
            } catch (Exception e) {
                exceptions.add(new NMSLoadException("NMSType: " + type.name(), e));
            }
        }

        // initialize methods
        for (NMSMethod nmsMethod : NMSMethod.values()) {
            try {
                nmsMethod.init();
            } catch (Exception e) {
                exceptions.add(new NMSLoadException("NMSMethod: " + nmsMethod.name(), e));
            }
        }

        // initialize fake viewer using reflection (so that it can support spigot + craftbukkit)
        FAKE_VIEWER = (FakeBackpackViewer) Proxy.newProxyInstance(FakeBackpackViewer.class.getClassLoader(),
                new Class[]{FakeBackpackViewer.class},
                (proxy, method, args) -> {
                    Class type = method.getReturnType();

                    if (type.equals(boolean.class)) return false;
                    if (type.equals(int.class)) return 0;
                    if (type.equals(double.class)) return 0D;
                    if (type.equals(float.class)) return 0F;
                    if (type.equals(long.class)) return 0L;
                    if (type.equals(short.class)) return (short)0;
                    if (type.equals(byte.class)) return (byte)0;

                    return null;
                });

        try {
            if (KnownVersion.v1_17_R1.isBefore()) {
                Field overWorldField = NMSClass.DimensionManager.getClazz().getDeclaredField("OVERWORLD");
                DIMENSION_MANAGER_OVERWORLD = overWorldField.get(null);

                TileEntity_world = NMSClass.TileEntity.getClazz().getDeclaredField("world");
                TileEntity_world.setAccessible(true);
            } else {
                Field overWorldKeyField = NMSClass.World.getClazz().getDeclaredField("f");
                DIMENSION_MANAGER_OVERWORLD = overWorldKeyField.get(null);

                TileEntity_world = NMSClass.TileEntity.getClazz().getDeclaredField("n");
                TileEntity_world.setAccessible(true);
            }
        } catch (Exception e) {
            exceptions.add(e);
        }

        return exceptions;
    }

    public static Object getWorldServer(Object dedicatedServer) throws InvocationTargetException, IllegalAccessException {
        if (KnownVersion.v1_13_R1.isBefore()) {
            return NMSMethod.getWorldServer.getMethod().invoke(dedicatedServer, 0);
        } else {
            return NMSMethod.getWorldServer.getMethod().invoke(dedicatedServer, NMS.DIMENSION_MANAGER_OVERWORLD);
        }
    }

    private static String getVersion() {
        String name = Bukkit.getServer().getClass().getPackage().getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }
}
