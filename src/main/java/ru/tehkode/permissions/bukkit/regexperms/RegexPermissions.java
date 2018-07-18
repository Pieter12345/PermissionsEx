package ru.tehkode.permissions.bukkit.regexperms;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;

import ru.tehkode.permissions.PermissionManager;
import ru.tehkode.permissions.bukkit.PermissionsEx;
import ru.tehkode.permissions.events.PermissionSystemEvent;

import java.util.logging.Level;

import static ru.tehkode.permissions.bukkit.CraftBukkitInterface.getCBClassName;

/**
 * @author zml2008
 */
public class RegexPermissions {
	private final PermissionsEx plugin;
	private PermissionList permsList;
	// Permissions subscriptions handling
	private PEXPermissionSubscriptionMap subscriptionHandler;

	public RegexPermissions(PermissionsEx plugin) {
		this.plugin = plugin;
		subscriptionHandler = PEXPermissionSubscriptionMap.inject(plugin, plugin.getServer().getPluginManager());
		permsList = PermissionList.inject(plugin.getServer().getPluginManager());
		plugin.getServer().getPluginManager().registerEvents(new EventListener(), plugin);
		injectAllPermissibles();
	}

	protected static final PermissibleInjector[] injectors = new PermissibleInjector[] {
			new PermissibleInjector.ClassPresencePermissibleInjector("net.glowstone.entity.GlowHumanEntity", "permissions", true),
			new PermissibleInjector.ClassPresencePermissibleInjector("org.getspout.server.entity.SpoutHumanEntity", "permissions", true),
			new PermissibleInjector.ClassNameRegexPermissibleInjector("org.getspout.spout.player.SpoutCraftPlayer", "perm", false, "org\\.getspout\\.spout\\.player\\.SpoutCraftPlayer"),
			new PermissibleInjector.ClassPresencePermissibleInjector(getCBClassName("entity.CraftHumanEntity"), "perm", true),
	};

	public void onDisable() {
		subscriptionHandler.uninject();
		uninjectAllPermissibles();
	}

	public boolean hasDebugMode() {
		PermissionManager manager = plugin.getPermissionsManager();
		return manager != null && manager.isDebug();
	}

	public PermissionList getPermissionList() {
		return permsList;
	}

	public void injectPermissible(Player player) {

		// Don't apply permissionsex matching for players which have the "permissionsex.disabled" permission.
		// Due to OP players getting all permissions, we have to do a slightly more extended check.
		final String permName = "permissionsex.disabled";

		if (player.isPermissionSet(permName)) {

			// Player has the permission, overriding the default value.
			if(player.hasPermission(permName)) {
				return;
			}
		} else {

			// Player does not have an overrive for the permission, so use the default value.
			// Don't fall back to Permission.DEFAULT_PERMISSION if no default permissions are set, since this will
			// give OPs all permissions.
			Permission perm = Bukkit.getServer().getPluginManager().getPermission(permName);
			if(perm != null && perm.getDefault().getValue(player.isOp())) {
				return;
			}
		}

		try {
			PermissiblePEX permissible = new PermissiblePEX(player, plugin);

			boolean success = false, found = false;
			for (PermissibleInjector injector : injectors) {
				if (injector.isApplicable(player)) {
					found = true;
					Permissible oldPerm = injector.inject(player, permissible);
					if (oldPerm != null) {
						permissible.setPreviousPermissible(oldPerm);
						success = true;
						break;
					}
				}
			}

			if (!found) {
				plugin.getLogger().warning("No Permissible injector found for your server implementation!");
			} else if (!success) {
				plugin.getLogger().warning("Unable to inject PEX's permissible for " + player.getName());
			}

			permissible.recalculatePermissions();

			if (success && hasDebugMode()) {
				plugin.getLogger().info("Permissions handler for " + player.getName() + " successfully injected");
			}
		} catch (Throwable e) {
			plugin.getLogger().log(Level.SEVERE, "Unable to inject permissible for " + player.getName(), e);
		}
	}

	private void injectAllPermissibles() {
		for (Player player : plugin.getServer().getOnlinePlayers()) {
			injectPermissible(player);
		}
	}

	private void uninjectPermissible(Player player) {
		try {
			boolean success = false;
			for (PermissibleInjector injector : injectors) {
				if (injector.isApplicable(player)) {
					Permissible pexPerm = injector.getPermissible(player);
					if (pexPerm instanceof PermissiblePEX) {
						if (injector.inject(player, ((PermissiblePEX) pexPerm).getPreviousPermissible()) != null) {
							success = true;
							break;
						}
					} else {
						success = true;
						break;
					}
				}
			}

			if (!success) {
				plugin.getLogger().warning("No Permissible injector found for your server implementation (while uninjecting for " + player.getName() + "!");
			} else if (hasDebugMode()) {
				plugin.getLogger().info("Permissions handler for " + player.getName() + " successfully uninjected");
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	private void uninjectAllPermissibles() {
		for (Player player : plugin.getServer().getOnlinePlayers()) {
			uninjectPermissible(player);
		}
	}

	private class EventListener implements Listener {
		@EventHandler(priority = EventPriority.LOWEST)
		public void onPlayerLogin(PlayerLoginEvent event) {
			injectPermissible(event.getPlayer());
		}

		@EventHandler(priority = EventPriority.MONITOR)
		// Technically not supposed to use MONITOR for this, but we don't want to remove before other plugins are done checking permissions
		public void onPlayerQuit(PlayerQuitEvent event) {
			uninjectPermissible(event.getPlayer());
		}

		@EventHandler(priority = EventPriority.LOWEST)
		public void onPermissionSystemEvent(PermissionSystemEvent event) {
			switch (event.getAction()) {
				case REINJECT_PERMISSIBLES:
				case RELOADED:
					uninjectAllPermissibles();
					injectAllPermissibles();
					break;
				default:
					return;
			}
		}
	}

}
