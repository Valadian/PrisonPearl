package com.untamedears.PrisonPearl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

class AltsList implements Listener {
	private PrisonPearlPlugin plugin_;
	private HashMap<String, List<String>> altsHash = new HashMap<String, List<String>>();
	private boolean initialised = false;

	public AltsList(PrisonPearlPlugin plugin) {
		plugin_ = plugin;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onAltsListUpdate(AltsListEvent event) {
		if (!initialised) {
			PrisonPearlPlugin.info("Missed AltsListUpdate during initialization");
			return;
		}
		final List<String> altsList = event.getAltsList();
		// Save the old alt lists in their entirety to reduce all the cross checking
		//  players for existence within the Set.
		final Set<List<String>> banListsToCheck =
			new HashSet<List<String>>(altsList.size());
		final List<String> normalizedList = new ArrayList<String>(altsList.size());
		for (String playerName : altsList) {
			playerName = playerName.toLowerCase();
			normalizedList.add(playerName);
			if (altsHash.containsKey(playerName)) {
				banListsToCheck.add(altsHash.get(playerName));
			}
			altsHash.put(playerName, normalizedList);
		}
		// Unroll the ban lists into the playerBansToCheck. Only need a single
		//  account from the banlist we just built to check it.
		final Set<String> playerBansToCheck = new HashSet<String>(
			banListsToCheck.size() * 10);
		playerBansToCheck.add(normalizedList.get(0));
		for (List<String> banList : banListsToCheck) {
			playerBansToCheck.addAll(banList);
		}
		// Check each player for bans, removing their alt list from the check list
		//  after they have been checked.
		final List<String> finalList = new ArrayList<String>(altsList.size());
		while (!playerBansToCheck.isEmpty()) {
			final String playerName = playerBansToCheck.iterator().next();
			final List<String> thisAltList = altsHash.get(playerName);
			if (thisAltList == null) {
				playerBansToCheck.remove(playerName);
				continue;
			}
			playerBansToCheck.removeAll(thisAltList);
			for (String altName : thisAltList) {
				plugin_.checkBan(playerName);
			}
		}
	}

	public void queryForUpdatedAltLists(List<String> playersToCheck) {
		// Fires the RequestAltsListEvent event with the list of players to
		//  check. This event won't contain results upon return. It is up to
		//  the upstream event handler to fire the AltsListEvent synchronously
		//  back to this class for each updated alts list to provide results.
		Bukkit.getServer().getPluginManager().callEvent(
			new RequestAltsListEvent(new ArrayList<String>(playersToCheck)));
	}

	public void cacheAltListFor(String playerName) {
		playerName = playerName.toLowerCase();
		if (altsHash.containsKey(playerName)) {
			return;
		}
		List<String> singleton = new ArrayList<String>(1);
		singleton.add(playerName);
		Bukkit.getServer().getPluginManager().callEvent(new RequestAltsListEvent(singleton));
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
		altsHash = new HashMap<String, List<String>>();
		FileInputStream fis;
		fis = new FileInputStream(file);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		String line;
		while ((line = br.readLine()) != null) {
			if (line.length() > 1) {
				final List<String> parts = Arrays.asList(line.split(" "));
				for (String part : parts) {
					altsHash.put(part, parts);
				}
			}
		}

		br.close();
	}

	public String[] getAltsArray(String name){
		if (!initialised || !altsHash.containsKey(name)) {
			return new String[0];
		}
		List<String> altNames = altsHash.get(name);
		List<String> alts = new ArrayList<String>(altNames.size() - 1);
		for (String altName : altNames) {
			if (!altName.equalsIgnoreCase(name)) {
				alts.add(altName);
			}
		}
		return alts.toArray(new String[alts.size()]);
	}

	public Set<String> getAllNames() {
		return altsHash.keySet();
	}
}
