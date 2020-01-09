package sprint;

import battlecode.common.*;

public class DesignSchool extends Building {

    MapLocation baseLocation;
    boolean defensive;

    public DesignSchool(RobotController rc) throws GameActionException {
        super(rc);
        //System.out.println(myLocation);
        baseLocation = null;
        int hqID = rc.getTeam().equals(Team.valueOf("A")) ? 0 : 1;
        defensive = rc.canSenseRobot(hqID);
        if (defensive) {
            RobotInfo baseInfo = rc.senseRobot(hqID);
            baseLocation = baseInfo.location;
            //System.out.println("I am a defensive d.school. Found our HQ:");
            //System.out.println(baseInfo);
        }
	    else {
	    	//System.out.println("I am an offensive d.school");
	    }
    }

    @Override
    public void run() throws GameActionException  {
        if (defensive) { // defensive d.school
        	Direction spawnDir = myLocation.directionTo(baseLocation);
        	for (int i = 8; i > 0; i--) {
        		tryBuild(RobotType.LANDSCAPER, spawnDir);
        		spawnDir = spawnDir.rotateLeft();
        	}
        }
    }
}
