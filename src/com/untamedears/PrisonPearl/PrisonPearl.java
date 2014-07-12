package com.untamedears.PrisonPearl;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class PrisonPearl {
    public static final int HOLDER_COUNT = 5;
    public class Holder {
        public Holder(Player p) {
            player = p;
            item = null;
            blocklocation = null;
        }
        public Holder(Item i) {
            player = null;
            item = i;
            blocklocation = null;
        }
        public Holder(Location bl) {
            player = null;
            item = null;
            blocklocation = bl;
        }
        public final Player player;
        public final Item item;
        public final Location blocklocation;
    }
    // Mostly used as a Deque, but clear() is used from LinkedList
    private LinkedList<Holder> holders = new LinkedList<Holder>();

	private final short id;
	private final String imprisonedname;
	private String motd = "";
	private boolean pearlOnCursor = false;
	private long lastMoved = 0;
	
	public PrisonPearl(short id, String imprisonedname, Player holderplayer) {
		this.id = id;
		this.imprisonedname = imprisonedname;
		this.holders.addFirst(new Holder(holderplayer));
	}
	
    public PrisonPearl(short id, String imprisonedname, Location blocklocation) {
		this.id = id;
		this.imprisonedname = imprisonedname;
		this.holders.addFirst(new Holder(blocklocation));
	}

	public static PrisonPearl makeFromLocation(short id, String imprisonedname, Location loc) {
		if (imprisonedname == null || loc == null)
			return null;
		BlockState bs = loc.getBlock().getState();
		if (bs instanceof InventoryHolder)
			return new PrisonPearl(id, imprisonedname, loc);
		else
			return null;
	}

	public short getID() {
		return id;
	}

	public String getImprisonedName() {
		return imprisonedname;
	}

	public Player getImprisonedPlayer() {
		return Bukkit.getPlayerExact(imprisonedname);
	}

	public OfflinePlayer getImprisonedOfflinePlayer() {
		return Bukkit.getOfflinePlayer(imprisonedname);
	}

    public Player getHolderPlayer() {
        return getHolderPlayer(this.holders.peekFirst());
    }

    public Player getHolderPlayer(final Holder holder) {
        if (holder != null) {
            return holder.player;
        }
        return null;
    }

    public BlockState getHolderBlockState() {
        return getHolderBlockState(this.holders.peekFirst());
    }

    public BlockState getHolderBlockState(final Holder holder) {
        if (holder != null && holder.blocklocation != null) {
            return holder.blocklocation.getBlock().getState();
        }
        return null;
    }

    public Item getHolderItem() {
        return getHolderItem(this.holders.peekFirst());
    }

    public Item getHolderItem(final Holder holder) {
        if (holder != null) {
            return holder.item;
        }
        return null;
    }

    public String getHolderName() {
        return getHolderName(this.holders.peekFirst());
    }

	public String getHolderName(final Holder holder) {
		if (holder.player != null) {
			return holder.player.getName();
		} else if (holder.item != null) {
			return "nobody";
		} else if (holder.blocklocation != null) {
			switch (getHolderBlockState(holder).getType()) {
			case CHEST:
			case TRAPPED_CHEST:
				return "a chest";
			case FURNACE:
				return "a furnace";
			case BREWING_STAND:
				return "a brewing stand";
			case DISPENSER:
				return "a dispenser";
			case ITEM_FRAME:
				return "a wall frame";
			case DROPPER:
				return "a dropper";
			case HOPPER:
				return "a hopper";
			case ENDER_CHEST:
				return "a chest";
			default:
				PrisonPearlPlugin.info("PrisonPearl " + id + " is inside an unknown block " + getHolderBlockState(holder).getType().toString());
				return "an unknown block"; 
			}
		} else {
			PrisonPearlPlugin.info("PrisonPearl " + id + " has no player, item, nor location");
			return "unknown"; 
		}
	}

	public Location getLocation() {
		return getLocation(this.holders.peekFirst());
	}

	public Location getLocation(final Holder holder) {
		if (holder.player != null) {
			return holder.player.getLocation().add(0, -.5, 0);
		} else if (holder.item != null) {
			return holder.item.getLocation();
		} else if (holder.blocklocation != null) {
			return holder.blocklocation;
		} else {
			throw new RuntimeException("PrisonPearl " + id + " has no player, item, nor location");
		}
	}

	public String describeLocation() {
		final Holder holder = this.holders.peekFirst();
		final Location loc = getLocation(holder);
		final Vector vec = loc.toVector();
		final String str = loc.getWorld().getName() + " " + vec.getBlockX() + " " + vec.getBlockY() + " " + vec.getBlockZ();
		return "held by " + getHolderName(holder) + " at " + str;
	}

	public boolean verifyHolder(Holder holder, StringBuilder feedback) {
		// Return true if the pearl exists in a valid location
		if (System.currentTimeMillis() - this.lastMoved < 2000) {
			// The pearl was recently moved. Due to a race condition, this exists to
			//  prevent players from spamming /ppl to get free when a pearl is moved.
			return true;
		}
		if (holder.item != null) {
			Chunk chunk = holder.item.getLocation().getChunk();
			for (Entity entity : chunk.getEntities()) {
				if (entity == holder.item)
					return true;
			}
			feedback.append("On ground not in chunk");
			return false;
		} else {
			Inventory inv;
			if (holder.player != null) {
				if (!holder.player.isOnline()) {
					feedback.append(String.format("Jailor %s not online",
						holder.player.getName()));
					return false;
				}
				if (pearlOnCursor) {
					return true;
				}
				ItemStack cursoritem = holder.player.getItemOnCursor();
				if (cursoritem.getType() == Material.ENDER_PEARL && cursoritem.getDurability() == id)
					return true;
				inv = holder.player.getInventory();
				feedback.append(String.format("Not in %s's inventory", holder.player.getName()));
			} else if (holder.blocklocation != null) {
				BlockState bs = getHolderBlockState(holder);
				if (bs == null) {
					feedback.append("BlockState is null");
					return false;
				}
				Location bsLoc = bs.getLocation();
				if (!(bs instanceof InventoryHolder)) {
					feedback.append(String.format(
						"%s not inventory at (%d,%d,%d)", bs.getType().toString(),
						bsLoc.getBlockX(), bsLoc.getBlockY(), bsLoc.getBlockZ()));
					return false;
				}
				inv = ((InventoryHolder)bs).getInventory();
				for (HumanEntity viewer : inv.getViewers()) {
					ItemStack cursoritem = viewer.getItemOnCursor();
					if (cursoritem.getType() == Material.ENDER_PEARL && cursoritem.getDurability() == id)
						return true;
				}
				feedback.append(String.format(
					"Not in %s at (%d,%d,%d)", bs.getType().toString(),
					bsLoc.getBlockX(), bsLoc.getBlockY(), bsLoc.getBlockZ()));
			} else {
				feedback.append("Has no player, item, nor location");
				return false;
			}
			for (ItemStack item : inv.all(Material.ENDER_PEARL).values()) {
				if (item.getDurability() == id)
					return true;
			}
			return false;
		}
	}

    public boolean verifyLocation() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("PP (%d, %s) failed verification: ",
            id, imprisonedname));
        for (final Holder holder : this.holders) {
            if (verifyHolder(holder, sb)) {
                return true;
            }
            sb.append(", ");
        }
        PrisonPearlPlugin.info(sb.toString());
        return false;
    }

    public void setHolder(Player player) {
        this.holders.addFirst(new Holder(player));
        this.pearlOnCursor = false;
        while (this.holders.size() > PrisonPearl.HOLDER_COUNT) {
            this.holders.removeLast();
        }
    }

    public void setCursorHolder(Player player) {
        this.holders.addFirst(new Holder(player));
        this.pearlOnCursor = true;
        while (this.holders.size() > PrisonPearl.HOLDER_COUNT) {
            this.holders.removeLast();
        }
    }

    public <ItemBlock extends BlockState & InventoryHolder> void setHolder(ItemBlock blockstate) {
        this.holders.addFirst(new Holder(blockstate.getLocation()));
        this.pearlOnCursor = false;
        while (this.holders.size() > PrisonPearl.HOLDER_COUNT) {
            this.holders.removeLast();
        }
    }
    
    public void setHolder(Location location) {
        this.holders.addFirst(new Holder(location));
        this.pearlOnCursor = false;
        while (this.holders.size() > PrisonPearl.HOLDER_COUNT) {
            this.holders.removeLast();
        }
    }

    public void setHolder(Item item) {
        this.holders.addFirst(new Holder(item));
        this.pearlOnCursor = false;
        while (this.holders.size() > PrisonPearl.HOLDER_COUNT) {
            this.holders.removeLast();
        }
    }

    public String getMotd() {
        return motd;
    }

    public void setMotd(String motd) {
        this.motd = motd;
    }

    public void markMove() {
        this.lastMoved = System.currentTimeMillis();
    }

    // ignoreList is a Set of lower-case player names to not send messages to
    public static Set<String> sendProximityMessage(
            final Location location, final Double distance, final String message, final Set<String> ignoreList) {
        final Double distSquared = distance * distance;
        final Double minX = location.getX() - distance;
        final Double maxX = location.getX() + distance;
        final Double minZ = location.getZ() - distance;
        final Double maxZ = location.getZ() + distance;
        final World world = location.getWorld();
        final Set<String> messagedPlayers = new HashSet<String>();
        for (Player other : world.getPlayers()) {
            final Location otherLoc = other.getLocation();
            final Double otherX =  otherLoc.getX();
            if (otherX < minX || otherX > maxX) {
                continue;
            }
            final Double otherZ =  otherLoc.getZ();
            if (otherZ < minZ || otherZ > maxZ) {
                continue;
            }
            final Double otherDistSq = location.distanceSquared(otherLoc);
            if (otherDistSq > distSquared) {
                continue;
            }
            final String otherNameLc = other.getName().toLowerCase();
            if (ignoreList != null && ignoreList.contains(otherNameLc)) {
                continue;
            }
            other.sendMessage(message);
            messagedPlayers.add(otherNameLc);
        }
        return messagedPlayers;
    }
}
