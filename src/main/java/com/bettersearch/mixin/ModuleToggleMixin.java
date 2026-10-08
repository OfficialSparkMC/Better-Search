package com.bettersearch.mixin;

import com.bettersearch.search.UsageTracker;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Learns preferences Wurst-style: every module toggle is counted,
 * so frequently used modules rank higher in Better Search.
 * Credits: OfficialSparkMC.
 */
@Mixin(Module.class)
public abstract class ModuleToggleMixin {
    @Inject(method = "toggle", at = @At("HEAD"))
    private void betterSearch$recordUsage(CallbackInfo ci) {
        try {
            Module self = (Module) (Object) this;
            // Don't learn the search module itself (it toggles on every open)
            if (self.name.equalsIgnoreCase("better-search")) return;
            UsageTracker.record(self);
            UsageTracker.save();
        } catch (Exception ignored) {
            // never break toggling because of usage tracking
        }
    }
}
