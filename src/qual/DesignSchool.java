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
    // int CLOSE_INNER_WALL_AT = 400;
    int startOuterWallAt = 0;
    int numTerraformersMade = 0; // set to 0 to enable, set to 100 to disable
    // int NUM_TERRAFORMERS_INITIAL = 3;
    // int NUM_TERRAFORMERS_TOTAL = 18;
    // int INNER_WALL_PAUSE_AT = 3;
    int WAIT_FOR_FIRST_VAPE_TILL = 600;
    int shouldHaveFirstVape = WAIT_FOR_FIRST_VAPE_TILL - 10;
    boolean waitingForSecondVape = true;
    int stuckCounter = 0;

    //For halting production and resuming it.
    boolean holdProduction = false;
    boolean firstRefineryExists = false; //this will only work if first refinery built after d.school exists
    boolean firstFullfillmentCenterExists = false;
    int turnAtProductionHalt = -1;
    int previousSoup = 200;
    int vaporatorsBuilt = 0;
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
        defensive = myLocation.distanceSquaredTo(hqLocation) <= Landscaper.LATTICE_SIZE; // arbitrary cutoff, but should be more than big enough.
        if (defensive) {
            System.out.println("I am a defensive d.school. Found our HQ: " + hqLocation.toString());

            // Determine if I am the primary defensive d.school or if I am an extra.
            primaryDefensive = !existsNearbyAllyOfType(RobotType.LANDSCAPER) || rc.getRoundNum() < 300;
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

        //readmessages before you were born to see if we are under attack
        int rn = rc.getRoundNum();
        int topr = Math.min(rn, 200);
        for(int i=1; i<topr; i++) {
            findMessagesFromAllies(i);
        }
    }

    @Override
    public void run() throws GameActionException {
        if(holdProduction || enemyAggression) {
            checkIfContinueHold();
        }

        if (defensive) {
            if(rc.getRoundNum() < 200 && !enemyAggression) {
                if(enemyAggressionCheck()) {
                    turnAtEnemyAggression = rc.getRoundNum();
                }
            }
            defense();
        }
        else if (aggressive) {
            aggro();
        }

        findMessagesFromAllies(rc.getRoundNum()-1);

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
            // look for enemy d.school
            MapLocation enemyDSchoolLocation = null;
            RobotInfo[] nearbyBots = rc.senseNearbyRobots();
            for (RobotInfo r : nearbyBots) { // TODO: merge with previous loop
                if (r.type.equals(RobotType.DESIGN_SCHOOL) && r.team.equals(enemyTeam)) {
                    enemyDSchoolLocation = r.getLocation();
                }
            }
            // spawn adjacent to enemy d.school if possible
            if (enemyDSchoolLocation != null) {
                for (Direction d : directions) {
                    if (myLocation.add(d).isAdjacentTo(enemyDSchoolLocation) && myLocation.add(d).isAdjacentTo(enemyHQLocation)) {
                        if (tryBuild(RobotType.LANDSCAPER, d)) {
                            System.out.println("Built aggressive landscaper in direction " + d);
                        }
                    }
                }
            }

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
            if (rc.getTeamSoup() >= 460 && shouldHaveFirstVape == WAIT_FOR_FIRST_VAPE_TILL - 10) {
                shouldHaveFirstVape = rc.getRoundNum();
                System.out.println("We should have the first vape now!");
            }
            if (rc.getRoundNum() > shouldHaveFirstVape) {
                rc.setIndicatorDot(myLocation, 255, 255, 0);
            }
            if (enemyAggression) { // rush defense
                System.out.println("Enemy aggression");
                if (numLandscapersMade < 5) {
                    System.out.println("A");
                    spawnInnerWaller();
                } else if (numTerraformersMade < 3) {
                    System.out.println("B");
                    spawnTerraformer();
                } else if (vaporatorsBuilt < 2) {
                    System.out.println("C");
                    return;
                } else if ((rc.getRoundNum() >= 500 || (firstRefineryExists && rc.getTeamSoup() >= 1000)) && numLandscapersMade < 8) {
                    System.out.println("D");
                    spawnInnerWaller();
                } else if (vaporatorsBuilt < 4 && rc.getTeamSoup() < 1000 && rc.getRoundNum() < 650) {
                    System.out.println("E");
                    return;
                } else if (numTerraformersMade < 18 && (rc.getRoundNum() >= 800 || rc.getTeamSoup() >= 521)) {
                    System.out.println("F");
                    spawnTerraformer();
                } else if (rc.getRoundNum() >= 1100 & rc.getTeamSoup() >= 400 + 3 * numTerraformersMade) {
                    System.out.println("G");
                    spawnTerraformer();
                } else {
                    System.out.println("Done");
                }
            } else { // normal operation, greedier
                System.out.println("No enemy aggression");
                if (vaporatorsBuilt < 1 || rc.getRoundNum() <= 165) {
                    System.out.println("A");
                    return;
                } else if (numTerraformersMade < 4 && rc.getRoundNum() < 250) {
                    System.out.println("B");
                    spawnTerraformer();
                } else if (rc.getRoundNum() >= 300 && numLandscapersMade < 3) {
                    System.out.println("C");
                    spawnInnerWaller();
                } else if (vaporatorsBuilt < 4 && rc.getTeamSoup() < 1000) {
                    System.out.println("D");
                    return;
                } else if (numTerraformersMade < 5 && rc.getRoundNum() >= 250 && rc.getTeamSoup() >= 510) {
                    System.out.println("E");
                    spawnTerraformer();
                } else if (vaporatorsBuilt < 10 && rc.getTeamSoup() < 1000) {
                    System.out.println("D");
                    return;
                } else if (numTerraformersMade < 8 && rc.getRoundNum() >= 500) {
                    System.out.println("F");
                    spawnTerraformer();
                } else if (numTerraformersMade < 18 && (rc.getRoundNum() >= 800 || rc.getTeamSoup() >= 521)) {
                    System.out.println("G");
                    spawnTerraformer();
                } else if (rc.getRoundNum() >= 1100 && rc.getTeamSoup() >= 400 + 3 * numTerraformersMade) {
                    System.out.println("H");
                    spawnTerraformer();
                } else {
                    System.out.println("Done");
                }
            }
        } else {
            boolean existPlacesToBuild = false;
            for (Direction d : directions) {
                if (rc.canBuildRobot(RobotType.LANDSCAPER, d) && myLocation.add(d).distanceSquaredTo(hqLocation) <= Landscaper.LATTICE_SIZE) {
                    existPlacesToBuild = true;
                }
            }
            if (!existPlacesToBuild) {
                System.out.println("I'm stuck.  Been stuck for " + Integer.toString(stuckCounter) + " turns before this.");
                stuckCounter++;
            } else {
                stuckCounter = 0;
            }
            if (stuckCounter >= 20) {
                rc.disintegrate();
            }
            if (rc.getRoundNum() > 800 || rc.getTeamSoup() >= 528) {
                spawnTerraformerTowardsHQ();
            }
        }
    }

    public boolean buildTerraformer(Direction spawnDir) throws GameActionException {
        if (myLocation.add(spawnDir).distanceSquaredTo(hqLocation) <= Landscaper.LATTICE_SIZE && tryBuild(RobotType.LANDSCAPER, spawnDir)) {
            System.out.println("Built terraformer in direction " + spawnDir);
            int terraformerID = rc.senseRobotAtLocation(myLocation.add(spawnDir)).ID;
            numTerraformersMade++;
            sendTerraformMessage(terraformerID);
            return true;
        } else {
            return false;
        }
    }

    public void spawnTerraformerEarly() throws GameActionException {
        System.out.println("Attempting to spawn terraformer");
        Direction spawnDir = myLocation.directionTo(hqLocation).rotateRight().rotateRight();
        if (buildTerraformer(spawnDir)) {
            return;
        }
        spawnDir = myLocation.directionTo(hqLocation).rotateLeft().rotateLeft();
        for (int i = 8; i > 0; i--) {
            if (buildTerraformer(spawnDir)) {
                break;
            } else {
                spawnDir = spawnDir.rotateLeft();
            }
        }
    }

    public void spawnTerraformer() throws GameActionException {
        System.out.println("Attempting to spawn terraformer");
        Direction spawnDir = myLocation.directionTo(hqLocation).opposite().rotateRight();
        for (int i = 8; i > 0; i--) {
            if (buildTerraformer(spawnDir)) {
                break;
            } else {
                spawnDir = spawnDir.rotateLeft();
            }
        }
    }

    public void spawnTerraformerTowardsHQ() throws GameActionException {
        System.out.println("Attempting to spawn terraformer");
        Direction spawnDir = myLocation.directionTo(hqLocation);
        for (Direction d : directionsClosestTo(spawnDir)) {
            if (buildTerraformer(d)) {
                break;
            }
        }
    }

    public void spawnInnerWaller() throws GameActionException {
        System.out.println("Attempting to spawn inner waller");
        // look for enemy d.school
        MapLocation enemyDSchoolLocation = null;
        RobotInfo[] nearbyBots = rc.senseNearbyRobots();
        for (RobotInfo r : nearbyBots) { // TODO: merge with previous loop
            if (r.type.equals(RobotType.DESIGN_SCHOOL) && r.team.equals(enemyTeam)) {
                enemyDSchoolLocation = r.getLocation();
            }
        }
        // spawn adjacent to enemy d.school if possible
        if (enemyDSchoolLocation != null) {
            for (Direction d : directions) {
                if (myLocation.add(d).isAdjacentTo(enemyDSchoolLocation) && myLocation.add(d).isAdjacentTo(hqLocation)) {
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

    public void spawnOuterWaller() throws GameActionException {
        System.out.println("Attempting to spawn outer waller");
        Direction spawnDir = myLocation.directionTo(hqLocation).opposite().rotateRight();
        for (int i = 8; i > 0; i--) {
            if (tryBuild(RobotType.LANDSCAPER, spawnDir)) {
                numLandscapersMade++;
            }
            else {
                spawnDir = spawnDir.rotateRight();
            }
        }
    }

    public boolean sendTerraformMessage(int i) throws GameActionException {
        System.out.println("[i] Sending terraform message");
        System.out.println("[i] ID: " + Integer.toString(i%1000));
        TerraformMessage t = new TerraformMessage(MAP_HEIGHT, MAP_WIDTH, teamNum, rc.getRoundNum());
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
            if (allyMessage(msg[0], rn)) {
                if(getSchema(msg[0])==3) {
                    HoldProductionMessage h = new HoldProductionMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum, rn);
                    System.out.println("[i] HOLDING PRODUCTION!");
                    holdProduction = true;
                    turnAtProductionHalt = rc.getRoundNum();
                } else if(getSchema(msg[0])==4 && trueEnemyHQLocation==null) {
                    checkForEnemyHQLocationMessageSubroutine(msg, rn);
                    if(ENEMY_HQ_LOCATION != null) {
                        trueEnemyHQLocation = ENEMY_HQ_LOCATION;
                    }
                } else if(getSchema(msg[0])==5) {
                    BuiltMessage b = new BuiltMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum, rn);
                    if(!firstRefineryExists && b.typeBuilt==3) {
                        firstRefineryExists = true;
                    } else if(!firstFullfillmentCenterExists && b.typeBuilt==1) {
                        firstFullfillmentCenterExists = true;
                    } else if(b.typeBuilt==4) {
                        vaporatorsBuilt += 1;
                        System.out.println("[i] We've built " + Integer.toString(vaporatorsBuilt) + " vaporators");
                    }
                } else if (getSchema(msg[0])==7) {
                    RushCommitMessage r = new RushCommitMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum, rn);
                    if(!enemyAggression) {
                        if(r.typeOfCommit==2) {
                            System.out.println("[i] Enemy is Rushing!");
                            enemyAggression = true;
                            turnAtEnemyAggression = rc.getRoundNum();
                        }
                    } else {
                        if(r.typeOfCommit==3) {
                            System.out.println("[i] Enemy has stopped rushing");
                            enemyAggression = false;
                            turnAtEnemyAggression = -1;
                        }
                    }
                }
            }
        }
    }

    //Returns true if should continue halting production
    //Returns false if should not continue halting production
    private boolean checkIfContinueHold() throws GameActionException {
        //resume production after 10 turns, at most
        if(holdProduction) {
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
        }
        if(enemyAggression) {
            if(rc.getRoundNum() - turnAtEnemyAggression > 200 || (numLandscapersMade >= 2 && !existsNearbyEnemyBuilding())) {
                enemyAggression = false;
                return false;
            }
        }
        //if neither condition happens (10 turns or -200), continue holding production
        return true;
    }
}
