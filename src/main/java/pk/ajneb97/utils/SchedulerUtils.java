package pk.ajneb97.utils;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

/**
 * Runs the tasks of the plugin on the right thread. Folia and its forks, like Canvas, have no
 * main thread and don't support the Bukkit scheduler, so there the tasks are run on the global
 * region, on the region that owns the entity they use, or asynchronously.
 */
public class SchedulerUtils {

    private static final boolean FOLIA = isFoliaServer();

    private static boolean isFoliaServer(){
        try{
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        }catch(ClassNotFoundException e){
            return false;
        }
    }

    public static boolean isFolia(){
        return FOLIA;
    }

    /**
     * Runs the task on the main thread, or on the global region on Folia. It is run right
     * away if the current thread is already that one.
     */
    public static void runGlobal(Plugin plugin, Runnable task){
        if(FOLIA){
            if(Bukkit.isGlobalTickThread()){
                task.run();
            }else{
                Bukkit.getGlobalRegionScheduler().execute(plugin,task);
            }
        }else if(Bukkit.isPrimaryThread()){
            task.run();
        }else{
            Bukkit.getScheduler().runTask(plugin,task);
        }
    }

    public static Task runGlobalTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks){
        if(FOLIA){
            ScheduledTask scheduledTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,t -> task.run(),
                    Math.max(1,delayTicks),Math.max(1,periodTicks));
            return scheduledTask::cancel;
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin,task,delayTicks,periodTicks);
        return bukkitTask::cancel;
    }

    public static void runAsync(Plugin plugin, Runnable task){
        if(FOLIA){
            Bukkit.getAsyncScheduler().runNow(plugin,t -> task.run());
        }else{
            Bukkit.getScheduler().runTaskAsynchronously(plugin,task);
        }
    }

    public static Task runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks){
        if(FOLIA){
            ScheduledTask scheduledTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin,t -> task.run(),
                    Math.max(0,delayTicks)*50L,Math.max(1,periodTicks)*50L,TimeUnit.MILLISECONDS);
            return scheduledTask::cancel;
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,task,delayTicks,periodTicks);
        return bukkitTask::cancel;
    }

    /**
     * Runs the task on the thread that owns the entity, right away if the current thread is
     * already that one. On Folia the task is not run if the entity is removed before it, for
     * example when a player leaves.
     */
    public static void runEntity(Plugin plugin, Entity entity, Runnable task){
        if(FOLIA){
            if(Bukkit.isOwnedByCurrentRegion(entity)){
                task.run();
            }else{
                entity.getScheduler().execute(plugin,task,null,1L);
            }
        }else if(Bukkit.isPrimaryThread()){
            task.run();
        }else{
            Bukkit.getScheduler().runTask(plugin,task);
        }
    }

    public static void runEntityLater(Plugin plugin, Entity entity, Runnable task, long delayTicks){
        if(FOLIA){
            entity.getScheduler().execute(plugin,task,null,delayTicks);
        }else{
            Bukkit.getScheduler().runTaskLater(plugin,task,delayTicks);
        }
    }

    public interface Task {
        void cancel();
    }
}
