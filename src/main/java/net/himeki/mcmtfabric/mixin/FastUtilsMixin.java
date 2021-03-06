package net.himeki.mcmtfabric.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.entity.ai.pathing.BirdPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import org.spongepowered.asm.mixin.Mixin;
import techreborn.blockentity.cable.CableBlockEntity;
import techreborn.blockentity.cable.CableTickManager;

import java.util.ArrayList;
import java.util.HashMap;

@Mixin(value = {PathNodeNavigator.class,CableTickManager.class,CableBlockEntity.CableTarget.class,CableBlockEntity.class,BirdPathNodeMaker.class,Object2LongOpenHashMap.class,ReferenceOpenHashSet.class,ReferenceArrayList.class,Int2ObjectOpenHashMap.class, Long2ObjectOpenHashMap.class, LongLinkedOpenHashSet.class, ObjectOpenCustomHashSet.class, Long2LongOpenHashMap.class, Long2ObjectLinkedOpenHashMap.class},
        targets = {"it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap$ValueIterator", "it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap$KeySet", "it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap$KeyIterator",
                "it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap$MapEntrySet", "it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap$EntryIterator", "it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap$MapIterator",
                "it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap$MapEntry", "it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap$FastEntryIterator",  "it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap$MapIterator",
                "it.unimi.dsi.fastutil.objects.ReferenceArrayList$Spliterator","it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet$SetIterator","it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap","net.minecraft.entity.ai.pathing.BirdPathNodeMaker","techreborn.blockentity.cable.CableBlockEntity","techreborn.blockentity.cable.CableBlockEntity$CableTarget","techreborn.blockentity.cable.CableTickManager"
                ,"net.minecraft.entity.ai.pathing.PathNodeNavigator"
})
public class FastUtilsMixin {
}
