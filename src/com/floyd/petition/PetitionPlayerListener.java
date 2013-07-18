package com.floyd.petition;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PetitionPlayerListener implements Listener {
  private final PetitionPlugin plugin;

  public PetitionPlayerListener(PetitionPlugin instance) {
    this.plugin = instance;
  }

  public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();

    String[] messages = this.plugin.getMessages(player);
    if (messages.length > 0) {
      for (String message : messages) {
        player.sendMessage(message);
      }
      player.sendMessage("[Pe] §7Use /petition to view, comment or close");
    }
  }
}