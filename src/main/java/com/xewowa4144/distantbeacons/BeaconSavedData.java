/** Persistent world data containing known beacon positions and their last known beam state. */
package com.xewowa4144.distantbeacons;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BeaconSavedData extends SavedData {
    public static final Codec<SavedSection> SECTION_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.fieldOf("color").forGetter(SavedSection::color),
            Codec.INT.fieldOf("height").forGetter(SavedSection::height)
        ).apply(instance, SavedSection::new)
    );

    public static final Codec<SavedBeacon> BEACON_CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("dimension").forGetter(SavedBeacon::dimension),
            Codec.INT.fieldOf("x").forGetter(SavedBeacon::x),
            Codec.INT.fieldOf("y").forGetter(SavedBeacon::y),
            Codec.INT.fieldOf("z").forGetter(SavedBeacon::z),
            Codec.BOOL.optionalFieldOf("active", false).forGetter(SavedBeacon::active),
            SECTION_CODEC.listOf().optionalFieldOf("sections", List.of()).forGetter(SavedBeacon::sections)
        ).apply(instance, SavedBeacon::new)
    );

    public static final Codec<BeaconSavedData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BEACON_CODEC.listOf().fieldOf("beacons").forGetter(data -> List.copyOf(data.beacons.values()))
        ).apply(instance, BeaconSavedData::new)
    );

    // Stored under the world data directory and automatically serialized by Minecraft's SavedData system.
    public static final SavedDataType<BeaconSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(DistantBeaconsMod.MOD_ID, "beacons"),
        BeaconSavedData::new,
        CODEC,
        null
    );

    private final Map<BeaconKey, SavedBeacon> beacons;

    public BeaconSavedData() {
        this.beacons = new LinkedHashMap<>();
    }

    private BeaconSavedData(List<SavedBeacon> savedBeacons) {
        this();
        for (SavedBeacon beacon : savedBeacons) {
            beacons.put(BeaconKey.of(beacon), beacon);
        }
    }

    public List<SavedBeacon> beacons() {
        return List.copyOf(beacons.values());
    }

    public boolean add(String dimension, int x, int y, int z) {
        return addOrUpdate(dimension, x, y, z, false, List.of());
    }

    public boolean updateState(
        String dimension,
        int x,
        int y,
        int z,
        boolean active,
        List<BeaconBeamPayload.Section> sections
    ) {
        return addOrUpdate(dimension, x, y, z, active, sections);
    }

    private boolean addOrUpdate(
        String dimension,
        int x,
        int y,
        int z,
        boolean active,
        List<BeaconBeamPayload.Section> sections
    ) {
        SavedBeacon replacement = new SavedBeacon(
            dimension,
            x, y, z,
            active,
            sections.stream()
                .map(section -> new SavedSection(section.color(), section.height()))
                .toList()
        );
        BeaconKey key = BeaconKey.of(replacement);
        SavedBeacon previous = beacons.put(key, replacement);
        if (replacement.equals(previous)) {
            return false;
        }
        setDirty();
        return true;
    }

    public boolean remove(String dimension, int x, int y, int z) {
        if (beacons.remove(new BeaconKey(dimension, x, y, z)) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    private record BeaconKey(String dimension, int x, int y, int z) {
        private static BeaconKey of(SavedBeacon beacon) {
            return new BeaconKey(beacon.dimension(), beacon.x(), beacon.y(), beacon.z());
        }
    }

    public record SavedSection(int color, int height) {}

    public record SavedBeacon(
        String dimension,
        int x,
        int y,
        int z,
        boolean active,
        List<SavedSection> sections
    ) {}
}
