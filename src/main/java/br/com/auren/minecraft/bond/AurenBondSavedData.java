package br.com.auren.minecraft.bond;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Authoritative world-level record. Inventory tokens are ONLY a presentation;
 * the real NBT lives in Overworld SavedData, never inside an ItemStack.
 *
 * BOUND is written before the live entity is discarded. When loading chunks,
 * any entity with a UUID recorded as BOUND is quarantined.
 */
public final class AurenBondSavedData extends SavedData {
    private static final String FILE = "aurenminecraft_head_bond_v1";
    private final Map<UUID, Entry> records = new HashMap<>();

    public record Entry(UUID owner, UUID dragon, boolean bound, CompoundTag entityNbt,
                        String worldKey) {}

    public static AurenBondSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(AurenBondSavedData::new, AurenBondSavedData::load), FILE);
    }

    private static AurenBondSavedData load(CompoundTag nbt, HolderLookup.Provider registries) {
        AurenBondSavedData result = new AurenBondSavedData();
        ListTag list = nbt.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); ++i) {
            CompoundTag row = list.getCompound(i);
            if (!row.hasUUID("Owner") || !row.hasUUID("Dragon")
                    || !row.contains("EntityNbt", Tag.TAG_COMPOUND)) continue;
            UUID owner = row.getUUID("Owner");
            result.records.put(owner, new Entry(owner, row.getUUID("Dragon"),
                    row.getBoolean("Bound"), row.getCompound("EntityNbt").copy(),
                    row.getString("WorldKey")));
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag output, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : records.values()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Owner", entry.owner());
            row.putUUID("Dragon", entry.dragon());
            row.putBoolean("Bound", entry.bound());
            row.put("EntityNbt", entry.entityNbt().copy());
            row.putString("WorldKey", entry.worldKey());
            list.add(row);
        }
        output.put("Entries", list);
        return output;
    }

    public Entry forOwner(UUID id) {
        return records.get(id);
    }

    public boolean isBoundDragon(UUID id) {
        return records.values().stream().anyMatch(e -> e.bound() && e.dragon().equals(id));
    }

    public boolean isKnownOtherDragon(UUID owner, UUID dragon) {
        return records.values().stream().anyMatch(e ->
                !e.owner().equals(owner) && e.dragon().equals(dragon));
    }

    public void put(Entry value) {
        records.put(value.owner(), value);
        setDirty();
    }
}
