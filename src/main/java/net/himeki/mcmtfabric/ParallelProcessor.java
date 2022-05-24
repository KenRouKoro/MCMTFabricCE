package net.himeki.mcmtfabric;

import net.himeki.mcmtfabric.config.BlockEntityLists;
import net.himeki.mcmtfabric.config.GeneralConfig;
import net.himeki.mcmtfabric.serdes.SerDesHookTypes;
import net.himeki.mcmtfabric.serdes.SerDesRegistry;
import net.himeki.mcmtfabric.serdes.filter.ISerDesFilter;
import net.himeki.mcmtfabric.serdes.pools.PostExecutePool;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.BlockEvent;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockEntityTickInvoker;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public class ParallelProcessor {

    private static final Logger LOGGER = LogManager.getLogger();

    static Phaser p;
    static ExecutorService ex;
    static MinecraftServer mcs;
    static AtomicBoolean isTicking = new AtomicBoolean();
    static AtomicInteger threadID = new AtomicInteger();

    public static void setupThreadPool(int parallelism) {
        threadID = new AtomicInteger();
        final ClassLoader cl = MCMT.class.getClassLoader();
        ForkJoinPool.ForkJoinWorkerThreadFactory fjpf = p -> {
            ForkJoinWorkerThread fjwt = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(p);
            fjwt.setName("MCMT-Pool-Thread-" + threadID.getAndIncrement());
            regThread("MCMT", fjwt);
            fjwt.setContextClassLoader(cl);
            return fjwt;
        };
        ex = new ForkJoinPool(parallelism, fjpf, null, true);
    }

    /**
     * Creates and sets up the thread pool
     */
    static {
        // Must be static here due to class loading shenanagins
        // setupThreadPool(4);
    }

    static Map<String, Set<Thread>> mcThreadTracker = new ConcurrentHashMap<String, Set<Thread>>();

    // Statistics
    public static AtomicInteger currentWorlds = new AtomicInteger();
    public static AtomicInteger currentEnts = new AtomicInteger();
    public static AtomicInteger currentTEs = new AtomicInteger();
    public static AtomicInteger currentEnvs = new AtomicInteger();

    //Operation logging
    public static Set<String> currentTasks = ConcurrentHashMap.newKeySet();

    public static void regThread(String poolName, Thread thread) {
        mcThreadTracker.computeIfAbsent(poolName, s -> ConcurrentHashMap.newKeySet()).add(thread);
    }

    public static boolean isThreadPooled(String poolName, Thread t) {
        return mcThreadTracker.containsKey(poolName) && mcThreadTracker.get(poolName).contains(t);
    }

    public static boolean serverExecutionThreadPatch(MinecraftServer ms) {
        return isThreadPooled("MCMT", Thread.currentThread());
    }

    static long tickStart = 0;

    public static void preTick(MinecraftServer server) {
        if (p != null) {
            LOGGER.warn("多个服务器?什么鬼！");
            return;
        } else {
            tickStart = System.nanoTime();
            isTicking.set(true);
            p = new Phaser();
            p.register();
            mcs = server;
        }
    }

    public static void callTick(ServerWorld serverworld, BooleanSupplier hasTimeLeft, MinecraftServer server) {
        GeneralConfig config = MCMT.config;
        if (config.disabled || config.disableWorld) {
            try {
                serverworld.tick(hasTimeLeft);
            } catch (Exception e) {
                throw e;
            }
            return;
        }
        if (mcs != server) {
            LOGGER.warn("多个服务器?什么鬼！");
            config.disabled = true;
            serverworld.tick(hasTimeLeft);
            return;
        } else {
            String taskName = null;
            if (config.opsTracing) {
                taskName = "WorldTick: " + serverworld.toString() + "@" + serverworld.hashCode();
                currentTasks.add(taskName);
            }
            String finalTaskName = taskName;
            p.register();
            ex.execute(() -> {
                try {
                    currentWorlds.incrementAndGet();
                    serverworld.tick(hasTimeLeft);
                }catch (Exception e){
                    String eMessage = e.getMessage();
                    LOGGER.error("MCMT捕捉到在 世界Tick线程:"+Thread.currentThread().getName()+" 抛出异常:"+e.getClass().getName()+":"+eMessage);
                    //throw e;
                }finally {
                    p.arriveAndDeregister();
                    currentWorlds.decrementAndGet();
                    if (config.opsTracing) currentTasks.remove(finalTaskName);
                }
            });
        }

    }

    public static long[] lastTickTime = new long[32];
    public static int lastTickTimePos = 0;
    public static int lastTickTimeFill = 0;

    public static void postTick(MinecraftServer server) {
        if (mcs != server) {
            LOGGER.warn("多个服务器?什么鬼！");
            return;
        } else {
            p.arriveAndAwaitAdvance();
            isTicking.set(false);
            p = null;
            //PostExecute logic
            Deque<Runnable> queue = PostExecutePool.POOL.getQueue();
            Iterator<Runnable> qi = queue.iterator();
            while (qi.hasNext()) {
                Runnable r = qi.next();
                r.run();
                qi.remove();
            }
            lastTickTime[lastTickTimePos] = System.nanoTime() - tickStart;
            lastTickTimePos = (lastTickTimePos+1)%lastTickTime.length;
            lastTickTimeFill = Math.min(lastTickTimeFill+1, lastTickTime.length-1);
        }
    }


    public static void callEntityTick(Entity entityIn, ServerWorld serverworld) {
        GeneralConfig config = MCMT.config;
        if (config.disabled || config.disableEntity) {
            entityIn.tick();
            return;
        }
        String taskName = null;
        if (config.opsTracing) {
            taskName = "EntityTick: " + /*entityIn.toString() + KG: Wayyy too slow. Maybe for debug but needs to be done via flag in that circumstance */ "@" + entityIn.hashCode();
            currentTasks.add(taskName);
        }
        String finalTaskName = taskName;
        p.register();
        ex.execute(() -> {
            try {
                final ISerDesFilter filter = SerDesRegistry.getFilter(SerDesHookTypes.EntityTick, entityIn.getClass());
                currentEnts.incrementAndGet();
                if (filter != null) {
                    filter.serialise(entityIn::tick, entityIn, entityIn.getBlockPos(), serverworld, SerDesHookTypes.EntityTick);
                } else {
                    try {
                        entityIn.tick();
                    }catch (Exception e){
                        String eMessage = e.getMessage();
                        LOGGER.error("MCMT捕捉到在 实体Tick线程:"+Thread.currentThread().getName()+" 抛出异常:"+e.getClass().getName()+":"+eMessage);
                        //throw e;
                    }
                }
            } finally {
                currentEnts.decrementAndGet();
                p.arriveAndDeregister();
                if (config.opsTracing) currentTasks.remove(finalTaskName);
            }
        });
    }

    public static void callTickChunks(ServerWorld world, WorldChunk chunk, int k) {
        GeneralConfig config = MCMT.config;
        if (config.disabled || config.disableEnvironment) {
            world.tickChunk(chunk, k);
            return;
        }
        String taskName = null;
        if (config.opsTracing) {
            taskName = "EnvTick: " + chunk.toString() + "@" + chunk.hashCode();
            currentTasks.add(taskName);
        }
        String finalTaskName = taskName;
        p.register();
        ex.execute(() -> {
            try {
                currentEnvs.incrementAndGet();
                world.tickChunk(chunk, k);
            } finally {
                currentEnvs.decrementAndGet();
                p.arriveAndDeregister();
                if (config.opsTracing) currentTasks.remove(finalTaskName);
            }
        });
    }

    public static boolean filterTE(BlockEntityTickInvoker tte) {
        GeneralConfig config = MCMT.config;
        boolean isLocking = false;
        if (BlockEntityLists.teBlackList.contains(tte.getClass())) {
            isLocking = true;
        }
        // Apparently a string starts with check is faster than Class.getPackage; who knew (I didn't)
        if (!isLocking && config.chunkLockModded && !tte.getClass().getName().startsWith("net.minecraft.block.entity.")) {
            isLocking = true;
        }
        if (isLocking && BlockEntityLists.teWhiteList.contains(tte.getClass())) {
            isLocking = false;
        }
        if (tte instanceof PistonBlockEntity) {
            isLocking = true;
        }
        return isLocking;
    }

    public static void callTileEntityTick(BlockEntityTickInvoker tte, World world) {
        GeneralConfig config = MCMT.config;
        if (config.disabled || config.disableTileEntity || !(world instanceof ServerWorld)) {
            tte.tick();
            return;
        }
        String taskName = null;
        if (config.opsTracing) {
            taskName = "TETick: " + tte.toString() + "@" + tte.hashCode();
            currentTasks.add(taskName);
        }
        String finalTaskName = taskName;
        p.register();
        ex.execute(() -> {
            try {
//                final boolean doLock = filterTE(tte);
                final ISerDesFilter filter = SerDesRegistry.getFilter(SerDesHookTypes.TETick, tte.getClass());
                currentTEs.incrementAndGet();
                if (filter != null) {
                    filter.serialise(tte::tick, tte, tte.getPos(), world, SerDesHookTypes.TETick);
                } else {
                    tte.tick();
                }
            } catch (Exception e) {
                System.err.println("Exception ticking TE at " + tte.getPos());
                e.printStackTrace();
            } finally {
                currentTEs.decrementAndGet();
                p.arriveAndDeregister();
                if (config.opsTracing) currentTasks.remove(finalTaskName);
            }
        });
    }

//    public static <T> void fixSTL(ServerTickScheduler<T> stl, Set<ScheduledTick<T>> scheduledTickActionsInOrder, Set<ScheduledTick<T>> scheduledTickActions) {
//        LOGGER.debug("FixSTL Called");
//        scheduledTickActionsInOrder.addAll(scheduledTickActions);
//    }

    public static void sendQueuedBlockEvents(Deque<BlockEvent> d, ServerWorld sw) {
        Iterator<BlockEvent> bed = d.iterator();
        while (bed.hasNext()) {
            BlockEvent BlockEvent = bed.next();
            if (sw.processBlockEvent(BlockEvent)) {
                /* 1.16.1 code; AKA the only thing that changed  */
                sw.getServer().getPlayerManager().sendToAround(null, BlockEvent.pos().getX(), BlockEvent.pos().getY(), BlockEvent.pos().getZ(), 64.0D, sw.getRegistryKey(), new BlockEventS2CPacket(BlockEvent.pos(), BlockEvent.block(), BlockEvent.type(), BlockEvent.data()));
                /* */
				/* 1.15.2 code; AKA the only thing that changed
				sw.getServer().getPlayerList().sendToAllNearExcept((PlayerEntity)null, (double)BlockEvent.getPosition().getX(), (double)BlockEvent.getPosition().getY(), (double)BlockEvent.getPosition().getZ(), 64.0D, sw.getDimension().getType(), new SBlockActionPacket(BlockEvent.getPosition(), BlockEvent.getBlock(), BlockEvent.getEventID(), BlockEvent.getEventParameter()));
				/* */
            }
            if (!isTicking.get()) {
                LOGGER.fatal("Block updates outside of tick");
            }
            bed.remove();
        }
    }

    public static boolean shouldThreadChunks() {
        return !MCMT.config.disableMultiChunk;
    }

}
