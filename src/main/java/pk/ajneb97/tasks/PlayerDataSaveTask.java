package pk.ajneb97.tasks;

import pk.ajneb97.PlayerKits2;
import pk.ajneb97.utils.SchedulerUtils;

public class PlayerDataSaveTask {

	private PlayerKits2 plugin;
	private SchedulerUtils.Task task;
	public PlayerDataSaveTask(PlayerKits2 plugin) {
		this.plugin = plugin;
	}
	
	public void end() {
		if(task != null) {
			task.cancel();
		}
	}
	
	public void start(int seconds) {
		long ticks = seconds* 20L;
		
		task = SchedulerUtils.runAsyncTimer(plugin, this::execute, 0L, ticks);
	}
	
	public void execute() {
		plugin.getConfigsManager().getPlayersConfigManager().saveConfigs();
	}
}
