package net.himeki.mcmtfabric.mixin.tech;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import techreborn.blockentity.cable.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;

@Mixin(targets = "techreborn.blockentity.cable.CableTickManager")
public abstract class CableTickManagerMixin implements TickIm{

    @Final
    @Shadow(remap = false)
    @Mutable
    private  static  Deque<CableBlockEntity> bfsQueue = new LinkedBlockingDeque<>();
    @Final
    @Shadow(remap = false)
    @Mutable
    private  static  List<CableBlockEntity> cableList = new CopyOnWriteArrayList<>();
    @Final
    @Shadow(remap = false)
    @Mutable
    private  static  List<?> targetStorages =  new CopyOnWriteArrayList<>();

    static private final Object synOBJ = new Object();

    @Invoker(value = "gatherCables",remap = false)
    private static void gatherCablesSh(CableBlockEntity start){
        throw new AssertionError();
    }

    @Invoker(value = "handleCableTick", remap = false)
    private static void handleCableTickSh(CableBlockEntity startingCable) {
        throw new AssertionError();
    }


    @Redirect(method = "handleCableTick", at = @At(value = "INVOKE", target = "Ltechreborn/blockentity/cable/CableTickManager;gatherCables(Ltechreborn/blockentity/cable/CableBlockEntity;)V"),remap = false)
    private static void runAway(CableBlockEntity adjCable){
        try {
            synchronized (synOBJ) {
                gatherCablesSh(adjCable);
            }
        }catch (NoSuchElementException ignored){
        }
    }
    @Override
    public void PublicHandleCableTick(CableBlockEntity startingCable){
        handleCableTickSh(startingCable);
    }

}
