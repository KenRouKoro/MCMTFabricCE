package net.himeki.mcmtfabric.parallelised;

import com.mojang.datafixers.DataFixer;
import me.shedaniel.autoconfig.AutoConfig;
import net.himeki.mcmtfabric.MCMT;
import net.himeki.mcmtfabric.ParallelProcessor;
import net.himeki.mcmtfabric.config.GeneralConfig;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.ChunkStatusChangeListener;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.LevelStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;


/* 1.16.1 code; AKA the only thing that changed  */
//import net.minecraft.world.storage.SaveFormat.LevelSave;
/* */

/* 1.15.2 code; AKA the only thing that changed
import java.io.File;
/* */

public class ParaServerChunkProvider extends ServerChunkManager {

    protected ConcurrentHashMap<ChunkCacheAddress, ChunkCacheLine> chunkCache = new ConcurrentHashMap<ChunkCacheAddress, ChunkCacheLine>();
    protected AtomicInteger access = new AtomicInteger(Integer.MIN_VALUE);

    private static final int CACHE_DURATION_INTERVAL = 50; // ms, multiplies CACHE_DURATION
    protected static final int CACHE_DURATION = 200; // Duration in ticks (ish...-- 50ms) for cached chucks to live

    private static final int HASH_PRIME = 16777619;
    private static final int HASH_INIT = 0x811c9dc5;

    protected Thread cacheThread;
    protected ChunkLock loadingChunkLock = new ChunkLock();
    Logger log = LogManager.getLogger();
    Marker chunkCleaner = MarkerManager.getMarker("ChunkCleaner");
    private final World world;

    /* 1.16.1 code; AKA the only thing that changed  */
    public ParaServerChunkProvider(ServerWorld world, LevelStorage.Session session, DataFixer dataFixer, StructureTemplateManager structureManager, Executor workerExecutor, ChunkGenerator chunkGenerator, int viewDistance, int simulationDistance, boolean dsync, WorldGenerationProgressListener worldGenerationProgressListener, ChunkStatusChangeListener chunkStatusChangeListener, Supplier persistentStateManagerFactory) {
        super(world,session,dataFixer,structureManager,workerExecutor,chunkGenerator,viewDistance,simulationDistance,dsync,worldGenerationProgressListener, chunkStatusChangeListener,persistentStateManagerFactory);
        this.world = world;
        cacheThread = new Thread(this::chunkCacheCleanup, "Chunk Cache Cleaner " + world.getRegistryKey().getValue().getPath());
        cacheThread.start();
    }

    @SuppressWarnings("unused")
    private Chunk getChunkyThing(long chunkPos, ChunkStatus requiredStatus, boolean load) {
        Chunk cl;
        synchronized (this) {
            cl = super.getChunk(ChunkPos.getPackedX(chunkPos), ChunkPos.getPackedZ(chunkPos), requiredStatus, load);
        }
        return cl;
    }

    /* */

	/* 1.15.2 code; AKA the only thing that changed
	public ParaServerChunkProvider(ServerWorld worldIn, File worldDirectory, DataFixer dataFixer,
			TemplateManager templateManagerIn, Executor executorIn, ChunkGenerator<?> chunkGeneratorIn,
			int viewDistance, IChunkStatusListener p_i51537_8_, Supplier<DimensionSavedDataManager> p_i51537_9_) {
		super(worldIn, worldDirectory, dataFixer, templateManagerIn, executorIn, chunkGeneratorIn, viewDistance, p_i51537_8_,
				p_i51537_9_);
		cacheThread = new Thread(this::chunkCacheCleanup, "Chunk Cache Cleaner " + worldIn.dimension.getType().getId());
		cacheThread.start();
	}
	/* */

    @Override
    @Nullable
    public Chunk getChunk(int chunkX, int chunkZ, ChunkStatus requiredStatus, boolean load) {

        if (MCMT.config.disabled || MCMT.config.disableChunkProvider) {
            if (ParallelProcessor.isThreadPooled("Main", Thread.currentThread())) {
                return CompletableFuture.supplyAsync(() -> {
                    return this.getChunk(chunkX, chunkZ, requiredStatus, load);
                }, this.mainThreadExecutor).join();
            }
            return super.getChunk(chunkX, chunkZ, requiredStatus, load);
        }
        if (ParallelProcessor.isThreadPooled("Main", Thread.currentThread())) {
            return CompletableFuture.supplyAsync(() -> {
                return this.getChunk(chunkX, chunkZ, requiredStatus, load);
            }, this.mainThreadExecutor).join();
        }

        long i = ChunkPos.toLong(chunkX, chunkZ);

        Chunk c = lookupChunk(i, requiredStatus, false);
        if (c != null) {
            return c;
        }

        //log.debug("Missed chunk " + i + " on status "  + requiredStatus.toString());

        Chunk cl;
        if (ParallelProcessor.shouldThreadChunks()) {
            // Multithread but still limit to 1 load op per chunk
            long[] locks = loadingChunkLock.lock(i, 0);
            try {
                if ((c = lookupChunk(i, requiredStatus, false)) != null) {
                    return c;
                }
                cl = super.getChunk(chunkX, chunkZ, requiredStatus, load);
            } finally {
                loadingChunkLock.unlock(locks);
            }
        } else {
            synchronized (this) {
                if (chunkCache.containsKey(new ChunkCacheAddress(i, requiredStatus)) && (c = lookupChunk(i, requiredStatus, false)) != null) {
                    return c;
                }
                cl = super.getChunk(chunkX, chunkZ, requiredStatus, load);
            }
        }
        cacheChunk(i, cl, requiredStatus);
        return cl;
    }

    public Chunk lookupChunk(long chunkPos, ChunkStatus status, boolean compute) {
        int oldAccess = access.getAndIncrement();
        if (access.get() < oldAccess) { // overflow
        	clearCache();
            return null;
        }
        ChunkCacheLine ccl;
        ccl = chunkCache.get(new ChunkCacheAddress(chunkPos, status));
        if (ccl != null) {
            ccl.updateLastAccess();
            return ccl.getChunk();
        }
        return null;

    }

    public void cacheChunk(long chunkPos, Chunk chunk, ChunkStatus status) {
        long oldAccess = access.getAndIncrement();
        if (access.get() < oldAccess) { // overflow
        	clearCache();
        }

        ChunkCacheLine ccl;
        if ((ccl = chunkCache.get(new ChunkCacheAddress(chunkPos, status))) != null) {
            ccl.updateLastAccess();
            ccl.updateChunkRef(chunk);
        }
        ccl = new ChunkCacheLine(chunk);
        chunkCache.put(new ChunkCacheAddress(chunkPos, status), ccl);
    }

    public void chunkCacheCleanup() {
        while (world == null || world.getServer() == null) {
            log.debug(chunkCleaner, "ChunkCleaner Waiting for startup");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        while (world.getServer().isRunning()) {
            try {
                Thread.sleep(CACHE_DURATION_INTERVAL * CACHE_DURATION);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        	clearCache();
        }
        log.debug(chunkCleaner, "ChunkCleaner terminating");
    }

    public void clearCache() {
    	//log.info("Clearing Chunk Cache; Size: " + chunkCache.size());
        chunkCache.clear(); // Doesn't resize but that's typically good
    }

     protected class ChunkCacheAddress {
        protected long chunk_pos;
        protected int status;
        protected int hash;

        public ChunkCacheAddress(long chunk_pos, ChunkStatus status) {
            super();
            this.chunk_pos = chunk_pos;
            this.status = status.getIndex();
            this.hash = makeHash(this.chunk_pos, this.status);
        }

        @Override
        public int hashCode() {
        	return hash;
        }

        @Override
        public boolean equals(Object obj) {
        	return (obj instanceof ChunkCacheAddress)
        			&& ((ChunkCacheAddress) obj).status == this.status
        			&& ((ChunkCacheAddress) obj).chunk_pos == this.chunk_pos;
        }

        public int makeHash(long chunk_pos, int status) {
        	int hash = HASH_INIT;
        	hash ^= status;
        	for (int b = 56; b >= 0; b -= 8) {
        		hash ^= (chunk_pos >> b) & 0xff;
        		hash *= HASH_PRIME;
        	}
        	return hash;
        }
    }

    protected class ChunkCacheLine {
        WeakReference<Chunk> chunk;
        int lastAccess;

        public ChunkCacheLine(Chunk chunk) {
            this(chunk, access.get());
        }

        public ChunkCacheLine(Chunk chunk, int lastAccess) {
            this.chunk = new WeakReference<>(chunk);
            this.lastAccess = lastAccess;
        }

        public Chunk getChunk() {
            return chunk.get();
        }

        public int getLastAccess() {
            return lastAccess;
        }

        public void updateLastAccess() {
            lastAccess = access.get();
        }

        public void updateChunkRef(Chunk c) {
            if (chunk.get() == null) {
                chunk = new WeakReference<>(c);
            }
        }
    }
}
