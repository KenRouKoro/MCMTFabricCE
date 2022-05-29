package net.himeki.mcmtfabric.mixin;

import net.himeki.mcmtfabric.ParallelProcessor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.chunk.BlockEntityTickInvoker;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class WorldMixin implements WorldAccess, AutoCloseable {
    @Shadow @Final @Mutable
    private Thread thread;

    @Shadow public abstract WorldChunk getChunk(int i, int j);

    @Shadow public abstract WorldChunk getWorldChunk(BlockPos pos);

    @Redirect(method = "tickBlockEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/BlockEntityTickInvoker;tick()V"))
    private void overwriteTick(BlockEntityTickInvoker blockEntityTickInvoker) {
        ParallelProcessor.callTileEntityTick(blockEntityTickInvoker, (World) (Object) this);
    }

    @Redirect(method = "getBlockEntity", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;currentThread()Ljava/lang/Thread;"))
    private Thread overwriteCurrentThread() {
        return this.thread;
    }
    @Inject(method = "getChunk(II)Lnet/minecraft/world/chunk/WorldChunk;",at = @At("HEAD"),cancellable = true)
    private void fixGetChunk(int i, int j, CallbackInfoReturnable<WorldChunk> cir){
        Chunk backChunk = getChunk(i, j, ChunkStatus.FULL);
        try {
            cir.setReturnValue((WorldChunk) backChunk);
        }catch (Exception e) {
            cir.setReturnValue(getChunkManager().getWorldChunk(i, j));
        }
        cir.cancel();
    }
}