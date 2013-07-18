package com.floyd.petition;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class PetitionObject {
  String path = "plugins/PetitionPlugin";
  String archive = "plugins/PetitionPlugin/archive";

  Integer id = Integer.valueOf(0);
  String owner = "";
  String title = "";
  String world = "";
  Double x = Double.valueOf(0.0D);
  Double y = Double.valueOf(0.0D);
  Double z = Double.valueOf(0.0D);
  Float pitch = Float.valueOf(0.0F);
  Float yaw = Float.valueOf(0.0F);
  String assignee = "*";
  ArrayList<String> log = new ArrayList<String>();
  Boolean closed = Boolean.valueOf(false);

  public PetitionObject(Integer newid, Player player, String newtitle) {
    if (player != null) {
      this.id = newid;
      this.owner = player.getName();
      this.title = newtitle;
      this.world = player.getLocation().getWorld().getName();
      this.x = Double.valueOf(player.getLocation().getX());
      this.y = Double.valueOf(player.getLocation().getY());
      this.z = Double.valueOf(player.getLocation().getZ());
      this.pitch = Float.valueOf(player.getLocation().getPitch());
      this.yaw = Float.valueOf(player.getLocation().getYaw());
      this.closed = Boolean.valueOf(false);
    } else {
      this.id = newid;
      this.owner = "(Console)";
      this.title = newtitle;
      this.world = "";
      this.x = Double.valueOf(64.0D);
      this.y = Double.valueOf(64.0D);
      this.z = Double.valueOf(64.0D);
      this.pitch = Float.valueOf(0.0F);
      this.yaw = Float.valueOf(0.0F);
      this.closed = Boolean.valueOf(false);
    }
    Save();
  }

  public PetitionObject(Integer getid) {
    String fname = this.archive + "/" + String.valueOf(getid) + ".ticket";
    this.closed = Boolean.valueOf(true);
    File f = new File(fname);

    if (!f.exists()) {
      fname = this.path + "/" + String.valueOf(getid) + ".ticket";
      this.closed = Boolean.valueOf(false);
    }
    try {
      BufferedReader input = new BufferedReader(new FileReader(fname));
      String line = null;
      while ((line = input.readLine()) != null) {
        String[] parts = line.split("=", 2);
        if (parts[0].equals("id")) this.id = Integer.valueOf(Integer.parseInt(parts[1]));
        if (parts[0].equals("owner")) this.owner = parts[1];
        if (parts[0].equals("title")) this.title = parts[1];
        if (parts[0].equals("world")) this.world = parts[1];
        if (parts[0].equals("x")) this.x = Double.valueOf(Double.parseDouble(parts[1]));
        if (parts[0].equals("y")) this.y = Double.valueOf(Double.parseDouble(parts[1]));
        if (parts[0].equals("z")) this.z = Double.valueOf(Double.parseDouble(parts[1]));
        if (parts[0].equals("pitch")) this.pitch = Float.valueOf(Float.parseFloat(parts[1]));
        if (parts[0].equals("yaw")) this.yaw = Float.valueOf(Float.parseFloat(parts[1]));
        if (parts[0].equals("assignee")) this.assignee = parts[1];
        if (parts[0].equals("log")) this.log.add(parts[1]);
      }
      input.close();
    }
    catch (FileNotFoundException e) {
      System.out.println("[Pe] Error reading " + e.getLocalizedMessage());
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void Save() {
    String fname = this.path + "/" + String.valueOf(this.id) + ".ticket";
    if (this.closed.booleanValue()) {
      fname = this.path + "/archive/" + String.valueOf(this.id) + ".ticket";
    }

    if (!isValid()) {
       return;
    }
    try {
      BufferedWriter output = new BufferedWriter(new FileWriter(fname));
      output.write("id=" + String.valueOf(this.id) + "\n");
      output.write("owner=" + this.owner + "\n");
      output.write("title=" + this.title + "\n");
      output.write("world=" + this.world + "\n");
      output.write("x=" + String.valueOf(this.x) + "\n");
      output.write("y=" + String.valueOf(this.y) + "\n");
      output.write("z=" + String.valueOf(this.z) + "\n");
      output.write("pitch=" + String.valueOf(this.pitch) + "\n");
      output.write("yaw=" + String.valueOf(this.yaw) + "\n");
      output.write("assignee=" + this.assignee + "\n");
      for (String entry : this.log) {
        output.write("log=" + entry + "\n");
      }
      output.close();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void Assign(Player player, String name) {
    String moderator = "(Console)";
    if (player != null) {
      moderator = player.getName();
    }
    this.log.add("Assigned to " + name + " by " + moderator);
    this.assignee = name;
    Save();
  }

  public void Unassign(Player player) {
    String moderator = "(Console)";
    if (player != null) {
      moderator = player.getName();
    }
    this.log.add("Unassigned by " + moderator);
    this.assignee = "*";
    Save();
  }

  public void Close(Player player, String message) {
    String moderator = "(Console)";
    if (player != null) {
      moderator = player.getName();
    }
    if (message.equals("")) {
      this.log.add("Closed by " + moderator);
    } else {
      this.log.add("Closed by " + moderator + ": " + message);
    }
    Save();
    File oldFile = new File(this.path + "/" + this.id + ".ticket");
    oldFile.renameTo(new File(this.archive + "/" + this.id + ".ticket"));
  }

  public void Reopen(Player player, String message) {
    String moderator = "(Console)";
    if (player != null) {
      moderator = player.getName();
    }
    if (message.equals("")) {
      this.log.add("Reopened by " + moderator);
    } else {
      this.log.add("Reopened by " + moderator + ": " + message);
    }
    Save();
    File oldFile = new File(this.archive + "/" + this.id + ".ticket");
    oldFile.renameTo(new File(this.path + "/" + this.id + ".ticket"));
  }

  public void Comment(Player player, String message) {
    String moderator = "(Console)";
    if (player != null) {
      moderator = player.getName();
    }
    if (message.equals("")) {
      return;
    }
    this.log.add(moderator + ": " + message);
    Save();
  }

  public boolean isValid() {
    return this.id.intValue() != 0;
  }

  public Boolean isOpen() {
    return Boolean.valueOf(!this.closed.booleanValue());
  }

  public Boolean isClosed() {
    return this.closed;
  }

  public String Owner() {
    return this.owner;
  }

  public String Owner(Server server) {
    if (server.getPlayer(this.owner) == null) {
      return "§4ø§f" + this.owner;
    }
    return "§2+§f" + this.owner;
  }

  public Boolean ownedBy(Player player) {
    if (player == null) {
      return Boolean.valueOf(false);
    }
    return Boolean.valueOf(Owner().equalsIgnoreCase(player.getName()));
  }

  public String Title() {
    return this.title;
  }

  public String Assignee() {
    return this.assignee;
  }

  public String Assignee(Server server) {
    if (server.getPlayer(this.assignee) == null) {
      return "§4ø§f" + this.assignee;
    }
    return "§2+§f" + this.assignee;
  }

  public String ID() {
    return String.valueOf(this.id);
  }

  public String Header(Server server) {
    return "§6#" + ID() + " " + Owner(server) + "§7 -> " + Assignee(server) + "§7: " + Title() + " (" + Log().length + ")";
  }

  public String[] Log() {
    String[] lines = new String[this.log.size()];
    this.log.toArray(lines);
    return lines;
  }

  public String World() {
    return this.world;
  }

  public Location getLocation(Server server) {
    List<World> worlds = server.getWorlds();
    World normal = null;
    System.out.println("Examining worlds"); //TODO This is a debug line, it doesn't need to be always on.
    for (World w : worlds) {
      if (w.getName().equals(this.world)) {
        return new Location(w, this.x.doubleValue(), this.y.doubleValue(), this.z.doubleValue(), this.yaw.floatValue(), this.pitch.floatValue());
      }
      if (w.getName().equals("world")) {
        normal = w;
      }
    }

    return new Location(normal, this.x.doubleValue(), this.y.doubleValue(), this.z.doubleValue(), this.yaw.floatValue(), this.pitch.floatValue());
  }
}