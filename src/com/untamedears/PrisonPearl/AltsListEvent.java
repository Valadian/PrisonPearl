package com.untamedears.PrisonPearl;

import java.util.List;
import java.util.UUID;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

// This event is for other plugins to notify PrisonPearl that an alts list has
// updated. PrisonPearl must reset all players in this alts list to point at
// this new list. Also any bans should be updated to reflect the new alt list
// state.
public class AltsListEvent extends Event {
  private static final HandlerList handlers = new HandlerList();

  private List<UUID> altsList_;

  public HandlerList getHandlers() {
    return handlers;
  }

  public static HandlerList getHandlerList() {
    return handlers;
  }

  public AltsListEvent(List<UUID> altsList) {
    super();
    altsList_ = altsList;
  }

  public List<UUID> getAltsList() {
    return altsList_;
  }

  public void setAltsList(List<UUID> altsList) {
    altsList_ = altsList;
  }
}
