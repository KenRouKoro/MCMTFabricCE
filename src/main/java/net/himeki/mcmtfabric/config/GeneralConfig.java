package net.himeki.mcmtfabric.config;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;
import net.himeki.mcmtfabric.MCMT;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

//汉化了配置文件
//Chineseized configuration file
@Config(name = "mcmtce")
public class GeneralConfig implements ConfigData {
    // Actual config stuff
    //////////////////////

    // General
    @Comment("全局禁用所有可切换功能。")
    public boolean disabled = false;

    // Parallelism
    @Comment("线程数配置；在标准模式下：将永远不会创建比CPU线程更多的线程（因为这将导致上下文切换紊乱）。\n" +
            "小于等于-1的值都被视为 \"全核心\"。")
    public int paraMax = -1;

    @Comment("""
            paraMax的其他模式
            Override。标准模式，但没有CoreCount上限（因此，如果你愿意，你可以有64k线程）。
            Reduction。平行性变成Math.max(CoreCount-paramax, 2)，如果paramax被设置为-1，则被当作0对待
            Todo：添加更多"""
    )
    public ParaMaxMode paraMaxMode = ParaMaxMode.Standard;

    // World
    @Comment("禁用世界并行化。")
    public boolean disableWorld = false;

    @Comment("禁用世界上的后勾选并行化。")
    public boolean disableWorldPostTick = false;

    @Comment("禁用世界上的并行区块加载。")
    public boolean disableMultiChunk = false;

    // Entity
    @Comment("禁用实体并行化。")
    public boolean disableEntity = false;

    // TE
    @Comment("禁用tile实体并行化。")
    public boolean disableTileEntity = false;

    @Comment("对任何未知的（即修改过的）tile实体使用区块锁。\n"
            + "区块锁意味着我们要防止多个tile实体在1个块的半径内被勾选，以限制并发影响。")
    public boolean chunkLockModded = true;

    @Comment("""
            总是被完全并行化的瓦片实体类的列表
            即使chunkLockModded被设置为 "true"，也会出现这种情况。
            在此添加设置将不会使其并行化"""
    )
    public List<String> teWhiteListString = new CopyOnWriteArrayList<>();

    @Comment("始终被分块锁定的tile实体类的列表。\n"
            + "即使chunkLockModded被设置为false，也会发生这种情况。")
    public List<String> teBlackListString = new CopyOnWriteArrayList<>();

    // Any TE class strings that aren't available in the current environment
    // We use classes for the main operation as class-class comparisons are memhash based
    // So (should) be MUCH faster than string-string comparisons
    @ConfigEntry.Gui.Excluded
    public List<String> teUnfoundWhiteList = new CopyOnWriteArrayList<>();
    @ConfigEntry.Gui.Excluded
    public List<String> teUnfoundBlackList = new CopyOnWriteArrayList<>();

    // Misc
    @Comment("禁用环境（植物 tick 等）平行化。")
    public boolean disableEnvironment = false;

    @Comment("禁用并行的块状缓存；这样做会导致性能大大降低，几乎没有收益。")
    public boolean disableChunkProvider = false;

    //Debug
    @Comment("启用区块加载超时；这将强行杀死任何未能在足够时间内加载的区块。\n"
            + "可能允许加载受损/损坏的世界")
    public boolean enableChunkTimeout = false;

    @Comment("试图重新加载已超时的数据块；似乎有效。")
    public boolean enableTimeoutRegen = false;

    @Comment("简单地返回一个新的空块，而不是重新生成一个完整的。")
    public boolean enableBlankReturn = false;

    @Comment("在宣布一个块的加载尝试超时之前，要等待的无工作迭代的数量\n"
            + "这是在~100us的迭代中（加减屈服时间），所以 timeout>= timeoutCount * 100us")
    public int timeoutCount = 5000;

    // More Debug
    @Comment("启用运行跟踪；这可能会对性能产生影响，但可以更好地进行调试。")
    public boolean opsTracing = false;

    @Comment("以10ms为单位，MCMT存在警报之间的最大时间间隔。")
    public int logCap = 720000;

    @Comment("设置是否打印实体异常报告")
    public boolean logEntityException = false;

    @Comment("设置是否打印世界异常报告")
    public boolean logWorldException = false;

    @Comment("设置是否打印区块异常报告")
    public boolean logChunkException = false;


    public enum ParaMaxMode {
        Standard,
        Override,
        Reduction
    }

    // Functions intended for usage
    ///////////////////////////////

    @Override
    public void validatePostLoad() throws ValidationException {
        if (paraMax >= -1 && paraMax <= Integer.MAX_VALUE)
            if (paraMaxMode == ParaMaxMode.Standard || paraMaxMode == ParaMaxMode.Override || paraMaxMode == ParaMaxMode.Reduction)
                if (timeoutCount >= 500 && timeoutCount <= 500000)
                    if (logCap >= 15000 && logCap <= Integer.MAX_VALUE)
                        return;
        throw new ValidationException("验证MCMTCE配置失败。");
    }

    public static int getParallelism() {
        GeneralConfig config = MCMT.config;
        switch (config.paraMaxMode) {
            case Standard:
                return config.paraMax <= 1 ?
                        Runtime.getRuntime().availableProcessors() :
                        Math.max(2, Math.min(Runtime.getRuntime().availableProcessors(), config.paraMax));
            case Override:
                return config.paraMax <= 1 ?
                        Runtime.getRuntime().availableProcessors() :
                        Math.max(2, config.paraMax);
            case Reduction:
                return Math.max(
                        Runtime.getRuntime().availableProcessors() - Math.max(0, config.paraMax),
                        2);
        }
        // Unsure quite how this is "Reachable code" but ok I guess
        return Runtime.getRuntime().availableProcessors();
    }

    public void loadTELists() {
        teWhiteListString.forEach(str -> {
            Class<?> c = null;
            try {
                c = Class.forName(str);
                BlockEntityLists.teWhiteList.add(c);
            } catch (ClassNotFoundException cnfe) {
                teUnfoundWhiteList.add(str);
            }
        });

        teBlackListString.forEach(str -> {
            Class<?> c = null;
            try {
                c = Class.forName(str);
                BlockEntityLists.teBlackList.add(c);
            } catch (ClassNotFoundException cnfe) {
                teUnfoundBlackList.add(str);
            }
        });
    }
}
