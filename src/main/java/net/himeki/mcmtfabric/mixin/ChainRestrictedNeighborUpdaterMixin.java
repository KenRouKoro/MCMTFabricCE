package net.himeki.mcmtfabric.mixin;

import net.minecraft.world.block.ChainRestrictedNeighborUpdater;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mixin(ChainRestrictedNeighborUpdater.class)
public class ChainRestrictedNeighborUpdaterMixin {
    @Shadow
    @Final
    @Mutable
    private final List<ChainRestrictedNeighborUpdater.Entry> pending = new CopyOnWriteArrayList<>();
}
