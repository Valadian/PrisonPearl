package com.untamedears.PrisonPearl;

import java.util.List;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

// This event is raised by PrisonPearl to request that alts lists for the
// specified players be returned via the AltsListEvent, if applicable alts
// lists exist.
public class RequestAltsListEvent extends Event {
  private static final HandlerList handlers = new HandlerList();

  private List<String> playersToCheck_;

  public HandlerList getHandlers() {
    return handlers;
  }

  public static HandlerList getHandlerList() {
    return handlers;
  }

  public RequestAltsListEvent(List<String> playersToCheck) {
    super();
    playersToCheck_ = playersToCheck;
  }

  public List<String> getPlayersToCheck() {
    return playersToCheck_;
  }
}
