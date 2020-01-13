package turtle;

import battlecode.common.*;

import java.util.Arrays;
import java.util.Comparator;

public abstract class Robot {

    RobotController rc;
    final int MAX_SQUARED_DISTANCE = Integer.MAX_VALUE;

    /* constant for each game */
    Direction[] directions = {Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST};
    Direction[] directionsWithCenter = {Direction.CENTER, Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST};
    RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL, RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};
    Team allyTeam;
    Team enemyTeam;
    int teamNum;
    int myId;
    final int MAP_WIDTH;
    final int MAP_HEIGHT;
    final int messageModulus=2;
    final int messageFrequency=5;
    //discretized grid for communicating map information
    final int squareWidth = 4;    //number of cells wide per tile
    final int squareHeight = 4;   //number of cells tall per tile
    final int numRows;
    final int numCols;

    /* updated per turn */
    MapLocation myLocation;

    public Robot(RobotController robotController) throws GameActionException {
        rc = robotController;
        allyTeam = rc.getTeam();
        enemyTeam = allyTeam == Team.A ? Team.B : Team.A;
        teamNum = allyTeam == Team.A ? 0 : 1;
        myId = rc.getID();
        myLocation = rc.getLocation();
        MAP_WIDTH = rc.getMapWidth();
        MAP_HEIGHT = rc.getMapHeight();
        numRows = (MAP_HEIGHT+squareHeight-1)/squareHeight;
        numCols = (MAP_WIDTH+squareWidth-1)/squareWidth;
    }

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

     /**
     * Grid used for communication
     * discretization.
     */
     MapLocation getCenterFromTileNumber(int tnum) throws GameActionException {
         int col = tnum % numCols;
         int row = tnum / numCols;
         int centerx = Math.min(squareWidth*col + squareWidth/2, MAP_WIDTH-1);
         int centery = Math.min(squareHeight*row + squareHeight/2, MAP_HEIGHT-1);
         MapLocation centerLoc = new MapLocation(centerx, centery);
         return centerLoc;
     }

     MapLocation getGridCenter(MapLocation loc) throws GameActionException {
         int centerx = loc.x/squareWidth;
         int centery = loc.y/squareHeight;
         MapLocation centerLoc = new MapLocation(
                                Math.min(squareWidth*centerx + squareWidth/2, MAP_WIDTH-1),
                                Math.min(squareHeight*centery + squareHeight/2, MAP_HEIGHT-1));
         return centerLoc;
     }

     MapLocation getGridCenterFromTileNumberNoBoundaries(int tnum) throws GameActionException {
         int col = tnum % numCols;
         int row = tnum / numCols;
         int centerx = squareWidth*col + squareWidth/2;
         int centery = squareHeight*row + squareHeight/2;
         MapLocation centerLoc = new MapLocation(centerx, centery);
         return centerLoc;
     }

     MapLocation[] getAllCellsFromTileNumber(int tnum) throws GameActionException {
         MapLocation[] allLocs = new MapLocation[squareWidth*squareHeight];
         MapLocation center = getGridCenterFromTileNumberNoBoundaries(tnum);
         for (int i=0; i<squareWidth; i++) {
             for(int j=0; j<squareHeight; j++) {
                 MapLocation newLoc = new MapLocation(
                        Math.min(center.x-squareWidth/2+i, MAP_WIDTH-1),
                        Math.min(center.y-squareHeight/2+j, MAP_HEIGHT-1));
                 allLocs[i*squareWidth+j] = newLoc;
             }
         }
         return allLocs;
     }

     //TODO: Better, easily invertible function
    int soupToPower(int soupAmount) {
        return Math.min((soupAmount+99)/100, 63);
    }

    int getTileNumber(MapLocation loc) throws GameActionException {
        MapLocation centerLoc = getGridCenter(loc);
        int xnum = (centerLoc.x - squareWidth/2)/squareWidth;
        int ynum = (centerLoc.y - squareHeight/2)/squareHeight;
        if(centerLoc.x == MAP_WIDTH-1) {
            xnum = numCols-1;
        }
        if(centerLoc.y == MAP_HEIGHT-1) {
            ynum = numRows-1;
        }
        return ynum*(numCols) + xnum;
    }

    boolean sendMessage(int[] message, int bid) throws GameActionException {
        if(message.length>GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH) {
            return false;
        }
        if (rc.canSubmitTransaction(message, bid)) {
            rc.submitTransaction(message, bid);
            return true;
        } else {
            return false;
        }
    }

    boolean tryBuildIfNotPresent(RobotType type, Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canBuildRobot(type, dir)) {
            if(!existsNearbyAllyOfType(type)) {
                rc.buildRobot(type, dir);
            }
            return true;
        }
        else {
            return false;
        }
    }

    boolean existsNearbyAllyOfType(RobotType type) throws GameActionException {
        RobotInfo[] nearbyBots = rc.senseNearbyRobots();
        for (RobotInfo botInfo : nearbyBots) {
            if (botInfo.type.equals(type) && botInfo.team.equals(allyTeam)) {
                return true;
            }
        }
        return false;
    }

    boolean existsNearbyEnemy() throws GameActionException {
        RobotInfo[] nearbyBots = rc.senseNearbyRobots();
        for (RobotInfo botInfo : nearbyBots) {
            if (botInfo.team.equals(enemyTeam)) {
                return true;
            }
        }
        return false;
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
        // //System.out.println(rc.getRoundMessages(turnCount-1));
    }
}
