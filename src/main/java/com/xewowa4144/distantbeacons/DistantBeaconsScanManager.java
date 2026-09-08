/** Event-driven server-side registry that tracks beacons without scanning or force-loading the world. */
package com.xewowa4144.distantbeacons;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Event-driven beacon registry with persistent last-known beam state. */
public final class DistantBeaconsScanManager {
    private static MinecraftServer server;
    private static BeaconSavedData savedData;
    private static boolean eventsRegistered;
    private static long tickCounter;

    private static final Map<BeaconKey, BeaconCandidate> knownBeacons = new HashMap<>();
    private static final Set<BeaconKey> activeBeacons = new HashSet<>();
    private static final Map<BeaconKey, List<BeaconBeamPayload.Section>> lastBeamSections = new HashMap<>();
    private static final Map<UUID, ResourceKey<Level>> lastPlayerDimensions = new HashMap<>();

    private DistantBeaconsScanManager() {}

    public static void start(MinecraftServer minecraftServer) {
        server = minecraftServer;
        tickCounter = 0;
        knownBeacons.clear();
        activeBeacons.clear();
        lastBeamSections.clear();
        lastPlayerDimensions.clear();

        savedData = server.getDataStorage().computeIfAbsent(BeaconSavedData.TYPE);
        loadSavedRegistry();

        if (!eventsRegistered) {
            registerEvents();
            eventsRegistered = true;
        }
    }

    // Register the server lifecycle, beacon-load, block-break, and tick hooks once.
    private static void registerEvents() {
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
            if (blockEntity instanceof BeaconBlockEntity) {
                rememberLoadedBeacon(level, blockEntity.getBlockPos());
            }
        });

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level instanceof ServerLevel serverLevel) {
                BeaconKey key = new BeaconKey(serverLevel.dimension(), pos.getX(), pos.getY(), pos.getZ());
                if (state.is(Blocks.BEACON) || knownBeacons.containsKey(key)) {
                    removeBeacon(key);
                }
                recheckNearbyBeacons(serverLevel, pos);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(DistantBeaconsScanManager::onServerTick);

        ServerLifecycleEvents.SERVER_STOPPING.register(stoppingServer -> {
            if (server == stoppingServer) {
                server = null;
                savedData = null;
                knownBeacons.clear();
                activeBeacons.clear();
                lastBeamSections.clear();
                lastPlayerDimensions.clear();
            }
        });
    }

    // Record a naturally loaded beacon without forcing its chunk to load.
    private static void rememberLoadedBeacon(ServerLevel level, BlockPos pos) {
        if (server == null || server != level.getServer()) {
            return;
        }

        BeaconCandidate candidate = candidateFrom(level, pos);
        BeaconKey key = BeaconKey.of(candidate);
        if (knownBeacons.put(key, candidate) == null && savedData != null) {
            savedData.add(
                candidate.dimension().identifier().toString(),
                candidate.x(), candidate.y(), candidate.z()
            );
        }
    }

    private static void onServerTick(MinecraftServer minecraftServer) {
        if (server != minecraftServer) {
            return;
        }

        // A one-second interval is enough for beacon pyramid/color changes while avoiding per-tick scans.
        if (++tickCounter % 20 == 0) {
            refreshLoadedBeacons();
            syncPlayerDimensions();
        }
    }

    // Recheck only known beacons whose chunks are already loaded.
    private static void refreshLoadedBeacons() {
        if (server == null) {
            return;
        }

        for (BeaconCandidate candidate : knownBeacons.values()) {
            ServerLevel level = findLevel(candidate.dimension().identifier().toString());
            if (level == null) {
                continue;
            }

            BlockPos pos = new BlockPos(candidate.x(), candidate.y(), candidate.z());
            // Never request a missing chunk. The registry is intentionally passive for unloaded areas.
            if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof BeaconBlockEntity beacon) {
                updateBeaconState(candidate, beacon);
            } else if (!level.getBlockState(pos).is(Blocks.BEACON)) {
                removeBeacon(BeaconKey.of(candidate));
            }
        }
    }

    private static void loadSavedRegistry() {
        if (savedData == null || server == null) {
            return;
        }

        for (BeaconSavedData.SavedBeacon saved : savedData.beacons()) {
            ServerLevel level = findLevel(saved.dimension());
            if (level == null) {
                continue;
            }

            BeaconCandidate candidate = new BeaconCandidate(
                level.dimension(), saved.x(), saved.y(), saved.z()
            );
            BeaconKey key = BeaconKey.of(candidate);
            knownBeacons.put(key, candidate);

            if (saved.active() && !saved.sections().isEmpty()) {
                activeBeacons.add(key);
                lastBeamSections.put(key, saved.sections().stream()
                    .map(section -> new BeaconBeamPayload.Section(section.color(), section.height()))
                    .toList());
            }
        }
    }

    private static ServerLevel findLevel(String dimension) {
        if (server == null) {
            return null;
        }

        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    private static void recheckNearbyBeacons(ServerLevel level, BlockPos changedPos) {
        for (BeaconCandidate candidate : knownBeacons.values()) {
            if (!candidate.dimension().equals(level.dimension())) {
                continue;
            }

            if (Math.abs(candidate.x() - changedPos.getX()) > 8
                || Math.abs(candidate.z() - changedPos.getZ()) > 8
                || Math.abs(candidate.y() - changedPos.getY()) > 4) {
                continue;
            }

            BlockPos beaconPos = new BlockPos(candidate.x(), candidate.y(), candidate.z());
            if (!level.getBlockState(beaconPos).is(Blocks.BEACON)) {
                removeBeacon(BeaconKey.of(candidate));
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(beaconPos);
            if (blockEntity instanceof BeaconBlockEntity beacon) {
                updateBeaconState(candidate, beacon);
            }
        }
    }

    // Read the live vanilla beam state and synchronize it when it changes.
    private static void updateBeaconState(BeaconCandidate candidate, BeaconBlockEntity beacon) {
        BeaconKey key = BeaconKey.of(candidate);
        // Vanilla decides whether the beacon is active; we preserve its actual beam sections rather than
        // recreating beacon effects or pyramid logic ourselves.
        boolean active = !beacon.getBeamSections().isEmpty();
        boolean wasActive = activeBeacons.contains(key);

        if (active) {
            activeBeacons.add(key);
            List<BeaconBeamPayload.Section> sections = getBeamSections(beacon);
            List<BeaconBeamPayload.Section> previous = lastBeamSections.put(key, sections);

            if (savedData != null) {
                savedData.updateState(
                    candidate.dimension().identifier().toString(),
                    candidate.x(), candidate.y(), candidate.z(), true, sections
                );
            }

            if (!wasActive || !sections.equals(previous)) {
                sendBeamUpdate(candidate, true, sections);
            }
            return;
        }

        activeBeacons.remove(key);
        lastBeamSections.remove(key);

        if (savedData != null) {
            savedData.updateState(
                candidate.dimension().identifier().toString(),
                candidate.x(), candidate.y(), candidate.z(), false, List.of()
            );
        }

        if (wasActive) {
            sendBeamUpdate(candidate, false, List.of());
        }
    }

    private static void removeBeacon(BeaconKey key) {
        BeaconCandidate candidate = knownBeacons.remove(key);
        boolean wasActive = activeBeacons.remove(key);
        lastBeamSections.remove(key);

        if (savedData != null) {
            savedData.remove(key.dimension().identifier().toString(), key.x(), key.y(), key.z());
        }

        if (candidate != null && wasActive) {
            sendBeamUpdate(candidate, false, List.of());
        }
    }

    private static void syncPlayerDimensions() {
        if (server == null) {
            return;
        }

        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            online.add(id);
            ResourceKey<Level> current = player.level().dimension();
            ResourceKey<Level> previous = lastPlayerDimensions.put(id, current);
            if (previous == null || !previous.equals(current)) {
                sendAllActiveBeaconsTo(player);
            }
        }
        lastPlayerDimensions.keySet().removeIf(id -> !online.contains(id));
    }

    private static BeaconCandidate candidateFrom(ServerLevel level, BlockPos pos) {
        return new BeaconCandidate(
            level.dimension(), pos.getX(), pos.getY(), pos.getZ()
        );
    }

    private static List<BeaconBeamPayload.Section> getBeamSections(BeaconBlockEntity beacon) {
        List<BeaconBeamPayload.Section> result = new ArrayList<>(beacon.getBeamSections().size());
        for (BeaconBeamOwner.Section section : beacon.getBeamSections()) {
            result.add(new BeaconBeamPayload.Section(section.getColor(), section.getHeight()));
        }
        return List.copyOf(result);
    }

    private static void sendBeamUpdate(
        BeaconCandidate candidate,
        boolean active,
        List<BeaconBeamPayload.Section> sections
    ) {
        if (server == null) {
            return;
        }

        BeaconBeamPayload payload = new BeaconBeamPayload(
            candidate.dimension().identifier().toString(),
            candidate.x(), candidate.y(), candidate.z(), active, sections
        );

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension().equals(candidate.dimension())) {
                BeaconNetwork.send(player, payload);
            }
        }
    }

    // Send persisted active-beam data to a player after joining or changing dimensions.
    public static void sendAllActiveBeaconsTo(ServerPlayer player) {
        if (server == null) {
            return;
        }

        for (BeaconKey key : activeBeacons) {
            BeaconCandidate candidate = knownBeacons.get(key);
            if (candidate == null || !player.level().dimension().equals(candidate.dimension())) {
                continue;
            }

            List<BeaconBeamPayload.Section> sections = lastBeamSections.get(key);
            if (sections != null && !sections.isEmpty()) {
                BeaconNetwork.send(player, new BeaconBeamPayload(
                    candidate.dimension().identifier().toString(),
                    candidate.x(), candidate.y(), candidate.z(), true, sections
                ));
            }
        }
    }

    private record BeaconKey(ResourceKey<Level> dimension, int x, int y, int z) {
        private static BeaconKey of(BeaconCandidate candidate) {
            return new BeaconKey(candidate.dimension(), candidate.x(), candidate.y(), candidate.z());
        }
    }

    private record BeaconCandidate(
        ResourceKey<Level> dimension,
        int x,
        int y,
        int z
    ) {}
}
