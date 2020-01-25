package hadesNoAtkDrones;

import battlecode.common.*;

import java.util.Arrays;
import java.util.Comparator;

public abstract class Robot {

    RobotController rc;
    final int MAX_SQUARED_DISTANCE = Integer.MAX_VALUE;

    // final int OUTER_RING_TARGET_ELEVATION = 50; // deprecated
    final int INNER_WALL_FORCE_TAKEOFF_DEFAULT = 460;
    final int INNER_WALL_FORCE_TAKEOFF_CONTESTED = 360;

    /* constant for each game */
    Direction[] directions = {Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST};
    Direction[] cardinal = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    Direction[] directionsWithCenter = {Direction.CENTER, Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST};
    RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL, RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};
    Team allyTeam;
    Team enemyTeam;
    int teamNum;
    int myId;
    final int MAP_WIDTH;
    final int MAP_HEIGHT;
    final int HQ_SEARCH = 31;
    final int messageModulus = 2;
    final int messageFrequency = 5;
    //for reading message headers
    final int arbitraryConstant = 94655; //make sure this is the same constant in Message.java
    final int header;
    final int headerLen = 16;
    final int schemaLen = 3;

    public MapLocation HEADQUARTERS_LOCATION = null;
    public MapLocation ENEMY_HQ_LOCATION = null;

    //discretized grid for communicating map information
    //if changing squareWidth and squareHeight, make sure to change
    //number of bits allocated to tile in HoldProductionMessage and MinePatchMessage and SoupMessage
    //(bitsPerPatch in MinePatchMessage) and
    //(bitsPerTile in HoldProductionMessage) and
    //(bitsPerTile in SoupMessage)
    final int squareWidth = 3;    //number of cells wide per tile
    final int squareHeight = 3;   //number of cells tall per tile
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
        numRows = (MAP_HEIGHT + squareHeight - 1) / squareHeight;
        numCols = (MAP_WIDTH + squareWidth - 1) / squareWidth;
        header = arbitraryConstant * (teamNum + 1) * MAP_HEIGHT * MAP_WIDTH % ((1 << headerLen) - 1);
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

    protected Direction[] getDirections() {
        return directions;
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
     * Communication methods.
     * Generally, most communication methods should go into the specific
     * robotType file. Put communication everyone will use here.
     */
    //Returns MapLocation if it finds a LocationMessage from our HQ.
    //returns null if it doesn't.
    public void checkForLocationMessage() throws GameActionException {
        int rn = rc.getRoundNum();
        for (int i = 1; i <= 3; i++) {
            if (i < rn) {
                Transaction[] msgs = rc.getBlock(i);
                for (Transaction transaction : msgs) {
                    int[] msg = transaction.getMessage();
                    if (allyMessage(msg[0])) {
                        if (getSchema(msg[0]) == 4) {
                            LocationMessage l = new LocationMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum);
                            if (l.unitType == 0) {
                                HEADQUARTERS_LOCATION = new MapLocation(l.xLoc, l.yLoc);
                            }
                        }
                    }
                }
            }
        }
    }

    public void initialCheckForEnemyHQLocationMessage() throws GameActionException {
        int rn = rc.getRoundNum();
        for (int i = 100; i < 105; i++) {
            if (i < rn) {
                Transaction[] msgs = rc.getBlock(i);
                for (Transaction transaction : msgs) {
                    int[] msg = transaction.getMessage();
                    if (allyMessage(msg[0])) {
                        if (getSchema(msg[0]) == 4) {
                            LocationMessage l = new LocationMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum);
                            if (l.unitType == 1) {
                                ENEMY_HQ_LOCATION = new MapLocation(l.xLoc, l.yLoc);
                            }
                        }
                    }
                }
            }
        }
    }

    public void checkForEnemyHQLocationMessage(int howfarback) throws GameActionException {
        int rn = rc.getRoundNum();
        for (int i = rn - howfarback; i < rn; i++) {
            if (i > 0) {
                Transaction[] msgs = rc.getBlock(i);
                for (Transaction transaction : msgs) {
                    int[] msg = transaction.getMessage();
                    if (allyMessage(msg[0])) {
                        if (getSchema(msg[0]) == 4) {
                            LocationMessage l = new LocationMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum);
                            if (l.unitType == 1) {
                                ENEMY_HQ_LOCATION = new MapLocation(l.xLoc, l.yLoc);
                            }
                        }
                    }
                }
            }
        }
    }

    public void checkForEnemyHQLocationMessageSubroutine(int[] msg) throws GameActionException {
        LocationMessage l = new LocationMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum);
        if (l.unitType == 1) {
            ENEMY_HQ_LOCATION = new MapLocation(l.xLoc, l.yLoc);
        }
    }

    /**
     * Grid used for communication
     * discretization.
     */
    MapLocation getCenterFromTileNumber(int tnum) throws GameActionException {
        int col = tnum % numCols;
        int row = tnum / numCols;
        int centerx = Math.min(squareWidth * col + squareWidth / 2, MAP_WIDTH - 1);
        int centery = Math.min(squareHeight * row + squareHeight / 2, MAP_HEIGHT - 1);
        MapLocation centerLoc = new MapLocation(centerx, centery);
        return centerLoc;
    }

    MapLocation getGridCenter(MapLocation loc) throws GameActionException {
        int centerx = loc.x / squareWidth;
        int centery = loc.y / squareHeight;
        MapLocation centerLoc = new MapLocation(
                Math.min(squareWidth * centerx + squareWidth / 2, MAP_WIDTH - 1),
                Math.min(squareHeight * centery + squareHeight / 2, MAP_HEIGHT - 1));
        return centerLoc;
    }

    MapLocation getGridCenterFromTileNumberNoBoundaries(int tnum) throws GameActionException {
        int col = tnum % numCols;
        int row = tnum / numCols;
        int centerx = squareWidth * col + squareWidth / 2;
        int centery = squareHeight * row + squareHeight / 2;
        return new MapLocation(centerx, centery);
    }

    MapLocation[] getAllCellsFromTileNumber(int tnum) throws GameActionException {
        MapLocation[] allLocs = new MapLocation[squareWidth * squareHeight];
        MapLocation center = getGridCenterFromTileNumberNoBoundaries(tnum);
        for (int i = 0; i < squareWidth; i++) {
            for (int j = 0; j < squareHeight; j++) {
                MapLocation newLoc = new MapLocation(
                        Math.min(center.x - squareWidth / 2 + i, MAP_WIDTH - 1),
                        Math.min(center.y - squareHeight / 2 + j, MAP_HEIGHT - 1));
                allLocs[i * squareWidth + j] = newLoc;
            }
        }
        return allLocs;
    }

    //TODO: Better, easily invertible function
    int soupToPower(int soupAmount) {
        if (soupAmount == -1) {
            return HQ_SEARCH;
        }
        return Math.min((soupAmount + 199) / 200, 30); //31 is used for HQ search
    }

    int powerToSoup(int powerAmount) {
        return powerAmount * 200;
    }

    int getTileNumber(MapLocation loc) throws GameActionException {
        MapLocation centerLoc = getGridCenter(loc);
        int xnum = (centerLoc.x - squareWidth / 2) / squareWidth;
        int ynum = (centerLoc.y - squareHeight / 2) / squareHeight;
        if (centerLoc.x == MAP_WIDTH - 1) {
            xnum = numCols - 1;
        }
        if (centerLoc.y == MAP_HEIGHT - 1) {
            ynum = numRows - 1;
        }
        return ynum * (numCols) + xnum;
    }

    boolean sendMessage(int[] message, int bid) throws GameActionException {
        if (message.length > GameConstants.NUMBER_OF_TRANSACTIONS_PER_BLOCK) {
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
            if (!existsNearbyAllyOfType(type)) {
                rc.buildRobot(type, dir);
            }
            return true;
        } else {
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
        return rc.senseNearbyRobots(rc.getCurrentSensorRadiusSquared(), enemyTeam).length > 0;
    }

    boolean allyMessage(int firstInt) throws GameActionException {
        if (firstInt >>> (32 - headerLen) == header) {
            return true;
        } else {
            return false;
        }
    }

    int getSchema(int firstInt) throws GameActionException {
        return (firstInt << headerLen) >>> (32 - schemaLen);
    }

    boolean isAccessible(MapLocation target) throws GameActionException {
        int lastElevation = rc.senseElevation(myLocation);
        MapLocation ptr = myLocation;
        while (ptr.distanceSquaredTo(target) > 2) { // adjacent to tile
            if (!rc.canSenseLocation(ptr) || rc.senseFlooding(ptr))
                return false;
            int elevation = rc.senseElevation(ptr);
            if (Math.abs(elevation - lastElevation) > 3)
                return false;
            lastElevation = elevation;
            ptr = ptr.add(ptr.directionTo(target));
        }
        return true;
    }
}
