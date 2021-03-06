package mc.alk.arena.controllers;

import java.util.Arrays;
import java.util.Set;

import mc.alk.arena.BattleArena;
import mc.alk.arena.Defaults;
import mc.alk.arena.controllers.containers.RoomContainer;
import mc.alk.arena.executors.BAExecutor;
import mc.alk.arena.objects.ArenaPlayer;
import mc.alk.arena.objects.MatchParams;
import mc.alk.arena.objects.arenas.Arena;
import mc.alk.arena.objects.arenas.ArenaType;
import mc.alk.arena.objects.options.TransitionOption;
import mc.alk.arena.objects.regions.PylamoRegion;
import mc.alk.arena.objects.regions.WorldGuardRegion;
import mc.alk.arena.serializers.PlayerContainerSerializer;
import mc.alk.arena.util.Log;
import mc.alk.arena.util.MessageUtil;
import mc.alk.arena.util.MinMax;
import mc.alk.arena.util.TeamUtil;
import mc.alk.arena.util.Util;
import mc.alk.arena.util.WorldEditUtil;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.bukkit.selections.Selection;

public class ArenaAlterController {
	public enum ChangeType{
		NLIVES(true,false),
		NTEAMS(true,false),
		TEAMSIZE(true,false),
		WAITROOM(true,false),
		SPECTATE(true,false),
		LOBBY(true,false),
		SPAWNLOC(true,false),
		VLOC(true,true),
		TYPE(true,false),
		GIVEITEMS(false,true),
		ADDREGION(false,true),
		ADDPYLAMOREGION(false,true);

		final boolean needsValue; /// whether the transition needs a value

		final boolean needsPlayer; /// whether we need a player

		ChangeType(Boolean hasValue, Boolean needsPlayer){
			this.needsValue = hasValue;
			this.needsPlayer = needsPlayer;
		}

		public boolean hasValue(){return needsValue;}

		public static ChangeType fromName(String str){
			str = str.toUpperCase();
			ChangeType ct = null;
			try {ct = ChangeType.valueOf(str);} catch (Exception e){}
			if (ct != null)
				return ct;
			if (str.equalsIgnoreCase("wr")) return WAITROOM;
			if (str.equalsIgnoreCase("s")) return SPECTATE;
			if (str.equalsIgnoreCase("l")) return LOBBY;
			if (str.equalsIgnoreCase("v")) return VLOC;
			try{
				if (Integer.valueOf(str) != null)
					return SPAWNLOC;
			} catch (Exception e){}
			if (str.equalsIgnoreCase("main"))
				return SPAWNLOC;
			if (TeamUtil.getTeamIndex(str) != null){
				return SPAWNLOC;}
			return null;
		}

		public static String getValidList() {
			StringBuilder sb = new StringBuilder();
			boolean first = true;
			for (ChangeType r: ChangeType.values()){
				if (!first) sb.append(", ");
				sb.append(r);
				first = false;
			}
			return sb.toString();
		}
	}

	public static boolean alterLobby(CommandSender sender, MatchParams params, String[] args) {
		if (args.length < 2){
			showAlterHelp(sender);
			return false;
		}
		BattleArenaController ac = BattleArena.getBAController();
		String[] otherOptions = args.length > 3 ? Arrays.copyOfRange(args, 3, args.length) : null;
		String changetype = args[1];
		String value = "1";
		if (args.length > 2)
			value = args[2];
		changeLobbySpawn(sender,params,ac,changetype,value,otherOptions);
		return true;
	}

	public static boolean setArenaOption(CommandSender sender, MatchParams params, Arena arena, String[] args)
		throws IllegalStateException
	{
		if (args.length < 3){
			showAlterHelp(sender);
			return false;
		}
		BattleArenaController ac = BattleArena.getBAController();

		String changetype = args[2];
		String value = "1";
		if (args.length > 3)
			value = args[3];
		String[] otherOptions = args.length > 4 ? Arrays.copyOfRange(args, 4, args.length) : null;
		if (Defaults.DEBUG) Log.info("alterArena arena=" + arena +":" + changetype + ":" + value);

		boolean success = false;
		ChangeType ct = ChangeType.fromName(changetype);
		if (ct == null){
			sendMessage(sender,ChatColor.RED+ "Option: &6" + changetype+"&c does not exist. \n&cValid options are &6"+ChangeType.getValidList());
			showAlterHelp(sender);
			return false;
		}
		if (value == null && ct.needsValue){
			sendMessage(sender,ChatColor.RED+ "Option: &6" + changetype+"&c needs a value");}
		if (!(sender instanceof Player) && ct.needsPlayer){
			sendMessage(sender,ChatColor.RED+ "Option: &6" + changetype+"&c needs you to be online");}
		Player player = null;
		if (ct.needsPlayer){
			player = (Player) sender;
		}

		switch(ct){
		case NLIVES: success = changeNLives(sender, arena, ac, value); break;
		case TEAMSIZE: success = changeTeamSize(sender, arena, ac, value); break;
		case NTEAMS: success = changeNTeams(sender, arena, ac, value); break;
		case TYPE: success = changeType(sender, arena, ac, value); break;
		case SPAWNLOC: success = changeSpawn(sender, arena, ac, changetype, value, otherOptions); break;
		case VLOC: success = changeVisitorSpawn(sender,arena,ac,changetype,value,otherOptions); break;
		case WAITROOM: success = changeWaitroomSpawn(sender,arena,ac,changetype,value,otherOptions); break;
		case SPECTATE: success = changeSpectateSpawn(sender,arena,ac,changetype,value,otherOptions); break;
		case LOBBY: success = changeLobbySpawn(sender,params,ac,changetype,value,otherOptions); break;
		case ADDREGION: success = addWorldGuardRegion(player,arena,ac,value); break;
		case ADDPYLAMOREGION: success = addPylamoRegion(player,arena,ac,value); break;
		default:
			sendMessage(sender,ChatColor.RED+ "Option: &6" + changetype+"&c does not exist. \n&cValid options are &6"+ChangeType.getValidList());
			break;
		}
		if (success)
			BattleArena.saveArenas(params.getType().getPlugin());
		return success;
	}

	private static boolean checkWorldGuard(CommandSender sender){
		if (!WorldGuardController.hasWorldGuard()){
			sendMessage(sender,"&cWorldGuard is not enabled");
			return false;
		}
		if (!(sender instanceof Player)){
			sendMessage(sender,"&cYou need to be in game to use this command");
			return false;
		}
		return true;
	}

	private static boolean addPylamoRegion(Player sender, Arena arena, BattleArenaController ac, String value) {
		if (!WorldGuardController.hasWorldEdit()){
			sendMessage(sender,"&cYou need world edit to use this command");
			return false;}
		if (!PylamoController.enabled()){
			sendMessage(sender,"&cYou need PylamoRestorationSystem to use this command");
			return false;}
		WorldEditPlugin wep = WorldEditUtil.getWorldEditPlugin();
		Selection sel = wep.getSelection(sender);
		if (sel == null){
			sendMessage(sender,"&cYou need to select a region to use this command.");
			return false;
		}
		String id = makeRegionName(arena);
		PylamoController.createRegion(id, sel.getMinimumPoint(), sel.getMaximumPoint());
		PylamoRegion region = new PylamoRegion(id);
		region.setID(id);
		arena.setPylamoRegion(region);
		return true;
	}

	private static boolean addWorldGuardRegion(Player sender, Arena arena, BattleArenaController ac, String value) {
		if (!checkWorldGuard(sender)){
			return false;}
		WorldEditPlugin wep = WorldEditUtil.getWorldEditPlugin();
		Selection sel = wep.getSelection(sender);
		if (sel == null){
			sendMessage(sender,"&cYou need to select a region to use this command.");
			return false;
		}

		WorldGuardRegion region = arena.getWorldGuardRegion();
		World w = sel.getWorld();
		try{
			String id = makeRegionName(arena);
			if (region != null){
				WorldGuardController.updateProtectedRegion(sender,id);
				sendMessage(sender,"&2Region updated! ");
			} else {
				region = WorldGuardController.createProtectedRegion(sender, id);
				if (region != null) {
					sendMessage(sender,"&2Region "+region.getRegionID()+" added! ");
				} else {
					sendMessage(sender,"&cRegion addition failed! ");
					return false;
				}
			}

			ConfigurationSection cs = BattleArena.getSelf().getConfig().getConfigurationSection("defaultWGFlags");
			if (cs != null){
				Set<String> set = cs.getKeys(false);
				if (set != null){
					for (String key: set){
						WorldGuardController.setFlag(region, key, cs.getBoolean(key, false));
					}
				}
			}

			arena.setWorldGuardRegion(region);
			WorldGuardController.saveSchematic(sender, id);
			MatchParams mp = ParamController.getMatchParams(arena.getArenaType().getName());
			if (mp != null && mp.getTransitionOptions().hasAnyOption(TransitionOption.WGNOENTER)){
				WorldGuardController.trackRegion(w.getName(), id);}
		} catch (Exception e) {
			sendMessage(sender,"&cAdding WorldGuard region failed!");
			sendMessage(sender, "&c" + e.getMessage());
			Log.printStackTrace(e);
		}
		return true;
	}

	public static String makeRegionName(Arena arena){
		return "ba-"+arena.getName().toLowerCase();
	}

	private static int verifySpawnLocation(CommandSender sender, String value){
		if (!(sender instanceof Player)){
			sendMessage(sender,"&cYou need to be in game to use this command");
			return -1;
		}
		try{return Integer.parseInt(value) -1;}catch(Exception e){}
		if (value.equalsIgnoreCase("main"))
			return Integer.MAX_VALUE;
		Integer locindex = TeamUtil.getTeamIndex(value);
		if (locindex == null || locindex > Defaults.MAX_SPAWNS){
			sendMessage(sender,"&cspawn number must be in the range [1-"+Defaults.MAX_SPAWNS+"] or be main");
			return -1;
		}
 		return locindex-1;
	}

	private static boolean changeLobbySpawn(CommandSender sender, MatchParams params, BattleArenaController ac,
			String changetype, String value, String[] otherOptions) {
		if (!BAExecutor.checkPlayer(sender))
			return false;
		int locindex = verifySpawnLocation(sender,value);
		if (locindex == -1)
			return false;
		String locstr = locindex == Integer.MAX_VALUE ? "main" : locindex+"";
		Player p = (Player) sender;
		Location loc = null;
		loc = parseLocation(p,value);
		if (loc == null){
			loc = p.getLocation();}
		RoomController.addLobby(params.getType(), locindex, loc);
		PlayerContainerSerializer pcs = new PlayerContainerSerializer();
		pcs.setConfig(BattleArena.getSelf().getDataFolder().getPath()+"/saves/containers.yml");
		pcs.save();
		return sendMessage(sender,"&2Lobby &6"+locstr +"&2 for&6 "+ params.getName() +" &2 set to location=&6" + Util.getLocString(loc));
	}

	private static boolean changeWaitroomSpawn(CommandSender sender, Arena arena, BattleArenaController ac,
			String changetype, String value, String[] otherOptions) {
		if (!BAExecutor.checkPlayer(sender))
			return false;
		int locindex = verifySpawnLocation(sender,value);
		if (locindex == -1)
			return false;
		String locstr = locindex == Integer.MAX_VALUE ? "main" : locindex+"";
		Player p = (Player) sender;
		Location loc = null;
		loc = parseLocation(p,value);
		if (loc == null){
			loc = p.getLocation();}
		MatchParams mp = ParamController.getMatchParams(arena.getArenaType());
		if (mp == null){
			throw new IllegalStateException("MatchParams for " + arena.getArenaType() +" could not be found");}
		if (arena.getWaitroom() == null){
			RoomContainer room = RoomController.getOrCreateWaitroom(arena);
			arena.setWaitRoom(room);
		}
		arena.setWaitRoomSpawnLoc(locindex,loc);
		ac.updateArena(arena);
		return sendMessage(sender,"&2waitroom &6" + locstr +"&2 set to location=&6" + Util.getLocString(loc));
	}

	private static boolean changeSpectateSpawn(CommandSender sender, Arena arena, BattleArenaController ac,
			String changetype, String value, String[] otherOptions) {
		if (!BAExecutor.checkPlayer(sender))
			return false;
		int locindex = verifySpawnLocation(sender,value);
		if (locindex == -1)
			return false;
		String locstr = locindex == Integer.MAX_VALUE ? "main" : locindex+"";
		Player p = (Player) sender;
		Location loc = null;
		loc = parseLocation(p,value);
		if (loc == null){
			loc = p.getLocation();}
		MatchParams mp = ParamController.getMatchParams(arena.getArenaType());
		if (mp == null){
			throw new IllegalStateException("MatchParams for " + arena.getArenaType() +" could not be found");}
		if (arena.getSpectate() == null){
			RoomContainer room = RoomController.getOrCreateSpectate(arena);
			arena.setSpectate(room);
		}
		arena.getSpectate().setSpawnLoc(locindex,loc);
		ac.updateArena(arena);
		return sendMessage(sender,"&spectator room &6" + locstr +"&2 set to location=&6" + Util.getLocString(loc));
	}

	private static boolean changeVisitorSpawn(CommandSender sender, Arena arena, BattleArenaController ac,
			String changetype, String value, String[] otherOptions) {
		if (!BAExecutor.checkPlayer(sender))
			return false;

		int locindex = verifySpawnLocation(sender,value);
		if (locindex == -1)
			return false;
		Player p = (Player) sender;
		Location loc = null;
		loc = parseLocation(p,value);
		if (loc == null){
			loc = p.getLocation();}

		arena.setSpawnLoc(locindex,loc);
		ac.updateArena(arena);

		return sendMessage(sender,"&2team &6" + changetype +"&2 spawn set to location=&6" + Util.getLocString(loc));
	}


	private static boolean changeSpawn(CommandSender sender, Arena arena, BattleArenaController ac,
			String changetype, String value, String[] otherOptions) {
		if (!BAExecutor.checkPlayer(sender))
			return false;
		int locindex = verifySpawnLocation(sender,changetype);
		if (locindex == -1)
			return false;

		Player p = (Player) sender;
		Location loc = null;
		loc = parseLocation(p,value);
		if (loc == null){
			loc = p.getLocation();}
		arena.setSpawnLoc(locindex,loc);
		ac.updateArena(arena);
		return sendMessage(sender,"&2 spawn &6"+changetype +" set to location=&6" + Util.getLocString(loc));
	}


	private static boolean changeType(CommandSender sender, Arena arena, BattleArenaController ac, String value) {
		ArenaType t = ArenaType.fromString(value);
		if (t == null){
			sendMessage(sender,"&ctype &6"+ value + "&c not found. valid types=&6"+ArenaType.getValidList());
			return false;
		}
		arena.getParams().setType(t);
		ac.updateArena(arena);
		return sendMessage(sender,"&2Altered arena type to &6" + value);
	}

	private static boolean changeNLives(CommandSender sender, Arena arena, BattleArenaController ac, String value) {
		try{
			Integer nlives = Integer.valueOf(value);
			arena.getParams().setNLives(nlives);
			ac.updateArena(arena);
			return sendMessage(sender,"&2Altered arena number of lives to &6" + value);
		} catch (Exception e){
			sendMessage(sender,"size "+ value + " not found");
			return false;
		}
	}

	private static boolean changeNTeams(CommandSender sender, Arena arena, BattleArenaController ac, String value) {
		try{
			final MinMax mm = MinMax.valueOf(value);
			arena.getParams().setNTeams(mm);
			ac.updateArena(arena);
			return sendMessage(sender,"&2Altered arena number of teams to &6" + value);
		} catch (Exception e){
			sendMessage(sender,"size "+ value + " not found");
			return false;
		}
	}

	private static boolean changeTeamSize(CommandSender sender, Arena arena, BattleArenaController ac, String value) {
		try{
			final MinMax mm = MinMax.valueOf(value);
			arena.getParams().setTeamSizes(mm);
			ac.updateArena(arena);
			return sendMessage(sender,"&2Altered arena team size to &6" + value);
		} catch (Exception e){
			sendMessage(sender,"size "+ value + " not found");
			return false;
		}
	}

	public static boolean restoreDefaultArenaOptions(Arena arena) {
		return restoreDefaultArenaOptions(arena,true);
	}

	private static boolean restoreDefaultArenaOptions(Arena arena, boolean save) {
		MatchParams ap = arena.getParams();
		MatchParams p = new MatchParams(ap.getType());
		p.setRating(ap.getRated());
		arena.setParams(p);
		BattleArenaController ac = BattleArena.getBAController();
		ac.updateArena(arena);
		if (save)
			BattleArena.saveArenas(arena.getArenaType().getPlugin());
		return true;
	}

	public static boolean restoreDefaultArenaOptions(MatchParams params) {
		BattleArenaController ac = BattleArena.getBAController();
		for (Arena a : ac.getArenas(params)){
			restoreDefaultArenaOptions(a);
		}
		BattleArena.saveArenas(params.getType().getPlugin());
		return true;
	}

	private static void showAlterHelp(CommandSender sender) {
		sendMessage(sender,ChatColor.GOLD+ "Usage: /arena edit <arenaname> <teamSize|nTeams|type|1|2|3...|vloc|waitroom> <value> [option]");
		sendMessage(sender,ChatColor.GOLD+ "Example: /arena edit MainArena 1 &e: sets spawn location 1 to where you are standing");
//		sendMessage(sender,ChatColor.GOLD+ "Example: /arena alter MainArena 1 wg &e: causes spawn 1 to have the worldguard area selected");
		sendMessage(sender,ChatColor.GOLD+ "Example: /arena edit MainArena teamSize 3+ ");
		sendMessage(sender,ChatColor.GOLD+ "Example: /arena edit MainArena nTeams 2 ");
		sendMessage(sender,ChatColor.GOLD+ "Example: /arena edit MainArena type deathmatch ");
		sendMessage(sender,ChatColor.GOLD+ "      or /arena edit MainArena waitroom 1 &e: sets waitroom 1 to your location");
	}

	public static Location parseLocation(CommandSender sender, String svl) {
		if (!(sender instanceof Player))
			return null;
		Player p = (Player) sender;
		if (svl == null)
			return null;
		String as[] = svl.split(":");
		if (svl.contains(",")){
			as = svl.split(",");
		}
		World w = p.getWorld();
		if (w == null){
			return null;}
		if (as.length < 3){
			return null;}
		try {
			int x = Integer.valueOf(as[0]);
			int y = Integer.valueOf(as[1]);
			int z = Integer.valueOf(as[2]);
			return new Location(w,x,y,z);
		} catch (Exception e){
			return null;
		}
	}

	public static boolean sendMessage(CommandSender sender, String msg){
		return MessageUtil.sendMessage(sender, msg);
	}

	public static boolean sendMessage(ArenaPlayer player, String msg){
		return MessageUtil.sendMessage(player, msg);
	}

}
