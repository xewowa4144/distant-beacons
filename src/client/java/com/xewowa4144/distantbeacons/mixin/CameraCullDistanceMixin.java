/** Extends the camera frustum's far culling plane so extreme-distance beams are not discarded. */
package com.xewowa4144.distantbeacons.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;

import com.xewowa4144.distantbeacons.DistantBeaconsConfig;

/**
 * Extends Minecraft's camera culling depth so very distant custom geometry
 * (such as Distant Beacons's remote beams) is not rejected by the camera's
 * culling frustum around the normal render-distance-derived depth.
 *
 * This deliberately changes the culling frustum only. It does not alter the
 * player's configured render distance or load any additional chunks.
 */
@Mixin(Camera.class)
public abstract class CameraCullDistanceMixin {
    @Shadow
    private float depthFar;

    @Inject(
        method = "update",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;createProjectionMatrixForCulling()Lorg/joml/Matrix4f;"
        )
    )
    private void distantbeacons$extendCullDistance(DeltaTracker deltaTracker, CallbackInfo ci) {
        depthFar = Math.max(depthFar, DistantBeaconsConfig.cullDistance);
    }
}
