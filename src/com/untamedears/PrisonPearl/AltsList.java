package com.untamedears.PrisonPearl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

class AltsList implements Listener {
	private HashMap<UUID, List<UUID>> altsHash;
	private boolean initialised = false;
	private PrisonPearlPlugin plugin_;

	public AltsList(PrisonPearlPlugin plugin) {
		plugin_ = plugin;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onAltsListUpdate(AltsListEvent event) {
		if (!initialised) {
			PrisonPearlPlugin
					.info("Missed AltsListUpdate during initialization");
			return;
		}
		final List<UUID> altsList = event.getAltsList();
		// Save the old alt lists in their entirety to reduce all the cross
		// checking
		// players for existence within the Set.
		final Set<List<UUID>> banListsToCheck = new HashSet<List<UUID>>(
				altsList.size());
		final List<UUID> normalizedList = new ArrayList<UUID>(altsList.size());
		for (UUID playerUUID : altsList) {
			normalizedList.add(playerUUID);
			if (altsHash.containsKey(playerUUID)) {
				banListsToCheck.add(altsHash.get(playerUUID));
			}
			altsHash.put(playerUUID, normalizedList);
		}
		// Unroll the ban lists into the playerBansToCheck. Only need a single
		// account from the banlist we just built to check it.
		final Set<UUID> playerBansToCheck = new HashSet<UUID>(
				banListsToCheck.size() * 10);
		playerBansToCheck.add(normalizedList.get(0));
		for (List<UUID> banList : banListsToCheck) {
			playerBansToCheck.addAll(banList);
		}
		// Check each player for bans, removing their alt list from the check
		// list
		// after they have been checked.
		final List<String> finalList = new ArrayList<String>(altsList.size());
		while (!playerBansToCheck.isEmpty()) {
			final UUID playerUUID = playerBansToCheck.iterator().next();
			final List<UUID> thisAltList = altsHash.get(playerUUID);
			if (thisAltList == null) {
				playerBansToCheck.remove(playerUUID);
				continue;
			}
			playerBansToCheck.removeAll(thisAltList);
			for (UUID altUUID : thisAltList) {
				plugin_.checkBan(altUUID);
			}
		}
	}

	public void queryForUpdatedAltLists(List<UUID> playersToCheck) {
		// Fires the RequestAltsListEvent event with the list of players to
		// check. This event won't contain results upon return. It is up to
		// the upstream event handler to fire the AltsListEvent synchronously
		// back to this class for each updated alts list to provide results.
		Bukkit.getServer()
				.getPluginManager()
				.callEvent(
						new RequestAltsListEvent(new ArrayList<UUID>(
								playersToCheck)));
	}

	public void cacheAltListFor(UUID playerUUID) {
		if (altsHash.containsKey(playerUUID)) {
			return;
		}
		List<UUID> singleton = new ArrayList<UUID>(1);
		singleton.add(playerUUID);
		Bukkit.getServer().getPluginManager()
				.callEvent(new RequestAltsListEvent(singleton));
	}

	public void load(File file) {
		try {
			loadAlts(file);
			initialised = true;
		} catch (IOException e) {
			e.printStackTrace();
			Bukkit.getLogger().info("Failed to load file!");
			initialised = false;
		}
	}

	private void loadAlts(File file) throws IOException {
		altsHash = new HashMap<UUID, List<UUID>>();
		FileInputStream fis;
		fis = new FileInputStream(file);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		String line;
		while ((line = br.readLine()) != null) {
			if (line.length() > 1) {
				String parts[] = line.split(" ");
				List<UUID> uuids = new ArrayList<UUID>(parts.length);
				// String[] newString = new String[parts.length];
				// System.arraycopy(parts, 0, newString, 0, parts.length);
				for (int i = 0; i < parts.length; i++) {
					uuids.set(i, UUID.fromString(parts[i]));
				}
				for (UUID uuid : uuids) {
					altsHash.put(uuid, uuids);
				}
			}
		}

		br.close();
	}

	public UUID[] getAltsArray(UUID uuid) {
		if (!initialised || !altsHash.containsKey(uuid))
			return new UUID[0];
		List<UUID> uuids = altsHash.get(uuid);
		List<UUID> alts = new ArrayList<UUID>(uuids.size() - 1);
		for (UUID altUUID : uuids) {
			if (!altUUID.equals(uuid)) {
				alts.add(altUUID);
			}
		}
		return alts.toArray(new UUID[alts.size()]);
	}

	public Set<UUID> getAllNames() {
		return altsHash.keySet();
	}
}
