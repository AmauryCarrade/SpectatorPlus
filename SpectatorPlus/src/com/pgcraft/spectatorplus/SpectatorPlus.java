package com.pgcraft.spectatorplus;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

@SuppressWarnings("deprecation")
public class SpectatorPlus extends JavaPlugin {
	
	protected HashMap <String, PlayerObject> user = new HashMap<String, PlayerObject>();
	
	protected String basePrefix = ChatColor.BLUE + "Spectator" + ChatColor.DARK_BLUE + "Plus";
	protected String prefix = ChatColor.GOLD + "[" + basePrefix + ChatColor.GOLD + "] ";
	
	protected double version = 1.92; // Plugin version
	
	protected ConsoleCommandSender console;
	
	protected ConfigAccessor setup = null;
	protected ConfigAccessor toggles = null;
	protected ConfigAccessor specs = null;
	
	protected ArenasManager arenasManager = null;
	
	private SpectateAPI api = null;

	// Manage toggles
	protected boolean compass;
	protected boolean clock;
	protected boolean specChat;
	protected boolean scoreboard;
	protected boolean output;
	protected boolean death;
	protected boolean seeSpecs;
	protected boolean blockCmds;
	protected boolean adminBypass;

	protected ScoreboardManager manager = null;
	protected Scoreboard board = null;
	protected Team team = null;

	
	@Override
	public void onEnable() {
		setup = new ConfigAccessor(this, "setup");
		toggles = new ConfigAccessor(this, "toggles");
		specs = new ConfigAccessor(this, "spectators");

		setup.saveDefaultConfig();
		toggles.saveDefaultConfig();
		specs.saveDefaultConfig();
		
		console = getServer().getConsoleSender();
		
		api = new SpectateAPI(this);
		arenasManager = new ArenasManager(this);
		
		// Fix config if from previous version
		if (toggles.getConfig().contains("version") && toggles.getConfig().getDouble("version")<version) {
			console.sendMessage(prefix+"Updating to version "+ChatColor.RED+version+ChatColor.GOLD+"...");
			if (!toggles.getConfig().contains("seespecs")) {
				toggles.getConfig().set("seespecs", false);
				console.sendMessage(ChatColor.GOLD+"Added "+ChatColor.WHITE+"seespecs: false"+ChatColor.GOLD+" to "+ChatColor.WHITE+"toggles.yml"+ChatColor.GOLD+"...");
			}
			if (!toggles.getConfig().contains("blockcmds")) {
			toggles.getConfig().set("blockcmds", true);
				console.sendMessage(ChatColor.GOLD+"Added "+ChatColor.WHITE+"blockcmds: true"+ChatColor.GOLD+" to "+ChatColor.WHITE+"toggles.yml"+ChatColor.GOLD+"...");
			}
			if (!toggles.getConfig().contains("adminbypass")) {
			toggles.getConfig().set("adminbypass", false);
				console.sendMessage(ChatColor.GOLD+"Added "+ChatColor.WHITE+"adminbypass: false"+ChatColor.GOLD+" to "+ChatColor.WHITE+"toggles.yml"+ChatColor.GOLD+"...");
			}
			// Config was updated, fix version number.
			toggles.getConfig().set("version",version);
			toggles.saveConfig();
		} else if (toggles.getConfig().contains("version") && toggles.getConfig().getDouble("version")>version) {
			console.sendMessage(ChatColor.GOLD+"Version "+ChatColor.RED+toggles.getConfig().getDouble("version")+ChatColor.GOLD+" available!");
		}
		
		compass = toggles.getConfig().getBoolean("compass", true);
		clock = toggles.getConfig().getBoolean("arenaclock", true);
		specChat = toggles.getConfig().getBoolean("specchat", true);
		scoreboard = toggles.getConfig().getBoolean("colouredtablist", true);
		output = toggles.getConfig().getBoolean("outputmessages", true);
		death = toggles.getConfig().getBoolean("deathspec", false);
		
		seeSpecs = toggles.getConfig().getBoolean("seespecs", false);
		if (!scoreboard) {
			seeSpecs = false;
		}
		
		blockCmds = toggles.getConfig().getBoolean("blockcmds", true);
		adminBypass = toggles.getConfig().getBoolean("adminbypass", false);

		if (scoreboard) {
			manager = getServer().getScoreboardManager();
			board = manager.getNewScoreboard();
			board.registerNewObjective("health", "health").setDisplaySlot(DisplaySlot.PLAYER_LIST);
			team = board.registerNewTeam("spec");
			team.setPrefix(ChatColor.DARK_GRAY + "[" + ChatColor.GRAY + "Spec" + ChatColor.DARK_GRAY + "] " + ChatColor.GRAY);
		}
		
		if (seeSpecs) {
			team.setCanSeeFriendlyInvisibles(true);
		}

		for (Player player : getServer().getOnlinePlayers()) {
			user.put(player.getName(), new PlayerObject());
		}
		
		getServer().getPluginManager().registerEvents(new SpectateListener(this), this);
		
		if(output) {
			console.sendMessage(prefix + "Version " + ChatColor.RED + version + ChatColor.GOLD + " is enabled!");
		}
		
		this.getCommand("spectate").setExecutor(new SpectateCommand(this));
		this.getCommand("spec").setExecutor(new SpectateCommand(this));
	}
	
	@Override
	public void onDisable() {
		if(output) {
			console.sendMessage(prefix + "Disabling...");
		}
		for (Player player : getServer().getOnlinePlayers()) {
			for (Player target : getServer().getOnlinePlayers()) {
				target.showPlayer(player);
			}
			if (user.get(player.getName()).spectating) {
				player.setAllowFlight(false);
				player.setGameMode(getServer().getDefaultGameMode());
				player.getInventory().clear();
				if (scoreboard) {
					team.removePlayer(player);
				}
				loadPlayerInv(player);
				spawnPlayer(player);
				user.get(player.getName()).spectating = false;
			}
		}
		
		// Just in case
		arenasManager.save();
	}

	// --------------
	// CUSTOM METHODS
	// --------------
	
	/**
	 * Teleports the player to the global lobby location.
	 * 
	 * @param player
	 * @return true if the player was  teleported, false else.
	 */
	protected boolean spawnPlayer(Player player) {
		player.setFireTicks(0);
		if (setup.getConfig().getBoolean("active")) {
			Location where = new Location(getServer().getWorld(setup.getConfig().getString("world")), setup.getConfig().getDouble("xPos"), setup.getConfig().getDouble("yPos"), setup.getConfig().getDouble("zPos"));
			Location aboveWhere = new Location(getServer().getWorld(setup.getConfig().getString("world")), setup.getConfig().getDouble("xPos"), setup.getConfig().getDouble("yPos") + 1, setup.getConfig().getDouble("zPos"));
			Location belowWhere = new Location(getServer().getWorld(setup.getConfig().getString("world")), setup.getConfig().getDouble("xPos"), setup.getConfig().getDouble("yPos") - 1, setup.getConfig().getDouble("zPos"));
			if (where.getBlock().getType() != Material.AIR || aboveWhere.getBlock().getType() != Material.AIR || (belowWhere.getBlock().getType() == Material.AIR || belowWhere.getBlock().getType() == Material.LAVA || belowWhere.getBlock().getType() == Material.WATER)) {
				while (where.getBlock().getType() != Material.AIR || aboveWhere.getBlock().getType() != Material.AIR || (belowWhere.getBlock().getType() == Material.AIR || belowWhere.getBlock().getType() == Material.LAVA || belowWhere.getBlock().getType() == Material.WATER)) {
					where.setY(where.getY()+1);
					aboveWhere.setY(aboveWhere.getY()+1);
					belowWhere.setY(belowWhere.getY()+1);
					if (where.getY() > getServer().getWorld(setup.getConfig().getString("world")).getHighestBlockYAt(where)) {
						where.setY(where.getY()-2);
						aboveWhere.setY(aboveWhere.getY()-2);
						belowWhere.setY(belowWhere.getY()-2);
					}
				}
			}
			user.get(player.getName()).teleporting = true;
			player.teleport(where);
			user.get(player.getName()).teleporting = false;
			return true;
		} else {
			return player.performCommand("spawn");
		}
	}
	
	
	/**
	 * Opens the player head GUI, to allow spectators to choose a player to teleport to.
	 * 
	 * @param spectator The GUI will be open for this spectator.
	 * @param region The UUID of the arena to use to choose the players to display on the GUI. Null if there isn't any arena set for this player.
	 */
	protected void showGUI(Player spectator, UUID region) {
		Inventory gui = null;
		for (Player player : getServer().getOnlinePlayers()) {
			if (setup.getConfig().getString("mode").equals("any")) {
				if (gui == null) {
					gui = Bukkit.getServer().createInventory(spectator, 27, ChatColor.BLACK + "Teleporter");
				}
				
				if (player.hasPermission("spectate.hide") == false && user.get(player.getName()).spectating == false) {
					ItemStack playerhead = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
					SkullMeta meta = (SkullMeta)playerhead.getItemMeta();
					meta.setOwner(player.getName());
					meta.setDisplayName(player.getName());
					playerhead.setItemMeta(meta);
					gui.addItem(playerhead);
				}
				
			}
			else if (setup.getConfig().getString("mode").equals("arena")) {
				if (region == null) {
					if(output) {spectator.sendMessage(prefix + "Pick an arena first using the clock!");}
					return;
				}
				else {
					if (gui == null) gui = Bukkit.getServer().createInventory(spectator, 27, ChatColor.BLACK + "Arena " + ChatColor.ITALIC + setup.getConfig().getString("arena." + region + ".name"));
					Location where = player.getLocation();
					Arena currentArena = arenasManager.getArena(region);
					int pos1y = currentArena.getCorner1().getBlockY();
					int pos2y = currentArena.getCorner2().getBlockY();
					int pos1x = currentArena.getCorner1().getBlockX();
					int pos2x = currentArena.getCorner2().getBlockX();
					int pos1z = currentArena.getCorner1().getBlockZ();
					int pos2z = currentArena.getCorner2().getBlockZ();
					// pos1 should have the highest co-ords of the arena, pos2 the lowest
					if (player.hasPermission("spectate.hide") == false && user.get(player.getName()).spectating == false) {
						if (Math.floor(where.getY()) < Math.floor(pos1y) && Math.floor(where.getY()) > Math.floor(pos2y)) {
							if (Math.floor(where.getX()) < pos1x && Math.floor(where.getX()) > pos2x) {
								if (Math.floor(where.getZ()) < pos1z && Math.floor(where.getZ()) > pos2z) {
									ItemStack playerhead = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
									
									SkullMeta meta = (SkullMeta)playerhead.getItemMeta();
									meta.setOwner(player.getName());
									meta.setDisplayName(player.getName());
									
									playerhead.setItemMeta(meta);
									
									gui.addItem(playerhead);
								}
							}
						}
					}
				}
			}
		}
		
		spectator.openInventory(gui);
	}
	
	
	/**
	 * Shows the arena selection GUI, full of books with the name of valid arenas.
	 * 
	 * @param spectator The GUI will be open for this spectator.
	 */
	protected void showArenaGUI(Player spectator) {
		Inventory gui = Bukkit.getServer().createInventory(spectator, 27, basePrefix);
		
		for (Arena arena : arenasManager.getArenas()) {
			ItemStack arenaBook = new ItemStack(Material.BOOK, 1);
			
			ItemMeta meta = (ItemMeta)arenaBook.getItemMeta();
			meta.setDisplayName(arena.getName());
			
			arenaBook.setItemMeta(meta);
			
			gui.addItem(arenaBook);
		}
		
		spectator.openInventory(gui);
	}
	
	
	/**
	 * Teleports the spectator to the player they have chosen using "/spec p <target>"
	 * 
	 * @param spectator The spectator to teleport.
	 * @param target The spectator will be teleported at the current location of this player.
	 */
	protected void choosePlayer(Player spectator, Player target) {
		spectator.teleport(target);
		
		if(output) {
			spectator.sendMessage(prefix + "Teleported you to " + ChatColor.RED + target.getName());
		}
	}
	
	
	/**
	 * Checks for problems and enables spectator mode for spectator, on behalf of sender.
	 * 
	 * @param spectator The player that will be a spectator.
	 * @param sender The sender of the /spec on [player] command.
	 */
	protected void enableSpectate(Player spectator, CommandSender sender) {
		if (user.get(spectator.getName()).spectating) {
			// Spectator mode was already on
			if (sender instanceof Player && spectator.getName().equals(sender.getName())) {
				spectator.sendMessage(prefix + "You are already spectating!");
			}
			else {
				sender.sendMessage(prefix + ChatColor.RED + spectator.getDisplayName() + ChatColor.GOLD + " is already spectating!");
			}
		}
		
		else {
			// Teleport them to the global lobby
			spawnPlayer(spectator);
			
			// Hide them from everyone
			for (Player target : getServer().getOnlinePlayers()) {
				if(seeSpecs&&user.get(target.getName()).spectating) {
					spectator.showPlayer(target);
				}
				else {
					target.hidePlayer(spectator); // Hide the spectator from non-specs: if seeSpecs mode is off and the target isn't spectating
				}
			}
			
			// Gamemode, 'ghost' and inventory
			spectator.setGameMode(GameMode.ADVENTURE);
			savePlayerInv(spectator);
			spectator.setAllowFlight(true);
			spectator.setFoodLevel(20);
			spectator.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 15), true);
			
			// Disable interaction
			user.get(spectator.getName()).spectating = true;
			
			// Give them compass if the toggle is on
			if (compass) {
				ItemStack compass = new ItemStack(Material.COMPASS, 1);
				ItemMeta compassMeta = (ItemMeta)compass.getItemMeta();
				compassMeta.setDisplayName(ChatColor.BLUE + "Teleporter");
				compass.setItemMeta(compassMeta);
				spectator.getInventory().addItem(compass);
			}
			
			// Give them clock (only for arena mode and if the toggle is on)
			if (clock) {
				String mode = setup.getConfig().getString("mode");
				if (mode.equals("arena")) {
					ItemStack book = new ItemStack(Material.WATCH, 1);
					ItemMeta bookMeta = (ItemMeta)book.getItemMeta();
					bookMeta.setDisplayName(ChatColor.DARK_RED + "Arena chooser");
					book.setItemMeta(bookMeta);
					spectator.getInventory().addItem(book);
				}
			}
			
			// Set the prefix in the tab list if the toggle is on
			if (scoreboard) {
				user.get(spectator.getName()).oldScoreboard = spectator.getScoreboard();
				spectator.setScoreboard(board);
				team.addPlayer(spectator);
			}
			
			// Manage messages if spectator was enabled
			if (sender instanceof Player && spectator.getName().equals(sender.getName())) {
				if(output) {
					spectator.sendMessage(prefix + "Spectator mode " + ChatColor.RED + "enabled");
				}
			} 
			else if (sender instanceof Player && !spectator.getName().equals(sender.getName())) {
				if(output) {
					spectator.sendMessage(prefix + "Spectator mode " + ChatColor.RED + "enabled" + ChatColor.GOLD + " by " + ChatColor.RED + ((Player) sender).getDisplayName());
				}
				sender.sendMessage(prefix + "Spectator mode " + ChatColor.RED + "enabled" + ChatColor.GOLD + " for " + ChatColor.RED + spectator.getDisplayName());
			} 
			else {
				if(output) {
					spectator.sendMessage(prefix + "Spectator mode " + ChatColor.RED + "enabled" + ChatColor.GOLD + " by " + ChatColor.DARK_RED + "Console");
				}
				sender.sendMessage(prefix + "Spectator mode " + ChatColor.RED + "enabled" + ChatColor.GOLD + " for " + ChatColor.RED + spectator.getDisplayName());
			}
			
			specs.getConfig().set(spectator.getName(), true);
			specs.saveConfig();
		}
	}
	
	/**
	 * Checks for problems and disables spectator mode for spectator, on behalf of sender.
	 * 
	 * @param spectator The spectator that will be a normal player.
	 * @param sender The sender of the /spec off [player] command.
	 */
	protected void disableSpectate(Player spectator, CommandSender sender) {
		if (user.get(spectator.getName()).spectating) {
			// Show them to everyone
			for (Player target : getServer().getOnlinePlayers()) {
				if (seeSpecs&&user.get(target.getName()).spectating) {
					spectator.hidePlayer(target);
				}
				target.showPlayer(spectator);
			}

			// Teleport to spawn
			spawnPlayer(spectator);

			// Allow interaction
			user.get(spectator.getName()).spectating = false;
			spectator.setGameMode(getServer().getDefaultGameMode());
			spectator.setAllowFlight(false);
			loadPlayerInv(spectator);
			spectator.removePotionEffect(PotionEffectType.INVISIBILITY);

			// Remove from spec team
			if (scoreboard) {
				spectator.setScoreboard(user.get(spectator.getName()).oldScoreboard);
				team.removePlayer(spectator);
			}

			if (sender instanceof Player && spectator.getName().equals(sender.getName())) {
				if(output) {
					spectator.sendMessage(prefix + "Spectator mode " + ChatColor.RED + "disabled");
				}
			}
			else if (sender instanceof Player && !spectator.getName().equals(sender.getName())) {
				if(output) {
					spectator.sendMessage(prefix + "Spectator mode " + ChatColor.RED + "disabled" + ChatColor.GOLD + " by " + ChatColor.RED + ((Player) sender).getDisplayName());
				}
				sender.sendMessage(prefix + "Spectator mode " + ChatColor.RED + "disabled" + ChatColor.GOLD + " for " + ChatColor.RED + spectator.getDisplayName());
			}
			else {
				if(output) {
					spectator.sendMessage(prefix + "Spectator mode " + ChatColor.RED + "disabled" + ChatColor.GOLD + " by " + ChatColor.DARK_RED + "Console");
				}
				sender.sendMessage(prefix + "Spectator mode " + ChatColor.RED + "disabled" + ChatColor.GOLD + " for " + ChatColor.RED + spectator.getDisplayName());
			}
			
			specs.getConfig().set(spectator.getName(), null);
			specs.saveConfig();
		} 
		else {
			// Spectate mode wasn't on
			if (sender instanceof Player && spectator.getName().equals(sender.getName())) {
				spectator.sendMessage(prefix + "You aren't spectating!");
			} 
			else {
				sender.sendMessage(prefix + ChatColor.RED + spectator.getDisplayName() + ChatColor.GOLD + " isn't spectating!");
			}
		}
	}
	
	/**
	 * Lets a player select two points and set up an arena.
	 * 
	 * @param player The player involved in the setup process.
	 * @param block The block punched by the player.
	 * 
	 * @return True if the player was setting up an arena; false else.
	 */
	protected boolean arenaSetup(Player player, Block block) {
		if (user.get(player.getName()).setup == 2) {
			user.get(player.getName()).pos2 = block.getLocation();
			user.get(player.getName()).setup = 0;

			Location lowPos, hiPos;
			lowPos = new Location(user.get(player.getName()).pos1.getWorld(), 0, 0, 0);
			hiPos = new Location(user.get(player.getName()).pos1.getWorld(), 0, 0, 0);
			
			// yPos
			if (Math.floor(user.get(player.getName()).pos1.getY()) > Math.floor(user.get(player.getName()).pos2.getY())) {
				hiPos.setY(Math.floor(user.get(player.getName()).pos1.getY()));
				lowPos.setY(Math.floor(user.get(player.getName()).pos2.getY()));
			} else {
				lowPos.setY(Math.floor(user.get(player.getName()).pos1.getY()));
				hiPos.setY(Math.floor(user.get(player.getName()).pos2.getY()));
			}
			
			// xPos
			if (Math.floor(user.get(player.getName()).pos1.getX()) > Math.floor(user.get(player.getName()).pos2.getX())) {
				hiPos.setX(Math.floor(user.get(player.getName()).pos1.getX()));
				lowPos.setX(Math.floor(user.get(player.getName()).pos2.getX()));
			} else {
				lowPos.setX(Math.floor(user.get(player.getName()).pos1.getX()));
				hiPos.setX(Math.floor(user.get(player.getName()).pos2.getX()));
			}
			
			// zPos
			if (Math.floor(user.get(player.getName()).pos1.getZ()) > Math.floor(user.get(player.getName()).pos2.getZ())) {
				hiPos.setZ(Math.floor(user.get(player.getName()).pos1.getZ()));
				lowPos.setZ(Math.floor(user.get(player.getName()).pos2.getZ()));
			} else {
				lowPos.setZ(Math.floor(user.get(player.getName()).pos1.getZ()));
				hiPos.setZ(Math.floor(user.get(player.getName()).pos2.getZ()));
			}
			
			arenasManager.registerArena(new Arena(user.get(player.getName()).arenaName, hiPos, lowPos));
			
			player.sendMessage(prefix + "Arena " + ChatColor.RED + user.get(player.getName()).arenaName + " (#" + (setup.getConfig().getInt("nextarena")-1) + ")" + ChatColor.GOLD + " successfully set up!");
			
			// returns true: Cancels breaking of the block that was punched
			return true;
		}
		else {
			if (user.get(player.getName()).setup == 1) {
				user.get(player.getName()).pos1 = block.getLocation();
				
				player.sendMessage(prefix + "Punch point " + ChatColor.RED + "#2" + ChatColor.GOLD + " - the opposite corner of the arena");
				
				user.get(player.getName()).setup = 2;
				
				// returns true: Cancels breaking of the block that was punched
				return true;
			}
			else {
				// returns false: The player was not setting up an arena.
				return false;
			}
		}
	}
	
	
	/**
	 * Removes an arena.
	 * 
	 * @param arenaName
	 * @return True if the arena was removed; false else (non-existant arena).
	 */
	protected boolean removeArena(String arenaName) {
		
		Arena arenaToBeRemoved = arenasManager.getArena(arenaName);
		if(arenaToBeRemoved == null) {
			return false;
		}
		
		arenasManager.unregisterArena(arenasManager.getArena(arenaName));
		
		// The players in the deleted arena are removed to the arena
		for(Player player : this.getServer().getOnlinePlayers()) {
			if(user.get(player.getName()).spectating) {
				if(user.get(player.getName()).arena.equals(arenaToBeRemoved.getUUID())) {
					removePlayerFromArena(player);
				}
			}
		}
		
		return true;
	}
	
	
	/**
	 * Sets the arena for the given player.
	 * Teleports the player to the lobby of that arena, if a lobby is available.
	 * 
	 * @param player The player.
	 * @param arenaName The name of the arena.
	 * @param teleportToLobby If true the player will be teleported to the lobby (if a lobby is set).
	 * @return True if the change was effective (i.e. the arena exists).
	 */
	protected boolean setArenaForPlayer(Player player, String arenaName, boolean teleportToLobby) {
		
		Arena arena = arenasManager.getArena(arenaName);
		
		user.get(player.getName()).arena = arena.getUUID();
		
		if(teleportToLobby) {
			Location lobbyLocation = arena.getLobby();
			
			if(lobbyLocation == null) { // No lobby set
				player.sendMessage(prefix + "No lobby location set for " + ChatColor.RED + arenaName);
				return true;
			}
			
			if(output) {
				player.sendMessage(prefix + "Teleported you to " + ChatColor.RED + arenaName);
			}
			
			player.teleport(lobbyLocation);
		}
		
		return true;
	}
	
	
	/**
	 * Sets the arena for the given player.
	 * Teleports the player to the lobby of that arena, if a lobby is available.
	 * 
	 * @param player The player.
	 * @param arenaName The name of the arena.
	 * @return True if the change was effective (i.e. the arena exists).
	 */
	protected boolean setArenaForPlayer(Player player, String arenaName) {
		return setArenaForPlayer(player, arenaName, true);
	}
	
	
	/**
	 * Removes a player from his arena.
	 * The player is teleported to the main lobby, if such a lobby is set.
	 * 
	 * @param player
	 */
	protected void removePlayerFromArena(Player player) {
		user.get(player.getName()).arena = null;
		
		boolean teleported = spawnPlayer(player);
		
		if(output) {
			if(teleported) {
				player.sendMessage(prefix + "You were removed from your current arena and teleported to the main lobby.");
			}
			else {
				player.sendMessage(prefix + "You were removed from your current arena.");
			}
		}
	}
	
	/**
	 * Saves the player's inventory and clears it before enabling spectator mode.
	 * 
	 * @param player The concerned player.
	 */
	protected void savePlayerInv(Player player) {
		user.get(player.getName()).inventory = player.getInventory().getContents();
		user.get(player.getName()).armour = player.getInventory().getArmorContents();
		
		player.getInventory().clear();
		player.getInventory().setArmorContents(null);
	}
	
	/**
	 * Loads the player's inventory after disabling the spectate mode.
	 * 
	 * @param player The concerned player.
	 */
	protected void loadPlayerInv(Player player) {
		player.getInventory().clear();
		player.getInventory().setContents(user.get(player.getName()).inventory);
		player.getInventory().setArmorContents(user.get(player.getName()).armour);
		
		user.get(player.getName()).inventory = null;
		user.get(player.getName()).armour = null;
		
		player.updateInventory(); // yes, it's deprecated. But it still works!
	}
	
	/**
	 * Broadcasts a message to all players with spectator mode enabled, and the sender.
	 * 
	 * @param sender The sender of the message to be broadcasted.
	 * @param message The message to broadcast.
	 */
	protected void broadcastToSpectators(CommandSender sender, String message) {
		String senderName = null;
		if(sender instanceof Player) {
			senderName = ChatColor.WHITE + ((Player) sender).getDisplayName();
		}
		else {
			senderName = ChatColor.DARK_RED + "CONSOLE";
		}
		
		String formattedMessage = ChatColor.GOLD + "[" + senderName + ChatColor.GOLD + " -> spectators] " + ChatColor.RESET + message;
		
		for (Player player : getServer().getOnlinePlayers()) {
			if(user.get(player.getName()).spectating || player.getName() == sender.getName()) {
				player.sendMessage(formattedMessage);
			}
		}
		
		console.sendMessage(formattedMessage);
	}
	
	/**
	 * Sends a spectator chat message, from one spectator to all other spectators. 
	 * Includes "/me" actions
	 * 
	 * @param sender The sender of the message.
	 * @param message The text of the message.
	 * @param isAction If true, the message will be displayed as an action message (like /me <message>).
	 */
	protected void sendSpectatorMessage(CommandSender sender, String message, Boolean isAction) {
		String playerName = null;
		if(sender instanceof Player) {
			playerName = ChatColor.WHITE + ((Player) sender).getDisplayName();
		} else {
			playerName = ChatColor.DARK_RED + "CONSOLE";
		}
		
		String invite = null;
		if(isAction) {
			invite = "* " + playerName + " " + ChatColor.GRAY;
		} else {
			invite = playerName + ChatColor.GRAY + ": ";
		}
		
		for (Player player : getServer().getOnlinePlayers()) {
			if(user.get(player.getName()).spectating) {
				player.sendMessage(ChatColor.GRAY + "[SPEC] " + invite + message);
			}
		}
		console.sendMessage(ChatColor.GRAY + "[SPEC] " + invite + message);
	}
	
	/**
	 * Returns the API.
	 * 
	 * @see SpectateAPI
	 * 
	 * @return The API.
	 */
	public SpectateAPI getAPI() {
		return api;
	}
}
