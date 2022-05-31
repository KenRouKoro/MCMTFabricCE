package net.himeki.mcmtfabric.mixin.create;

import com.simibubi.create.content.contraptions.KineticNetwork;
import com.simibubi.create.content.contraptions.base.KineticTileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(KineticNetwork.class)
public class KineticNetworkMixin {
    @Shadow(remap = false)
    public boolean containsFlywheel;
    @Shadow(remap = false)
    public Map<KineticTileEntity, Float> sources = new ConcurrentHashMap<>();
    @Shadow(remap = false)
    public Map<KineticTileEntity, Float> members = new ConcurrentHashMap<>();

    /*
    @Inject(remap = false,method = "<init>()V",at = @At("HEAD"),cancellable = true)
    private static void mixinKineticNetworkInit(CallbackInfo ci){
    }
    */

}
