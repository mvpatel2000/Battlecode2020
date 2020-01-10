package sprint;

import java.util.*;
import battlecode.common.*;

// TODO: replace all actions with try

public class Landscaper extends Unit {

    boolean defensive;
    Map<MapLocation, RobotInfo> nearbyBotsMap;
    RobotInfo[] nearbyBots;

    // class variables used specifically by defensive landscapers:
    MapLocation baseLocation;
    int wallPhase;
    MapLocation dSchoolLocation;
    MapLocation holdPositionLoc;

    public Landscaper(RobotController rc) throws GameActionException {
        super(rc);
        System.out.println(myLocation);

        holdPositionLoc = null; // this is important; used in updateNearbyBots() to prevent circular reasoning
        nearbyBotsMap = new HashMap<>();
        updateNearbyBots();

        // scan for d.school location
        for (Direction dir : directions) {                   // Marginally cheaper than sensing in radius 2
            MapLocation t = myLocation.add(dir);
            if (nearbyBotsMap.containsKey(t) && nearbyBotsMap.get(t).type.equals(RobotType.DESIGN_SCHOOL)) {
                dSchoolLocation = t;
                break;
            }
        }
        System.out.println("Found my d.school: " + dSchoolLocation.toString());

        // scan for HQ location
        baseLocation = null;
        holdPositionLoc = null;
        wallPhase = 0;
        int hqID = rc.getTeam().equals(Team.valueOf("A")) ? 0 : 1;
        defensive = rc.canSenseRobot(hqID);
        if (defensive) {
            RobotInfo baseInfo = rc.senseRobot(hqID);
            baseLocation = baseInfo.location;
            System.out.println("I am a defensive landscaper. Found our HQ:");
            System.out.println(baseInfo);

            updateHoldPositionLoc();
            System.out.println("My hold position location: ");
            System.out.println(holdPositionLoc);
        }
        else {
            System.out.println("I am an offensive landscaper");
        }
    }

    @Override
    public void run() throws GameActionException {
        super.run();
        
        updateNearbyBots();

        if (defensive) {
            Direction hqDir = myLocation.directionTo(baseLocation);
            int baseDist = myLocation.distanceSquaredTo(baseLocation);
            updateHoldPositionLoc();
            checkWallStage();

            if (!myLocation.equals(holdPositionLoc)) { // first priotiy: path to holdPositionLoc
                System.out.println("Pathing towards my holdPositionLoc: " + holdPositionLoc.toString());
                path(holdPositionLoc);
            }
            else if (rc.canDigDirt(hqDir)) { // second priority: heal HQ
                System.out.println("Healing HQ");
                tryDig(hqDir);
            }
            else if (rc.getDirtCarrying() < RobotType.LANDSCAPER.dirtLimit) { // third priority: dig dirt
                Direction digDir = hqDir.opposite();
                if (baseDist == 2) {
                    digDir = hqDir.rotateRight().rotateRight();
                }
                if (!rc.canDigDirt(digDir)) {
                    digDir = digDir.rotateRight();
                }
                System.out.println("Digging dirt from direction " + digDir.toString());
                tryDig(digDir);
            }
            else if (wallPhase == 0) { // inner wall not yet complete; deposit under yourself
                tryDeposit(Direction.CENTER);
                System.out.println("Dumping dirt under myself");
            }
            else { // inner wall tight; distribute around it
                Direction dump = Direction.CENTER;
                int height = rc.senseElevation(myLocation.add(dump));
                if(rc.senseElevation(myLocation.add(hqDir.rotateLeft())) < height) {
                    dump = hqDir.rotateLeft();
                    height = rc.senseElevation(myLocation.add(hqDir.rotateLeft()));
                }
                if(rc.senseElevation(myLocation.add(hqDir.rotateRight())) < height) {
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
                tryDeposit(dump);
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
        int numInnerWall = 0;
        for (Direction dir : directions) {
            if (nearbyBotsMap.containsKey(baseLocation.add(dir))) {
                RobotInfo botInfo = nearbyBotsMap.get(baseLocation.add(dir));
                if (botInfo.type.equals(RobotType.LANDSCAPER) && botInfo.team.equals(allyTeam)) {
                    numInnerWall++;
                }
            }
        }
        if (numInnerWall == 7 && holdPositionLoc != null && myLocation.equals(holdPositionLoc)) {
            System.out.println("I see that the inner wall is tight!");
            wallPhase = 1;
        }
    }

    void updateHoldPositionLoc() throws GameActionException {
        holdPositionLoc = baseLocation.add(baseLocation.directionTo(myLocation));
        int maxDist = holdPositionLoc.distanceSquaredTo(dSchoolLocation);
        for (Direction dir : directions) {
            MapLocation t = baseLocation.add(dir);
            if (!nearbyBotsMap.containsKey(t)) {
                int d = t.distanceSquaredTo(dSchoolLocation);
                if (d > maxDist) {
                    maxDist = d;
                    holdPositionLoc = t;
                }
            }
        }
    }
}
