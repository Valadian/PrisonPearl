package com.untamedears.PrisonPearl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Chest;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Furnace;
import org.bukkit.configuration.Configuration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.valadian.nametracker.NameAPI;

//import com.untamedears.EnderExpansion.Enderplugin;

public class PrisonPearlStorage implements SaveLoad {
	private PrisonPearlPlugin plugin;
	private final Map<Short, PrisonPearl> pearls_byid;
	private final Map<UUID, PrisonPearl> pearls_byimprisoned;
	private short nextid;
	
	private boolean isNameLayer;
	private boolean dirty;
	
	public PrisonPearlStorage(PrisonPearlPlugin plugin) {
		isNameLayer = Bukkit.getPluginManager().getPlugin("NameLayer").isEnabled();
		this.plugin = plugin;
		pearls_byid = new HashMap<Short, PrisonPearl>();
		pearls_byimprisoned = new HashMap<UUID, PrisonPearl>();
		nextid = 1;
	}
	
	public boolean isDirty() {
		return dirty;
	}
	
	public void markDirty() {
		dirty = true;
	}

//    public String normalizeName(String name) {
//        return name.toLowerCase();
//    }

	public void load(File file) throws IOException {
		FileInputStream fis = new FileInputStream(file);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		
		nextid = Short.parseShort(br.readLine());
		
		String line;
		while ((line = br.readLine()) != null) {
			String parts[] = line.split(" ");
			short id = Short.parseShort(parts[0]);
			UUID imprisoned = UUID.fromString(parts[1]);
			Location loc = new Location(Bukkit.getWorld(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), Integer.parseInt(parts[5]));
			String name = "";
			if (isNameLayer)
				name = NameAPI.getCurrentName(imprisoned);
			else
				name = Bukkit.getOfflinePlayer(imprisoned).getName();
			PrisonPearl pp = PrisonPearl.makeFromLocation(id, name, imprisoned, loc);
			if (parts.length != 6) {
				String motd = "";
				for (int i = 6; i < parts.length; i++) {
					motd = motd.concat(parts[i] + " ");
				}
				pp.setMotd(motd);
			}
			if (pp == null) {
				System.err.println("PrisonPearl for " + imprisoned + " didn't validate, so is now set free. Chunks and/or prisonpearls.txt are corrupt");
				continue;
			}
			
			addPearl(pp);
		}
		
		fis.close();
		
		dirty = false;
	}
	
	public void save(File file) throws IOException {
		FileOutputStream fos = new FileOutputStream(file);
		BufferedWriter br = new BufferedWriter(new OutputStreamWriter(fos));
	
		br.write(nextid + "\n");
		
		for (PrisonPearl pp : pearls_byid.values()) {
			if (pp.getHolderBlockState() == null)
				continue;
			
			Location loc = pp.getLocation();
			br.append(String.valueOf(pp.getID()));
			br.append(" ");
			br.append(pp.getImprisonedId().toString());
			br.append(" ");
			br.append(loc.getWorld().getName());
			br.append(" ");
			br.append(String.valueOf(loc.getBlockX()));
			br.append(" ");
			br.append(String.valueOf(loc.getBlockY()));
			br.append(" ");
			br.append(String.valueOf(loc.getBlockZ()));
			br.append(" ");
			br.append(pp.getMotd());
			br.append("\n");
		}
		
		br.flush();
		fos.close();
		
		dirty = false;
	}

	public short getNextId() {
		while(pearls_byid.containsKey(nextid)) {
			++nextid;
			if (nextid > 0x7FF0) {
				nextid = 10;
			}
		}
		return nextid++;
	}
	
	public PrisonPearl newPearl(Player imprisoned, Player imprisoner) {
		return newPearl(imprisoned.getName(), imprisoned.getUniqueId(), imprisoner);
	}
	
	public PrisonPearl newPearl(String imprisonedName, UUID imprisonedId, Player imprisoner) {
		PrisonPearl pp = new PrisonPearl(getNextId(), imprisonedName, imprisonedId, imprisoner);
		addPearl(pp);
		return pp;
	}

	private final int HolderStateToInventory_SUCCESS = 0;
	private final int HolderStateToInventory_BADPARAM = 1;
	private final int HolderStateToInventory_NULLSTATE = 2;
	private final int HolderStateToInventory_BADCONTAINER = 3;
	private final int HolderStateToInventory_NULLINV = 4;

	private int HolderStateToInventory(PrisonPearl pp, Inventory inv[]) {
		if (pp == null || inv == null) {
			return HolderStateToInventory_BADPARAM;
		}
		BlockState inherentViolence = pp.getHolderBlockState();
//		if (Bukkit.getPluginManager().isPluginEnabled("EnderExpansion")){
//			if (pp.getLocation().getBlock().getType() == Material.ENDER_CHEST){
//				inv[0] = Enderplugin.getchestInventory(pp.getLocation());
//				return HolderStateToInventory_SUCCESS;
//			}
//		}
		if (inherentViolence == null) {
			return HolderStateToInventory_NULLSTATE;
		}
		Material mat = inherentViolence.getType();
		
		switch(mat) {
		case FURNACE:
			inv[0] = ((Furnace)inherentViolence).getInventory();
			break;
		case DISPENSER:
			inv[0] = ((Dispenser)inherentViolence).getInventory();
			break;
		case BREWING_STAND:
			inv[0] = ((BrewingStand)inherentViolence).getInventory();
			break;
		case CHEST:
		case LOCKED_CHEST:
		case TRAPPED_CHEST:
			Chest c = ((Chest)inherentViolence);
			DoubleChestInventory dblInv = null;
			try {
				dblInv = (DoubleChestInventory)c.getInventory();
				inv[0] = dblInv.getLeftSide();
				inv[1] = dblInv.getRightSide();
			} catch(Exception e){
				inv[0] = c.getInventory();
			}
			break;
		default:
			return HolderStateToInventory_BADCONTAINER;
		}
		if (inv[0] == null && inv[1] == null) {
			return HolderStateToInventory_NULLINV;
		}
		return HolderStateToInventory_SUCCESS;
	}

	public void removePearlFromContainer(PrisonPearl pp) {
		Inventory inv[] = new Inventory[2];
		if (HolderStateToInventory(pp, inv) != HolderStateToInventory_SUCCESS) {
			return;
		}
		Inventory real_inv = null;
		int pearlslot = -1;
		int pp_id = pp.getID();
		for (int inv_idx = 0; inv_idx <= 1 && pearlslot == -1; ++inv_idx) {
			 if (inv[inv_idx] == null) {
				 continue;
			 }
			 HashMap<Integer, ? extends ItemStack> inv_contents = inv[inv_idx].all(Material.ENDER_PEARL);
			 for (int inv_slot : inv_contents.keySet()) {
				 ItemStack slot_item = inv_contents.get(inv_slot);
				 if (slot_item.getDurability() == pp_id) {
					 real_inv = inv[inv_idx];
					 pearlslot = inv_slot;
					 break;
				 }
			 }
		}
		if (real_inv == null || pearlslot == -1) {
			return;
		}
		real_inv.setItem(pearlslot, new ItemStack(Material.ENDER_PEARL));
	}
	
	public void deletePearl(PrisonPearl pp) {
		removePearlFromContainer(pp);
		pearls_byid.remove(pp.getID());
		pearls_byimprisoned.remove(pp.getImprisonedId());
		dirty = true;
	}
	
	public void addPearl(PrisonPearl pp) {
		PrisonPearl old = pearls_byimprisoned.get(pp.getImprisonedId());
		if (old != null)
			pearls_byid.remove(old.getID());
		
		pearls_byid.put(pp.getID(), pp);
		pearls_byimprisoned.put(pp.getImprisonedId(), pp);
		dirty = true;
	}
	
	public PrisonPearl getByID(short id) {
		return pearls_byid.get(id);
	}
	
	public PrisonPearl getByItemStack(ItemStack item) {
		if (item == null || item.getType() != Material.ENDER_PEARL || item.getDurability() == 0)
			return null;
		else
			return pearls_byid.get(item.getDurability());
	}
	
	public PrisonPearl getByImprisoned(UUID id) {
		return pearls_byimprisoned.get(id);
	}
	
	public PrisonPearl getByImprisoned(Player player) {
		return pearls_byimprisoned.get(player.getUniqueId());
	}
	
	public Integer getPearlCount(){
		return pearls_byimprisoned.size();
	}
	
	boolean isImprisoned(UUID id) {
		return pearls_byimprisoned.containsKey(id);
	}
	
	boolean isImprisoned(Player player) {
		return pearls_byimprisoned.containsKey(player.getUniqueId());
	}
	
	public Integer getImprisonedCount(UUID[] ids) {
		Integer count = 0;
		for (UUID id : ids) {
			if (pearls_byimprisoned.containsKey(id)) {
				count++;
			}
		}
		return count;
	}
	
	public UUID[] getImprisonedIds(UUID[] ids) {
		List<UUID> imdIds = new ArrayList<UUID>();
		for (UUID id : ids) {
			if (pearls_byimprisoned.containsKey(id)) {
				imdIds.add(id);
			}
		}
		int count = imdIds.size();
		UUID[] results = new UUID[count];
		for (int i = 0; i < count; i++) {
			results[i] = imdIds.get(i);
		}
		return results;
	}

	public boolean upgradePearl(Inventory inv, PrisonPearl pp) {
		final UUID prisonerId = pp.getImprisonedId();
		final String prisoner = Bukkit.getOfflinePlayer(prisonerId).getName();
		ItemStack is = new ItemStack(Material.ENDER_PEARL, 1, pp.getID());
		int pearlslot = inv.first(is);
		if (pearlslot < 0) {
			// If the pearl has been converted, first won't return it here
			// as the metadata doesn't match.
			return false;
		}
		ItemStack existing_is = inv.getItem(pearlslot);
		if (existing_is != null) {
			ItemMeta existing_meta = existing_is.getItemMeta();
			if (existing_meta != null) {
				String existing_name = existing_meta.getDisplayName();
				if (existing_name != null &&
					existing_name.compareTo(prisoner) == 0) {
					return true;
				}
			}
		}
		ItemMeta im = is.getItemMeta(); 
		// Rename pearl to that of imprisoned player 
		im.setDisplayName(prisoner);
		List<String> lore = new ArrayList<String>(); 
		lore.add(prisoner + " is held within this pearl");
		// Given enchantment effect
		// Durability used because it doesn't affect pearl behaviour
		im.addEnchant(Enchantment.DURABILITY, 1, true);
		im.setLore(lore);
		is.setItemMeta(im);
		is.removeEnchantment(Enchantment.DURABILITY); 
		inv.clear(pearlslot);
		inv.setItem(pearlslot, is);
		return true;
	}

	public String feedPearls(PrisonPearlManager pearlman){
		String message = "";
		String log = "";
		ConcurrentHashMap<Short,PrisonPearl> map = new ConcurrentHashMap<Short,PrisonPearl>(pearls_byid);

		long inactive_seconds = this.getConfig().getLong("ignore_feed.seconds", 0);
		long inactive_hours = this.getConfig().getLong("ignore_feed.hours", 0);
		long inactive_days = this.getConfig().getLong("ignore_feed.days", 0);

		int pearlsfed = 0;
		int coalfed = 0;
		int freedpearls = 0;
		for (PrisonPearl pp : map.values()) {

			final UUID prisonerId = pp.getImprisonedId();
			//final String prisoner = Bukkit.getPlayer(prisonerId).getName();
			Inventory inv[] = new Inventory[2];
			int retval = HolderStateToInventory(pp, inv);
			if (retval == HolderStateToInventory_BADCONTAINER) {
				pearlman.freePearl(pp);
				plugin.getLogger().info(prisonerId + " is being freed. Reason: Freed during coal feed, container was corrupt.");
				log+="\n freed:"+prisonerId+",reason:"+"badcontainer";
				freedpearls++;
				continue;
			} else if (retval != HolderStateToInventory_SUCCESS) {
				continue;
			}
			if (!upgradePearl(inv[0], pp) && inv[1] != null) {
				upgradePearl(inv[1], pp);
			}
			if (inactive_seconds != 0 || inactive_hours != 0 || inactive_days != 0) {
				long inactive_time = pp.getImprisonedOfflinePlayer().getLastPlayed();
				long inactive_millis = inactive_seconds * 1000 + inactive_hours * 3600000 + inactive_days * 86400000;
				inactive_time += inactive_millis;
				if (inactive_time <= System.currentTimeMillis()) {
					// if player has not logged on in the set amount of time than ignore feeding
					log += "\nnot fed inactive: " + prisonerId;
					continue;
				}
			}
			message = message + "Pearl #" + pp.getID() + ",Id: " + prisonerId + " in a " + pp.getHolderBlockState().getType();
			ItemStack requirement = plugin.getPPConfig().getUpkeepResource();
			int requirementSize = requirement.getAmount();

			if(inv[0].containsAtLeast(requirement,requirementSize)) {
				int pearlnum;
				pearlnum = inv.length;
				message = message + "\n Chest contains enough purestrain coal.";
				inv[0].removeItem(requirement);
				pearlsfed++;
				coalfed += requirementSize;
				log+="\n fed:" + prisonerId + ",location:"+ pp.describeLocation();
			} else if(inv[1] != null && inv[1].containsAtLeast(requirement,requirementSize)){
				message = message + "\n Chest contains enough purestrain coal.";
				inv[1].removeItem(requirement);
				pearlsfed++;
				coalfed += requirementSize;
				log+="\n fed:" + prisonerId + ",location:"+ pp.describeLocation();
			} else {
				message = message + "\n Chest does not contain enough purestrain coal.";
				plugin.getLogger().info(prisonerId + " is being freed. Reason: Freed during coal feed, container did not have enough coal.");
				pearlman.freePearl(pp);
				log+="\n freed:"+prisonerId+",reason:"+"nocoal"+",location:"+pp.describeLocation();
				freedpearls++;
			}
		}
		message = message + "\n Feeding Complete. " + pearlsfed + " were fed " + coalfed + " coal. " + freedpearls + " players were freed.";
		return message;
	}
	
	public String restorePearls(PrisonPearlManager pearlman, String config){
		//Read pearl config
		
		//For each entry
		
		//Create pearl for player
		
		//Place in chest
		
		//Check imprisonment status
		
		//Report restoration
		return "";
	}
	private Configuration getConfig() {
		return plugin.getConfig();
	}
}
