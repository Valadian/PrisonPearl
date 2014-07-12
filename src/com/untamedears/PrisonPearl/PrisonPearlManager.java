package com.untamedears.PrisonPearl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import net.minecraft.server.v1_7_R2.EntityPlayer;
import net.minecraft.server.v1_7_R2.MinecraftServer;
import net.minecraft.server.v1_7_R2.PlayerInteractManager;
import net.minecraft.util.com.mojang.authlib.GameProfile;

import org.bukkit.craftbukkit.v1_7_R2.CraftServer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Chest;
import org.bukkit.block.Dispenser;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Furnace;
import org.bukkit.configuration.Configuration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

class PrisonPearlManager implements Listener {
	private final PrisonPearlPlugin plugin;
	private final PrisonPearlStorage pearls;
	private EnderExpansion ee;

	public PrisonPearlManager(PrisonPearlPlugin plugin, PrisonPearlStorage pearls, EnderExpansion ee) {
		this.plugin = plugin;
		this.pearls = pearls;
		this.ee = ee;
		Bukkit.getPluginManager().registerEvents(this, plugin);
	}

	public boolean imprisonPlayer(Player imprisoned, Player imprisoner) {
		return imprisonPlayer(imprisoned.getName(), imprisoner);
	}

	/**
	 * @param imprisonedname
	 * @param imprisoner
	 * @return
	 */
	/**
	 * @param imprisonedname
	 * @param imprisoner
	 * @return
	 */
	public boolean imprisonPlayer(String imprisonedname, Player imprisoner) {
		World respawnworld = Bukkit.getWorld(getConfig().getString("free_world"));

		// set up the imprisoner's inventory
		Inventory inv = imprisoner.getInventory();
		ItemStack stack = null;
		int stacknum = -1;

		// scan for the smallest stack of normal ender pearls
		for (Entry<Integer, ? extends ItemStack> entry :
				inv.all(Material.ENDER_PEARL).entrySet()) {
			ItemStack newstack = entry.getValue();
			int newstacknum = entry.getKey();
			if (newstack.getDurability() == 0) {
				if (stack != null) {
					// don't keep a stack bigger than the previous one
					if (newstack.getAmount() > stack.getAmount()) {
						continue;
					}
					// don't keep an identical sized stack in a higher slot
					if (newstack.getAmount() == stack.getAmount() &&
							newstacknum > stacknum) {
						continue;
					}
				}

				stack = newstack;
				stacknum = entry.getKey();
			}
		}

		int pearlnum;
		ItemStack dropStack = null;
		if (stacknum == -1) { // no pearl (admin command)
			// give him a new one at the first empty slot
			pearlnum = inv.firstEmpty();
		} else if (stack.getAmount() == 1) { // if he's just got one pearl
			pearlnum = stacknum; // put the prison pearl there
		} else {
			// otherwise, put the prison pearl in the first empty slot
			pearlnum = inv.firstEmpty();
			if (pearlnum > 0) {
				// and reduce his stack of pearls by one
				stack.setAmount(stack.getAmount() - 1);
				inv.setItem(stacknum, stack);
			} else { // no empty slot?
				dropStack = new ItemStack(Material.ENDER_PEARL, stack.getAmount() - 1);
				pearlnum = stacknum; // then overwrite his stack of pearls
			}
		}

		// drop pearls that otherwise would be deleted
		if (dropStack != null) {
			imprisoner.getWorld().dropItem(imprisoner.getLocation(), dropStack);
			Bukkit.getLogger().info(
				imprisoner.getLocation() + ", " + dropStack.getAmount());
		}

		if (!imprisoner.hasPermission("prisonpearl.normal.pearlplayer")) {
			return false;
		}

		// create the prison pearl
		PrisonPearl pp = pearls.newPearl(imprisonedname, imprisoner);
		// set off an event
		if (!prisonPearlEvent(pp, PrisonPearlEvent.Type.NEW, imprisoner)) {
			pearls.deletePearl(pp);
			return false;
		}
		pp.markMove();

		// Create the inventory item
		ItemStack is = new ItemStack(Material.ENDER_PEARL, 1, pp.getID());
		ItemMeta im = is.getItemMeta();
		// Rename pearl to that of imprisoned player
		im.setDisplayName(pp.getImprisonedName());
		List<String> lore = new ArrayList<String>();
		// Gives pearl lore that says more info when hovered over
		lore.add(pp.getImprisonedName() + " is held within this pearl");
		// Given enchantment effect (durability used because it doesn't affect pearl behaviour)
		im.addEnchant(Enchantment.DURABILITY, 1, true);
		im.setLore(lore);
		is.setItemMeta(im);
		is.removeEnchantment(Enchantment.DURABILITY);
		// Give it to the imprisoner
		inv.setItem(pearlnum, is);
		// Reason for edit: Gives pearl enchantment effect (distinguishable, unstackable) Gives name of prisoner in inventory.

		if (getConfig().getBoolean("prison_resetbed")) {
			Player imprisoned = Bukkit.getPlayerExact(imprisonedname);
			// clear out the players bed
			if (imprisoned != null) {
				imprisoned.setBedSpawnLocation(respawnworld.getSpawnLocation());
			}
		}
		return true;
	}

	public boolean freePlayer(Player player) {
		PrisonPearl pp = pearls.getByImprisoned(player);
		return pp != null && freePearl(pp);
	}

	public boolean freePearl(PrisonPearl pp) {
		// set off an event
		if (!prisonPearlEvent(pp, PrisonPearlEvent.Type.FREED)) {
			return false;
		}
		pearls.deletePearl(pp);
		return true;
	}

	// Announce the person in a pearl when a player holds it
	@EventHandler(priority = EventPriority.MONITOR)
	public void onItemHeldChange(PlayerItemHeldEvent event) {

		Inventory inv = event.getPlayer().getInventory();
		ItemStack item = inv.getItem(event.getNewSlot());
		ItemStack newitem = announcePearl(event.getPlayer(), item);
		if (newitem != null)
			inv.setItem(event.getNewSlot(), newitem);
	}

	private ItemStack announcePearl(Player player, ItemStack item) {
		if (item == null)
			return null;

		if (item.getType() == Material.ENDER_PEARL && item.getDurability() != 0) {
			PrisonPearl pp = pearls.getByID(item.getDurability());

			if (pp == null) {
				return new ItemStack(Material.ENDER_PEARL, 1);
			}
			pp.markMove();
		}

		return null;
	}

	// Free pearls when right clicked
	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerInteract(PlayerInteractEvent event) {

		PrisonPearl pp = pearls.getByItemStack(event.getItem());
		if (pp == null)
			return;
		pp.markMove();

		if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			Material m = event.getClickedBlock().getType();
			if (m == Material.CHEST || m == Material.WORKBENCH
					|| m == Material.FURNACE || m == Material.DISPENSER
					|| m == Material.BREWING_STAND)
				return;
		} else if (event.getAction() != Action.RIGHT_CLICK_AIR) {
			return;
		}

		Player player = event.getPlayer();
		player.getInventory().setItemInHand(null);
		event.setCancelled(true);

		freePearl(pp);
		plugin.getLogger().info(pp.getImprisonedName() + " is being freed. Reason: " + player.getDisplayName() + " threw the pearl.");
		player.sendMessage("You've freed " + pp.getImprisonedName());
	}

	// Called from CombatTagListener.onNpcDespawn
	public void handleNpcDespawn(String plrname, Location loc) {
		World world = loc.getWorld();
		Player player = plugin.getServer().getPlayer(plrname);
		if (player == null) { // If player is offline
			MinecraftServer server = ((CraftServer)plugin.getServer()).getServer();
			GameProfile prof = new GameProfile(null, plrname);
			EntityPlayer entity = new EntityPlayer(
				server, server.getWorldServer(0), prof,
				new PlayerInteractManager(server.getWorldServer(0)));
			player = (entity == null) ? null : (Player) entity.getBukkitEntity();
			if (player == null) {
				return;
			}
			player.loadData();
		}

		Inventory inv = player.getInventory();
		int end = inv.getSize();
		for (int slot = 0; slot < end; ++slot) {
			ItemStack item = inv.getItem(slot);
			if (item == null) {
				continue;
			}
			if (!item.getType().equals(Material.ENDER_PEARL)) {
			   continue;
			}
			PrisonPearl pp = pearls.getByItemStack(item);
			if (pp==null){
				continue;
			}
			inv.clear(slot);
			world.dropItemNaturally(loc, item);  // drops pearl to ground.
		}
		player.saveData();
	}

	// Drops a pearl when a player leaves.
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		Player imprisoner = event.getPlayer();
		CombatTagManager ctm = plugin.getCombatTagManager();
		if (ctm.isCombatTagged(imprisoner)) { // if player is tagged
			return;
		}
		Location loc = imprisoner.getLocation();
		World world = imprisoner.getWorld();
		Inventory inv = imprisoner.getInventory();
		for (Entry<Integer, ? extends ItemStack> entry :
				inv.all(Material.ENDER_PEARL).entrySet()) {
			ItemStack item = entry.getValue();
			PrisonPearl pp = pearls.getByItemStack(item);
			if (pp == null) {
				continue;
			}
			pp.markMove();
			int slot = entry.getKey();
			inv.clear(slot);
			world.dropItemNaturally(loc, item);
		}
		imprisoner.saveData();
	}

	// Free the pearl if it despawns
	@EventHandler(priority = EventPriority.MONITOR)
	public void onItemDespawn(ItemDespawnEvent event) {
		PrisonPearl pp = pearls
				.getByItemStack(event.getEntity().getItemStack());
		if (pp == null)
			return;

		plugin.getLogger().info(pp.getImprisonedName() + " is being freed. Reason: PrisonPearl item despawned.");
		freePearl(pp);
	}

	// Free the pearl if its on a chunk that unloads
	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkUnload(ChunkUnloadEvent event) {
		for (Entity e : event.getChunk().getEntities()) {
			if (!(e instanceof Item))
				continue;

			final PrisonPearl pp = pearls.getByItemStack(
				((Item) e).getItemStack());
			if (pp == null) {
				continue;
			}

			final Entity entity = e;
			// doing this in onChunkUnload causes weird things to happen
			Bukkit.getScheduler().callSyncMethod(plugin, new Callable<Void>() {
						public Void call() throws Exception {
							if (freePearl(pp))
							{
								plugin.getLogger().info(pp.getImprisonedName() + " is being freed. Reason: Chunk with PrisonPearl unloaded.");
								entity.remove();
							}
							return null;
						}
					});

			event.setCancelled(true);
		}
	}

	// Free the pearl if it combusts in lava/fire
	@EventHandler(priority = EventPriority.MONITOR)
	public void onEntityCombustEvent(EntityCombustEvent event) {
		if (!(event.getEntity() instanceof Item))
			return;

		PrisonPearl pp = pearls.getByItemStack(
			((Item) event.getEntity()).getItemStack());
		if (pp == null)
			return;

		plugin.getLogger().info(pp.getImprisonedName() + " is being freed. Reason: PrisonPearl combusted(lava/fire).");
		freePearl(pp);
	}
	
	
	// Handle inventory dragging properly.
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInventoryDrag(InventoryDragEvent event) {
		if(event.isCancelled())
			return;
		
		Map<Integer, ItemStack> items = event.getNewItems();
		
		for(Integer slot : items.keySet()) {
			ItemStack item = items.get(slot);
			
			PrisonPearl pearl = pearls.getByItemStack(item);
			
			if(pearl != null) {
				boolean clickedTop = event.getView().convertSlot(slot) == slot;
				
				InventoryHolder holder = clickedTop ? event.getView().getTopInventory().getHolder() : event.getView().getBottomInventory().getHolder();
				
				pearl.markMove();
				updatePearlHolder(pearl, holder, event);
				
				if(event.isCancelled()) {
					return;
				}
			}
		}
	}

	// Track the location of a pearl
	// Forbid pearls from being put in storage minecarts
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInventoryClick(InventoryClickEvent event) {
		if(event.isCancelled())
			return;
		
		// announce an prisonpearl if it is clicked
		ItemStack newitem = announcePearl(
			(Player) event.getWhoClicked(), event.getCurrentItem());
		if (newitem != null)
			event.setCurrentItem(newitem);
		
		if(event.getAction() == InventoryAction.COLLECT_TO_CURSOR
				|| event.getAction() == InventoryAction.PICKUP_ALL
				|| event.getAction() == InventoryAction.PICKUP_HALF
				|| event.getAction() == InventoryAction.PICKUP_ONE) {
			PrisonPearl pearl = pearls.getByItemStack(event.getCurrentItem());
			
			if(pearl != null) {
				pearl.markMove();
				updatePearl(pearl, (Player) event.getWhoClicked(), true);
			}
		}
		else if(event.getAction() == InventoryAction.PLACE_ALL
				|| event.getAction() == InventoryAction.PLACE_SOME
				|| event.getAction() == InventoryAction.PLACE_ONE) {	
			PrisonPearl pearl = pearls.getByItemStack(event.getCursor());
			
			if(pearl != null) {
				boolean clickedTop = event.getView().convertSlot(event.getRawSlot()) == event.getRawSlot();
				
				InventoryHolder holder = clickedTop ? event.getView().getTopInventory().getHolder() : event.getView().getBottomInventory().getHolder();
				if (holder==null){
					Player player=pearl.getHolderPlayer();
					pearl.markMove();
					ee.updateEnderStoragePrison(pearl, event, player.getTargetBlock(null, 5).getLocation());
				}
				else{
				pearl.markMove();
				updatePearlHolder(pearl, holder, event);
				}
			}
		}
		else if(event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {			
			PrisonPearl pearl = pearls.getByItemStack(event.getCurrentItem());
			
			if(pearl != null) {
				boolean clickedTop = event.getView().convertSlot(event.getRawSlot()) == event.getRawSlot();
				
				InventoryHolder holder = !clickedTop ? event.getView().getTopInventory().getHolder() : event.getView().getBottomInventory().getHolder();
				if (holder==null){
					Player player=pearl.getHolderPlayer();
					pearl.markMove();
					ee.updateEnderStoragePrison(pearl, event, player.getTargetBlock(null, 5).getLocation());
				}
				else if(holder.getInventory().firstEmpty() >= 0) {
					pearl.markMove();
					updatePearlHolder(pearl, holder, event);
				}
			}
		}
		else if(event.getAction() == InventoryAction.HOTBAR_SWAP) {
			PlayerInventory playerInventory = event.getWhoClicked().getInventory();
			PrisonPearl pearl = pearls.getByItemStack(playerInventory.getItem(event.getHotbarButton()));
			
			if(pearl != null) {
				boolean clickedTop = event.getView().convertSlot(event.getRawSlot()) == event.getRawSlot();
				
				InventoryHolder holder = clickedTop ? event.getView().getTopInventory().getHolder() : event.getView().getBottomInventory().getHolder();
				
				pearl.markMove();
				updatePearlHolder(pearl, holder, event);
			}
			
			if(event.isCancelled())
				return;
			
			pearl = pearls.getByItemStack(event.getCurrentItem());
			
			if(pearl != null) {
				pearl.markMove();
				updatePearl(pearl, (Player) event.getWhoClicked());
			}
		}
		else if (event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
			PrisonPearl pearl = pearls.getByItemStack(event.getCursor());
			
			if(pearl != null) {
				boolean clickedTop = event.getView().convertSlot(event.getRawSlot()) == event.getRawSlot();
				
				InventoryHolder holder = clickedTop ? event.getView().getTopInventory().getHolder() : event.getView().getBottomInventory().getHolder();
				
				pearl.markMove();
				updatePearlHolder(pearl, holder, event);
			}
			
			if(event.isCancelled())
				return;
			
			pearl = pearls.getByItemStack(event.getCurrentItem());
			
			if(pearl != null) {
				pearl.markMove();
				updatePearl(pearl, (Player) event.getWhoClicked(), true);
			}
		}
		else if(event.getAction() == InventoryAction.DROP_ALL_CURSOR
				|| event.getAction() == InventoryAction.DROP_ALL_SLOT
				|| event.getAction() == InventoryAction.DROP_ONE_CURSOR
				|| event.getAction() == InventoryAction.DROP_ONE_SLOT) {
			// Handled by onItemSpawn
		}
		else {
			if(pearls.getByItemStack(event.getCurrentItem()) != null || pearls.getByItemStack(event.getCursor()) != null) {
				((Player) event.getWhoClicked()).sendMessage(ChatColor.RED + "Error: PrisonPearl doesn't support this inventory functionality quite yet!");
				
				event.setCancelled(true);
			}
		}
	}
	
	private void updatePearlHolder(PrisonPearl pearl, InventoryHolder holder, Cancellable event) {
		
		if (holder instanceof Chest) {
			updatePearl(pearl, (Chest) holder);
		} else if (holder instanceof DoubleChest) {
			updatePearl(pearl, (Chest) ((DoubleChest) holder).getLeftSide());
		} else if (holder instanceof Furnace) {
			updatePearl(pearl, (Furnace) holder);
		} else if (holder instanceof Dispenser) {
			updatePearl(pearl, (Dispenser) holder);
		} else if (holder instanceof BrewingStand) {
			updatePearl(pearl, (BrewingStand) holder);
		} else if (holder instanceof Player) {
			updatePearl(pearl, (Player) holder);
		}else {
			event.setCancelled(true);
		}
	}
	

	// Track the location of a pearl if it spawns as an item for any reason
	@EventHandler(priority = EventPriority.MONITOR)
	public void onItemSpawn(ItemSpawnEvent event) {
		Item item = event.getEntity();
		PrisonPearl pp = pearls.getByItemStack(item.getItemStack());
		if (pp == null)
			return;
		pp.markMove();
		updatePearl(pp, item);
	}

	// Track the location of a pearl if a player picks it up
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerPickupItem(PlayerPickupItemEvent event) {
		PrisonPearl pp = pearls.getByItemStack(event.getItem().getItemStack());
		if (pp == null)
			return;
		pp.markMove();
		updatePearl(pp, event.getPlayer());
	}

	private void updatePearl(PrisonPearl pp, Item item) {
		pp.setHolder(item);
		pearls.markDirty();
		Bukkit.getPluginManager().callEvent(
				new PrisonPearlEvent(pp, PrisonPearlEvent.Type.DROPPED));
	}

	private void updatePearl(PrisonPearl pp, Player player) {
	    updatePearl(pp, player, false);
	}

	private void updatePearl(PrisonPearl pp, Player player, boolean isOnCursor) {
		if (isOnCursor) {
			pp.setCursorHolder(player);
		} else {
			pp.setHolder(player);
		}
		pearls.markDirty();
		Bukkit.getPluginManager().callEvent(
				new PrisonPearlEvent(pp, PrisonPearlEvent.Type.HELD));
	}

	private <ItemBlock extends InventoryHolder & BlockState> void updatePearl(
			PrisonPearl pp, ItemBlock block) {
		pp.setHolder(block);
		pearls.markDirty();
		Bukkit.getPluginManager().callEvent(
				new PrisonPearlEvent(pp, PrisonPearlEvent.Type.HELD));
	}
	
	

	private boolean prisonPearlEvent(PrisonPearl pp, PrisonPearlEvent.Type type) {
		return prisonPearlEvent(pp, type, null);
	}

	private boolean prisonPearlEvent(PrisonPearl pp,
			PrisonPearlEvent.Type type, Player imprisoner) {
		PrisonPearlEvent event = new PrisonPearlEvent(pp, type, imprisoner);
		Bukkit.getPluginManager().callEvent(event);
		return !event.isCancelled();
	}
	public boolean prisonCommandEvent(String command){
		PrisonCommandEvent event= new PrisonCommandEvent(command);
		Bukkit.getPluginManager().callEvent(event);
		return !event.isCancelled();
	}
	private Configuration getConfig() {
		return plugin.getConfig();
	}

}
