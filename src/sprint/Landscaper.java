package sprint;

import battlecode.common.*;

public class Landscaper extends Unit {

    boolean defensive;
    MapLocation baseLocation;

    public Landscaper(RobotController rc) throws GameActionException {
        super(rc);
        System.out.println(myLocation);
        baseLocation = null;
        int hqID = rc.getTeam().equals(Team.valueOf("A")) ? 0 : 1;
        defensive = rc.canSenseRobot(hqID);
        if (defensive) {
            RobotInfo baseInfo = rc.senseRobot(hqID);
            baseLocation = baseInfo.location;
            System.out.println("I am a defensive landscaper. Found our HQ:");
            System.out.println(baseInfo);
        }
        else {
            System.out.println("I am an offensive landscaper");
        }
    }

    @Override
    public void run() throws GameActionException {
        super.run();
        
        Direction hqDir = myLocation.directionTo(baseLocation);

        if (defensive) {
            if (myLocation.distanceSquaredTo(baseLocation) <= 2) {
                if (rc.canDigDirt(hqDir)) {
                    System.out.println("Healing HQ");
                    rc.digDirt(hqDir);
                }
                else if (rc.canDigDirt(hqDir.opposite())) {
                    System.out.println("Digging dirt from direction " + hqDir.toString());
                    rc.digDirt(hqDir.opposite());
                }
                else {
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
                    if (myLocation.distanceSquaredTo(baseLocation) == 1) {
                        if(rc.senseElevation(myLocation.add(hqDir.rotateLeft().rotateLeft())) < height) {
                            dump = hqDir.rotateLeft().rotateLeft();
                            height = rc.senseElevation(myLocation.add(hqDir.rotateLeft().rotateLeft()));
                        }
                        if(rc.senseElevation(myLocation.add(hqDir.rotateRight().rotateRight())) < height) {
                            dump = hqDir.rotateRight().rotateRight();
                            height = rc.senseElevation(myLocation.add(hqDir.rotateRight().rotateRight()));
                        }
                    }
                    if (rc.canDepositDirt(dump)) {
                        System.out.println("Dumping dirt in direction " + dump.toString());
                        rc.depositDirt(dump);
                    }
                }
            }
            else {
                System.out.println("Attempting to move towards " + baseLocation.toString() + " from myLocation, which is " + myLocation.toString());
                fuzzyMoveToLoc(baseLocation);
            }
        }
    }
}
