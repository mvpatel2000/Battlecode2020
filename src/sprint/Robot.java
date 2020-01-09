package sprint;

import battlecode.common.*;

import java.util.Arrays;
import java.util.Comparator;

public abstract class Robot {

    RobotController rc;
    final int MAX_SQUARED_DISTANCE = Integer.MAX_VALUE;

    /* constant for each game */
    Direction[] directions = {Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST};
    Direction[] directionsWithCenter = {Direction.CENTER, Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST};

    RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};
    Team allyTeam;
    Team enemyTeam;
    int myId;

    /* updated per turn */
    int turnCount;
    MapLocation myLocation;


    public Direction toward(MapLocation me, MapLocation dest) {
        switch (Integer.compare(me.x, dest.x) + 3 * Integer.compare(me.y, dest.y)) {
            case -4:
                return Direction.NORTHEAST;
            case -3:
                return Direction.NORTH;
            case -2:
                return Direction.NORTHWEST;
            case -1:
                return Direction.EAST;
            case 0:
                return Direction.CENTER;
            case 1:
                return Direction.WEST;
            case 2:
                return Direction.SOUTHEAST;
            case 3:
                return Direction.SOUTH;
            case 4:
                return Direction.SOUTHWEST;
            default:
                return null;
        }
    }

    public Direction intToDirection(int i) {
        switch (i) {
            case 0:
                return Direction.NORTH;
            case 1:
                return Direction.NORTHEAST;
            case 2:
                return Direction.EAST;
            case 3:
                return Direction.SOUTHEAST;
            case 4:
                return Direction.SOUTH;
            case 5:
                return Direction.SOUTHWEST;
            case 6:
                return Direction.WEST;
            case 7:
                return Direction.NORTHWEST;
            case 8:
                return Direction.CENTER;
            default:
                return null;
        }
    }

    public int directionToInt(Direction d) {
        switch (d) {
            case NORTH:
                return 0;
            case NORTHEAST:
                return 1;
            case EAST:
                return 2;
            case SOUTHEAST:
                return 3;
            case SOUTH:
                return 4;
            case SOUTHWEST:
                return 5;
            case WEST:
                return 6;
            case NORTHWEST:
                return 7;
            case CENTER:
                return 8;
            default:
                return -1;
        }
    }

    public Robot(RobotController robotController) {
        rc = robotController;
        allyTeam = rc.getTeam();
        enemyTeam = allyTeam == Team.A ? Team.B : Team.A;
        myId = rc.getID();
        myLocation = rc.getLocation();
    }


    public abstract void run() throws GameActionException;

    /**
     * Returns a random Direction.
     *
     * @return a random Direction
     */
    Direction randomDirection() {
        return intToDirection((int) (Math.random() * 8));
    }

    /**
     * Returns a random RobotType spawned by miners.
     *
     * @return a random RobotType
     */
    RobotType randomSpawnedByMiner() {
        return spawnedByMiner[(int) (Math.random() * spawnedByMiner.length)];
    }

    /**
     * Attempts to build a given robot in a given direction.
     *
     * @param type The type of the robot to build
     * @param dir  The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    boolean tryBuild(RobotType type, Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canBuildRobot(type, dir)) {
            rc.buildRobot(type, dir);
            return true;
        } else {
            return false;
        }
    }

    void tryBlockchain() throws GameActionException {
        if (4 < 3) {
            int[] message = new int[10];
            for (int i = 0; i < 10; i++) {
                message[i] = 123;
            }
            if (rc.canSubmitTransaction(message, 10))
                rc.submitTransaction(message, 10);
        }
        // System.out.println(rc.getRoundMessages(turnCount-1));
    }
}
