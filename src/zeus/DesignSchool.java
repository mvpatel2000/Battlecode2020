package smite;

import battlecode.common.*;

public class DesignSchool extends Building {

    MapLocation hqLocation = null;
    boolean defensive;
    boolean primaryDefensive = false; // For now only the primary defensive d.school does anything.
    int numLandscapersMade;
    int DEFAULT_CLOSE_INNER_WALL_AT = 300;
    int closeInnerWallAt = DEFAULT_CLOSE_INNER_WALL_AT; // TODO: tweak this

    boolean wallProxy = false;
    MapLocation enemyHQLocation = null;

    public DesignSchool(RobotController rc) throws GameActionException {
        super(rc);
        //System.out.println(myLocation);
        int hqID = rc.getTeam().equals(Team.valueOf("A")) ? 0 : 1;
        defensive = rc.canSenseRobot(hqID);
        if (defensive) {
            RobotInfo hqInfo = rc.senseRobot(hqID);
            hqLocation = hqInfo.location;
            //System.out.println("I am a defensive d.school. Found our HQ:");
            //System.out.println(hqInfo);

            // Determine if I am the primary defensive d.school or if I am an extra.
            primaryDefensive = !existsNearbyAllyOfType(RobotType.LANDSCAPER);
        }
        else {
            //System.out.println("I am an offensive d.school.");
            int enemyHQID = 1 - hqID;
            if (rc.canSenseRobot(enemyHQID)) {
                RobotInfo enemyHQInfo = rc.senseRobot(enemyHQID);
                enemyHQLocation = enemyHQInfo.location;
                if (enemyHQLocation.isAdjacentTo(myLocation)) {
                    //System.out.println("I am a wall proxy");
                    wallProxy = true;
                }
            }
        }
    }

    @Override
    public void run() throws GameActionException  {
        if (defensive) {
            defense();
        }
        else {
            aggro();
        }
    }

    public void aggro() throws GameActionException {
        if (wallProxy) {
            for (Direction d : directions) {
                MapLocation t = enemyHQLocation.add(d);
                if(tryBuild(RobotType.LANDSCAPER, myLocation.directionTo(t))) {
                    //System.out.println("Built aggressive landscaper at " + t.toString());
                }
            }
        }
    }

    public void defense() throws GameActionException {
        if (existsNearbyEnemy()) {
            //System.out.println("Enemy detected!  I will hurry and close this wall.");
            closeInnerWallAt = 0;
        }
        if (primaryDefensive) { // primary defensive d.school.
            if ((numLandscapersMade < 5 || (rc.getRoundNum() >= closeInnerWallAt && numLandscapersMade < 8))) { // WALL PHASE 0 AND 1
                Direction spawnDir = myLocation.directionTo(hqLocation).rotateRight(); // note: added rotateRight for rush defense purposes
                for (int i = 8; i > 0; i--) {
                    if (tryBuild(RobotType.LANDSCAPER, spawnDir)) {
                        numLandscapersMade++;
                    }
                    else {
                        spawnDir = spawnDir.rotateLeft();
                    }
                }
            }
            else if(numLandscapersMade >= 8 && numLandscapersMade < 19) { // WALL PHASE 2
                Direction spawnDir = myLocation.directionTo(hqLocation).rotateRight().rotateRight();
                for (int i = 8; i > 0; i--) {
                    if (tryBuild(RobotType.LANDSCAPER, spawnDir)) {
                        numLandscapersMade++;
                    }
                    else {
                        spawnDir = spawnDir.rotateRight();
                    }
                }
            }
            else if(numLandscapersMade == 19) {
                if (tryBuild(RobotType.LANDSCAPER, myLocation.directionTo(hqLocation).rotateLeft().rotateLeft())) {
                    numLandscapersMade++;
                }
            }
        }
    }
}
