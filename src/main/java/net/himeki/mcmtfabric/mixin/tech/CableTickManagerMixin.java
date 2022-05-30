package net.himeki.mcmtfabric.mixin.tech;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import techreborn.blockentity.cable.CableBlockEntity;
import techreborn.blockentity.cable.CableTickManager;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;


@Mixin(CableTickManager.class)
public class CableTickManagerMixin {
    @Shadow(remap = false)
    @Final
    @Mutable
    private static List<CableBlockEntity> cableList = new CopyOnWriteArrayList<>();
    @Shadow(remap = false)
    @Final
    @Mutable
    private static List<Object> targetStorages = new CopyOnWriteArrayList<>();
    @Shadow(remap = false)
    @Final
    @Mutable
    private static Deque<CableBlockEntity> bfsQueue = new LinkedBlockingDeque<>();

}
