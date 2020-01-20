package qual;

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
    int numTerraformersMade = 1000; // set to 0 to enable

    //For halting production and resuming it.
    boolean holdProduction = false;
    boolean firstRefineryExists = false; //this will only work if first refinery built after d.school exists
    int turnAtProductionHalt = -1;
    int previousSoup = 200;
    MapLocation trueEnemyHQLocation = null;

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
        checkForLocationMessage();
        hqLocation = HEADQUARTERS_LOCATION;
        defensive = myLocation.distanceSquaredTo(hqLocation) <= 49; // arbitrary cutoff, but should be more than big enough.
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
            Direction enemyHQDir = myLocation.directionTo(enemyHQLocation);
            Direction[] buildDirs = {enemyHQDir.rotateLeft(), enemyHQDir.rotateRight(), enemyHQDir.rotateLeft().rotateLeft(), enemyHQDir.rotateRight().rotateRight()};

            for (Direction d : buildDirs) {
                MapLocation t = myLocation.add(d);
                if(tryBuild(RobotType.LANDSCAPER, myLocation.directionTo(t))) {
                    System.out.println("Built aggressive landscaper at " + t.toString());
                }
            }
        }
    }

    public void defense() throws GameActionException {
        if (primaryDefensive && !holdProduction) { // primary defensive d.school.
            if (numLandscapersMade == 5 && numTerraformersMade == 0) { // build terraformer
                Direction spawnDir = myLocation.directionTo(hqLocation).opposite().rotateRight();
                for (int i = 8; i > 0; i--) {
                    if (tryBuild(RobotType.LANDSCAPER, spawnDir)) {
                        System.out.println("Built terraformer in direction " + spawnDir);
                        int terraformerID = rc.senseRobotAtLocation(myLocation.add(spawnDir)).ID;
                        numTerraformersMade++;
                        sendTerraformMessage(terraformerID);
                    }
                    else {
                        spawnDir = spawnDir.rotateLeft();
                    }
                }
            }
            if ((numLandscapersMade < 5 || ((rc.getRoundNum() >= CLOSE_INNER_WALL_AT || firstRefineryExists) && numLandscapersMade < 8))) { // WALL PHASE 0 AND 1
                System.out.println("Ready to make inner wall landscaper");

                // look for enemy d.school
                enemyDSchoolLocation = null;
                for (RobotInfo r : nearbyBots) { // TODO: merge with previous loop
                    if (r.type.equals(RobotType.DESIGN_SCHOOL) && r.team.equals(enemyTeam)) {
                        enemyDSchoolLocation = r.getLocation();
                    }
                }
                // spawn adjacent to enemy d.school if possible
                if (enemyDSchoolLocation != null) {
                    for (Direction d : directions) {
                        if (myLocation.add(d).isAdjacentTo(enemyDSchoolLocation)) {
                            if (tryBuild(RobotType.LANDSCAPER, d)) {
                                System.out.println("Built landscaper in direction " + d);
                                numLandscapersMade++;
                            }
                        }
                    }
                }

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
            if (numLandscapersMade == 8 && numTerraformersMade < 4) {
                Direction spawnDir = myLocation.directionTo(hqLocation).opposite().rotateRight();
                for (int i = 8; i > 0; i--) {
                    if (tryBuild(RobotType.LANDSCAPER, spawnDir)) {
                        System.out.println("Built terraformer in direction " + spawnDir);
                        int terraformerID = rc.senseRobotAtLocation(myLocation.add(spawnDir)).ID;
                        numTerraformersMade++;
                        sendTerraformMessage(terraformerID);
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

    public boolean sendTerraformMessage(int i) throws GameActionException {
        System.out.println("[i] Sending terraform message");
        System.out.println("[i] ID: " + Integer.toString(i%1000));
        TerraformMessage t = new TerraformMessage(MAP_HEIGHT, MAP_WIDTH, teamNum);
        t.writeTypeAndID(1, i%1000); //1 is landscaper, id is max 10 bits, hence mod 1000
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
                    System.out.println("[i] HOLDING PRODUCTION!");
                    holdProduction = true;
                    turnAtProductionHalt = rc.getRoundNum();
                } else if(getSchema(msg[0])==4 && trueEnemyHQLocation==null) {
                    checkForEnemyHQLocationMessageSubroutine(msg);
                    if(ENEMY_HQ_LOCATION != null) {
                        trueEnemyHQLocation = ENEMY_HQ_LOCATION;
                    }
                } else if(getSchema(msg[0])==5 && !firstRefineryExists) {
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
            System.out.println("[i] UNHOLDING PRODUCTION!");
            holdProduction = false;
            return false;
        }
        //-200 soup in one turn good approximation for building net gun
        //so we resume earlier than 10 turns if this happens
        if(previousSoup - rc.getTeamSoup() > 200) {
            System.out.println("[i] UNHOLDING PRODUCTION!");
            holdProduction = false;
            return false;
        }
        //if neither condition happens (10 turns or -200), continue holding production
        return true;
    }
}
