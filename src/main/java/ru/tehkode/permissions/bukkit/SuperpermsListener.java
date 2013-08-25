package ru.tehkode.permissions.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionDefault;
import ru.tehkode.permissions.PermissionGroup;
import ru.tehkode.permissions.PermissionUser;
import ru.tehkode.permissions.events.PermissionEntityEvent;
import ru.tehkode.permissions.events.PermissionSystemEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * PEX permissions database integration with superperms
 */
public class SuperpermsListener implements Listener {
	private final PermissionsEx plugin;
	private final Map<String, PermissionAttachment> attachments = new HashMap<String, PermissionAttachment>();
	private final Map<String, Permission> mainPermissions = new HashMap<String, Permission>(), // player->permission object
			optionPermissions = new HashMap<String, Permission>();

	public SuperpermsListener(PermissionsEx plugin) {
		this.plugin = plugin;
	}

	protected void updateAttachment(Player player) {
		PermissionAttachment attach = attachments.get(player.getName());
		Permission playerPerm = getCreateWrapper(player, "", mainPermissions);
		Permission playerOptionPerm = getCreateWrapper(player, ".options", optionPermissions);
		if (attach == null) {
			attach = player.addAttachment(plugin);
			attachments.put(player.getName(), attach);
			attach.setPermission(playerPerm, true);
		}

		PermissionUser user = plugin.getPermissionsManager().getUser(player);
		if (user != null) {
			updatePlayerPermission(playerPerm, player, user);
			updatePlayerMetadata(playerOptionPerm, player, user);
			player.recalculatePermissions();
		}
	}

	private Permission getCreateWrapper(Player player, String suffix, Map<String, Permission> permissions) {
		Permission perm = permissions.get(player.getName());
		if (perm == null) {
			perm = new Permission("permissionsex.player." + player.getName() + suffix, "Internal permission for PEX. DO NOT SET DIRECTLY", PermissionDefault.FALSE);
			permissions.put(player.getName(), perm);
			plugin.getServer().getPluginManager().addPermission(perm);
		}

		return perm;

	}

	private void updatePlayerPermission(Permission permission, Player player, PermissionUser user) {
		permission.getChildren().clear();
		permission.getChildren().put("permissionsex.player." + player.getName() + ".options", true);
		for (String perm : user.getPermissions(player.getWorld().getName())) {
			boolean value = true;
			if (perm.startsWith("-")) {
				value = false;
				perm = perm.substring(1);
			}
			if (!permission.getChildren().containsKey(perm)) {
				permission.getChildren().put(perm, value);
			}
		}
	}

	private void updatePlayerMetadata(Permission rootPermission, Player player, PermissionUser user) {
		rootPermission.getChildren().clear();
		final String[] groups = user.getGroupsNames(player.getWorld().getName());
		final Map<String, String> options = user.getOptions(player.getWorld().getName());
		// Metadata
		// Groups
		for (String group : groups) {
			rootPermission.getChildren().put("groups." + group, true);
			rootPermission.getChildren().put("group." + group, true);
		}

		// Options
		for (Map.Entry<String, String> option : options.entrySet()) {
			rootPermission.getChildren().put("options." + option.getKey() + "." + option.getValue(), true);
		}

		// Prefix and Suffix
		rootPermission.getChildren().put("prefix." + user.getPrefix(player.getWorld().getName()), true);
		rootPermission.getChildren().put("suffix." + user.getSuffix(player.getWorld().getName()), true);

	}

	protected void removeAttachment(Player player) {
		PermissionAttachment attach = attachments.remove(player.getName());
		if (attach != null) {
			attach.remove();
		}

		Permission mainPerm = mainPermissions.remove(player.getName());
		if (mainPerm != null) {
			plugin.getServer().getPluginManager().removePermission(mainPerm);
		}

		Permission optionPerm = optionPermissions.remove(player.getName());
		if (optionPerm != null) {
			plugin.getServer().getPluginManager().removePermission(optionPerm);
		}
	}

	public void onDisable() {
		for (PermissionAttachment attach : attachments.values()) {
			attach.remove();
		}
		attachments.clear();
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerLogin(PlayerLoginEvent event) {
		try {
			updateAttachment(event.getPlayer());
		} catch (Throwable t) {
			ErrorReport.handleError("Superperms event login", t);
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		try {
			removeAttachment(event.getPlayer());
		} catch (Throwable t) {
			ErrorReport.handleError("Superperms event quit", t);
		}
	}

	private void updateSelective(PermissionEntityEvent event, PermissionUser user) {
		final Player p = plugin.getServer().getPlayerExact(user.getName());
		if (p != null) {
			switch (event.getAction()) {
				case SAVED:
					break;

				case PERMISSIONS_CHANGED:
				case TIMEDPERMISSION_EXPIRED:
					updatePlayerPermission(getCreateWrapper(p, "", mainPermissions), p, user);
					p.recalculatePermissions();
					break;

				case OPTIONS_CHANGED:
				case INFO_CHANGED:
					updatePlayerMetadata(getCreateWrapper(p, ".options", optionPermissions), p, user);
					p.recalculatePermissions();
					break;

				default:
					updateAttachment(p);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onEntityEvent(PermissionEntityEvent event) {
		try {
			if (event.getEntity() instanceof PermissionUser) { // update user only
				updateSelective(event, (PermissionUser) event.getEntity());
			} else if (event.getEntity() instanceof PermissionGroup) { // update all members of group, might be resource hog
				for (PermissionUser user : plugin.getPermissionsManager().getUsers(event.getEntity().getName(), true)) {
					updateSelective(event, user);
				}
			}
		} catch (Throwable t) {
			ErrorReport.handleError("Superperms event permission entity", t);
		}
	}

	@EventHandler
	public void onWorldChanged(PlayerChangedWorldEvent event) {
		try {
			updateAttachment(event.getPlayer());
		} catch (Throwable t) {
			ErrorReport.handleError("Superperms event world change", t);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onSystemEvent(PermissionSystemEvent event) {
		try {
			if (event.getAction() == PermissionSystemEvent.Action.DEBUGMODE_TOGGLE) {
				return;
			}
			switch (event.getAction()) {
				case DEBUGMODE_TOGGLE:
				case REINJECT_PERMISSIBLES:
					return;
				default:
					for (Player p : plugin.getServer().getOnlinePlayers()) {
						updateAttachment(p);
					}
			}
		} catch (Throwable t) {
			ErrorReport.handleError("Superperms event permission system event", t);
		}
	}
}
