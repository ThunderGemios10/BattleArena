package mc.alk.arena.competition.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mc.alk.arena.competition.Competition;
import mc.alk.arena.controllers.TeamController;
import mc.alk.arena.objects.ArenaPlayer;
import mc.alk.arena.objects.MatchParams;
import mc.alk.arena.objects.teams.CompositeTeam;
import mc.alk.arena.objects.teams.Team;
import mc.alk.arena.objects.teams.TeamHandler;

public abstract class TeamJoinHandler implements TeamHandler {

	public static final TeamJoinResult CANTFIT = new TeamJoinResult(TeamJoinStatus.CANT_FIT,-1,null);
	public static final TeamJoinResult NOTOPEN = new TeamJoinResult(TeamJoinStatus.NOT_OPEN,-1,null);

	public static enum TeamJoinStatus{
		ADDED, CANT_FIT, ADDED_TO_EXISTING, WAITING_FOR_PLAYERS, NOT_OPEN
	}

	public static class TeamJoinResult{
		final public TeamJoinStatus status;
		final public int remaining;
		final public Team team;

		public TeamJoinResult(TeamJoinStatus status, int remaining, Team team){
			this.status = status; this.remaining = remaining; this.team = team;}
		public TeamJoinStatus getEventType(){ return status;}
		public int getRemaining(){return remaining;}
	}

	final List<Team> teams;
	ArrayList<CompositeTeam> pickupTeams = new ArrayList<CompositeTeam>();
	final MatchParams mp;
	final Competition competition;
	final int minTeamSize,maxTeamSize;
	final int minTeams,maxTeams;

	public TeamJoinHandler(Competition competition){
		this.competition = competition;
		this.mp = competition.getParams();
		this.minTeamSize = mp.getMinTeamSize(); this.maxTeamSize = mp.getMaxTeamSize();
		this.minTeams = mp.getMinTeams(); this.maxTeams = mp.getMaxTeams();
		this.teams = competition.getTeams();
	}

	public void deconstruct() {
		for (Team t: pickupTeams){
			TeamController.removeTeamHandler(t, this);
		}
		pickupTeams.clear();
	}

	public abstract TeamJoinResult joiningTeam(Team team);

	public boolean canLeave(ArenaPlayer p) {
		return true;
	}
	public boolean leave(ArenaPlayer p) {
		for (Team t: pickupTeams){
			if (t.hasMember(p)){
				pickupTeams.remove(t);
				return true;
			}
		}
		return true;
	}

	public Set<ArenaPlayer> getExcludedPlayers() {
		Set<ArenaPlayer> tplayers = new HashSet<ArenaPlayer>();
		for (Team t: pickupTeams){
			tplayers.addAll(t.getPlayers());}
		return tplayers;
	}

	public boolean hasEnough(){
		final int teamssize = teams.size();
		if (teamssize < minTeams || teamssize > maxTeams)
			return false;
		for (Team t: teams){
			final int tsize = t.size();
			if (tsize < minTeamSize || tsize > maxTeamSize)
				return false;
		}
		return true;
	}

	public boolean isFull() {
		/// Check to see if we have filled up our number of teams
		if ( maxTeams > teams.size()){
			return false;}
		/// Check to see if there is any space left on the team
		for (Team t: teams){
			if (t.size() < maxTeamSize){
				return false;}
		}
		/// we can't add a team.. and all teams are full
		return true;
	}

}