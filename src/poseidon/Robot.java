package poseidon;

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
    Direction[] cardinalCenter = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    Direction[] directionsWithCenter = {Direction.CENTER, Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST};
    Direction[][] dirsClosestTo = {{Direction.NORTH, Direction.NORTHWEST, Direction.NORTHEAST, Direction.WEST, Direction.EAST, Direction.SOUTHWEST, Direction.SOUTHEAST, Direction.SOUTH}, {Direction.NORTHWEST, Direction.WEST, Direction.NORTH, Direction.SOUTHWEST, Direction.NORTHEAST, Direction.SOUTH, Direction.EAST, Direction.SOUTHEAST}, {Direction.WEST, Direction.SOUTHWEST, Direction.NORTHWEST, Direction.SOUTH, Direction.NORTH, Direction.SOUTHEAST, Direction.NORTHEAST, Direction.EAST}, {Direction.SOUTHWEST, Direction.SOUTH, Direction.WEST, Direction.SOUTHEAST, Direction.NORTHWEST, Direction.EAST, Direction.NORTH, Direction.NORTHEAST}, {Direction.SOUTH, Direction.SOUTHEAST, Direction.SOUTHWEST, Direction.EAST, Direction.WEST, Direction.NORTHEAST, Direction.NORTHWEST, Direction.NORTH}, {Direction.SOUTHEAST, Direction.EAST, Direction.SOUTH, Direction.NORTHEAST, Direction.SOUTHWEST, Direction.NORTH, Direction.WEST, Direction.NORTHWEST}, {Direction.EAST, Direction.NORTHEAST, Direction.SOUTHEAST, Direction.NORTH, Direction.SOUTH, Direction.NORTHWEST, Direction.SOUTHWEST, Direction.WEST}, {Direction.NORTHEAST, Direction.NORTH, Direction.EAST, Direction.NORTHWEST, Direction.SOUTHEAST, Direction.WEST, Direction.SOUTH, Direction.SOUTHWEST}};
    RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL, RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};
    Team allyTeam;
    Team enemyTeam;
    int teamNum;
    int myId;
    final int MAP_WIDTH;
    final int MAP_HEIGHT;
    final int HQ_SEARCH = 31;
    final int messageModulus = 2;
    final int messageFrequency = 10;
    //for reading message headers
    final int arbitraryConstant = 64556; //make sure this is the same constant in Message.java
    final int headerLen = 16;
    final int schemaLen = 3;

    public MapLocation HEADQUARTERS_LOCATION = null;
    public MapLocation ENEMY_HQ_LOCATION = null;

    boolean enemyAggression = false;
    int turnAtEnemyAggression = -1;
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
    }

    public Direction[] directionsClosestTo(Direction d) {
        switch (d) {
            case NORTH:
                return dirsClosestTo[0];
            case NORTHEAST:
                return dirsClosestTo[1];
            case EAST:
                return dirsClosestTo[2];
            case SOUTHEAST:
                return dirsClosestTo[3];
            case SOUTH:
                return dirsClosestTo[4];
            case SOUTHWEST:
                return dirsClosestTo[5];
            case WEST:
                return dirsClosestTo[6];
            case NORTHWEST:
                return dirsClosestTo[7];
            default:
                return directions;
        }
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

    public boolean onBoundary(MapLocation t) {
        return t.x == 0 || t.y == 0 || t.x == MAP_WIDTH-1 || t.y == MAP_HEIGHT-1;
    }

    public boolean enemyAggressionCheck() throws GameActionException {
        ////System.out.println("[i] Enemy Aggression Check");
        if(enemyAggression == false) {
            RobotInfo[] nearbyBots = rc.senseNearbyRobots();
            for (RobotInfo botInfo : nearbyBots) {
                if (!botInfo.type.equals(RobotType.DELIVERY_DRONE) && !botInfo.type.equals(RobotType.HQ) && botInfo.team.equals(enemyTeam)) {
                    //System.out.println("[i] Found enemy aggression!");
                    enemyAggression = true;
                }
            }
            if(enemyAggression) {
                RushCommitMessage r = new RushCommitMessage(MAP_HEIGHT, MAP_WIDTH, teamNum, rc.getRoundNum());
                r.writeTypeOfCommit(2); //2 is enemy is attacking/rushing
                if(sendMessage(r.getMessage(), 1)) {
                    enemyAggression = true;
                    //System.out.println("[i] Telling allies enemy is rushing!!!");
                }
            }
        }
        return enemyAggression;
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
                    if (allyMessage(msg[0], i)) {
                        if (getSchema(msg[0]) == 4) {
                            LocationMessage l = new LocationMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum, i);
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
                    if (allyMessage(msg[0], i)) {
                        if (getSchema(msg[0]) == 4) {
                            LocationMessage l = new LocationMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum, i);
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
                    if (allyMessage(msg[0], i)) {
                        if (getSchema(msg[0]) == 4) {
                            LocationMessage l = new LocationMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum, i);
                            if (l.unitType == 1) {
                                ENEMY_HQ_LOCATION = new MapLocation(l.xLoc, l.yLoc);
                            }
                        }
                    }
                }
            }
        }
    }

    public MapLocation initialCheckForWaterLocation() throws GameActionException {
        int rn = rc.getRoundNum();
        int start = 100*(rn/100);
        for (int i = start+1; i<start+5; i++) {
            if (i < rn) {
                Transaction[] msgs = rc.getBlock(i);
                for (Transaction transaction : msgs) {
                    int[] msg = transaction.getMessage();
                    if (allyMessage(msg[0], i)) {
                        if (getSchema(msg[0]) == 4) {
                            LocationMessage l = new LocationMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum, i);
                            if (l.unitType == 2) {
                                MapLocation wl = new MapLocation(l.xLoc, l.yLoc);
                                //System.out.println("[i] Discovered water location");
                                System.out.println(wl);
                                return wl;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public void checkForEnemyHQLocationMessageSubroutine(int[] msg, int roundNum) throws GameActionException {
        LocationMessage l = new LocationMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum, roundNum);
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

    boolean existsNearbyEnemyOfType(RobotType type) throws GameActionException {
        RobotInfo[] nearbyBots = rc.senseNearbyRobots();
        for (RobotInfo botInfo : nearbyBots) {
            if (botInfo.type.equals(type) && botInfo.team.equals(enemyTeam)) {
                return true;
            }
        }
        return false;
    }

    boolean existsNearbyEnemyOfType(RobotType type, int radius) throws GameActionException {
        RobotInfo[] nearbyBots = rc.senseNearbyRobots(radius);
        for (RobotInfo botInfo : nearbyBots) {
            if (botInfo.type.equals(type) && botInfo.team.equals(enemyTeam)) {
                return true;
            }
        }
        return false;
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

    boolean existsNearbyEnemyExcept(RobotType type) throws GameActionException {
        RobotInfo[] nearbyBots = rc.senseNearbyRobots();
        for (RobotInfo botInfo : nearbyBots) {
            if (!botInfo.type.equals(type) && botInfo.team.equals(enemyTeam)) {
                return true;
            }
        }
        return false;
    }

    boolean existsNearbyEnemyBuilding() throws GameActionException {
        RobotInfo[] nearbyBots = rc.senseNearbyRobots();
        for (RobotInfo botInfo : nearbyBots) {
            if (botInfo.team.equals(enemyTeam) && botInfo.type.isBuilding()) {
                return true;
            }
        }
        return false;
    }

    int getHeader(int roundNumber) {
        return Math.floorMod(arbitraryConstant*(teamNum+1)*MAP_HEIGHT*MAP_WIDTH*roundNumber, ((1 << headerLen) - 1));
    }

    boolean allyMessage(int firstInt, int roundNum) throws GameActionException {
        if (firstInt >>> (32 - headerLen) == getHeader(roundNum)) {
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


    int[] xydist(MapLocation a, MapLocation b) {
        int dx = Math.abs(a.x - b.x);
        int dy = Math.abs(a.y - b.y);
        return new int[]{dx, dy};
    }

    MapLocation add(MapLocation a, int[] delta) {
        return new MapLocation(a.x + delta[0], a.y + delta[1]);
    }
}
