package qual;

import java.util.*;
import battlecode.common.*;

// TODO: heal d.school if it is being attacked.  Haven't started this yet; it is straightforward if in lategame.  But it is nontrivial if in early game.

public class Landscaper extends Unit {

    boolean defensive = false;
    Map<MapLocation, RobotInfo> nearbyBotsMap;
    RobotInfo[] nearbyBots;
    MapLocation baseLocation;

    // class variables used specifically by terraformers:
    int bornTurn;
    boolean terraformer = false;
    MapLocation plotCenter = null;

    // class variables used specifically by defensive landscapers:
    MapLocation hqLocation = null;
    int wallPhase;
    MapLocation holdPositionLoc = null; // this is important; used in updateNearbyBots() to prevent circular reasoning
    boolean innerWaller = true;
    Direction[] innerWallFillOrder;
    Direction[][] outerRing = {
        {Direction.NORTHWEST, Direction.NORTHWEST},
        {Direction.NORTH, Direction.NORTHWEST},
        {Direction.NORTH, Direction.NORTH},
        {Direction.NORTH, Direction.NORTHEAST},
        {Direction.NORTHEAST, Direction.NORTHEAST},
        {Direction.EAST, Direction.NORTHEAST},
        {Direction.EAST, Direction.EAST},
        {Direction.EAST, Direction.SOUTHEAST},
        {Direction.SOUTHEAST, Direction.SOUTHEAST},
        {Direction.SOUTH, Direction.SOUTHEAST},
        {Direction.SOUTH, Direction.SOUTH},
        {Direction.SOUTH, Direction.SOUTHWEST},
        {Direction.SOUTHWEST, Direction.SOUTHWEST},
        {Direction.WEST, Direction.SOUTHWEST},
        {Direction.WEST, Direction.WEST},
        {Direction.WEST, Direction.NORTHWEST}
    };
    Direction[][] outerRingDig = {
        {Direction.NORTHWEST, Direction.NORTHEAST, Direction.SOUTHWEST, Direction.NORTH, Direction.SOUTH},
        {Direction.EAST, Direction.NORTHWEST, Direction.NORTHEAST, Direction.NORTH},
        {Direction.CENTER},
        {Direction.WEST, Direction.NORTHEAST, Direction.NORTHWEST, Direction.NORTH},
        {Direction.NORTHWEST, Direction.NORTHEAST, Direction.SOUTHEAST, Direction.NORTH, Direction.EAST},
        {Direction.SOUTH, Direction.NORTHEAST, Direction.SOUTHEAST, Direction.EAST},
        {Direction.CENTER},
        {Direction.NORTH, Direction.SOUTHEAST, Direction.NORTHEAST, Direction.EAST},
        {Direction.SOUTHEAST, Direction.NORTHEAST, Direction.SOUTHWEST, Direction.EAST, Direction.SOUTH},
        {Direction.WEST, Direction.SOUTHEAST, Direction.SOUTHWEST, Direction.SOUTH},
        {Direction.CENTER},
        {Direction.EAST, Direction.SOUTHWEST, Direction.SOUTHEAST, Direction.SOUTH},
        {Direction.SOUTHWEST, Direction.SOUTHEAST, Direction.NORTHWEST, Direction.WEST, Direction.SOUTH},
        {Direction.NORTH, Direction.SOUTHWEST, Direction.NORTHWEST, Direction.WEST},
        {Direction.CENTER},
        {Direction.SOUTH, Direction.NORTHWEST, Direction.SOUTHWEST, Direction.WEST}
    };
    Direction[][] outerRingDeposit = {
        {Direction.SOUTHEAST},
        {Direction.SOUTHEAST, Direction.SOUTH},
        {Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST},
        {Direction.SOUTH, Direction.SOUTHWEST},
        {Direction.SOUTHWEST},
        {Direction.SOUTHWEST, Direction.WEST},
        {Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST},
        {Direction.WEST, Direction.NORTHWEST},
        {Direction.NORTHWEST},
        {Direction.NORTHWEST, Direction.NORTH},
        {Direction.NORTHWEST, Direction.NORTH, Direction.NORTHEAST},
        {Direction.NORTH, Direction.NORTHEAST},
        {Direction.NORTHEAST},
        {Direction.NORTHEAST, Direction.EAST},
        {Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST},
        {Direction.EAST, Direction.SOUTHEAST}
    };
    int outerRingIndex = 0;
    int forceInnerWallTakeoffAt = INNER_WALL_FORCE_TAKEOFF_DEFAULT;
    boolean currentlyInInnerWall = false;
    int pauseDigAndWaitForAllyToPass = 0;

    // class variables used by aggressive landscapers:
    boolean aggressive = false;
    boolean wallProxy = false;
    MapLocation enemyHQLocation = null;


    public Landscaper(RobotController rc) throws GameActionException {
        super(rc);
        System.out.println(myLocation);
        bornTurn = rc.getRoundNum();

        construct();
    }

    private void construct() throws GameActionException {
        nearbyBotsMap = new HashMap<>();
        updateNearbyBots();

        // scan for d.school location
        for (Direction dir : directions) {                   // Marginally cheaper than sensing in radius 2
            MapLocation t = myLocation.add(dir);
            if (nearbyBotsMap.containsKey(t) && nearbyBotsMap.get(t).type.equals(RobotType.DESIGN_SCHOOL) && nearbyBotsMap.get(t).team.equals(allyTeam)) {
                baseLocation = t;
                break;
            }
        }
        if (baseLocation != null) {
            System.out.println("Found my d.school: " + baseLocation.toString());
        }

        // scan for HQ location
        hqLocation = null;
        holdPositionLoc = null;
        wallPhase = 0;
        hqLocation = checkForLocationMessage();
        defensive = myLocation.distanceSquaredTo(hqLocation) <= 36; // arbitrary cutoff, but should be more than big enough.
        // TODO: check if i am a terraformer
        if (defensive) {
            innerWallFillOrder = computeInnerWallFillOrder(hqLocation, baseLocation);
            System.out.println("I am a defensive landscaper. Found our HQ at " + hqLocation.toString());
            updateHoldPositionLoc();
            //System.out.println("My hold position location: " + holdPositionLoc.toString());
        }
        else {
            System.out.println("I am far from my HQ");
            MapLocation[] enemyHQCandidateLocs = {
                new MapLocation(rc.getMapWidth() - hqLocation.x - 1, hqLocation.y),
                new MapLocation(rc.getMapWidth() - hqLocation.x - 1, rc.getMapHeight() - hqLocation.y - 1),
                new MapLocation(hqLocation.x, rc.getMapHeight() - hqLocation.y - 1)
            };
            for (MapLocation enemyHQCandidateLoc : enemyHQCandidateLocs) {
                if (rc.canSenseLocation(enemyHQCandidateLoc)) {
                    System.out.println("I am an aggressive landscaper");
                    aggressive = true;
                    enemyHQLocation = enemyHQCandidateLoc;
                    if (enemyHQLocation.isAdjacentTo(myLocation)) {
                        System.out.println("I am in the enemy wall :o");
                        wallProxy = true;
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void run() throws GameActionException {
        super.run();

        updateNearbyBots();

        if(rc.getRoundNum()-bornTurn==5) {
            readMessages();
        }

        if (defensive) {
            defense();
        }
        else if (aggressive) {
            aggro();
        }
        else if (terraformer) {
            terraform();
        }
        else {
            construct();
        }
    }

    public void terraform() throws GameActionException {
        System.out.println("Terraform");
        // if (plotCenter == null) {
        //     if (checkIfValidPlot()) {
        //         plotCenter = myLocation;
        //     }
        //     else {

        //     }
        // }
        // if (plotCenter != null) {

        // }
    }

    public boolean checkIfValidPlot() throws GameActionException { // TODO: this is extremely naive
        for (Direction d : directions) {
            if (!rc.canMove(d)) {
                return false;
            }
        }
        return true;
    }

    public void aggro() throws GameActionException {
        // update d.school location
        baseLocation = null;
        for (Direction dir : directions) {                   // Marginally cheaper than sensing in radius 2
            MapLocation t = myLocation.add(dir);
            if (nearbyBotsMap.containsKey(t) && nearbyBotsMap.get(t).type.equals(RobotType.DESIGN_SCHOOL) && nearbyBotsMap.get(t).team.equals(allyTeam)) {
                baseLocation = t;
                break;
            }
        }

        Direction enemyHQDir = myLocation.directionTo(enemyHQLocation);

        if (rc.getDirtCarrying() == 0) { // dig
            if (baseLocation != null && rc.canDigDirt(myLocation.directionTo(baseLocation))) { // heal d.school
                System.out.println("Digging from d.school at " + baseLocation.toString());
                tryDig(myLocation.directionTo(baseLocation));
            }
            else {
                if (rc.senseElevation(myLocation.add(Direction.CENTER)) < rc.senseElevation(myLocation.add(enemyHQDir.opposite()))) {
                    if (rc.canDigDirt(enemyHQDir.opposite()) && !isAdjacentToWater(myLocation.add(enemyHQDir.opposite())) && notTrappingAlly(enemyHQDir.opposite())) {
                        System.out.println("Digging in direction " + enemyHQDir.opposite().toString());
                        tryDig(enemyHQDir.opposite());
                    }
                }
                else {
                    System.out.println("Digging under myself");
                    tryDig(Direction.CENTER);
                }
            }
        }
        else {
            System.out.println("Depositing under enemy HQ at " + myLocation.directionTo(enemyHQLocation));
            tryDeposit(enemyHQDir);
        }
    }

    public boolean notTrappingAlly(Direction d) throws GameActionException {
        MapLocation t = myLocation.add(d);
        if (nearbyBotsMap.containsKey(t) && nearbyBotsMap.get(t).team.equals(allyTeam)) {
            pauseDigAndWaitForAllyToPass++;
            return pauseDigAndWaitForAllyToPass >= 2;
        }
        else {
            pauseDigAndWaitForAllyToPass = 0;
            return true;
        }
    }

    public boolean isAdjacentToWater(MapLocation t) throws GameActionException {
        // is this location adjacent to water to the best of my knowledge
        for (Direction d : directionsWithCenter) {
            if (rc.canSenseLocation(t.add(d)) && rc.senseFlooding(t.add(d))) {
                return true;
            }
        }
        return false;
    }

    public void defense() throws GameActionException {
        Direction hqDir = myLocation.directionTo(hqLocation);
        int hqDist = myLocation.distanceSquaredTo(hqLocation);

        // TODO: If we start exceeding bytecode limits, investigate ways to not do these two functions every turn.
        updateHoldPositionLoc();
        checkWallStage();

        for (Direction d : directions) {// zeroth priority: kill an an enemy building
            if (nearbyBotsMap.containsKey(myLocation.add(d))) {
                RobotInfo botInfo = nearbyBotsMap.get(myLocation.add(d));
                if (botInfo.team.equals(enemyTeam) && (botInfo.type.equals(RobotType.DESIGN_SCHOOL) || botInfo.type.equals(RobotType.FULFILLMENT_CENTER) || botInfo.type.equals(RobotType.NET_GUN))) {
                    if (rc.getDirtCarrying() > 0) {
                        System.out.println("Dumping dirt on enemy building at " + botInfo.location);
                        if (tryDeposit(d)) {
                            return;
                        }
                    }
                    else {
                        System.out.println("Attempting to gather dirt in an emergency to kill the enemy building");
                        if (tryDig(d.opposite())) { // TODO: should we allow gathering dirt from anywhere?
                            return;
                        }
                    }
                }
            }
        }
        if (!myLocation.equals(holdPositionLoc)) { // first priotiy: path to holdPositionLoc
            System.out.println("Pathing towards my holdPositionLoc: " + holdPositionLoc.toString());
            path(holdPositionLoc);
        }
        else { // i have already reached my position in the turtle, and can now do the dirty work
            if (wallPhase < 2) { // i am an inner landscaper
                if (rc.canDigDirt(hqDir)) { // first priority: heal HQ
                    System.out.println("Healing HQ");
                    tryDig(hqDir);
                }
                else if (rc.getDirtCarrying() < RobotType.LANDSCAPER.dirtLimit) { // dig dirt
                    boolean foundDigSite = false;
                    int hqElevation = rc.senseElevation(hqLocation);
                    for (Direction d : directions) { // dig down after killing an enemy rush building (empty inner wall tile with elev > HQ)
                        if (!rc.onTheMap(hqLocation.add(d))) {
                            continue;
                        }
                        if (rc.getRoundNum() < forceInnerWallTakeoffAt && hqLocation.add(d).isAdjacentTo(myLocation) && !hqLocation.add(d).equals(myLocation) && !nearbyBotsMap.containsKey(hqLocation.add(d)) && rc.senseElevation(hqLocation.add(d)) > 0.5*(rc.senseElevation(hqLocation) + rc.senseElevation(hqLocation.add(d).add(d)))) {
                            foundDigSite = true;
                            System.out.println("Digging from pile in direction " + myLocation.directionTo(hqLocation.add(d)));
                            tryDig(myLocation.directionTo(hqLocation.add(d)));
                        }
                    }
                    if (!foundDigSite) {
                        Direction digDir = hqDir.opposite();
                        if (hqDist == 2) {
                            digDir = hqDir.rotateRight().rotateRight();
                        }
                        if (!rc.canDigDirt(digDir)) {
                            digDir = digDir.rotateRight();
                        }
                        if (notTrappingAlly(digDir)) {
                            System.out.println("Digging from designated dig-site " + digDir.toString());
                            if (!tryDig(digDir)) {
                                System.out.println("Can't dig...");
                            }
                        }
                    }
                }
                else if (wallPhase == 0) { // inner wall not yet complete; deposit under yourself
                    boolean foundDumpSite = false;
                    int hqElevation = rc.senseElevation(hqLocation);
                    for (Direction d : directions) { // dig down after killing an enemy rush building (empty inner wall tile with elev > HQ)
                        if (!rc.onTheMap(hqLocation.add(d))) {
                            continue;
                        }
                        if (rc.getRoundNum() < forceInnerWallTakeoffAt && hqLocation.add(d).isAdjacentTo(myLocation) && !hqLocation.add(d).equals(myLocation) && !nearbyBotsMap.containsKey(hqLocation.add(d)) && rc.senseElevation(hqLocation.add(d)) < 0.5*(rc.senseElevation(hqLocation) + rc.senseElevation(hqLocation.add(d).add(d)))) {
                            foundDumpSite = true;
                            System.out.println("Dumping to pit in direction " + myLocation.directionTo(hqLocation.add(d)));
                            tryDeposit(myLocation.directionTo(hqLocation.add(d)));
                        }
                    }
                    if (!foundDumpSite) {
                        System.out.println("Dumping dirt under myself");
                        tryDeposit(Direction.CENTER);
                    }
                }
                else { // inner wall tight; distribute to the lowest point of the inner wall around it
                    Direction dump = Direction.CENTER;
                    int height = rc.senseElevation(myLocation.add(dump));
                    MapLocation candidateDumpLoc = myLocation.add(hqDir.rotateLeft());
                    if (rc.canSenseLocation(candidateDumpLoc) && rc.senseElevation(candidateDumpLoc) < height) { // check rotate left
                        dump = hqDir.rotateLeft();
                        height = rc.senseElevation(candidateDumpLoc);
                    }
                    candidateDumpLoc = myLocation.add(hqDir.rotateRight());
                    if (rc.canSenseLocation(candidateDumpLoc) && rc.senseElevation(candidateDumpLoc) < height) { // check rotate right
                        dump = hqDir.rotateRight();
                        height = rc.senseElevation(candidateDumpLoc);
                    }
                    if (hqDist == 1) {
                        candidateDumpLoc = myLocation.add(hqDir.rotateLeft().rotateLeft());
                        if (rc.canSenseLocation(candidateDumpLoc) && rc.senseElevation(candidateDumpLoc) < height) { // check rotate left
                            dump = hqDir.rotateLeft().rotateLeft();
                            height = rc.senseElevation(candidateDumpLoc);
                        }
                        candidateDumpLoc = myLocation.add(hqDir.rotateRight().rotateRight());
                        if (rc.canSenseLocation(candidateDumpLoc) && rc.senseElevation(candidateDumpLoc) < height) { // check rotate right
                            dump = hqDir.rotateRight().rotateRight();
                            height = rc.senseElevation(candidateDumpLoc);
                        }
                    }
                    System.out.println("Dumping dirt in direction " + dump.toString());
                    tryDeposit(dump);
                }
            }
            else if (wallPhase == 2) { // i am an outer landscaper
                if (rc.getDirtCarrying() == 0) { // dig dirt.  Note that outer landscapers keep their dirt at 0 or 1 while inner landscapers keep their dirt maximized.
                    // TODO: handle the case where we can't dig where we want to because of buildings, e.g. enemy net guns.
                    for (Direction d : outerRingDig[outerRingIndex]) {
                        System.out.println("Attempting to dig from direction " + d);
                        if (tryDig(d)) {
                            break;
                        }
                    }
                }
                else if (rc.senseElevation(myLocation) > -10 && (rc.getRoundNum() < INNER_WALL_FORCE_TAKEOFF_DEFAULT || GameConstants.getWaterLevel(rc.getRoundNum() + 3) >= rc.senseElevation(myLocation))) { // deposit under myself if i am not in a dig site and either the inner wall hasn't been force-closed yet or i'm about to die
                    System.out.println("Dumping dirt under myself");
                    tryDeposit(Direction.CENTER);
                }
                else {
                    Direction dumpDir = outerRingDeposit[outerRingIndex][0];
                    int minElev = 50000;
                    for (Direction d : outerRingDeposit[outerRingIndex]) {
                        if (rc.senseElevation(myLocation.add(d)) < minElev) {
                            dumpDir = d;
                            minElev = rc.senseElevation(myLocation.add(d));
                        }
                    }
                    System.out.println("Dumping dirt in direction " + dumpDir.toString());
                    tryDeposit(dumpDir);
                }
            }
        }
    }

    boolean tryDeposit(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDepositDirt(dir)) {
            rc.depositDirt(dir);
            if (dir.equals(Direction.CENTER)) {
                rc.setIndicatorDot(myLocation, 150, 160, 110);
            }
            else {
                rc.setIndicatorLine(myLocation, myLocation.add(dir), 150, 160, 110);
            }
            return true;
        } else {
            return false;
        }
    }

    boolean tryDig(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDigDirt(dir)) {
            rc.digDirt(dir);
            if (dir.equals(Direction.CENTER)) {
                rc.setIndicatorDot(myLocation, 250, 250, 250);
            }
            else {
                rc.setIndicatorLine(myLocation, myLocation.add(dir), 250, 250, 250);
            }
            return true;
        } else {
            return false;
        }
    }

    void updateNearbyBots() throws GameActionException {
        nearbyBots = rc.senseNearbyRobots();
        // System.out.println("Bots around me: ");
        nearbyBotsMap.clear();
        forceInnerWallTakeoffAt = INNER_WALL_FORCE_TAKEOFF_DEFAULT;
        for (RobotInfo botInfo : nearbyBots) {
            nearbyBotsMap.put(botInfo.location, botInfo);
            // System.out.println(botInfo);
            if (botInfo.team.equals(enemyTeam) && (botInfo.type.equals(RobotType.MINER) || botInfo.type.equals(RobotType.LANDSCAPER))) {
                forceInnerWallTakeoffAt = INNER_WALL_FORCE_TAKEOFF_CONTESTED;
            }
        }
    }

    void checkWallStage() throws GameActionException {
        if (wallPhase < 2) {
            int numInnerWallOurs = 0; // number of OUR LANSCAPERS in the inner wall (not counting current robot if applicable)
            int numInnerWall = 0; // number of ALL ROBOTS in the inner wall (not counting current robot if applicable)
            int numInnerWallSpots = 0;
            for (Direction dir : directions) {
                if (rc.onTheMap(hqLocation.add(dir))) {
                    numInnerWallSpots++;
                    if (nearbyBotsMap.containsKey(hqLocation.add(dir))) {
                        numInnerWall++;
                        RobotInfo botInfo = nearbyBotsMap.get(hqLocation.add(dir));
                        if (botInfo.type.equals(RobotType.LANDSCAPER) && botInfo.team.equals(allyTeam)) {
                            numInnerWallOurs++;
                        }
                    }
                }
            }
            wallPhase = 0;
            if (currentlyInInnerWall && numInnerWallOurs == numInnerWallSpots-1 && holdPositionLoc != null && myLocation.equals(holdPositionLoc)) {
                System.out.println("I see that the inner wall is tight!");
                wallPhase = 1;
            }
            else if (numInnerWall == numInnerWallSpots-1 && holdPositionLoc != null && currentlyInInnerWall && myLocation.equals(holdPositionLoc) && rc.getRoundNum() > 300) { // TODO: important constant round num 300
                System.out.println("The inner wall is full, including some enemies.  Trying to close it off right now.");
                wallPhase = 1;
            }
            else if (currentlyInInnerWall && rc.getRoundNum() > forceInnerWallTakeoffAt) {
                System.out.println("It's round " + Integer.toString(forceInnerWallTakeoffAt) + " and about time to force the inner wall up even if it's not closed.");
                wallPhase = 1;
            }
            else if (numInnerWall == numInnerWallSpots || (rc.getRoundNum() > forceInnerWallTakeoffAt && !currentlyInInnerWall)) {
                System.out.println("The inner wall is already full.  So I am an outer landscaper.");
                wallPhase = 2;
            }
        }
        System.out.println("Wall phase: " + Integer.toString(wallPhase));
    }

    Direction[] computeInnerWallFillOrder(MapLocation hqLoc, MapLocation dSchoolLoc) {
        Direction[] lDir = new Direction[8];
        Direction hqToD = hqLoc.directionTo(dSchoolLoc);
        lDir[7] = hqToD; //always build away from d.school first
        //case 1: Underneath
        if(directionToInt(hqToD)%2==0) {
            lDir[2] = lDir[7].rotateRight();
            lDir[5] = lDir[2].rotateRight();
            lDir[0] = lDir[5].rotateRight();
            lDir[4] = lDir[0].rotateRight();
            lDir[3] = lDir[4].rotateRight();
            lDir[1] = lDir[3].rotateRight();
            lDir[6] = lDir[1].rotateRight();
        } else {
            //case 2: diagonal
            lDir[1] = lDir[7].rotateRight();
            lDir[3] = lDir[1].rotateRight();
            lDir[4] = lDir[3].rotateRight();
            lDir[0] = lDir[4].rotateRight();
            lDir[5] = lDir[0].rotateRight();
            lDir[2] = lDir[5].rotateRight();
            lDir[6] = lDir[2].rotateRight();
        }
        return lDir;
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
            if (allyMessage(msg[0])) {
                if(getSchema(msg[0])==6) {
                    TerraformMessage t = new TerraformMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum);
                    if(t.type==1) {
                        System.out.println("[i] YAY, I'm A TERRAFORMER!");
                        terraformer = true;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    void updateHoldPositionLoc() throws GameActionException {
        if (wallPhase < 2) {
            holdPositionLoc = null;
            boolean enemyInWall = false;
            currentlyInInnerWall = false;
            for (Direction dir : innerWallFillOrder) {
                MapLocation t = hqLocation.add(dir);
                if (!rc.onTheMap(t)) {
                    continue;
                }
                if (holdPositionLoc == null && !nearbyBotsMap.containsKey(t)) { // find the first empty spot in the fill order
                    if (rc.canSenseLocation(t) && rc.senseElevation(t) >= rc.senseElevation(myLocation) - 6 && rc.senseElevation(t) <= rc.senseElevation(myLocation) + 6) {
                        holdPositionLoc = t;
                    }
                }
                if (t.equals(myLocation)) {
                    currentlyInInnerWall = true;
                }
                if (nearbyBotsMap.containsKey(t) && nearbyBotsMap.get(t).team.equals(enemyTeam)) {
                    enemyInWall = true;
                }
            }
            if (enemyInWall && currentlyInInnerWall) { // emergency override: if enemy is spotted in the wall and i'm already in the wall, just hold there
                holdPositionLoc = myLocation;
            }
        }
        if (wallPhase >= 2 || holdPositionLoc == null) {
            boolean amInOuterRing = false;
            for (int i = 0; i < 16; i++) {
                if (hqLocation.add(outerRing[i][0]).add(outerRing[i][1]).equals(myLocation))  {
                    outerRingIndex = i;
                    System.out.println("I'm already in the outer ring.");
                    holdPositionLoc = myLocation;
                    amInOuterRing = true;
                    break;
                }
            }
            // if (rc.senseElevation(myLocation) < -5 || rc.senseElevation(myLocation) > 10) { // I am in a pit/hill, just stay
            //     holdPositionLoc = myLocation;
            //     for (int i = 0; i < 16; i++) { // update outerRingIndex
            //         if (hqLocation.add(outerRing[i][0]).add(outerRing[i][1]).equals(myLocation)) {
            //             outerRingIndex = i;
            //         }
            //     }
            //     // TODO: there's a random edge case where a landscaper gets stuck in a pit/hill but is not part of the outer wall.
            //     // I don't handle this for now.
            // }
            if (!amInOuterRing) {
                holdPositionLoc = hqLocation.add(outerRing[outerRingIndex][0]).add(outerRing[outerRingIndex][1]);
                while (!rc.onTheMap(holdPositionLoc) || nearbyBotsMap.containsKey(holdPositionLoc) || (rc.canSenseLocation(holdPositionLoc) && (rc.senseElevation(holdPositionLoc) < -5 || rc.senseElevation(holdPositionLoc) > 10))) {
                    // if the holdposition is off the map or occupied or is a pit/hill that we likely can't path to, then try the next holdposition in the ring
                    outerRingIndex = (outerRingIndex + 1) % 16;
                    holdPositionLoc = hqLocation.add(outerRing[outerRingIndex][0]).add(outerRing[outerRingIndex][1]);
                }
            }
        }
    }
}
