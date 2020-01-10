package sprint;

import battlecode.common.*;

public class DesignSchool extends Building {

    MapLocation baseLocation;
    boolean defensive;
    boolean primaryDefensive; // For now only the primary defensive d.school does anything.
    int numLandscapersMade;
    int closeInnerWallAt;

    public DesignSchool(RobotController rc) throws GameActionException {
        super(rc);
        System.out.println(myLocation);
        baseLocation = null;
        int hqID = rc.getTeam().equals(Team.valueOf("A")) ? 0 : 1;
        defensive = rc.canSenseRobot(hqID);
        primaryDefensive = false;
        if (defensive) {
            RobotInfo baseInfo = rc.senseRobot(hqID);
            baseLocation = baseInfo.location;
            System.out.println("I am a defensive d.school. Found our HQ:");
            System.out.println(baseInfo);

            // Determine if I am the primary defensive d.school or if I am an extra.
            primaryDefensive = !existsNearbyAllyOfType(RobotType.LANDSCAPER);

            closeInnerWallAt = 300;
        }
	    else {
	    	System.out.println("I am an offensive d.school");
	    }
    }

    @Override
    public void run() throws GameActionException  {
    	if (existsNearbyEnemy()) {
    		closeInnerWallAt = 0;
    	}
        if (primaryDefensive && (numLandscapersMade < 5 || (rc.getRoundNum() >= closeInnerWallAt && numLandscapersMade < 8))) { // primary defensive d.school. TODO: constant 400 should be tweaked
        	Direction spawnDir = myLocation.directionTo(baseLocation);
        	for (int i = 8; i > 0; i--) {
        		if (tryBuild(RobotType.LANDSCAPER, spawnDir)) {
        			numLandscapersMade++;
        		}
        		else {
        			spawnDir = spawnDir.rotateLeft();
        		}
        	}
        }
    }
}
