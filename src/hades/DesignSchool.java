package hades;

import battlecode.common.*;

import java.util.Arrays;
import java.util.function.Function;

public class DesignSchool extends Building {

    // defense variables
    MapLocation hqLocation = null;
    boolean defensive;
    boolean primaryDefensive = false; // For now only the primary defensive d.school does anything.
    int numLandscapersMade;
    int CLOSE_INNER_WALL_AT = 400;
    int startOuterWallAt = 0;
    int terraformersBuilt = 1;

    //For halting production and resuming it.
    boolean holdProduction = false;
    boolean firstRefineryExists = false; //this will only work if first refinery built after d.school exists
    int turnAtProductionHalt = -1;
    int previousSoup = 200;
    MapLocation enemyHQLocApprox = null;

    // aggression variables
    boolean aggressive = false;
    boolean wallProxy = false;
    MapLocation enemyHQLocation = null;

    public DesignSchool(RobotController rc) throws GameActionException {
        super(rc);
        System.out.println(myLocation);

        construct();
    }

    private void construct() throws GameActionException {
        hqLocation = checkForLocationMessage();
        defensive = myLocation.distanceSquaredTo(hqLocation) <= 25; // arbitrary cutoff, but should be more than big enough.
        if (defensive) {
            System.out.println("I am a defensive d.school. Found our HQ: " + hqLocation.toString());

            // Determine if I am the primary defensive d.school or if I am an extra.
            primaryDefensive = !existsNearbyAllyOfType(RobotType.LANDSCAPER);
        }
        else {
            System.out.println("I am far from my HQ.");
            MapLocation[] enemyHQCandidateLocs = {
                new MapLocation(rc.getMapWidth() - hqLocation.x - 1, hqLocation.y),
                new MapLocation(rc.getMapWidth() - hqLocation.x - 1, rc.getMapHeight() - hqLocation.y - 1),
                new MapLocation(hqLocation.x, rc.getMapHeight() - hqLocation.y - 1)
            };
            for (MapLocation enemyHQCandidateLoc : enemyHQCandidateLocs) {
                if (rc.canSenseLocation(enemyHQCandidateLoc)) {
                    System.out.println("I am an offensive d.school.");
                    aggressive = true;
                    enemyHQLocation = enemyHQCandidateLoc;
                    if (enemyHQLocation.isAdjacentTo(myLocation)) {
                        System.out.println("I am a wall proxy");
                        wallProxy = true;
                    }
                    break;
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
        else if (aggressive) {
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
            return;
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
        if (primaryDefensive && !holdProduction) { // primary defensive d.school.
            if (numLandscapersMade == 5 && terraformersBuilt == 0) { // build terraformer
                Direction spawnDir = myLocation.directionTo(hqLocation).opposite().rotateRight(); // note: added rotateRight for rush defense purposes
                for (int i = 8; i > 0; i--) {
                    if (tryBuild(RobotType.LANDSCAPER, spawnDir)) { // TODO: hardcoded base cost of landscaper
                        System.out.println("Built landscaper in direction " + spawnDir);
                        terraformersBuilt++;
                        // TODO: send terraformer message
                    }
                    else {
                        spawnDir = spawnDir.rotateLeft();
                    }
                }
            }
            if ((numLandscapersMade < 5 || ((rc.getRoundNum() >= CLOSE_INNER_WALL_AT || firstRefineryExists) && numLandscapersMade < 8))) { // WALL PHASE 0 AND 1
                System.out.println("Ready to make inner wall landscaper");
                Direction spawnDir = myLocation.directionTo(hqLocation).rotateRight(); // note: added rotateRight for rush defense purposes
                for (int i = 8; i > 0; i--) {
                    if (tryBuild(RobotType.LANDSCAPER, spawnDir)) { // TODO: hardcoded base cost of landscaper
                        System.out.println("Built landscaper in direction " + spawnDir);
                        numLandscapersMade++;
                    }
                    else {
                        spawnDir = spawnDir.rotateLeft();
                    }
                }
            }
            else if(numLandscapersMade >= 8 && numLandscapersMade <= 19) { // WALL PHASE 2
                System.out.println("Ready to make outer wall landscaper");
                if (startOuterWallAt == 0) {
                    startOuterWallAt = rc.getRoundNum();
                }
                if (rc.getRoundNum() - startOuterWallAt < 80 && rc.getTeamSoup() < 400) {
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
            else if(numLandscapersMade > 19 && numLandscapersMade < 22 && rc.getTeamSoup() > 400) {
                System.out.println("Building extra landscaper");
                Direction spawnDir = myLocation.directionTo(hqLocation).rotateLeft().rotateLeft();
                if (tryBuild(RobotType.LANDSCAPER, spawnDir)) {
                    numLandscapersMade++;
                }
                else {
                    spawnDir = spawnDir.rotateLeft();
                }
            }
            else if(numLandscapersMade >= 22) {
                System.out.println("My work is complete.  Goodbye, beautiful world...");
                rc.disintegrate();
            }
        }
    }

    public boolean sendTerraformMessage() throws GameActionException {
        TerraformMessage t = new TerraformMessage(MAP_HEIGHT, MAP_WIDTH, teamNum);
        t.writeType(1);
        return sendMessage(t.getMessage(), 1);
    }

    public boolean readMessages() throws GameActionException {
        int rn = rc.getRoundNum();
        int prev1 = rn-5;
        for(int i=prev1; i<rn; i++) {
            if(i>0) {
                findMessagesFromAllies(i);
            }
        }
        return false;
    }
    //Find message from allies given a round number rn
    //Checks block of round number rn, loops through messages
    //Currently: Checks for haltProductionMessage from a Miner
    public void findMessagesFromAllies(int rn) throws GameActionException {
        Transaction[] msgs = rc.getBlock(rn);
        for (Transaction transaction : msgs) {
            int[] msg = transaction.getMessage();
            if (allyMessage(msg[0])) {
                if(getSchema(msg[0])==3) {
                    HoldProductionMessage h = new HoldProductionMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum);
                    //System.out.println("[i] HOLDING PRODUCTION!");
                    holdProduction = true;
                    turnAtProductionHalt = rc.getRoundNum();
                    enemyHQLocApprox = getCenterFromTileNumber(h.enemyHQTile);
                }
                if(getSchema(msg[0])==5 && !firstRefineryExists) {
                    BuiltMessage b = new BuiltMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum);
                    if(b.typeBuilt==3) {
                        firstRefineryExists = true;
                    }
                }
            }
        }
    }

    //Returns true if should continue halting production
    //Returns false if should not continue halting production
    private boolean checkIfContinueHold() throws GameActionException {
        //resume production after 10 turns, at most
        if(rc.getRoundNum()-turnAtProductionHalt>30) {
            //System.out.println("[i] UNHOLDING PRODUCTION!");
            holdProduction = false;
            return false;
        }
        //-200 soup in one turn good approximation for building net gun
        //so we resume earlier than 10 turns if this happens
        if(previousSoup - rc.getTeamSoup() > 200) {
            //System.out.println("[i] UNHOLDING PRODUCTION!");
            holdProduction = false;
            return false;
        }
        //if neither condition happens (10 turns or -200), continue holding production
        return true;
    }
}
