package com.floyd.petition;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.milkbowl.vault.permission.Permission;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class PetitionPlugin extends JavaPlugin {
  private final PetitionPlayerListener Listener = new PetitionPlayerListener(this);
  protected Notifier notifier = new Notifier(this);
  public static Permission permission = null;
  private final ConcurrentHashMap<Player, Boolean> debugees = new ConcurrentHashMap<Player, Boolean>();
  private final ConcurrentHashMap<Integer, String> semaphores = new ConcurrentHashMap<Integer, String>();
  public final ConcurrentHashMap<String, String> settings = new ConcurrentHashMap<String, String>();
  public String cache;
  String baseDir = "plugins/PetitionPlugin";
  String archiveDir = "archive";
  String mailDir = "mail";
  String ticketFile = "last_ticket_id.txt";
  String configFile = "settings.txt";
  String logFile = "petitionlog.txt";
  String fname = this.baseDir + "/" + this.logFile;
  String newline = System.getProperty("line.separator");

  protected Long interval = Long.valueOf(300000L);

  public static final Logger logger = Logger.getLogger("Minecraft.PetitionPlugin");

  public void onDisable() {
    PluginDescriptionFile pdfFile = getDescription();
    logger.info(pdfFile.getName() + " version " + pdfFile.getVersion() + " is disabled!");
  }

  public void onEnable() {
    setupPermissions();
    preFlightCheck();
    loadSettings();
    startNotifier();
    setupLog();

    PluginDescriptionFile pdfFile = getDescription();
    logger.info(pdfFile.getName() + " version " + pdfFile.getVersion() + " is enabled!");
  }

  public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
    String cmdname = cmd.getName().toLowerCase();
    Player player = null;
    if ((sender instanceof Player)) {
      player = (Player)sender;
    }

    if ((cmdname.equalsIgnoreCase("pe")) || (cmdname.equalsIgnoreCase("petition")) || (cmdname.equalsIgnoreCase("helpop"))) {
      if ((player == null) || (player.hasPermission("petition.pe"))) {
        if (args.length == 0) {
          performHelp(player);
          return true;
        }
        if (args.length >= 1) {
          if (args[0].equalsIgnoreCase("list")) {
            performList(player, args);
            return true;
          }
        }
        if (args.length >= 2) {
          if (args[0].equalsIgnoreCase("view")) {
            performView(player, args);
            return true;
          }

          if (args[0].equalsIgnoreCase("assign")) {
            performAssign(player, args);
            return true;
          }

          if (args[0].equalsIgnoreCase("unassign")) {
            performAssign(player, args);
            return true;
          }

          if (args[0].equalsIgnoreCase("close")) {
            performClose(player, args);
            return true;
          }

          if (args[0].equalsIgnoreCase("reopen")) {
            performReopen(player, args);
            return true;
          }

          if ((args[0].equalsIgnoreCase("comment")) || (args[0].equalsIgnoreCase("log"))) {
            performComment(player, args);
            return true;
          }

          if ((args[0].equalsIgnoreCase("open")) || (args[0].equalsIgnoreCase("new")) || (args[0].equalsIgnoreCase("create"))) {
            performOpen(player, args);
            return true;
          }

          if ((args[0].equalsIgnoreCase("warp")) || (args[0].equalsIgnoreCase("goto"))) {
            performWarp(player, args);
            return true;
          }
        }
      } else {
        logger.info("[Pe] Access denied for " + player.getName());
      }
    }
    return false;
  }

  private void performWarp(Player player, String[] args) {
    Integer id = Integer.valueOf(args[1]);
    Boolean moderator = Boolean.valueOf(false);
    String name = "(Console)";
    if (player == null) {
      respond(player, "[Pe] That would be a neat trick.");
      return;
    }
    name = player.getName();

    if (player.hasPermission("petition.moderate"))
      moderator = Boolean.valueOf(true);
    try {
      getLock(id, player);
      PetitionObject petition = new PetitionObject(id);
      if ((petition.isValid()) && ((petition.isOpen().booleanValue()) || (moderator.booleanValue()))) {
        if (canWarpTo(player, petition).booleanValue()) {
          respond(player, "[Pe] §7" + petition.Header(getServer()));
          if (player.teleport(petition.getLocation(getServer()))) {
            respond(player, "[Pe] §7Teleporting you to where the " + ((String)this.settings.get("single")).toLowerCase() + " was opened");
            logger.info(name + " teleported to " + ((String)this.settings.get("single")).toLowerCase() + id);
          } else {
            respond(player, "[Pe] §7Teleport failed");
            logger.info(name + " teleport to " + ((String)this.settings.get("single")).toLowerCase() + id + " FAILED");
          }
        } else {
          logger.info("[Pe] Access to warp to #" + id + " denied for " + name);
          respond(player, "§4[Pe] Access denied.");
        }
      } else {
        respond(player, "§4[Pe] No open " + ((String)this.settings.get("single")).toLowerCase() + " #" + args[1] + " found.");
      }
    } 
    finally {
      releaseLock(id, player);
    }
  }

  private void performOpen(Player player, String[] args) {
    Integer id = IssueUniqueTicketID();
    String name = "(Console)";
    if (player != null) {
      name = player.getName();
    } 
    try {
      getLock(id, player);
      String title = "";
      Integer index = Integer.valueOf(1);
      while (index.intValue() < args.length) {
        title = title.concat(" " + args[index.intValue()]);
        index = Integer.valueOf(index.intValue() + 1);
      }
      if (title.length() > 0) {
        title = title.substring(1);
      }
      PetitionObject petition = new PetitionObject(id, player, title);
      releaseLock(id, player);
      if (petition.isValid()) {
        respond(player, "[Pe] §7Thank you, your ticket is §6#" + petition.ID() + "§7. (Use '/petition' to manage it)");
        String[] except = { petition.Owner() };
        notifyModerators("[Pe] §7" + (String)this.settings.get("single") + " §6#" + petition.ID() + "§7 opened by " + name + ": " + title, except);
        logger.info(name + " opened " + ((String)this.settings.get("single")).toLowerCase() + " #" + id + ". " + title);
        logAction(name + " opened " + ((String)this.settings.get("single")).toLowerCase() + " #" + id + ". " + title);
      } else {
        respond(player, "§4[Pe] There was an error creating your ticket, please try again later.");
        System.out.println("[Pe] ERROR: PetitionPlugin failed to create a ticket, please check that plugins/PetitionPlugin exists and is writeable!");
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void performComment(Player player, String[] args) {
    Integer id = Integer.valueOf(args[1]);
    Boolean moderator = Boolean.valueOf(false);
    if ((player == null) || (player.hasPermission("petition.moderate"))) {
      moderator = Boolean.valueOf(true);
    }
    String name = "(Console)";
    if (player != null) {
      name = player.getName();
    }
    try {
      getLock(id, player);
      PetitionObject petition = new PetitionObject(id);
      if ((petition.isValid()) && (petition.isOpen().booleanValue())) {
        if ((petition.ownedBy(player).booleanValue()) || (moderator.booleanValue())) {
          String message = "";
          Integer index = Integer.valueOf(2);
          while (index.intValue() < args.length) {
            message = message.concat(" " + args[index.intValue()]);
            index = Integer.valueOf(index.intValue() + 1);
          }
          if (message.length() > 0) {
            message = message.substring(1);
          }

          notifyNamedPlayer(petition.Owner(), "[Pe] §7Your " + ((String)this.settings.get("single")).toLowerCase() + " §6#" + id + "§7 was updated: " + message);
          notifyNamedPlayer(petition.Assignee(), "[Pe] §7" + (String)this.settings.get("single") + " §6#" + id + "§7 has been updated by " + name + ".");
          String[] except = { petition.Owner(), petition.Assignee() };
          notifyModerators("[Pe] §7" + (String)this.settings.get("single") + " §6#" + id + "§7 comment added by " + name + ".", except);
          petition.Comment(player, message);
          logger.info(name + " commented " + ((String)this.settings.get("single")).toLowerCase() + " #" + id + ". " + message);
          logAction(name + " commented " + ((String)this.settings.get("single")).toLowerCase() + " #" + id + ". " + message);
        } else {
          logger.info("[Pe] Access to comment on #" + id + " denied for " + name);
        }
      } else {
        respond(player, "§4[Pe] No open " + ((String)this.settings.get("single")).toLowerCase() + " #" + args[1] + " found.");
      }
    }
    finally {
      releaseLock(id, player);
    }
  }

  private void performClose(Player player, String[] args) {
    Integer id = Integer.valueOf(args[1]);
    Boolean moderator = Boolean.valueOf(false);
    if ((player == null) || (player.hasPermission("petition.moderate"))) {
      moderator = Boolean.valueOf(true);
    }
    String name = "(Console)";
    if (player != null) {
      name = player.getName();
    }
    try {
      getLock(id, player);
      PetitionObject petition = new PetitionObject(id);
      if ((petition.isValid()) && (petition.isOpen().booleanValue())) {
        if ((petition.ownedBy(player).booleanValue()) || (moderator.booleanValue())) {
          String message = "";
          Integer index = Integer.valueOf(2);
          while (index.intValue() < args.length) {
            message = message.concat(" " + args[index.intValue()]);
            index = Integer.valueOf(index.intValue() + 1);
          }
          if (message.length() > 0) {
            message = message.substring(1);
          }

          notifyNamedPlayer(petition.Owner(), "[Pe] §7Your " + ((String)this.settings.get("single")).toLowerCase() + " §6#" + id + "§7 was closed. " + message);
          notifyNamedPlayer(petition.Assignee(), "[Pe] §7" + (String)this.settings.get("single") + " §6#" + id + "§7 was closed by " + name + ".");
          String[] except = { petition.Owner(), petition.Assignee() };

          if (Boolean.parseBoolean((String)this.settings.get("single"))) {
            notifyAll("[Pe] §7" + (String)this.settings.get("single") + " §6#" + id + "§7 was closed.", except);
          } else {
            notifyModerators("[Pe] §7" + (String)this.settings.get("single") + " §6#" + id + "§7 was closed. " + message, except);
          }
          petition.Close(player, message);
          logger.info(name + " closed " + ((String)this.settings.get("single")).toLowerCase() + " #" + id + ". " + message);
          logAction(name + " closed " + ((String)this.settings.get("single")).toLowerCase() + " #" + id + ". " + message);
        } else {
          logger.info("[Pe] Access to close #" + id + " denied for " + name);
        }
      } else {
        respond(player, "§4[Pe] No open " + ((String)this.settings.get("single")).toLowerCase() + " #" + args[1] + " found.");
      }
    }
    finally {
      releaseLock(id, player);
    }
  }

  private void performReopen(Player player, String[] args) {
    Integer id = Integer.valueOf(args[1]);
    Boolean moderator = Boolean.valueOf(false);
    if ((player == null) || (player.hasPermission("petition.moderate"))) {
      moderator = Boolean.valueOf(true);
    }
    String name = "(Console)";
    if (player != null) {
      name = player.getName();
     }
    try {
      getLock(id, player);
      PetitionObject petition = new PetitionObject(id);
      if ((petition.isValid()) && (petition.isClosed().booleanValue())) {
        if (moderator.booleanValue()) {
          String message = "";
          Integer index = Integer.valueOf(2);
          while (index.intValue() < args.length) {
            message = message.concat(" " + args[index.intValue()]);
            index = Integer.valueOf(index.intValue() + 1);
          }
          if (message.length() > 0) {
            message = message.substring(1);
          }

          notifyNamedPlayer(petition.Owner(), "[Pe] §7Your " + ((String)this.settings.get("single")).toLowerCase() + " §6#" + id + "§7 was reopened. " + message);
          notifyNamedPlayer(petition.Assignee(), "[Pe] §7" + (String)this.settings.get("single") + " §6#" + id + "§7 was reopened by " + name + ".");
          String[] except = { petition.Owner(), petition.Assignee() };
          notifyModerators("[Pe] §7" + (String)this.settings.get("single") + " §6#" + id + "§7 was reopened. " + message, except);
          petition.Reopen(player, message);
          logger.info(name + " reopened " + ((String)this.settings.get("single")).toLowerCase() + " #" + id + ". " + message);
          logAction(name + " reopened " + ((String)this.settings.get("single")).toLowerCase() + " #" + id + ". " + message);
        } else {
          logger.info("[Pe] Access to reopen #" + id + " denied for " + name);
        }
      }
      else respond(player, "§4[Pe] No closed " + ((String)this.settings.get("single")).toLowerCase() + " #" + args[1] + " found.");
    }
    finally {
      releaseLock(id, player);
    }
  }

  private void performAssign(Player player, String[] args) {
    Integer id = Integer.valueOf(args[1]);
    Boolean moderator = Boolean.valueOf(false);
    if ((player == null) || (player.hasPermission("petition.moderate"))) {
      moderator = Boolean.valueOf(true);
    }
    String name = "(Console)";
    if (player != null) {
      name = player.getName();
    }
    if (!moderator.booleanValue()) {
      logger.info("[Pe] Access to assign #" + id + " denied for " + name);
      respond(player, "§4[Pe] Only moderators may assign " + (String)this.settings.get("plural"));
      return;
    }
    try {
      getLock(id, player);
      PetitionObject petition = new PetitionObject(id);
      if ((petition.isValid()) && (petition.isOpen().booleanValue())) {
        if (args.length == 3) {
          petition.Assign(player, args[2]);
        } else {
          petition.Assign(player, name);
        }

        if (Boolean.parseBoolean((String)this.settings.get("notify-owner-on-assign"))) {
          notifyNamedPlayer(petition.Owner(), "[Pe] §7Your " + ((String)this.settings.get("single")).toLowerCase() + " §6#" + id + "§7 assigned to " + petition.Assignee() + ".");
        }
        notifyNamedPlayer(petition.Assignee(), "[Pe] §7" + (String)this.settings.get("single") + " §6#" + id + "§7 has been assigned to you by " + name + ".");
        String[] except = { petition.Owner(), petition.Assignee() };
        notifyModerators("[Pe] §7" + (String)this.settings.get("single") + " §6#" + id + "§7 has been assigned to " + petition.Assignee() + ".", except);
        logger.info(name + " assigned " + ((String)this.settings.get("single")).toLowerCase() + " #" + id + " to " + petition.Assignee());
        logAction(name + " assigned " + ((String)this.settings.get("single")).toLowerCase() + " #" + id + " to " + petition.Assignee());
      } else {
        respond(player, "§4[Pe] No open " + ((String)this.settings.get("single")).toLowerCase() + " #" + args[1] + " found.");
      }
    }
    finally {
      releaseLock(id, player);
    }
  }

  private void performView(Player player, String[] args) {
    Boolean moderator = Boolean.valueOf(false);
    if ((player == null) || (player.hasPermission("petition.moderate"))) {
      moderator = Boolean.valueOf(true);
    }
    String name = "(Console)";
    if (player != null) {
      name = player.getName();
    }
    Integer id = Integer.valueOf(args[1]);
    try {
      getLock(id, player);
      PetitionObject petition = new PetitionObject(id);
      if (petition.isValid()) {
        if ((petition.ownedBy(player).booleanValue()) || (moderator.booleanValue())) {
          respond(player, "[Pe] §7" + petition.Header(getServer()));
          for (String line : petition.Log())
            respond(player, "[Pe] §6#" + petition.ID() + " §7" + line);
        } else {
          logger.info("[Pe] Access to view #" + id + " denied for " + name);
        }
      }
      else respond(player, "§4[Pe] No open " + ((String)this.settings.get("single")).toLowerCase() + " #" + args[1] + " found.");
    }
    finally {
      releaseLock(id, player);
    }
  }

  private <T> void performList(Player player, String[] args) {
    Integer count = Integer.valueOf(0);
    Integer showing = Integer.valueOf(0);
    Integer limit = Integer.valueOf(10);
    Boolean include_offline = Boolean.valueOf(true);
    Boolean include_online = Boolean.valueOf(true);
    Boolean use_archive = Boolean.valueOf(false);
    Boolean sort_reverse = Boolean.valueOf(false);
    Boolean ignore_assigned = Boolean.valueOf(false);
    Boolean special = Boolean.valueOf(false);
    Pattern pattern = null;
    String filter = "";
    if (args.length >= 2) {
      for (Integer index = Integer.valueOf(1); index.intValue() < args.length; index = Integer.valueOf(index.intValue() + 1))
        if (args[index.intValue()].equalsIgnoreCase("closed")) {
          use_archive = Boolean.valueOf(true);
        } else if (args[index.intValue()].equalsIgnoreCase("newest")) {
          sort_reverse = Boolean.valueOf(true);
        } else if (args[index.intValue()].equalsIgnoreCase("unassigned")) {
          ignore_assigned = Boolean.valueOf(true);
        } else if (args[index.intValue()].equalsIgnoreCase("online")) {
          include_offline = Boolean.valueOf(false);
        } else if (args[index.intValue()].equalsIgnoreCase("offline")) {
          include_online = Boolean.valueOf(false);
        } else if (args[index.intValue()].equalsIgnoreCase("d")) {
          special = Boolean.valueOf(true);
        } else if (args[index.intValue()].matches("^\\d+$")) {
          limit = Integer.valueOf(args[index.intValue()]);
        } else {
          filter = args[index.intValue()];
           pattern = Pattern.compile(filter, 2);
        }
    }
    Boolean moderator = Boolean.valueOf(false);
    if ((player == null) || (player.hasPermission("petition.moderate"))) {
      moderator = Boolean.valueOf(true);
    }
    File dir;
    if (use_archive.booleanValue()) {
      dir = new File(this.baseDir + "/" + this.archiveDir);
    } else {
      dir = new File(this.baseDir);
    }
    String[] filenames = dir.list();

    Comparator numerical = new Comparator() {
      public int compare(Object o1, Object o2) {
        return 0;
      }
    };
    extracted(filenames, numerical);
    if (sort_reverse.booleanValue()) {
      filenames = reverseOrder(filenames);
    }

    if (filenames != null) {
      for (String filename : filenames) {
        if (filename.endsWith(".ticket")) {
          if (special.booleanValue() == true) {
            String[] parts = filename.split("['.']");
            Integer id = Integer.valueOf(parts[0]);
            try {
              getLock(id, player);
              PetitionObject petition = new PetitionObject(id);
              if ((petition.isValid()) && ((petition.ownedBy(player).booleanValue()) || (moderator.booleanValue()))) {
                Boolean ignore = Boolean.valueOf(false);
                String owner = petition.Owner();
                Player p = getServer().getPlayer(owner);
                String world = petition.world;
                if ((p == null) && (!include_offline.booleanValue())) {
                  ignore = Boolean.valueOf(true);
                }
                if ((p != null) && (!include_online.booleanValue())) {
                  ignore = Boolean.valueOf(true);
                }
                if (pattern != null) {
                  Matcher matcher = pattern.matcher(petition.Header(getServer()));
                  if (!matcher.find()) {
                    ignore = Boolean.valueOf(true);
                  }
                }
                if ((!petition.Assignee().matches("\\*")) && (ignore_assigned.booleanValue())) {
                  ignore = Boolean.valueOf(true);
                }
                if (!ignore.booleanValue()) {
                  if ((count.intValue() < limit.intValue()) && permission.has(world, owner, "petition.special")) {
                    respond(player, "[SpecialPe] " + petition.Header(getServer()));
                    showing = Integer.valueOf(showing.intValue() + 1);
                  }
                  count = Integer.valueOf(count.intValue() + 1);
                }
              }
            }
            finally {
              releaseLock(id, player);
            }
          } else {
            String[] parts = filename.split("['.']");
            Integer id = Integer.valueOf(parts[0]);
            try {
              getLock(id, player);
              PetitionObject petition = new PetitionObject(id);
              if ((petition.isValid()) && ((petition.ownedBy(player).booleanValue()) || (moderator.booleanValue()))) {
                Boolean ignore = Boolean.valueOf(false);
                Player p = getServer().getPlayer(petition.Owner());
                if ((p == null) && (!include_offline.booleanValue())) {
                  ignore = Boolean.valueOf(true);
                }
                if ((p != null) && (!include_online.booleanValue())) {
                  ignore = Boolean.valueOf(true);
                }
                if (pattern != null) {
                  Matcher matcher = pattern.matcher(petition.Header(getServer()));
                  if (!matcher.find()) {
                    ignore = Boolean.valueOf(true);
                  }
                }
                if ((!petition.Assignee().matches("\\*")) && (ignore_assigned.booleanValue())) {
                  ignore = Boolean.valueOf(true);
                }
                if (!ignore.booleanValue()) {
                  if (count.intValue() < limit.intValue()) {
                    respond(player, "[Pe] " + petition.Header(getServer()));
                    showing = Integer.valueOf(showing.intValue() + 1);
                  }
                  count = Integer.valueOf(count.intValue() + 1);
                }
              }
            }
            finally {
              releaseLock(id, player);
            }
          }
        }
      }
    }
    respond(player, "[Pe] §7" + (use_archive.booleanValue() ? "Closed" : "Open") + " " + ((String)this.settings.get("plural")).toLowerCase() + (pattern == null ? "" : new StringBuilder(" matching ").append(filter).toString()) + ": " + count + " (Showing " + showing + ")");
  }

  private void extracted(String[] filenames, Comparator<String> numerical) {
    Arrays.sort(filenames, numerical);
  }

  private void performHelp(Player player) {
    String cmd = "pe";
    Boolean moderator = Boolean.valueOf(false);
    if ((player == null) || (player.hasPermission("petition.moderate"))) {
      moderator = Boolean.valueOf(true);
    }

    respond(player, "[Pe] §7" + (String)this.settings.get("single") + " usage:");
    respond(player, "[Pe] §7/" + cmd + " open|create|new <Message>");
    respond(player, "[Pe] §7/" + cmd + " comment|log <#> <Message>");
    respond(player, "[Pe] §7/" + cmd + " close <#> [<Message>]");
    respond(player, "[Pe] §7/" + cmd + " list [online|offline|newest|closed|unassigned] [<count>|<pattern>]");
    respond(player, "[Pe] §7/" + cmd + " view <#>");
    if (canWarpAtAll(player).booleanValue()) {
      respond(player, "[Pe] §7/" + cmd + " warp|goto <#>");
    }
    if (moderator.booleanValue()) {
      respond(player, "[Pe] §7/" + cmd + " assign <#> [<Operator>]");
      respond(player, "[Pe] §7/" + cmd + " unassign <#>");
      respond(player, "[Pe] §7/" + cmd + " reopen <#> [<Message>]");
    }
  }

  public boolean isDebugging(Player player) {
    if (this.debugees.containsKey(player)) {
      return ((Boolean)this.debugees.get(player)).booleanValue();
    }
    return false;
  }

  public void setDebugging(Player player, boolean value) {
    this.debugees.put(player, Boolean.valueOf(value));
  }

  public synchronized boolean SetPetitionLock(Integer id, String owner, Boolean release) {
    if (!release.booleanValue()) {
      if ((this.semaphores.containsKey(id)) && (((String)this.semaphores.get(id)).equals(owner))) {
        logger.severe("[Pe] INTERNAL ERROR! Petition #" + id + " is ALREADY locked by " + (String)this.semaphores.get(id));
        logger.severe("[Pe] This was probably caused by a previous crash while accessing this petition.");
        logger.severe("[Pe] Please report this issue to the plugin author.");
        return true;
      }

      if (this.semaphores.containsKey(id)) {
        logger.warning("[Pe] Denied " + owner + " lock on #" + id + "; currently locked by " + (String)this.semaphores.get(id));
      } else {
        this.semaphores.put(id, owner);
        return true;
      }

    } else if ((this.semaphores.containsKey(id)) && (this.semaphores.get(id) == owner)) {
      this.semaphores.remove(id);
      return true;
    }

    return false;
  }

  public synchronized Integer IssueUniqueTicketID() {
    String fname = this.baseDir + "/" + this.ticketFile;
    String line = null;
    try {
      BufferedReader input = new BufferedReader(new FileReader(fname));
      if ((line = input.readLine()) != null) {
        line = line.trim();
      }
      input.close();
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    if (line == null) {
      line = "0";
    }

    line = String.valueOf(Integer.parseInt(line) + 1);

    String newline = System.getProperty("line.separator");
    try {
      BufferedWriter output = new BufferedWriter(new FileWriter(fname));
      output.write(line + newline);
      output.close();
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    logger.fine("[Pe] Issued ticket #" + line);
    return Integer.valueOf(line);
  }

  public void setupLog() {
    String fname = this.baseDir + "/" + this.logFile;
    try {
      File f = new File(fname);
      if (!f.exists())
        f.createNewFile();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void logAction(String line) {
    Date now = new Date();
    SimpleDateFormat format = new SimpleDateFormat("MMMM d, yyyy, H:mm");
    String out = "[" + format.format(now) + "] " + line + this.newline;
    try {
      if (this.cache == null) {
        this.cache = readLog();
      }

      this.cache = (out + this.cache);

      BufferedWriter output = new BufferedWriter(new FileWriter(this.fname));
      output.write(this.cache);
      output.flush();
      output.close();
    } catch (IOException ioe) {
      logger.severe("[Pe] Error writing to the log file!");
    }
    logger.fine("[Pe] Logged action of #" + line);
  }

  public String readLog() throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(this.fname)));
    String read = null;
    StringBuilder out = new StringBuilder();

    while ((read = reader.readLine()) != null) {
      out.append(read).append(this.newline);
    }
    reader.close();
    return out.toString();
  }

  private void loadSettings() {
    String fname = this.baseDir + "/" + this.configFile;
    String line = null;

    this.settings.put("single", "Petition");
    this.settings.put("plural", "Petitions");

    this.settings.put("notify-all-on-close", "false");
    this.settings.put("notify-owner-on-assign", "false");
    this.settings.put("notify-owner-on-unassign", "false");

    this.settings.put("notify-interval-seconds", "300");

    this.settings.put("warp-requires-permission", "false");
    try {
      BufferedReader input = new BufferedReader(new FileReader(fname));
      while ((line = input.readLine()) != null) {
        line = line.trim();
        if ((!line.startsWith("#")) && (line.contains("="))) {
          String[] pair = line.split("=", 2);
          this.settings.put(pair[0], pair[1]);
          if ((pair[0].equals("command")) || (pair[0].equals("commandalias")))
            logger.warning("[Pe] Warning: The '" + pair[0] + "' setting has been deprecated and no longer has any effect");
        }
      }
      input.close();
    }
    catch (FileNotFoundException e) {
      logger.warning("[Pe] Error reading " + e.getLocalizedMessage() + ", using defaults");
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void preFlightCheck() {
    String fname = "";

    fname = this.baseDir;
    File f = new File(fname);
    if ((!f.exists()) && (f.mkdir())) {
      logger.info("[Pe] Created directory '" + fname + "'");
    }

    fname = this.baseDir + "/" + this.archiveDir;
    f = new File(fname);
    if ((!f.exists()) && (f.mkdir())) {
      logger.info("[Pe] Created directory '" + fname + "'");
    }

    fname = this.baseDir + "/" + this.mailDir;
    f = new File(fname);
    if ((!f.exists()) && (f.mkdir())) {
      logger.info("[Pe] Created directory '" + fname + "'");
    }

    fname = this.baseDir + "/" + this.configFile;
    f = new File(fname);
    if (!f.exists()) {
      String newline = System.getProperty("line.separator");
      try {
        BufferedWriter output = new BufferedWriter(new FileWriter(fname));
        output.write("single=Petition" + newline);
        output.write("plural=Petitions" + newline);
        output.write("notify-all-on-close=false" + newline);
        output.write("notify-owner-on-assign=true" + newline);
        output.write("notify-owner-on-unassign=true" + newline);
        output.write("notify-interval-seconds=300" + newline);
        output.write("warp-requires-permission=false" + newline);
        output.close();
        logger.info("[Pe] Created config file '" + fname + "'");
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    fname = this.baseDir + "/" + this.ticketFile;
    f = new File(fname);
    if (!f.exists()) {
      String newline = System.getProperty("line.separator");
      try {
        BufferedWriter output = new BufferedWriter(new FileWriter(fname));
        output.write("0" + newline);
        output.close();
        logger.info("[Pe] Created ticket file '" + fname + "'");
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    fname = this.baseDir + "/" + this.logFile;
    f = new File(fname);
    if (!f.exists()) {
      try {
        BufferedWriter output = new BufferedWriter(new FileWriter(fname));
        output.write("");
        output.close();
        logger.info("[Pe] Created log file '" + fname + "'");
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private void getLock(Integer id, Player player) {
    String name = "";
    if (player != null) {
      name = player.getName();
    }
    while (!SetPetitionLock(id, name, Boolean.valueOf(false)))
      try {
        Thread.sleep(100L);
      }
    catch (InterruptedException e) {
      logger.warning("[Pe] Sleep interrupted while waiting for lock");
    }
  }

  private void releaseLock(Integer id, Player player) {
    String name = "";
    if (player != null) {
      name = player.getName();
    }
    SetPetitionLock(id, name, Boolean.valueOf(true));
  }

  private void notifyNamedPlayer(String name, String message) {
    if ((name.equals("")) || (name.equals("*")) || (name.equalsIgnoreCase("(Console)"))) {
      return;
    }
    Player[] players = getServer().getOnlinePlayers();
    Boolean online = Boolean.valueOf(false);
    for (Player player : players) {
      if (player.getName().equalsIgnoreCase(name)) {
        player.sendMessage(message);
        online = Boolean.valueOf(true);
      }
    }
    if (!online.booleanValue()) {
      name = name.toLowerCase();

      String fname = this.baseDir + "/" + this.mailDir + "/" + name;
      File f = new File(fname);
      if ((!f.exists()) && (f.mkdir())) {
        logger.info("[Pe] Created directory '" + fname + "'");
      }

      fname = this.baseDir + "/" + this.mailDir + "/" + name + "/tmp";
      f = new File(fname);
      if ((!f.exists()) && (f.mkdir())) {
        logger.info("[Pe] Created directory '" + fname + "'");
      }

      fname = this.baseDir + "/" + this.mailDir + "/" + name + "/inbox";
      f = new File(fname);
      if ((!f.exists()) && (f.mkdir())) {
        logger.info("[Pe] Created directory '" + fname + "'");
      }

      UUID uuid = UUID.randomUUID();
      fname = this.baseDir + "/" + this.mailDir + "/" + name + "/tmp/" + uuid;
      String fname_final = this.baseDir + "/" + this.mailDir + "/" + name + "/inbox/" + uuid;

      String newline = System.getProperty("line.separator");
      try {
        BufferedWriter output = new BufferedWriter(new FileWriter(fname));
        output.write(message + newline);
        output.close();

        f = new File(fname);
        f.renameTo(new File(fname_final));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public void notifyModerators(String message, String[] exceptlist) {
    Player[] players = getServer().getOnlinePlayers();
    for (Player player : players) {
      extracted();
      if (player.hasPermission("petition.moderate")) {
        Boolean skip = Boolean.valueOf(false);
        for (String except : exceptlist) {
          if (player.getName().toLowerCase().equals(except.toLowerCase())) {
            skip = Boolean.valueOf(true);
          }
        }
        if (!skip.booleanValue())
          player.sendMessage(message);
      }
    }
  }

  public void notifyAll(String message, String[] exceptlist) {
    Player[] players = getServer().getOnlinePlayers();
    for (Player player : players) {
      Boolean skip = Boolean.valueOf(false);
      for (String except : exceptlist) {
        if (player.getName().toLowerCase().equals(except.toLowerCase())) {
          skip = Boolean.valueOf(true);
        }
      }
      if (!skip.booleanValue())
        player.sendMessage(message);
    }
  }

  public String[] getMessages(Player player) {
    String[] messages = new String[0];
    String name = player.getName().toLowerCase();
    String pname = this.baseDir + "/" + this.mailDir + "/" + name + "/inbox";
    File dir = new File(pname);
    String[] filenames = dir.list();
    if (filenames != null) {
      messages = new String[filenames.length];
      Integer index = Integer.valueOf(0);
      for (String fname : filenames) {
        try {
          BufferedReader input = new BufferedReader(new FileReader(pname + "/" + fname));
          messages[index.intValue()] = input.readLine();
          input.close();
          boolean success = new File(pname + "/" + fname).delete();
          if (!success)
            logger.warning("[Pe] Could not delete " + pname + "/" + fname);
        }
        catch (FileNotFoundException e) {
          logger.warning("[Pe] Unexpected error reading " + e.getLocalizedMessage());
        }
        catch (Exception e) {
          e.printStackTrace();
        }
        index = Integer.valueOf(index.intValue() + 1);
      }
    }
    return messages;
  }

  private void respond(Player player, String message) {
    if (player == null) {
      Pattern pattern = Pattern.compile("\\§[0-9a-f]");
      Matcher matcher = pattern.matcher(message);
      message = matcher.replaceAll("");

      System.out.println(message);
    } else {
      player.sendMessage(message);
    }
  }

  private Boolean canWarpAtAll(Player player) {
    if (!Boolean.parseBoolean((String)this.settings.get("warp-requires-permission"))) {
      return Boolean.valueOf(true);
    }

    if (player == null) {
      return Boolean.valueOf(true);
    }
    extracted();

    if (player.hasPermission("petition.moderator")) {
      return Boolean.valueOf(true);
    }
    extracted();
    if (player.hasPermission("petition.warp-to-own-if-assigned")) {
      return Boolean.valueOf(true);
    }
    extracted();
    if (player.hasPermission("petition.warp-to-own")) {
      return Boolean.valueOf(true);
    }
    return Boolean.valueOf(false);
  }

  private Boolean canWarpTo(Player player, PetitionObject petition) {
    if (player == null) {
      return Boolean.valueOf(true);
    }
    extracted();

    if (player.hasPermission("petition.moderator")) {
      return Boolean.valueOf(true);
    }

    if (!petition.ownedBy(player).booleanValue()) {
      return Boolean.valueOf(false);
    }

    if (!Boolean.parseBoolean((String)this.settings.get("warp-requires-permission"))) {
      return Boolean.valueOf(true);
    }
    extracted();

    if (player.hasPermission("petition.warp-to-own")) {
      return Boolean.valueOf(true);
    }

    if (petition.Assignee().equals("*")) {
      return Boolean.valueOf(false);
    }
    extracted();

    if (player.hasPermission("petition.warp-to-own-assigned")) {
      return Boolean.valueOf(true);
    }
    String[] except = { petition.Owner() };
    notifyModerators("[Pe] " + player.getName() + " requested warp access to " + ((String)this.settings.get("single")).toLowerCase() + " #" + petition.ID(), except);
    return Boolean.valueOf(false);
  }

  private void extracted() {}

  private String[] reverseOrder(String[] list) {
    String[] newlist = new String[list.length];
    Integer i = Integer.valueOf(list.length - 1);
    for (String item : list) {
      newlist[i.intValue()] = item;
      i = Integer.valueOf(i.intValue() - 1);
    }
    return newlist;
  }

  private void startNotifier() {
    Integer seconds = Integer.valueOf(0);
    try {
      seconds = Integer.valueOf(Integer.parseInt((String)this.settings.get("notify-interval-seconds")));
    }
    catch (Exception e) {
      logger.warning("[Pe] Error parsing option 'notify-interval-seconds'; must be an integer.");
      logger.warning("[Pe] Using default value (300)");
    }
    if (seconds.intValue() > 0) {
      Long interval = setInterval(seconds);
      getServer().getScheduler().runTaskTimerAsynchronously(this, notifier, interval, interval);
    } else {
      logger.info("[Pe] Notification thread disabled");
    }
  }

  public PetitionPlayerListener getListener() {
    return this.Listener;
  }

  public Long setInterval(Integer sec) {
    Long ticks = Long.valueOf(sec.intValue() * 20L);
    if (ticks.longValue() < 6000L ) { ticks = Long.valueOf(6000L); }
    System.out.println("[Pe] Notifier interval set to " + sec + " seconds");
    return ticks;
  }

  protected boolean setupPermissions() {
      RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
      if (permissionProvider != null) {
          permission = permissionProvider.getProvider();
      }
      return (permission != null);
  }
}
