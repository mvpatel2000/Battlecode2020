package sprint;

import battlecode.common.*;

import java.util.Arrays;
import java.util.function.Function;

public class DesignSchool extends Building {

    MapLocation hqLocation = null;
    boolean defensive;
    boolean primaryDefensive = false; // For now only the primary defensive d.school does anything.
    int numLandscapersMade;
    int DEFAULT_CLOSE_INNER_WALL_AT = 400;
    int closeInnerWallAt = DEFAULT_CLOSE_INNER_WALL_AT; // TODO: tweak this
    int startOuterWallAt = 0;

    //For halting production and resuming it.
    boolean holdProduction = false;
    int turnAtProductionHalt = -1;
    int previousSoup = 200;
    MapLocation enemyHQLocApprox = null;

    boolean wallProxy = false;
    MapLocation enemyHQLocation = null;

    public DesignSchool(RobotController rc) throws GameActionException {
        super(rc);
        System.out.println(myLocation);
        int hqID = rc.getTeam().equals(Team.valueOf("A")) ? 0 : 1;
        defensive = rc.canSenseRobot(hqID);
        if (defensive) {
            RobotInfo hqInfo = rc.senseRobot(hqID);
            hqLocation = hqInfo.location;
            System.out.println("I am a defensive d.school. Found our HQ:");
            System.out.println(hqInfo);

            // Determine if I am the primary defensive d.school or if I am an extra.
            primaryDefensive = !existsNearbyAllyOfType(RobotType.LANDSCAPER);
        }
        else {
            System.out.println("I am an offensive d.school.");
            int enemyHQID = 1 - hqID;
            if (rc.canSenseRobot(enemyHQID)) {
                RobotInfo enemyHQInfo = rc.senseRobot(enemyHQID);
                enemyHQLocation = enemyHQInfo.location;
                if (enemyHQLocation.isAdjacentTo(myLocation)) {
                    System.out.println("I am a wall proxy");
                    wallProxy = true;
                }
            }
        }
    }

    @Override
    public void run() throws GameActionException  {
        if(holdProduction) {
            checkIfContinueHold();
        }

        if (defensive) {
            defense();
        }
        else {
            aggro();
        }

        if(rc.getRoundNum()%5==3) {
            readMessages();
        }

        //should always be the last thing
        previousSoup = rc.getTeamSoup();
    }

    private int countAggroLandscapers(Team t) {
        return Arrays.stream(rc.senseNearbyRobots()).filter(x ->
                x.getLocation().distanceSquaredTo(enemyHQLocation) < 3
                        && x.getType().equals(RobotType.LANDSCAPER)
                        && x.getTeam().equals(t)).toArray(RobotInfo[]::new).length;
    }

    public void aggro() throws GameActionException {
        if (countAggroLandscapers(allyTeam) < countAggroLandscapers(enemyTeam) - 1) // give up if they are beating us by two
        if (wallProxy && !holdProduction) {
            for (Direction d : directions) {
                MapLocation t = enemyHQLocation.add(d);
                if(tryBuild(RobotType.LANDSCAPER, myLocation.directionTo(t))) {
                    System.out.println("Built aggressive landscaper at " + t.toString());
                }
            }
        }
    }

    public void defense() throws GameActionException {
        if (existsNearbyEnemy() && numLandscapersMade >= 3) {
            System.out.println("Enemy detected!  I will hurry and close this wall.");
            closeInnerWallAt = 0;
        }
        if (primaryDefensive && !holdProduction) { // primary defensive d.school.
            if ((numLandscapersMade < 5 || (rc.getRoundNum() >= closeInnerWallAt && numLandscapersMade < 8))) { // WALL PHASE 0 AND 1
                Direction spawnDir = myLocation.directionTo(hqLocation).rotateRight(); // note: added rotateRight for rush defense purposes
                for (int i = 8; i > 0; i--) {
                    if (tryBuild(RobotType.LANDSCAPER, spawnDir)) { // TODO: hardcoded base cost of landscaper
                        numLandscapersMade++;
                    }
                    else {
                        spawnDir = spawnDir.rotateLeft();
                    }
                }
            }
            else if(numLandscapersMade >= 8 && numLandscapersMade < 19) { // WALL PHASE 2
                if (startOuterWallAt == 0) {
                    startOuterWallAt = rc.getRoundNum();
                }
                if (rc.getRoundNum() - startOuterWallAt < 80) {
                    return;
                }
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


    public boolean readMessages() throws GameActionException {
        int rn = rc.getRoundNum();
        int prev1 = rn-5;
        for(int i=prev1; i<rn; i++) {
            if(i>0) {
                if(findMessagesFromAllies(i)) {
                    return true;
                }
            }
        }
        return false;
    }
    //Find message from allies given a round number rn
    //Checks block of round number rn, loops through messages
    //Currently: Checks for haltProductionMessage from a Miner
    public boolean findMessagesFromAllies(int rn) throws GameActionException {
        Transaction[] msgs = rc.getBlock(rn);
        for (Transaction transaction : msgs) {
            int[] msg = transaction.getMessage();
            Message m = new Message(msg, MAP_HEIGHT, MAP_WIDTH, teamNum);
            if (m.origin) {
                if(m.schema == 3) {
                    HoldProductionMessage h = new HoldProductionMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum);
                    System.out.println("HOLDING PRODUCTION!");
                    holdProduction = true;
                    turnAtProductionHalt = rc.getRoundNum();
                    enemyHQLocApprox = getCenterFromTileNumber(h.enemyHQTile);
                    return true;
                }
            }
        }
        return false;
    }

    //Returns true if should continue halting production
    //Returns false if should not continue halting production
    private boolean checkIfContinueHold() throws GameActionException {
        //resume production after 10 turns, at most
        if(rc.getRoundNum()-turnAtProductionHalt>10) {
            System.out.println("UNHOLDING PRODUCTION!");
            holdProduction = false;
            return false;
        }
        //-200 soup in one turn good approximation for building net gun
        //so we resume earlier than 10 turns if this happens
        if(previousSoup - rc.getTeamSoup() > 200) {
            System.out.println("UNHOLDING PRODUCTION!");
            holdProduction = false;
            return false;
        }
        //if neither condition happens (10 turns or -200), continue holding production
        return true;
    }
}
