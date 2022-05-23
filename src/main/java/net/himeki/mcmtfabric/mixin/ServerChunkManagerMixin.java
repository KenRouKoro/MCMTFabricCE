package net.himeki.mcmtfabric.mixin;

import com.mojang.datafixers.util.Either;
import net.himeki.mcmtfabric.DebugHookTerminator;
import net.himeki.mcmtfabric.ParallelProcessor;
import net.minecraft.server.world.*;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkManager;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("OverwriteModifiers")
@Mixin(value = ServerChunkManager.class,priority = 2147483647)
public abstract class ServerChunkManagerMixin extends ChunkManager {

    @Shadow
    @Final
    @Mutable
    public ServerChunkManager.MainThreadExecutor mainThreadExecutor;

    @Shadow
    @Final
    @Mutable
    private ChunkStatus[] chunkStatusCache;
    @Shadow
    @Final
    @Mutable
    private long[] chunkPosCache;
    @Shadow
    @Final
    @Mutable
    private  Chunk[] chunkCache;
    @Shadow
    @Final
    @Mutable
    ServerWorld world;


    @Shadow
    protected abstract void putInCache(long pos, Chunk chunk, ChunkStatus status);
    @Shadow
    protected abstract CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> getChunkFuture(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create);

    @Redirect(method = "tickChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;tickChunk(Lnet/minecraft/world/chunk/WorldChunk;I)V"))
    private void overwriteTickChunk(ServerWorld serverWorld, WorldChunk chunk, int randomTickSpeed) {
        ParallelProcessor.callTickChunks(serverWorld, chunk, randomTickSpeed);
    }

    @Redirect(method = /*{"getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;",*/ "getWorldChunk"/*}*/, at = @At(value = "FIELD", target = "Lnet/minecraft/server/world/ServerChunkManager;serverThread:Ljava/lang/Thread;", opcode = Opcodes.GETFIELD))
    private Thread overwriteServerThread(ServerChunkManager mgr) {
        return Thread.currentThread();
    }
/*
    @Redirect(method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;visit(Ljava/lang/String;)V"))
    private void overwriteProfilerVisit(Profiler instance, String s) {
        if (ParallelProcessor.shouldThreadChunks())
            return;
        else instance.visit("getChunkCacheMiss");
    }



    @Inject(method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerChunkManager$MainThreadExecutor;runTasks(Ljava/util/function/BooleanSupplier;)V"), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void callCompletableFutureHook(int x, int z, ChunkStatus leastStatus, boolean create, CallbackInfoReturnable<Chunk> cir, Profiler profiler, long chunkPos, CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> i) {
        DebugHookTerminator.chunkLoadDrive(this.mainThreadExecutor, i::isDone, (ServerChunkManager) (Object) this, i, chunkPos);

    }

 */
    @Nullable
    @Override
    public Chunk getChunk(int x, int z, ChunkStatus leastStatus, boolean create) {
        if (Thread.currentThread() != Thread.currentThread()) {
            return (Chunk)CompletableFuture.supplyAsync(() -> {
                return this.getChunk(x, z, leastStatus, create);
            }, this.mainThreadExecutor).join();
        } else {
            Profiler profiler = this.world.getProfiler();
            profiler.visit("getChunk");
            long l = ChunkPos.toLong(x, z);

            Chunk chunk;
            for(int i = 0; i < 4; ++i) {
                if (l == this.chunkPosCache[i] && leastStatus == this.chunkStatusCache[i]) {
                    chunk = this.chunkCache[i];
                    if (chunk != null || !create) {
                        return chunk;
                    }
                }
            }

            profiler.visit("getChunkCacheMiss");
            CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> completableFuture = this.getChunkFuture(x, z, leastStatus, create);
            ServerChunkManager.MainThreadExecutor var10000 = this.mainThreadExecutor;
            Objects.requireNonNull(completableFuture);
            DebugHookTerminator.chunkLoadDrive(this.mainThreadExecutor, completableFuture::isDone, (ServerChunkManager) (Object) this, completableFuture, l);
            var10000.runTasks(completableFuture::isDone);
            chunk = (Chunk)((Either)completableFuture.join()).map((chunkx) -> {
                return chunkx;
            }, (unloaded) -> {
                if (create) {
                    throw (IllegalStateException)Util.throwOrPause(new IllegalStateException("Chunk not there when requested: " + unloaded));
                } else {
                    return null;
                }
            });
            this.putInCache(l, chunk, leastStatus);
            return chunk;
        }
    }
}