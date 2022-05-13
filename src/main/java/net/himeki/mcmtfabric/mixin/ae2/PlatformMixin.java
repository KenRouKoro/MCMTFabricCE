package net.himeki.mcmtfabric.mixin.ae2;

import appeng.core.AppEng;
import appeng.util.Platform;
import net.himeki.mcmtfabric.ParallelProcessor;
import org.checkerframework.checker.units.qual.A;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Platform.class)
public class PlatformMixin {
    @Inject( method = "isServer",at = @At("HEAD"),cancellable = true,remap = false)
    private static void isServerEX(CallbackInfoReturnable<Boolean> cir){
        try {
            var currentServer = AppEng.instance().getCurrentServer();
            //cir.setReturnValue(currentServer != null && ParallelProcessor.serverExecutionThreadPatch(currentServer));
            cir.setReturnValue(currentServer != null && (ParallelProcessor.serverExecutionThreadPatch(currentServer)||(Thread.currentThread().getName().equalsIgnoreCase("Server thread"))));
            //System.out.println((currentServer != null && ParallelProcessor.serverExecutionThreadPatch(currentServer)) + "  test  "+Thread.currentThread().getName());
            cir.cancel();
        } catch (NullPointerException npe) {
            // FIXME TEST HACKS
            // Running from tests: AppEng.instance() is null... :(
            cir.setReturnValue(false);
            cir.cancel();
        }
    }


}
