package com.floyd.petition;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class Notifier extends BukkitRunnable {
  private ConcurrentHashMap<String, Integer> count = new ConcurrentHashMap<String, Integer>();
  private ConcurrentHashMap<String, Integer> scount = new ConcurrentHashMap<String, Integer>();
  private PetitionPlugin plugin = null;
  private String baseDir = "plugins/PetitionPlugin";
  private Player PlayerListener = null;
  public static final Logger logger = Logger.getLogger("Minecraft.PetitionPlugin");
  protected Integer found = Integer.valueOf(0);
  protected Integer sfound = Integer.valueOf(0);

  public Notifier(PetitionPlugin owner) {
    this.plugin = owner;
  }

  public void run() {
    Integer total = Integer.valueOf(0);
    Integer stotal = Integer.valueOf(0);

    this.count.clear();
    this.scount.clear();
    File dir = new File(baseDir);
    for (String filename : dir.list()) {
      if (filename.endsWith(".ticket")) {
        String[] parts = filename.split("['.']");
        Integer id = Integer.valueOf(parts[0]);
        PetitionObject petition = new PetitionObject(id);
        if (petition.isValid()) {
          String owner = petition.Owner();
          String world = petition.world;
          //normal alerts
          found = (Integer)count.get(petition.Owner());
          if (found == null) { found = Integer.valueOf(0); }
          this.count.put(petition.Owner(), Integer.valueOf(found.intValue() + 1));
          //special alerts
          sfound = (Integer)scount.get(petition.Owner());
          if (sfound == null) { sfound = Integer.valueOf(0); }
          if (PetitionPlugin.permission.has(world, owner, "petition.special")) {
            this.scount.put(petition.Owner(), Integer.valueOf(sfound.intValue() + 1));
            stotal = Integer.valueOf(stotal + 1);
          }
        }
      }
    }

    //normal alerts for members
    for (String name : this.count.keySet()) {
      Integer found = (Integer)this.count.get(name);
      total = Integer.valueOf(total.intValue() + found.intValue());
      Player p = this.plugin.getServer().getPlayer(name);
      if (p != null) {
        if (found.intValue() == 1) {
          p.sendMessage("[Pe] §7You have 1 open " + ((String)this.plugin.settings.get("single")).toLowerCase() + " waiting, use '/pe list' to review");
        } else {
          p.sendMessage("[Pe] §7You have " + found + " open " + ((String)this.plugin.settings.get("plural")).toLowerCase() + " waiting, use '/pe list' to review");
        }
      }
    }

    String[] except = new String[0];
    //normal alerts for moderators
    if (total.intValue() > 0) {
      if (total.intValue() == 1) {
        this.plugin.notifyModerators("[Pe] §7There is 1 open " + ((String)this.plugin.settings.get("single")).toLowerCase() + " waiting, use '/pe list' to review", except);
      } else {
        this.plugin.notifyModerators("[Pe] §7There are " + total + " open " + ((String)this.plugin.settings.get("plural")).toLowerCase() + " waiting, use '/pe list' to review", except);
      }
    }

    //special alerts for moderators
    if (stotal.intValue() > 0) {
      if (stotal.intValue() == 1) {
        this.plugin.notifyModerators(ChatColor.RED + "[SpecialPe] There is 1 open special petition! Type '/pe list d' to view it.", except);
      } else {
        this.plugin.notifyModerators(ChatColor.RED + "[SpecialPe] There are " + stotal + " open special petitions! Type /pe list d to view them.", except);
      }
    }
  }

  public Player getPlayerListener() {
    return this.PlayerListener;
  }

  public void setPlayerListener(Player playerListener) {
    this.PlayerListener = playerListener;
  }
}
