package com.example.teamwars;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.Map;

/** Хранит зоны захвата в мире (сохраняется вместе с миром). */
public class ZoneData extends SavedData {

    public static class Zone {
        public String name = "";
        public String dim = "minecraft:overworld";
        public AABB box = new AABB(0, 0, 0, 1, 1, 1);
        public int seconds = 30;      // сколько секунд нужно для захвата
        public int progress = 0;      // текущий прогресс захвата
        public String owner = "";     // имя команды-владельца
        public String capturing = ""; // команда, которая сейчас захватывает

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("name", name);
            t.putString("dim", dim);
            t.putDouble("x1", box.minX);
            t.putDouble("y1", box.minY);
            t.putDouble("z1", box.minZ);
            t.putDouble("x2", box.maxX);
            t.putDouble("y2", box.maxY);
            t.putDouble("z2", box.maxZ);
            t.putInt("seconds", seconds);
            t.putInt("progress", progress);
            t.putString("owner", owner);
            t.putString("capturing", capturing);
            return t;
        }

        static Zone load(CompoundTag t) {
            Zone z = new Zone();
            z.name = t.getString("name");
            z.dim = t.getString("dim");
            z.box = new AABB(t.getDouble("x1"), t.getDouble("y1"), t.getDouble("z1"),
                    t.getDouble("x2"), t.getDouble("y2"), t.getDouble("z2"));
            z.seconds = t.getInt("seconds");
            z.progress = t.getInt("progress");
            z.owner = t.getString("owner");
            z.capturing = t.getString("capturing");
            return z;
        }
    }

    public final Map<String, Zone> zones = new LinkedHashMap<>();

    public static ZoneData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(ZoneData::load, ZoneData::new, "teamwars_zones");
    }

    public static ZoneData load(CompoundTag tag) {
        ZoneData data = new ZoneData();
        ListTag list = tag.getList("zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Zone z = Zone.load(list.getCompound(i));
            data.zones.put(z.name, z);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Zone z : zones.values()) list.add(z.save());
        tag.put("zones", list);
        return tag;
    }
}
