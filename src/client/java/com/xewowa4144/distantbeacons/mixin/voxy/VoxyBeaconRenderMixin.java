package com.xewowa4144.distantbeacons.mixin.voxy;

import com.xewowa4144.distantbeacons.VoxyBeaconRenderer;
import me.cortex.voxy.client.core.RenderProperties;
import me.cortex.voxy.client.core.rendering.Viewport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.AbstractRenderPipeline")
public abstract class VoxyBeaconRenderMixin {
    @org.spongepowered.asm.mixin.Shadow
    public RenderProperties properties;
    @Inject(
        method = "runPipeline(Lme/cortex/voxy/client/core/rendering/Viewport;IIII)V",
        at = @At(
            value = "INVOKE",
            target = "Lme/cortex/voxy/client/core/rendering/section/backend/AbstractSectionRenderer;renderTranslucent(Lme/cortex/voxy/client/core/rendering/Viewport;)V",
            shift = At.Shift.AFTER
        ),
        require = 0
    )
    private void distantbeacons$renderVoxyBeams(Viewport<?> viewport, int sourceDepthTexture,
                                                   int sourceColourTexture, int srcWidth, int srcHeight,
                                                   CallbackInfo ci) {
        VoxyBeaconRenderer.render(viewport, this.properties.closerEqualDepthCompare());
    }
}
