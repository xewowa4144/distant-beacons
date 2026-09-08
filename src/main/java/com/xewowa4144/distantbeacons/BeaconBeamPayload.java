/** Network payload containing one beacon's position, dimension, active state, and beam sections. */
package com.xewowa4144.distantbeacons;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record BeaconBeamPayload(
    String dimension,
    int x,
    int y,
    int z,
    boolean active,
    List<Section> sections
) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(
        DistantBeaconsMod.MOD_ID, "beacon_beam"
    );

    public static final Type<BeaconBeamPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, BeaconBeamPayload> CODEC =
        StreamCodec.of(BeaconBeamPayload::write, BeaconBeamPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Serialize one beacon and all of its beam sections into the network buffer.
    private static void write(RegistryFriendlyByteBuf buf, BeaconBeamPayload payload) {
        buf.writeUtf(payload.dimension());
        buf.writeInt(payload.x());
        buf.writeInt(payload.y());
        buf.writeInt(payload.z());
        buf.writeBoolean(payload.active());
        buf.writeVarInt(payload.sections().size());
        for (Section section : payload.sections()) {
            buf.writeInt(section.color());
            buf.writeVarInt(section.height());
        }
    }

    // Deserialize a beacon update received from the server.
    private static BeaconBeamPayload read(RegistryFriendlyByteBuf buf) {
        String dimension = buf.readUtf();
        int x = buf.readInt();
        int y = buf.readInt();
        int z = buf.readInt();
        boolean active = buf.readBoolean();
        int count = buf.readVarInt();
        List<Section> sections = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            sections.add(new Section(buf.readInt(), buf.readVarInt()));
        }
        return new BeaconBeamPayload(dimension, x, y, z, active, List.copyOf(sections));
    }

    public record Section(int color, int height) {}
}
