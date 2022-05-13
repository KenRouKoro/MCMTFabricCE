package net.himeki.mcmtfabric.mixin.tech;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import techreborn.blockentity.cable.CableBlockEntity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Mixin( CableBlockEntity.class)
public class CableBlockEntityMixin {
    private static final Object synOBJ = new Object();
    @Redirect(method = "tick*", at = @At(value = "INVOKE", target = "Ltechreborn/blockentity/cable/CableTickManager;handleCableTick(Ltechreborn/blockentity/cable/CableBlockEntity;)V"),remap = false)
    private void handleCableTick(){
        synchronized (synOBJ){
            //CableTickManager.handleCableTick(this);
        }
    }
}
