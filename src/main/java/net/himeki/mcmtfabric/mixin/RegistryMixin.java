package net.himeki.mcmtfabric.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.IndexedIterable;
import net.minecraft.util.registry.MutableRegistry;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Registry.class)
public abstract class RegistryMixin <T> implements IndexedIterable<T> {
    //static protected
    @Inject(method = "register(Lnet/minecraft/util/registry/Registry;Lnet/minecraft/util/registry/RegistryKey;Ljava/lang/Object;)Ljava/lang/Object;",
        at = @At("HEAD"),cancellable = true
    )
    private static <V, T extends V> void register(Registry<V> registry, RegistryKey<V> key, T entry, CallbackInfoReturnable<T> cir) {
        try {
            ((MutableRegistry)registry).add(key, entry, Lifecycle.stable());
            cir.setReturnValue(entry);
            cir.cancel();
        }catch (RuntimeException e){
            Logger logger = LoggerFactory.getLogger("MCMT");
            logger.error("MCMT检测到Registry(注册)抛出异常："+e.getMessage());
            cir.setReturnValue(entry);
        }
    }
}
