package sprint;

import java.util.*;
import battlecode.common.*;

// TODO: heal d.school if it is being attacked.  Haven't started this yet; it is straightforward if in lategame.  But it is nontrivial if in early game.

public class Landscaper extends Unit {

    boolean defensive;
    Map<MapLocation, RobotInfo> nearbyBotsMap;
    RobotInfo[] nearbyBots;
    MapLocation baseLocation;

    // class variables used specifically by defensive landscapers:
    MapLocation hqLocation = null;
    int wallPhase;
    MapLocation holdPositionLoc = null; // this is important; used in updateNearbyBots() to prevent circular reasoning
    boolean innerWaller = true;
    Direction[][] outerRing = {{Direction.NORTHWEST, Direction.NORTHWEST}, {Direction.NORTH, Direction.NORTHWEST}, {Direction.NORTH, Direction.NORTH}, {Direction.NORTH, Direction.NORTHEAST}, {Direction.NORTHEAST, Direction.NORTHEAST}, {Direction.EAST, Direction.NORTHEAST}, {Direction.EAST, Direction.EAST}, {Direction.EAST, Direction.SOUTHEAST}, {Direction.SOUTHEAST, Direction.SOUTHEAST}, {Direction.SOUTH, Direction.SOUTHEAST}, {Direction.SOUTH, Direction.SOUTH}, {Direction.SOUTH, Direction.SOUTHWEST}, {Direction.SOUTHWEST, Direction.SOUTHWEST}, {Direction.WEST, Direction.SOUTHWEST}, {Direction.WEST, Direction.WEST}, {Direction.WEST, Direction.NORTHWEST}};
    // Note: Dig pattern assumes we don't have landscapers in the four cardinal directions in the outer ring.  Update if this changes.
    Direction[] outerRingDig = {Direction.NORTHWEST, Direction.EAST, Direction.CENTER, Direction.WEST, Direction.NORTHEAST, Direction.SOUTH, Direction.CENTER, Direction.NORTH, Direction.SOUTHEAST, Direction.WEST, Direction.CENTER, Direction.EAST, Direction.SOUTHWEST, Direction.NORTH, Direction.CENTER, Direction.SOUTH};
    Direction[] outerRingDeposit = {Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTH, Direction.SOUTHWEST, Direction.SOUTHWEST, Direction.WEST, Direction.WEST, Direction.NORTHWEST, Direction.NORTHWEST, Direction.NORTH, Direction.NORTH, Direction.NORTHEAST, Direction.NORTHEAST, Direction.EAST, Direction.EAST, Direction.SOUTHEAST};
    int outerRingIndex = 0;
    int OUTER_RING_TARGET_ELEVATION = 50; // TODO: tweak constant
    int INNER_WALL_FORCE_TAKEOFF = 360;
    boolean currentlyInInnerWall = false;

    // class variables used by aggressive landscapers:
    boolean wallProxy = false;
    MapLocation enemyHQLocation = null;


    public Landscaper(RobotController rc) throws GameActionException {
        super(rc);
        System.out.println(myLocation);

        nearbyBotsMap = new HashMap<>();
        updateNearbyBots();

        // scan for d.school location
        for (Direction dir : directions) {                   // Marginally cheaper than sensing in radius 2
            MapLocation t = myLocation.add(dir);
            if (nearbyBotsMap.containsKey(t) && nearbyBotsMap.get(t).type.equals(RobotType.DESIGN_SCHOOL)) {
                baseLocation = t;
                break;
            }
        }
        System.out.println("Found my d.school: " + baseLocation.toString());

        // scan for HQ location
        hqLocation = null;
        holdPositionLoc = null;
        wallPhase = 0;
        int hqID = rc.getTeam().equals(Team.valueOf("A")) ? 0 : 1;
        defensive = rc.canSenseRobot(hqID);
        if (defensive) {
            RobotInfo hqInfo = rc.senseRobot(hqID);
            hqLocation = hqInfo.location;
            System.out.println("I am a defensive landscaper. Found our HQ:");
            System.out.println(hqInfo);

            updateHoldPositionLoc();
            System.out.println("My hold position location: ");
            System.out.println(holdPositionLoc);
        }
        else {
            System.out.println("I am an offensive landscaper");
            int enemyHQID = 1 - hqID;
            if (rc.canSenseRobot(enemyHQID)) {
                RobotInfo enemyHQInfo = rc.senseRobot(enemyHQID);
                enemyHQLocation = enemyHQInfo.location;
                if (enemyHQLocation.isAdjacentTo(myLocation)) {
                    System.out.println("I am in the enemy wall :o");
                    wallProxy = true;
                }
            }
        }
    }

    @Override
    public void run() throws GameActionException {
        super.run();
        
        updateNearbyBots();

        if (defensive) {
            defense();
        }
        else {
            aggro();
        }
    }

    public void aggro() throws GameActionException {
        // update d.school location
        for (Direction dir : directions) {                   // Marginally cheaper than sensing in radius 2
            MapLocation t = myLocation.add(dir);
            if (nearbyBotsMap.containsKey(t) && nearbyBotsMap.get(t).type.equals(RobotType.DESIGN_SCHOOL)) {
                baseLocation = t;
                break;
            }
        }

        Direction enemyHQDir = myLocation.directionTo(enemyHQLocation);

        if (rc.getDirtCarrying() == 0) { // dig
            if (rc.canDigDirt(myLocation.directionTo(baseLocation))) { // heal d.school
                System.out.println("Digging under d.school at " + baseLocation.toString());
                tryDig(myLocation.directionTo(baseLocation));
            }
            else {
                if (rc.senseElevation(myLocation.add(Direction.CENTER)) < rc.senseElevation(myLocation.add(enemyHQDir.opposite()))) {
                    if (rc.canDigDirt(enemyHQDir.opposite())) {
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

    public void defense() throws GameActionException {
        Direction hqDir = myLocation.directionTo(hqLocation);
        int baseDist = myLocation.distanceSquaredTo(hqLocation);

        // TODO: If we start exceeding bytecode limits, investigate ways to not do these two functions every turn.
        updateHoldPositionLoc();
        checkWallStage();

        
        if (rc.getDirtCarrying() > 0) { // zeroth priority: kill an an enemy building
            for (Direction d : directions) {
                if (nearbyBotsMap.containsKey(myLocation.add(d))) {
                    RobotInfo botInfo = nearbyBotsMap.get(myLocation.add(d));
                    if (botInfo.team.equals(enemyTeam) && (botInfo.type.equals(RobotType.DESIGN_SCHOOL) || botInfo.type.equals(RobotType.FULFILLMENT_CENTER) || botInfo.type.equals(RobotType.NET_GUN))) {
                        System.out.println("Dumping dirt on enemy building at " + botInfo.location);
                        tryDeposit(d);
                        return;
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
                        if (hqLocation.add(d).isAdjacentTo(myLocation) && !nearbyBotsMap.containsKey(hqLocation.add(d)) && rc.senseElevation(hqLocation.add(d)) > rc.senseElevation(hqLocation)) {
                            foundDigSite = true;
                            System.out.println("Digging from pile in direction " + d.toString());
                            tryDig(d);
                        }
                    }
                    if (!foundDigSite) {
                        Direction digDir = hqDir.opposite();
                        if (baseDist == 2) {
                            digDir = hqDir.rotateRight().rotateRight();
                        }
                        if (!rc.canDigDirt(digDir)) {
                            digDir = digDir.rotateRight();
                        }
                        System.out.println("Digging from designated dig-site " + digDir.toString());
                        tryDig(digDir);
                    }
                }
                else if (wallPhase == 0) { // inner wall not yet complete; deposit under yourself
                    System.out.println("Dumping dirt under myself");
                    tryDeposit(Direction.CENTER);
                }
                else { // inner wall tight; distribute to the lowest point of the inner wall around it
                    Direction dump = Direction.CENTER;
                    int height = rc.senseElevation(myLocation.add(dump));
                    if(rc.senseElevation(myLocation.add(hqDir.rotateLeft())) < height) { // check rotate left
                        dump = hqDir.rotateLeft();
                        height = rc.senseElevation(myLocation.add(hqDir.rotateLeft()));
                    }
                    if(rc.senseElevation(myLocation.add(hqDir.rotateRight())) < height) { // check rotate right
                        dump = hqDir.rotateRight();
                        height = rc.senseElevation(myLocation.add(hqDir.rotateRight()));
                    }
                    if (baseDist == 1) {
                        if(rc.senseElevation(myLocation.add(hqDir.rotateLeft().rotateLeft())) < height) {
                            dump = hqDir.rotateLeft().rotateLeft();
                            height = rc.senseElevation(myLocation.add(hqDir.rotateLeft().rotateLeft()));
                        }
                        if(rc.senseElevation(myLocation.add(hqDir.rotateRight().rotateRight())) < height) {
                            dump = hqDir.rotateRight().rotateRight();
                            height = rc.senseElevation(myLocation.add(hqDir.rotateRight().rotateRight()));
                        }
                    }
                    System.out.println("Dumping dirt in direction " + dump.toString());
                    tryDeposit(dump);
                }
            }
            else if (wallPhase == 2) { // i am an outer landscaper
                if (rc.getDirtCarrying() == 0) { // dig dirt.  Note that outer landscapers keep their dirt at 0 or 1 while inner landscapers keep their dirt maximized.
                    // TODO: handle the case where we can't dig where we want to because of buildings, e.g. enemy net guns.
                    System.out.println("Digging dirt from direction " + outerRingDig[outerRingIndex]);
                    tryDig(outerRingDig[outerRingIndex]);
                }
                else if (rc.senseElevation(myLocation) < OUTER_RING_TARGET_ELEVATION) { // deposit under myself if i'm not tall enough yet
                    System.out.println("Dumping dirt under myself");
                    tryDeposit(Direction.CENTER);
                }
                else {
                    System.out.println("Dumping dirt in direction " + outerRingDeposit[outerRingIndex].toString());
                    tryDeposit(outerRingDeposit[outerRingIndex]);
                }
            }
        }
    }

    boolean tryDeposit(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDepositDirt(dir)) {
            rc.depositDirt(dir);
            return true;
        } else {
            return false;
        }
    }

    boolean tryDig(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDigDirt(dir)) {
            rc.digDirt(dir);
            return true;
        } else {
            return false;
        }
    }

    void updateNearbyBots() throws GameActionException {
        nearbyBots = rc.senseNearbyRobots();
        // System.out.println("Bots around me: ");
        nearbyBotsMap.clear();
        for (RobotInfo botInfo : nearbyBots) {
            nearbyBotsMap.put(botInfo.location, botInfo);
            // System.out.println(botInfo);
        }
    }

    void checkWallStage() throws GameActionException {
        int numInnerWallOurs = 0; // number of OUR LANSCAPERS in the inner wall (not counting current robot if applicable)
        int numInnerWall = 0; // number of ENEMY ROBOTS in the inner wall (not counting current robot if applicable)
        for (Direction dir : directions) {
            if (nearbyBotsMap.containsKey(hqLocation.add(dir))) {
                numInnerWall++;
                RobotInfo botInfo = nearbyBotsMap.get(hqLocation.add(dir));
                if (botInfo.type.equals(RobotType.LANDSCAPER) && botInfo.team.equals(allyTeam)) {
                    numInnerWallOurs++;
                }
            }
        }
        if (numInnerWallOurs == 7 && holdPositionLoc != null && myLocation.equals(holdPositionLoc)) {
            System.out.println("I see that the inner wall is tight!");
            wallPhase = 1;
        }
        else if (numInnerWall == 7 && holdPositionLoc != null && currentlyInInnerWall && myLocation.equals(holdPositionLoc) && rc.getRoundNum() > 300) { // TODO: important constant round num 300
            System.out.println("The inner wall is full, including some enemies.  Trying to close it off right now.");
            wallPhase = 1;
        }
        else if (currentlyInInnerWall && rc.getRoundNum() > INNER_WALL_FORCE_TAKEOFF) {
            System.out.println("It's round " + Integer.toString(INNER_WALL_FORCE_TAKEOFF) + " and about time to force the inner wall up even if it's not closed.");
            wallPhase = 1;
        }
        else if (numInnerWallOurs == 8 || (rc.getRoundNum() > INNER_WALL_FORCE_TAKEOFF && !currentlyInInnerWall)) {
            System.out.println("The inner wall is already full.  So I am an outer landscaper.");
            wallPhase = 2;
        }
        System.out.println("Wall phase: " + Integer.toString(wallPhase));
    }

    void updateHoldPositionLoc() throws GameActionException {
        if (wallPhase < 2) {
            holdPositionLoc = hqLocation.add(hqLocation.directionTo(myLocation));
            int maxDist = holdPositionLoc.distanceSquaredTo(baseLocation);
            boolean enemyInWall = false;
            currentlyInInnerWall = false;
            for (Direction dir : directions) {
                MapLocation t = hqLocation.add(dir);
                if (t.equals(myLocation)) {
                    currentlyInInnerWall = true;
                }
                if (!nearbyBotsMap.containsKey(t)) { // find the farthest hold position from d.school
                    int d = t.distanceSquaredTo(baseLocation);
                    if (d > maxDist) {
                        maxDist = d;
                        holdPositionLoc = t;
                    }
                }
                else if (nearbyBotsMap.get(t).team.equals(enemyTeam)) {
                    enemyInWall = true;
                }
            }
            if (enemyInWall && currentlyInInnerWall) { // emergency case: if enemy is spotted in the wall and i'm already in the wall, just hold there
                holdPositionLoc = myLocation;
            }
        }
        else {
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
                while (nearbyBotsMap.containsKey(holdPositionLoc) || (rc.canSenseLocation(holdPositionLoc) && (rc.senseElevation(holdPositionLoc) < -5 || rc.senseElevation(holdPositionLoc) > 10))) {
                    // if the holdposition is occupied or is a pit/hill that we can't path to, then try the next holdposition in the ring
                    outerRingIndex = (outerRingIndex + 1) % 16;
                    holdPositionLoc = hqLocation.add(outerRing[outerRingIndex][0]).add(outerRing[outerRingIndex][1]);
                }
            }
        }
    }
}
