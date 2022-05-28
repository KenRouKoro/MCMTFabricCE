package net.himeki.mcmtfabric.mixin.tech;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.reborn.energy.api.EnergyStorage;
import techreborn.blockentity.cable.CableBlockEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mixin(CableBlockEntity.class)
public class CableBlockEntityMixin {

    @Shadow(remap = false)
    List<CableBlockEntity.CableTarget> targets;


    @Redirect(remap = false,method = "appendTargets", at = @At(value = "FIELD", target = "Ltechreborn/blockentity/cable/CableBlockEntity;targets:Ljava/util/List;", opcode = Opcodes.PUTFIELD))
    private void synList(CableBlockEntity instance, List<CableBlockEntity.CableTarget> value) {
        targets = Collections.synchronizedList(value);
    }
}
