package poseidon;

import battlecode.common.*;

import java.util.*;

public class DeliveryDrone extends Unit {

    public static final int START_FERRY = 300;
    public static final int FILL_WALL_ROUND = 600;
    public static final int FILL_OUTER_ROUND = 1000;
    public static final int SHRINK_SHELL_ROUND = 2600;
    private static final int POKE_DURATION = 50;
    private static final int POKE_RADIUS = 64;
    private static final int POSTURE_POKE_TIME = 20;
    private static final int HOLD_CORNER_ROUND = 600;
    long[] waterChecked = new long[64]; // align to top right
    WaterList waterLocations = new WaterList();

    final int[][] SPIRAL_ORDER = {{0, 0}, {-1, 0}, {0, -1}, {0, 1}, {1, 0}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}, {-2, 0}, {0, -2}, {0, 2}, {2, 0}, {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}, {-2, -2}, {-2, 2}, {2, -2}, {2, 2}, {-3, 0}, {0, -3}, {0, 3}, {3, 0}, {-3, -1}, {-3, 1}, {-1, -3}, {-1, 3}, {1, -3}, {1, 3}, {3, -1}, {3, 1}, {-3, -2}, {-3, 2}, {-2, -3}, {-2, 3}, {2, -3}, {2, 3}, {3, -2}, {3, 2}, {-4, 0}, {0, -4}, {0, 4}, {4, 0}, {-4, -1}, {-4, 1}, {-1, -4}, {-1, 4}, {1, -4}, {1, 4}, {4, -1}, {4, 1}, {-3, -3}, {-3, 3}, {3, -3}, {3, 3}, {-4, -2}, {-4, 2}, {-2, -4}, {-2, 4}, {2, -4}, {2, 4}, {4, -2}, {4, 2}, {-5, 0}, {-4, -3}, {-4, 3}, {-3, -4}, {-3, 4}, {0, -5}, {0, 5}, {3, -4}, {3, 4}, {4, -3}, {4, 3}, {5, 0}, {-5, -1}, {-5, 1}, {-1, -5}, {-1, 5}, {1, -5}, {1, 5}, {5, -1}, {5, 1}, {-5, -2}, {-5, 2}, {-2, -5}, {-2, 5}, {2, -5}, {2, 5}, {5, -2}, {5, 2}, {-4, -4}, {-4, 4}, {4, -4}, {4, 4}, {-5, -3}, {-5, 3}, {-3, -5}, {-3, 5}, {3, -5}, {3, 5}, {5, -3}, {5, 3}};
    final Direction[] cardinalDirections = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    int[] tilesVisited;
    int stuckCount;
    List<RobotInfo> nearbyNetGuns;

    MapLocation nearestWaterLocation;
    MapLocation baseLocation;
    MapLocation hqLocation;
    MapLocation enemyLocation;
    boolean enemyVisited;
    MapLocation destination;
    int whichEnemyLocation;
    //enemy hq communication
    boolean hasSentEnemyLoc = false;
    //water communication
    MapLocation commedWaterLocation = null;

    boolean attackDrone;
    final int DEFEND_TURN = 1100;
    final int ATTACK_TURN = 1875;
    final int POKE_TURN = 900;

    boolean carrying;
    boolean giveUpOnAMove;
    boolean carryingCow;
    private boolean trapped;
    private boolean ferrying;
    private boolean landscaping;
    private boolean shellDrone;
    List<MapLocation> outerWall;
    List<MapLocation> centerDigSites;

    MapLocation defensiveDSchoolLocation = null;
    MapLocation reservedForDSchoolBuild = null;
    private boolean shrunk;
    private boolean giveUpOnPoke;
    private boolean cornerHolder;
    private boolean alwaysAttack;

    public DeliveryDrone(RobotController rc) throws GameActionException {
        super(rc);
        for (Direction dir : directions) {                   // Marginally cheaper than sensing in radius 2
            MapLocation t = myLocation.add(dir);
            if (rc.canSenseLocation(t)) {
                RobotInfo r = rc.senseRobotAtLocation(t);
                if (r != null && r.getType() == RobotType.FULFILLMENT_CENTER) {
                    baseLocation = t;
                    break;
                }
            }
        }
        if (baseLocation == null)
            baseLocation = myLocation;

        //initial message check
        //looking for locations broadcast before you were born
        //currently looks for allied hq location, enemy hq location, first water location.
        checkForLocationMessage();
        initialCheckForEnemyHQLocationMessage();
        commedWaterLocation = initialCheckForWaterLocation();
        if(commedWaterLocation != null) {
            waterLocations.add(commedWaterLocation);
        }

        if (ENEMY_HQ_LOCATION != null) {
            enemyLocation = ENEMY_HQ_LOCATION;
            //System.out.println("[i] I know enemy HQ");
        }
        hqLocation = HEADQUARTERS_LOCATION;
        hqLocation = hqLocation != null ? hqLocation : baseLocation;

        tilesVisited = new int[numRows * numCols];
        stuckCount = 0;

        destination = hqLocation;
        enemyLocation = new MapLocation(MAP_WIDTH - destination.x, MAP_HEIGHT - destination.y);
        enemyVisited = false;
        carrying = false;
        carryingCow = false;
        giveUpOnAMove = false;
        ferrying = false;
        landscaping = false;
        nearbyNetGuns = new ArrayList<>();
        outerWall = new ArrayList<>();
        centerDigSites = new ArrayList<>();
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                MapLocation loc = add(hqLocation, new int[]{i, j});
                if (loc.equals(hqLocation))
                    continue;
                if (loc.distanceSquaredTo(hqLocation) == 4)
                    centerDigSites.add(loc);
                else
                    outerWall.add(loc);
            }
        }

        attackDrone = false;
        Direction toBase = myLocation.directionTo(baseLocation);
        if (myLocation.distanceSquaredTo(hqLocation) > myLocation.add(toBase).add(toBase).distanceSquaredTo(hqLocation)) {
            attackDrone = true;
        }

        tilesVisited[getTileNumber(enemyLocation)] = 1;
        updateVisitedTiles(hqLocation);

        whichEnemyLocation = 0;
        nearestWaterLocation = updateNearestWaterLocation();
        Clock.yield(); //TODO: Hacky way to avoid recomputing location twice. Remove and do more efficiently?
    }

    private boolean wallMissing() {
        try {
            if (rc.getRoundNum() < FILL_WALL_ROUND)
                return false;
            for (MapLocation loc : outerWall) {
                if (rc.canSenseLocation(loc) && !rc.isLocationOccupied(loc) && !rc.senseFlooding(loc))
                    return true;
            }
            for (MapLocation loc : centerDigSites) {
                if (rc.canSenseLocation(loc) && !rc.isLocationOccupied(loc) && !rc.senseFlooding(loc))
                    return true;
            }
        } catch (GameActionException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean innerWallMissing() {
        if (rc.getRoundNum() < FILL_WALL_ROUND)
            return false;
        for (Direction d : directions) {
            MapLocation loc = hqLocation.add(d);
            try {
                if (rc.canSenseLocation(loc) && !rc.isLocationOccupied(loc) && !rc.senseFlooding(loc))
                    return true;
            } catch (GameActionException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public void run() throws GameActionException {
        super.run();

        updateVisitedTiles(myLocation);

        //TODO: Issue. Currently this does not handle water tiles becoming flooded, which should become closer drop points
        EnemyInfo enemyInfo = new EnemyInfo().invoke();
        RobotInfo[] nearby = enemyInfo.getNearby();
        RobotInfo nearest = enemyInfo.getNearest();
        int distToNearest = enemyInfo.getDistToNearest();
        int droneCount = enemyInfo.getDroneCount();

        updateDefensiveDSchoolLocation(nearby);

        //System.out.println(myLocation + " " + destination + " " + nearestWaterLocation + " " + carrying + " " + ferrying);

        checkIfDoneCornerHolding();

        System.out.println("Starting if statement: " + Clock.getBytecodeNum());

        if (shellDrone) {
            checkShell();
        }

        if (landscaping && wallMissing()) {
            dropOntoWall();
        } else if (ferrying) { // ferry ally onto lattice
            dropOntoLattice();
        } else if (carrying) { // go to water and drop
            if (carryingCow && nearest != null && !nearest.getType().equals(RobotType.COW))
                dropToward(nearest.getLocation());
            else
                goToWaterAndDrop();
        } else {
            if (rc.getRoundNum() + 100 > DEFEND_TURN)  // retreat all drones
                attackDrone = false;
            System.out.println("Choosing: " + distToNearest + " " + myLocation + " " + attackDrone + " " + DEFEND_TURN);

            if (distToNearest <= GameConstants.DELIVERY_DRONE_PICKUP_RADIUS_SQUARED) { // pick up
                tryPickUp(nearest);
            } else if (checkToLandscape(nearby)) {
            } else if (checkToFerry(nearby)) {
            } else if (shouldPoke()) {
                checkIfDoneWithPoke(nearby);
                handlePoke(nearest);
            } else if (shouldAMove()) { // drone attack-move
                checkIfDoneWithAMove(nearby);
                handleAMove(nearest);
            } else if (shouldChase(nearest)) { // chase enemy unless defending
                chaseEnemy(nearest);
            } else if (isAttackDrone()) { // attack drone
                handleAttack();
            } else { // defend drone / go back to base
                handleDefense(nearby);
            }
        }

        //Enemy agression, headquarters, and water communication
        findMessagesFromAllies(rc.getRoundNum()-1);

        //defensive drones report enemy aggression if they see it
        if(!attackDrone && rc.getRoundNum()<200 && !enemyAggression) {
            if(enemyAggressionCheck()) {
                turnAtEnemyAggression = rc.getRoundNum();
            }
        }

        if(commedWaterLocation == null) {
            System.out.println("I don't know water yet.");
            if(waterLocations.nearest != null) {
                LocationMessage lw = new LocationMessage(MAP_HEIGHT, MAP_WIDTH, teamNum, rc.getRoundNum());
                lw.writeInformation(waterLocations.nearest.mapLocation.x, waterLocations.nearest.mapLocation.y, 2); // 2 is water.
                if(sendMessage(lw.getMessage(), 1)) {
                    //System.out.println("[i] Sending water location to allies...");
                    commedWaterLocation = waterLocations.nearest.mapLocation;
                }
            }
        }

    }

    private void checkShell() throws GameActionException {
        if (!inShell()) {
            shellDrone = false;
            path(hqLocation);
            shellDrone = true;
        }
    }

    private void checkIfDoneCornerHolding() {
        if (cornerHolder) {
            if (underFire(myLocation)) {
                cornerHolder = false;
            }
            int[] dxy = xydist(myLocation, enemyLocation);
            if (dxy[0] != 3 || dxy[1] != 3) {
                cornerHolder = false;
            }
        }
    }

    private boolean isAttackDrone() {
        return attackDrone && (rc.getRoundNum() < DEFEND_TURN || alwaysAttack);
    }

    private void checkIfDoneWithPoke(RobotInfo[] nearby) throws GameActionException {
        if (enemyLocation != null && rc.canSenseLocation(enemyLocation)) {
            boolean giveUp = true;
            for (RobotInfo x : nearby) {
                if (!x.getTeam().equals(allyTeam) && x.getType() == RobotType.LANDSCAPER) {
                    giveUp = false;
                    break;
                }
            }
            if (giveUp) {
                giveUpOnPoke = true;
                handleDefense(nearby);
            }
        }
    }

    private boolean shouldChase(RobotInfo nearest) {
        return nearest != null && (rc.getRoundNum() < DEFEND_TURN
                || myLocation.distanceSquaredTo(hqLocation) < 100 || rc.getRoundNum() > ATTACK_TURN);
    }

    private boolean shouldAMove() {
        return rc.getRoundNum() > ATTACK_TURN - 200 && !giveUpOnAMove && !inShell()
                && rc.getRoundNum() < SHRINK_SHELL_ROUND;
    }

    private boolean shouldPoke() {
        return rc.getRoundNum() > POKE_TURN && rc.getRoundNum() < POKE_TURN + POKE_DURATION
                && !giveUpOnPoke && myLocation.distanceSquaredTo(enemyLocation) < POKE_RADIUS;
    }

    public void updateDefensiveDSchoolLocation(RobotInfo[] nearby) throws GameActionException {
        if (defensiveDSchoolLocation != null) {
            return;
        }
        for (RobotInfo r : nearby) {
            if (r.team.equals(allyTeam) && r.type.equals(RobotType.DESIGN_SCHOOL) && r.location.distanceSquaredTo(hqLocation) < 9) {
                defensiveDSchoolLocation = r.location;
                reservedForDSchoolBuild = defensiveDSchoolLocation.add(defensiveDSchoolLocation.directionTo(hqLocation).opposite().rotateRight());
            }
        }
    }

    private int countAlliedDrones() {
        int i = 0;
        for (RobotInfo x : rc.senseNearbyRobots()) {
            if (x.getTeam().equals(allyTeam) && x.getType() == RobotType.DELIVERY_DRONE)
                i++;
        }
        return i;
    }

    private void dropOntoLattice() throws GameActionException {
        for (Direction di : directions) {
            MapLocation loc = myLocation.add(di);
            int[] dxy = xydist(hqLocation, loc);
            if ((rc.canSenseLocation(loc) && rc.senseFlooding(loc)) || !rc.canDropUnit(di)) continue;
            if (Math.max(dxy[0] % 3, dxy[1] % 3) > 0) {
                if (loc.distanceSquaredTo(hqLocation) > 8 && !loc.equals(reservedForDSchoolBuild) &&
                        loc.distanceSquaredTo(hqLocation) < Landscaper.LATTICE_SIZE) {
                    dropToward(loc);
                    System.out.println("FERRYING TO " + loc);
                    return;
                }
            }
        }
        path(hqLocation);
    }

    private void checkIfDoneWithAMove(RobotInfo[] nearby) throws GameActionException {
        if (enemyLocation != null && rc.canSenseLocation(enemyLocation)) {
            if (Arrays.stream(nearby).noneMatch(x -> !x.getTeam().equals(allyTeam) && x.getType() == RobotType.LANDSCAPER)) {
                giveUpOnAMove = true;
                handleDefense(nearby);
            }
        }
    }

    private void dropToward(MapLocation location) throws GameActionException {
        if (!rc.isReady())
            return;
        Direction dir = Arrays.stream(directions)
                .filter(x -> rc.canDropUnit(x))
                .min(Comparator.comparingInt(d -> myLocation.add(d).distanceSquaredTo(location)))
                .orElse(null);
        if (dir != null) {
            rc.dropUnit(dir);
            carrying = false;
            carryingCow = false;
            ferrying = false;
            landscaping = false;
        }
    }

    private void tryPickUp(RobotInfo nearest) throws GameActionException {
        if (rc.isReady()) {
            rc.pickUpUnit(nearest.getID());
            if (rc.senseRobot(nearest.getID()).getType().equals(RobotType.COW)) {
                carryingCow = true;
            }
            carrying = true;
        }
    }

    private void updateVisitedTiles(MapLocation myLocation) throws GameActionException {
        int myTileNum = getTileNumber(myLocation);
        tilesVisited[myTileNum] = 1;
        if (myTileNum % numCols == 0) { // left border
            tilesVisited[myTileNum + 1] = 1;
        } else if (myTileNum % numCols == numCols - 1) { // right border
            tilesVisited[myTileNum - 1] = 1;
        } else {
            tilesVisited[myTileNum - 1] = 1;
            tilesVisited[myTileNum + 1] = 1;
        }
        if (myTileNum < numCols) { // bottom
            tilesVisited[myTileNum + numCols] = 1;
        } else if (myTileNum >= numCols * (numRows - 1)) { // top
            tilesVisited[myTileNum - numCols] = 1;
        } else {
            tilesVisited[myTileNum - numCols] = 1;
            tilesVisited[myTileNum + numCols] = 1;
        }
    }

    private void dropOntoWall() throws GameActionException {
        if (innerWallMissing()) {
            for (Direction d : directions) {
                MapLocation loc = myLocation.add(d);
                if (loc.isAdjacentTo(hqLocation) && !rc.isLocationOccupied(loc) && !rc.senseFlooding(loc)) {
                    System.out.println("DROP ONTO WALL: " + loc);
                    dropToward(loc);
                    return;
                }
            }
        } else {
            for (MapLocation loc : outerWall) {
                if (loc.isAdjacentTo(myLocation) && !rc.isLocationOccupied(loc) && !rc.senseFlooding(loc)) {
                    System.out.println("DROP ONTO WALL: " + loc);
                    dropToward(loc);
                    return;
                }
            }
            for (MapLocation loc : centerDigSites) {
                if (loc.isAdjacentTo(myLocation) && !rc.isLocationOccupied(loc) && !rc.senseFlooding(loc)) {
                    System.out.println("DROP ONTO WALL: " + loc);
                    dropToward(loc);
                    return;
                }
            }
        }
        path(hqLocation);
    }

    private boolean checkToLandscape(RobotInfo[] nearby) throws GameActionException {
        if (!rc.isReady() || myLocation.isAdjacentTo(hqLocation)
                || myLocation.distanceSquaredTo(hqLocation) > Landscaper.LATTICE_SIZE)
            return landscaping;

        if (rc.getRoundNum() < FILL_OUTER_ROUND && !innerWallMissing()) {
            return landscaping;
        }

        if (innerWallMissing()) {
            for (RobotInfo x : nearby) {
                if (!x.getTeam().equals(allyTeam) || !x.getType().equals(RobotType.LANDSCAPER) || x.getLocation().isAdjacentTo(hqLocation))
                    continue;
                MapLocation loc = x.getLocation();
                if (loc.isAdjacentTo(myLocation)) {
                    tryPickUp(x);
                    ferrying = true;
                    landscaping = true;
                } else {
                    path(loc);
                }
                return landscaping;
            }
        } else if (wallMissing()) {
            for (RobotInfo x : nearby) {
                if (!x.getTeam().equals(allyTeam) || !x.getType().equals(RobotType.LANDSCAPER) || outerWall.contains(x.getLocation()))
                    continue;
                MapLocation loc = x.getLocation();
                if (loc.isAdjacentTo(myLocation)) {
                    tryPickUp(x);
                    ferrying = true;
                    landscaping = true;
                } else {
                    path(loc);
                }
                return landscaping;
            }
        }
        return landscaping;
    }

    private boolean checkToFerry(RobotInfo[] nearby) throws GameActionException {
        if (rc.getRoundNum() < START_FERRY)
            return false;
        System.out.println("FERRY CHECK");
        if (!rc.isReady()) return ferrying;
        if (myLocation.distanceSquaredTo(hqLocation) > Landscaper.LATTICE_SIZE
                || myLocation.isAdjacentTo(hqLocation))
            return ferrying;
        for (RobotInfo x : nearby) {
            if (!x.getTeam().equals(allyTeam) || x.getType().equals(RobotType.DELIVERY_DRONE) || x.getType().isBuilding())
                continue;
            int[] dxy = xydist(x.getLocation(), hqLocation);
            MapLocation loc = x.getLocation();
            if ((dxy[0] % 3 + dxy[1] % 3 == 0
                    && loc.distanceSquaredTo(hqLocation) < Landscaper.LATTICE_SIZE
                    && loc.distanceSquaredTo(hqLocation) > 8)
                    || (loc.distanceSquaredTo(hqLocation) == 4 && !x.getType().equals(RobotType.LANDSCAPER))
                    || (x.getType().equals(RobotType.MINER) && loc.distanceSquaredTo(hqLocation) < 9) || loc.equals(reservedForDSchoolBuild)) {
                if (loc.isAdjacentTo(myLocation)) {
                    tryPickUp(x);
                    ferrying = true;
                    if (x.getType() == RobotType.LANDSCAPER)
                        landscaping = true;
                } else {
                    path(loc);
                }
                return ferrying;
            }
        }
        return ferrying;
    }

    private boolean inShell(int rad) {
        int[] dxy = xydist(myLocation, hqLocation);
        return dxy[0] <= rad && dxy[1] == rad || dxy[0] == rad && dxy[1] <= rad;
    }

    private boolean inShell() {
        if (shrunk || rc.getRoundNum() >= SHRINK_SHELL_ROUND)
            shrunk = true;
        int rad = shrunk ? 2 : 3;
        return inShell(rad);
    }

    private void handleDefense(RobotInfo[] nearby) throws GameActionException {
        System.out.println("Handle Defense");
        if (!cornerHolder) {
            destination = hqLocation;
            if (rc.getRoundNum() < 300) {
                fuzzyMoveToLoc(hqLocation.add(hqLocation.directionTo(enemyLocation)));
            } else if (rc.getRoundNum() < DEFEND_TURN) {
                spiral(destination, false);
            } else {
                if (!inShell()) {
                    if (inShell(1) || inShell(2))
                        path(enemyLocation, false);
                    else
                        path(destination, false);
                } else
                    shellDrone = true;
            }
        }
        nearestWaterLocation = updateNearestWaterLocation();
    }

    private void handlePoke(RobotInfo nearest) throws GameActionException {
        System.out.println("POKING " + enemyLocation);
        MapLocation enemyLocation = nearest == null ? this.enemyLocation : nearest.getLocation();
        alwaysAttack = true;
        if (giveUpOnPoke)
            return;
        if (rc.getRoundNum() > POKE_TURN + POSTURE_POKE_TIME) {
            fuzzyMoveToLoc(enemyLocation);
        } else if (rc.getRoundNum() > POKE_TURN && !cornerHolder) {
            path(enemyLocation, true);
        }
    }

    private void handleAMove(RobotInfo nearest) throws GameActionException {
        MapLocation enemyLocation = nearest == null ? this.enemyLocation : nearest.getLocation();
        if (giveUpOnAMove)
            return;
        if (ENEMY_HQ_LOCATION == null && rc.canSenseLocation(enemyLocation)) {
            RobotInfo enemy = rc.senseRobotAtLocation(enemyLocation);
            if (enemy == null || enemy.type != RobotType.HQ) {
                switch (whichEnemyLocation) {
                    case 0:
                        enemyLocation = new MapLocation(destination.x, MAP_HEIGHT - destination.y);
                        whichEnemyLocation++;
                        break;
                    case 1:
                        enemyLocation = new MapLocation(MAP_WIDTH - destination.x, destination.y);
                        whichEnemyLocation++;
                        break;
                    case 2:
                        System.out.println("Critical Error!! Somehow checked all HQ locations and didn't find anything.");
                }
            }
        }
        if (rc.getRoundNum() > ATTACK_TURN) {
            fuzzyMoveToLoc(enemyLocation);
        } else if (rc.getRoundNum() > ATTACK_TURN - 25 && !cornerHolder) {
            path(enemyLocation, true);
        } else if (rc.getRoundNum() > ATTACK_TURN - 200 && !cornerHolder) {
            spiral(enemyLocation, true);
        }
    }

    private void chaseEnemy(RobotInfo nearest) throws GameActionException {
        if (!cornerHolder) {
            int[] dxy = xydist(myLocation, enemyLocation);
            if (dxy[0] == 3 && dxy[1] == 3 && rc.getRoundNum() > HOLD_CORNER_ROUND && !underFire(myLocation)) {
                cornerHolder = true;
            } else if (rc.getRoundNum() > ATTACK_TURN) { // charge after ATTACK_TURN
                fuzzyMoveToLoc(nearest.location);
            } else if (rc.getRoundNum() < 200 && myLocation.distanceSquaredTo(hqLocation) < 100
                    && nearbyNetGuns.size() == 0 && !attackDrone) { // guard HQ
                Direction optimalDir = null;
                int optimalScore = Integer.MAX_VALUE;
                for (Direction dir : directions) {
                    if (rc.canMove(dir)) {
                        int enemyDist = myLocation.add(dir).distanceSquaredTo(nearest.location);
                        if (enemyDist == 1) // don't give special priority to cardinal directions
                            enemyDist = 2;
                        int score = enemyDist * 1000 + myLocation.add(dir).distanceSquaredTo(hqLocation);
                        System.out.println(dir + " " + score);
                        if (score < optimalScore) {
                            optimalDir = dir;
                            optimalScore = score;
                        }
                    }
                }
                if (optimalDir != null) {
                    rc.move(optimalDir);
                }
//                path(nearest.location, false); // path recklessly
            } else {
                path(nearest.location, true);
            }
        }
        nearestWaterLocation = updateNearestWaterLocation();
    }

    private void handleAttack() throws GameActionException {
        int[] dxy = xydist(myLocation, enemyLocation);
        if (cornerHolder) {
        } else if (dxy[0] == 3 && dxy[1] == 3 && rc.getRoundNum() > HOLD_CORNER_ROUND && !underFire(myLocation)) {
            cornerHolder = true;
        } else if (myLocation.distanceSquaredTo(hqLocation) < 50) {
            spiral(enemyLocation, enemyAggression);
        } else if (myLocation.distanceSquaredTo(enemyLocation) > 50 && rc.getRoundNum() < 150) { // early game surge
            spiral(enemyLocation, false);
        } else {
            spiral(enemyLocation, true);
        }
        //rc.setIndicatorLine(myLocation, enemyLocation, 100, 0, 0);
        if (ENEMY_HQ_LOCATION == null && rc.canSenseLocation(enemyLocation)) {
            RobotInfo enemy = rc.senseRobotAtLocation(enemyLocation);
            if (enemy == null || enemy.type != RobotType.HQ) {
                switch (whichEnemyLocation) {
                    case 0:
                        enemyLocation = new MapLocation(destination.x, MAP_HEIGHT - destination.y);
                        whichEnemyLocation++;
                        break;
                    case 1:
                        enemyLocation = new MapLocation(MAP_WIDTH - destination.x, destination.y);
                        whichEnemyLocation++;
                        break;
                    case 2:
                        System.out.println("Critical Error!! Somehow checked all HQ locations and didn't find anything.");
                }
            }
        }
        nearestWaterLocation = updateNearestWaterLocation();
    }

    private boolean goToWaterAndDrop() throws GameActionException {
        if (cornerHolder) {
            return waterDrop();
        }
        //rc.setIndicatorLine(myLocation, nearestWaterLocation, 255, 0, 255);
        int distanceToDestination = myLocation.distanceSquaredTo(nearestWaterLocation);
        if (distanceToDestination <= 2) { // drop
//                System.out.println("Drop this guy " + rc.isReady());
            if (waterDrop()) return true;
            nearestWaterLocation = updateNearestWaterLocation(); // Adjacent to explore zone
            if (myLocation.equals(nearestWaterLocation)) {
                for (Direction dir : directions) { // you're on the water
                    if (rc.isReady() && rc.canMove(dir)) {
                        rc.move(dir);
                    }
                }
            }
            if (myLocation.distanceSquaredTo(hqLocation) < 100) {
                path(nearestWaterLocation, enemyAggression);
            } else {
                path(nearestWaterLocation, true);
            }
        } else {
//                System.out.println("Path to water: " + myLocation + " " + nearestWaterLocation);
            if (waterDrop()) return true;
            if (myLocation.distanceSquaredTo(hqLocation) < 100) {
                path(nearestWaterLocation, enemyAggression);
            } else {
                path(nearestWaterLocation, true);
            }
            nearestWaterLocation = updateNearestWaterLocation();
        }
        return false;
    }

    private boolean waterDrop() throws GameActionException {
        for (Direction dir : directions) { // drop anywhere wet
            if (rc.isReady() && rc.canDropUnit(dir) &&
                    rc.canSenseLocation(myLocation.add(dir)) && rc.senseFlooding(myLocation.add(dir))) {
                rc.dropUnit(dir);
                carrying = false;
                carryingCow = false;
                return true;
            }
        }
        return false;
    }

    public void spiral(MapLocation center, boolean safe) throws GameActionException {
        int dx = myLocation.x - center.x;
        int dy = myLocation.y - center.y;
        double cs = Math.cos(.5);
        double sn = Math.sin(.5);
        int x = (int) (dx * cs - dy * sn);
        int y = (int) (dx * sn + dy * cs);
        if (myLocation.distanceSquaredTo(center) > 35) {
            System.out.println("Spiral is pushing me in. Safe " + safe);
            path(center, safe);
        } else if (myLocation.distanceSquaredTo(center) < 20) {
            System.out.println("Spiral is pushing me out. Safe: " + safe);
            path(myLocation.add(center.directionTo(myLocation)), safe);
        } else {
            //System.out.println("Spiraling to " + center.translate(x, y));
            path(center.translate(x, y), safe);
        }
    }

    protected void updateDrones() {
        nearbyNetGuns.clear();
        for (RobotInfo x : rc.senseNearbyRobots()) {
            if (!x.getTeam().equals(allyTeam) &&
                    (x.getType().equals(RobotType.NET_GUN) || x.getType().equals(RobotType.HQ))) {
                nearbyNetGuns.add(x);
            }
        }

        trapped = true;

        outer:
        for (Direction d : directionsWithCenter) {
            for (RobotInfo n : nearbyNetGuns) {
                if (n.getLocation().distanceSquaredTo(myLocation.add(d)) <= GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED) {
                    continue outer;
                }
            }
            trapped = false;
        }
    }

    protected boolean canMove(Direction in) {
        return canMove(myLocation, in);
    }

    protected boolean canMove(MapLocation from, Direction in) {
        if (safe) {
            if (!Arrays.asList(cardinalDirections).contains(in)) {
                return false;
            }
        }
        if (shellDrone)
            return false;
        MapLocation to = from.add(in);
        if (landscaping && wallMissing()) {
            if (innerWallMissing()) {
                if (to.isAdjacentTo(hqLocation))
                    return false;
            } else {
                if (outerWall.contains(to))
                    return false;
            }
        }
        if (to.equals(reservedForDSchoolBuild))
            return false;
        try {
            return rc.canSenseLocation(to)
                    && !rc.isLocationOccupied(to)
                    && (trapped || !underFire(to))
                    && (!trapped
                    || enemyLocation == null
                    || to.distanceSquaredTo(enemyLocation) > from.distanceSquaredTo(enemyLocation));
        } catch (GameActionException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean underFire(MapLocation to) {
        for (RobotInfo y : nearbyNetGuns) {
            if (y.getLocation().distanceSquaredTo(to) <= GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED) {
                return true;
            }
        }
        return (enemyLocation != null && enemyLocation.distanceSquaredTo(to) <= GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED);
    }

    protected Direction[] getDirections() {
        if (safe)
            return cardinal;
        else
            return directions;
    }

    private boolean safe = false;

    public void path(MapLocation target, boolean safe) throws GameActionException {
        this.safe = safe;
        super.path(target);
        this.safe = false;
    }

    public void path(MapLocation target) throws GameActionException {
        this.safe = false;
        super.path(target);
    }

    // Returns location of nearest water
    public MapLocation updateNearestWaterLocation() throws GameActionException {
        int distanceToNearest = MAX_SQUARED_DISTANCE;
        MapLocation nearest = null;
        if (nearestWaterLocation != null && !(rc.canSenseLocation(nearestWaterLocation) && !rc.senseFlooding(nearestWaterLocation))) {
            nearest = nearestWaterLocation;
            distanceToNearest = myLocation.distanceSquaredTo(nearest);
        }

        //System.out.println("start map scan " + Clock.getBytecodeNum());
        for (int x = Math.max(myLocation.x - 5, 0); x <= Math.min(myLocation.x + 5, MAP_WIDTH - 1); x++) {
            //TODO: this ignores left most pt bc bit mask size 10. Switch too big to fit with 11. How to fix?
            for (int y : getLocationsToCheck(((waterChecked[x] >> Math.max(myLocation.y - 5, 0)) << Math.max(5 - myLocation.y, 0)) & 1023)) {
                MapLocation newLoc = new MapLocation(x, myLocation.y + y - 5);
                if (rc.canSenseLocation(newLoc)) {
                    if (rc.senseFlooding(newLoc)) {
                        waterLocations.add(newLoc);
                    }
                    waterChecked[x] = waterChecked[x] | (1L << Math.min(Math.max(myLocation.y + y - 5, 0), MAP_HEIGHT - 1));
                }
            }
        }

        for (Direction dir : directionsWithCenter) {
            MapLocation newLoc = myLocation.add(dir);
            if (rc.canSenseLocation(newLoc) && rc.senseFlooding(newLoc)) {
                return newLoc;
            }
        }
        //System.out.println("end map scan " + Clock.getBytecodeNum());

        //System.out.println("start find nearest " + Clock.getBytecodeNum());
        nearest = waterLocations.findNearest();
        //System.out.println("end find nearest " + Clock.getBytecodeNum());

        if (nearest != null) {
            return nearest;
        }
        return getNearestUnexploredTile();
    }

    //TODO: Optimize this. Scan outward with switch statements? Replace int[] for tiles with bits?
    public MapLocation getNearestUnexploredTile() throws GameActionException {
        int currentTile = getTileNumber(myLocation);
        int scanRadius = rc.getCurrentSensorRadiusSquared();
        for (int[] shift : SPIRAL_ORDER) {
            int newTile = currentTile + shift[0] + numCols * shift[1];
            if (newTile >= 0 && newTile < numRows * numCols && tilesVisited[newTile] == 0) {
                //rc.setIndicatorDot(getCenterFromTileNumber(newTile), 255, 0, 255);
                return getCenterFromTileNumber(newTile);
            }
        }
        return enemyLocation; // explored entire map and no water seen??
    }

    private class WaterList {

        private WaterLoc head;
        private WaterLoc nearest;

        public WaterList() {
            head = new WaterLoc(null, null);
            nearest = null;
        }

        public void add(MapLocation ml) {
            head.next = new WaterLoc(ml, head.next);
        }

        public boolean isSurroundedByWater(MapLocation loc) throws GameActionException {
            for (Direction dir : directions) {
                if (rc.canSenseLocation(loc.add(dir)) && !rc.senseFlooding(loc.add(dir)))
                    return false;
            }
            return true;
        }

        public void printAll() {
            WaterLoc ptr = head;
            while (ptr.next != null) {
                System.out.println("List: " + ptr.mapLocation);
                ptr = ptr.next;
            }
        }

        public MapLocation findNearest() throws GameActionException {
            int scanRadius = rc.getCurrentSensorRadiusSquared();
            if (nearest != null && myLocation.distanceSquaredTo(nearest.mapLocation) <= 2
                    && rc.canSenseLocation(nearest.mapLocation) &&
                    rc.senseFlooding(nearest.mapLocation)) { // cache nearest
                return nearest.mapLocation;
            }
            int nearestDist = Integer.MAX_VALUE;
            nearest = null;
            WaterLoc oldPtr = head;
            WaterLoc ptr = head.next;
            while (ptr != null) {
                int distToPtr = ptr.mapLocation.distanceSquaredTo(myLocation);
                MapLocation ptrLocation = ptr.mapLocation;

                int waterDistance = myLocation.distanceSquaredTo(ptrLocation);
                if (rc.canSenseLocation(ptrLocation) && !rc.senseFlooding(ptrLocation)) {
                    oldPtr.next = oldPtr.next.next;
                } else if (waterDistance < nearestDist) {
                    nearest = ptr;
                    nearestDist = waterDistance;
                    if (distToPtr <= 2) {
                        break;
                    }
                }
                oldPtr = ptr;
                ptr = ptr.next;
                if (Clock.getBytecodesLeft() < 800) {
                    break;
                }
            }
            return nearest != null ? nearest.mapLocation : null;
        }

        private class WaterLoc {
            public WaterLoc next;
            public MapLocation mapLocation;

            public WaterLoc(MapLocation ml, WaterLoc n) {
                mapLocation = ml;
                next = n;
            }
        }
    }

    //Find message from allies given a round number rn
    //Checks block of round number rn, loops through messages
    //Currently: Checks for LocationMessage of Water from other Drones
    public void findMessagesFromAllies(int rn) throws GameActionException {
        Transaction[] msgs = rc.getBlock(rn);
        for (Transaction transaction : msgs) {
            int[] msg = transaction.getMessage();
            if (allyMessage(msg[0], rn)) {
                if(getSchema(msg[0])==4) {
                    LocationMessage l = new LocationMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum, rn);
                    if (l.unitType == 1 && enemyLocation != ENEMY_HQ_LOCATION) {
                        ENEMY_HQ_LOCATION = new MapLocation(l.xLoc, l.yLoc);
                        enemyLocation = ENEMY_HQ_LOCATION;
                        System.out.println("I know enemy HQ location");
                        //rc.setIndicatorDot(enemyLocation, 255, 83, 126);
                    } else if(l.unitType==2 && commedWaterLocation == null)  { //2 is water
                        //System.out.println("[i] I know water location!");
                        commedWaterLocation = new MapLocation(l.xLoc, l.yLoc);
                        waterLocations.add(commedWaterLocation);
                        //System.out.println(commedWaterLocation);
                    }
                } else if(getSchema(msg[0])==7) {
                    RushCommitMessage r = new RushCommitMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum, rn);
                    if(r.typeOfCommit == 2) {
                        if(!enemyAggression) {
                            //System.out.println("[i] I know Enemy is Rushing!");
                            enemyAggression = true;
                            turnAtEnemyAggression = rc.getRoundNum();
                        }
                    } else if (r.typeOfCommit == 3) {
                        if(enemyAggression) {
                            //System.out.println("[i] Enemy has stopped rushing");
                            enemyAggression = false;
                            turnAtEnemyAggression = -1;
                        }
                    }
                }
            }
        }
    }

    public int[] getLocationsToCheck(long mask) {
        switch ((int) mask) {
            case 0:
                return new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
            case 1:
                return new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
            case 2:
                return new int[]{0, 2, 3, 4, 5, 6, 7, 8, 9};
            case 3:
                return new int[]{2, 3, 4, 5, 6, 7, 8, 9};
            case 4:
                return new int[]{0, 1, 3, 4, 5, 6, 7, 8, 9};
            case 5:
                return new int[]{1, 3, 4, 5, 6, 7, 8, 9};
            case 6:
                return new int[]{0, 3, 4, 5, 6, 7, 8, 9};
            case 7:
                return new int[]{3, 4, 5, 6, 7, 8, 9};
            case 8:
                return new int[]{0, 1, 2, 4, 5, 6, 7, 8, 9};
            case 9:
                return new int[]{1, 2, 4, 5, 6, 7, 8, 9};
            case 10:
                return new int[]{0, 2, 4, 5, 6, 7, 8, 9};
            case 11:
                return new int[]{2, 4, 5, 6, 7, 8, 9};
            case 12:
                return new int[]{0, 1, 4, 5, 6, 7, 8, 9};
            case 13:
                return new int[]{1, 4, 5, 6, 7, 8, 9};
            case 14:
                return new int[]{0, 4, 5, 6, 7, 8, 9};
            case 15:
                return new int[]{4, 5, 6, 7, 8, 9};
            case 16:
                return new int[]{0, 1, 2, 3, 5, 6, 7, 8, 9};
            case 17:
                return new int[]{1, 2, 3, 5, 6, 7, 8, 9};
            case 18:
                return new int[]{0, 2, 3, 5, 6, 7, 8, 9};
            case 19:
                return new int[]{2, 3, 5, 6, 7, 8, 9};
            case 20:
                return new int[]{0, 1, 3, 5, 6, 7, 8, 9};
            case 21:
                return new int[]{1, 3, 5, 6, 7, 8, 9};
            case 22:
                return new int[]{0, 3, 5, 6, 7, 8, 9};
            case 23:
                return new int[]{3, 5, 6, 7, 8, 9};
            case 24:
                return new int[]{0, 1, 2, 5, 6, 7, 8, 9};
            case 25:
                return new int[]{1, 2, 5, 6, 7, 8, 9};
            case 26:
                return new int[]{0, 2, 5, 6, 7, 8, 9};
            case 27:
                return new int[]{2, 5, 6, 7, 8, 9};
            case 28:
                return new int[]{0, 1, 5, 6, 7, 8, 9};
            case 29:
                return new int[]{1, 5, 6, 7, 8, 9};
            case 30:
                return new int[]{0, 5, 6, 7, 8, 9};
            case 31:
                return new int[]{5, 6, 7, 8, 9};
            case 32:
                return new int[]{0, 1, 2, 3, 4, 6, 7, 8, 9};
            case 33:
                return new int[]{1, 2, 3, 4, 6, 7, 8, 9};
            case 34:
                return new int[]{0, 2, 3, 4, 6, 7, 8, 9};
            case 35:
                return new int[]{2, 3, 4, 6, 7, 8, 9};
            case 36:
                return new int[]{0, 1, 3, 4, 6, 7, 8, 9};
            case 37:
                return new int[]{1, 3, 4, 6, 7, 8, 9};
            case 38:
                return new int[]{0, 3, 4, 6, 7, 8, 9};
            case 39:
                return new int[]{3, 4, 6, 7, 8, 9};
            case 40:
                return new int[]{0, 1, 2, 4, 6, 7, 8, 9};
            case 41:
                return new int[]{1, 2, 4, 6, 7, 8, 9};
            case 42:
                return new int[]{0, 2, 4, 6, 7, 8, 9};
            case 43:
                return new int[]{2, 4, 6, 7, 8, 9};
            case 44:
                return new int[]{0, 1, 4, 6, 7, 8, 9};
            case 45:
                return new int[]{1, 4, 6, 7, 8, 9};
            case 46:
                return new int[]{0, 4, 6, 7, 8, 9};
            case 47:
                return new int[]{4, 6, 7, 8, 9};
            case 48:
                return new int[]{0, 1, 2, 3, 6, 7, 8, 9};
            case 49:
                return new int[]{1, 2, 3, 6, 7, 8, 9};
            case 50:
                return new int[]{0, 2, 3, 6, 7, 8, 9};
            case 51:
                return new int[]{2, 3, 6, 7, 8, 9};
            case 52:
                return new int[]{0, 1, 3, 6, 7, 8, 9};
            case 53:
                return new int[]{1, 3, 6, 7, 8, 9};
            case 54:
                return new int[]{0, 3, 6, 7, 8, 9};
            case 55:
                return new int[]{3, 6, 7, 8, 9};
            case 56:
                return new int[]{0, 1, 2, 6, 7, 8, 9};
            case 57:
                return new int[]{1, 2, 6, 7, 8, 9};
            case 58:
                return new int[]{0, 2, 6, 7, 8, 9};
            case 59:
                return new int[]{2, 6, 7, 8, 9};
            case 60:
                return new int[]{0, 1, 6, 7, 8, 9};
            case 61:
                return new int[]{1, 6, 7, 8, 9};
            case 62:
                return new int[]{0, 6, 7, 8, 9};
            case 63:
                return new int[]{6, 7, 8, 9};
            case 64:
                return new int[]{0, 1, 2, 3, 4, 5, 7, 8, 9};
            case 65:
                return new int[]{1, 2, 3, 4, 5, 7, 8, 9};
            case 66:
                return new int[]{0, 2, 3, 4, 5, 7, 8, 9};
            case 67:
                return new int[]{2, 3, 4, 5, 7, 8, 9};
            case 68:
                return new int[]{0, 1, 3, 4, 5, 7, 8, 9};
            case 69:
                return new int[]{1, 3, 4, 5, 7, 8, 9};
            case 70:
                return new int[]{0, 3, 4, 5, 7, 8, 9};
            case 71:
                return new int[]{3, 4, 5, 7, 8, 9};
            case 72:
                return new int[]{0, 1, 2, 4, 5, 7, 8, 9};
            case 73:
                return new int[]{1, 2, 4, 5, 7, 8, 9};
            case 74:
                return new int[]{0, 2, 4, 5, 7, 8, 9};
            case 75:
                return new int[]{2, 4, 5, 7, 8, 9};
            case 76:
                return new int[]{0, 1, 4, 5, 7, 8, 9};
            case 77:
                return new int[]{1, 4, 5, 7, 8, 9};
            case 78:
                return new int[]{0, 4, 5, 7, 8, 9};
            case 79:
                return new int[]{4, 5, 7, 8, 9};
            case 80:
                return new int[]{0, 1, 2, 3, 5, 7, 8, 9};
            case 81:
                return new int[]{1, 2, 3, 5, 7, 8, 9};
            case 82:
                return new int[]{0, 2, 3, 5, 7, 8, 9};
            case 83:
                return new int[]{2, 3, 5, 7, 8, 9};
            case 84:
                return new int[]{0, 1, 3, 5, 7, 8, 9};
            case 85:
                return new int[]{1, 3, 5, 7, 8, 9};
            case 86:
                return new int[]{0, 3, 5, 7, 8, 9};
            case 87:
                return new int[]{3, 5, 7, 8, 9};
            case 88:
                return new int[]{0, 1, 2, 5, 7, 8, 9};
            case 89:
                return new int[]{1, 2, 5, 7, 8, 9};
            case 90:
                return new int[]{0, 2, 5, 7, 8, 9};
            case 91:
                return new int[]{2, 5, 7, 8, 9};
            case 92:
                return new int[]{0, 1, 5, 7, 8, 9};
            case 93:
                return new int[]{1, 5, 7, 8, 9};
            case 94:
                return new int[]{0, 5, 7, 8, 9};
            case 95:
                return new int[]{5, 7, 8, 9};
            case 96:
                return new int[]{0, 1, 2, 3, 4, 7, 8, 9};
            case 97:
                return new int[]{1, 2, 3, 4, 7, 8, 9};
            case 98:
                return new int[]{0, 2, 3, 4, 7, 8, 9};
            case 99:
                return new int[]{2, 3, 4, 7, 8, 9};
            case 100:
                return new int[]{0, 1, 3, 4, 7, 8, 9};
            case 101:
                return new int[]{1, 3, 4, 7, 8, 9};
            case 102:
                return new int[]{0, 3, 4, 7, 8, 9};
            case 103:
                return new int[]{3, 4, 7, 8, 9};
            case 104:
                return new int[]{0, 1, 2, 4, 7, 8, 9};
            case 105:
                return new int[]{1, 2, 4, 7, 8, 9};
            case 106:
                return new int[]{0, 2, 4, 7, 8, 9};
            case 107:
                return new int[]{2, 4, 7, 8, 9};
            case 108:
                return new int[]{0, 1, 4, 7, 8, 9};
            case 109:
                return new int[]{1, 4, 7, 8, 9};
            case 110:
                return new int[]{0, 4, 7, 8, 9};
            case 111:
                return new int[]{4, 7, 8, 9};
            case 112:
                return new int[]{0, 1, 2, 3, 7, 8, 9};
            case 113:
                return new int[]{1, 2, 3, 7, 8, 9};
            case 114:
                return new int[]{0, 2, 3, 7, 8, 9};
            case 115:
                return new int[]{2, 3, 7, 8, 9};
            case 116:
                return new int[]{0, 1, 3, 7, 8, 9};
            case 117:
                return new int[]{1, 3, 7, 8, 9};
            case 118:
                return new int[]{0, 3, 7, 8, 9};
            case 119:
                return new int[]{3, 7, 8, 9};
            case 120:
                return new int[]{0, 1, 2, 7, 8, 9};
            case 121:
                return new int[]{1, 2, 7, 8, 9};
            case 122:
                return new int[]{0, 2, 7, 8, 9};
            case 123:
                return new int[]{2, 7, 8, 9};
            case 124:
                return new int[]{0, 1, 7, 8, 9};
            case 125:
                return new int[]{1, 7, 8, 9};
            case 126:
                return new int[]{0, 7, 8, 9};
            case 127:
                return new int[]{7, 8, 9};
            case 128:
                return new int[]{0, 1, 2, 3, 4, 5, 6, 8, 9};
            case 129:
                return new int[]{1, 2, 3, 4, 5, 6, 8, 9};
            case 130:
                return new int[]{0, 2, 3, 4, 5, 6, 8, 9};
            case 131:
                return new int[]{2, 3, 4, 5, 6, 8, 9};
            case 132:
                return new int[]{0, 1, 3, 4, 5, 6, 8, 9};
            case 133:
                return new int[]{1, 3, 4, 5, 6, 8, 9};
            case 134:
                return new int[]{0, 3, 4, 5, 6, 8, 9};
            case 135:
                return new int[]{3, 4, 5, 6, 8, 9};
            case 136:
                return new int[]{0, 1, 2, 4, 5, 6, 8, 9};
            case 137:
                return new int[]{1, 2, 4, 5, 6, 8, 9};
            case 138:
                return new int[]{0, 2, 4, 5, 6, 8, 9};
            case 139:
                return new int[]{2, 4, 5, 6, 8, 9};
            case 140:
                return new int[]{0, 1, 4, 5, 6, 8, 9};
            case 141:
                return new int[]{1, 4, 5, 6, 8, 9};
            case 142:
                return new int[]{0, 4, 5, 6, 8, 9};
            case 143:
                return new int[]{4, 5, 6, 8, 9};
            case 144:
                return new int[]{0, 1, 2, 3, 5, 6, 8, 9};
            case 145:
                return new int[]{1, 2, 3, 5, 6, 8, 9};
            case 146:
                return new int[]{0, 2, 3, 5, 6, 8, 9};
            case 147:
                return new int[]{2, 3, 5, 6, 8, 9};
            case 148:
                return new int[]{0, 1, 3, 5, 6, 8, 9};
            case 149:
                return new int[]{1, 3, 5, 6, 8, 9};
            case 150:
                return new int[]{0, 3, 5, 6, 8, 9};
            case 151:
                return new int[]{3, 5, 6, 8, 9};
            case 152:
                return new int[]{0, 1, 2, 5, 6, 8, 9};
            case 153:
                return new int[]{1, 2, 5, 6, 8, 9};
            case 154:
                return new int[]{0, 2, 5, 6, 8, 9};
            case 155:
                return new int[]{2, 5, 6, 8, 9};
            case 156:
                return new int[]{0, 1, 5, 6, 8, 9};
            case 157:
                return new int[]{1, 5, 6, 8, 9};
            case 158:
                return new int[]{0, 5, 6, 8, 9};
            case 159:
                return new int[]{5, 6, 8, 9};
            case 160:
                return new int[]{0, 1, 2, 3, 4, 6, 8, 9};
            case 161:
                return new int[]{1, 2, 3, 4, 6, 8, 9};
            case 162:
                return new int[]{0, 2, 3, 4, 6, 8, 9};
            case 163:
                return new int[]{2, 3, 4, 6, 8, 9};
            case 164:
                return new int[]{0, 1, 3, 4, 6, 8, 9};
            case 165:
                return new int[]{1, 3, 4, 6, 8, 9};
            case 166:
                return new int[]{0, 3, 4, 6, 8, 9};
            case 167:
                return new int[]{3, 4, 6, 8, 9};
            case 168:
                return new int[]{0, 1, 2, 4, 6, 8, 9};
            case 169:
                return new int[]{1, 2, 4, 6, 8, 9};
            case 170:
                return new int[]{0, 2, 4, 6, 8, 9};
            case 171:
                return new int[]{2, 4, 6, 8, 9};
            case 172:
                return new int[]{0, 1, 4, 6, 8, 9};
            case 173:
                return new int[]{1, 4, 6, 8, 9};
            case 174:
                return new int[]{0, 4, 6, 8, 9};
            case 175:
                return new int[]{4, 6, 8, 9};
            case 176:
                return new int[]{0, 1, 2, 3, 6, 8, 9};
            case 177:
                return new int[]{1, 2, 3, 6, 8, 9};
            case 178:
                return new int[]{0, 2, 3, 6, 8, 9};
            case 179:
                return new int[]{2, 3, 6, 8, 9};
            case 180:
                return new int[]{0, 1, 3, 6, 8, 9};
            case 181:
                return new int[]{1, 3, 6, 8, 9};
            case 182:
                return new int[]{0, 3, 6, 8, 9};
            case 183:
                return new int[]{3, 6, 8, 9};
            case 184:
                return new int[]{0, 1, 2, 6, 8, 9};
            case 185:
                return new int[]{1, 2, 6, 8, 9};
            case 186:
                return new int[]{0, 2, 6, 8, 9};
            case 187:
                return new int[]{2, 6, 8, 9};
            case 188:
                return new int[]{0, 1, 6, 8, 9};
            case 189:
                return new int[]{1, 6, 8, 9};
            case 190:
                return new int[]{0, 6, 8, 9};
            case 191:
                return new int[]{6, 8, 9};
            case 192:
                return new int[]{0, 1, 2, 3, 4, 5, 8, 9};
            case 193:
                return new int[]{1, 2, 3, 4, 5, 8, 9};
            case 194:
                return new int[]{0, 2, 3, 4, 5, 8, 9};
            case 195:
                return new int[]{2, 3, 4, 5, 8, 9};
            case 196:
                return new int[]{0, 1, 3, 4, 5, 8, 9};
            case 197:
                return new int[]{1, 3, 4, 5, 8, 9};
            case 198:
                return new int[]{0, 3, 4, 5, 8, 9};
            case 199:
                return new int[]{3, 4, 5, 8, 9};
            case 200:
                return new int[]{0, 1, 2, 4, 5, 8, 9};
            case 201:
                return new int[]{1, 2, 4, 5, 8, 9};
            case 202:
                return new int[]{0, 2, 4, 5, 8, 9};
            case 203:
                return new int[]{2, 4, 5, 8, 9};
            case 204:
                return new int[]{0, 1, 4, 5, 8, 9};
            case 205:
                return new int[]{1, 4, 5, 8, 9};
            case 206:
                return new int[]{0, 4, 5, 8, 9};
            case 207:
                return new int[]{4, 5, 8, 9};
            case 208:
                return new int[]{0, 1, 2, 3, 5, 8, 9};
            case 209:
                return new int[]{1, 2, 3, 5, 8, 9};
            case 210:
                return new int[]{0, 2, 3, 5, 8, 9};
            case 211:
                return new int[]{2, 3, 5, 8, 9};
            case 212:
                return new int[]{0, 1, 3, 5, 8, 9};
            case 213:
                return new int[]{1, 3, 5, 8, 9};
            case 214:
                return new int[]{0, 3, 5, 8, 9};
            case 215:
                return new int[]{3, 5, 8, 9};
            case 216:
                return new int[]{0, 1, 2, 5, 8, 9};
            case 217:
                return new int[]{1, 2, 5, 8, 9};
            case 218:
                return new int[]{0, 2, 5, 8, 9};
            case 219:
                return new int[]{2, 5, 8, 9};
            case 220:
                return new int[]{0, 1, 5, 8, 9};
            case 221:
                return new int[]{1, 5, 8, 9};
            case 222:
                return new int[]{0, 5, 8, 9};
            case 223:
                return new int[]{5, 8, 9};
            case 224:
                return new int[]{0, 1, 2, 3, 4, 8, 9};
            case 225:
                return new int[]{1, 2, 3, 4, 8, 9};
            case 226:
                return new int[]{0, 2, 3, 4, 8, 9};
            case 227:
                return new int[]{2, 3, 4, 8, 9};
            case 228:
                return new int[]{0, 1, 3, 4, 8, 9};
            case 229:
                return new int[]{1, 3, 4, 8, 9};
            case 230:
                return new int[]{0, 3, 4, 8, 9};
            case 231:
                return new int[]{3, 4, 8, 9};
            case 232:
                return new int[]{0, 1, 2, 4, 8, 9};
            case 233:
                return new int[]{1, 2, 4, 8, 9};
            case 234:
                return new int[]{0, 2, 4, 8, 9};
            case 235:
                return new int[]{2, 4, 8, 9};
            case 236:
                return new int[]{0, 1, 4, 8, 9};
            case 237:
                return new int[]{1, 4, 8, 9};
            case 238:
                return new int[]{0, 4, 8, 9};
            case 239:
                return new int[]{4, 8, 9};
            case 240:
                return new int[]{0, 1, 2, 3, 8, 9};
            case 241:
                return new int[]{1, 2, 3, 8, 9};
            case 242:
                return new int[]{0, 2, 3, 8, 9};
            case 243:
                return new int[]{2, 3, 8, 9};
            case 244:
                return new int[]{0, 1, 3, 8, 9};
            case 245:
                return new int[]{1, 3, 8, 9};
            case 246:
                return new int[]{0, 3, 8, 9};
            case 247:
                return new int[]{3, 8, 9};
            case 248:
                return new int[]{0, 1, 2, 8, 9};
            case 249:
                return new int[]{1, 2, 8, 9};
            case 250:
                return new int[]{0, 2, 8, 9};
            case 251:
                return new int[]{2, 8, 9};
            case 252:
                return new int[]{0, 1, 8, 9};
            case 253:
                return new int[]{1, 8, 9};
            case 254:
                return new int[]{0, 8, 9};
            case 255:
                return new int[]{8, 9};
            case 256:
                return new int[]{0, 1, 2, 3, 4, 5, 6, 7, 9};
            case 257:
                return new int[]{1, 2, 3, 4, 5, 6, 7, 9};
            case 258:
                return new int[]{0, 2, 3, 4, 5, 6, 7, 9};
            case 259:
                return new int[]{2, 3, 4, 5, 6, 7, 9};
            case 260:
                return new int[]{0, 1, 3, 4, 5, 6, 7, 9};
            case 261:
                return new int[]{1, 3, 4, 5, 6, 7, 9};
            case 262:
                return new int[]{0, 3, 4, 5, 6, 7, 9};
            case 263:
                return new int[]{3, 4, 5, 6, 7, 9};
            case 264:
                return new int[]{0, 1, 2, 4, 5, 6, 7, 9};
            case 265:
                return new int[]{1, 2, 4, 5, 6, 7, 9};
            case 266:
                return new int[]{0, 2, 4, 5, 6, 7, 9};
            case 267:
                return new int[]{2, 4, 5, 6, 7, 9};
            case 268:
                return new int[]{0, 1, 4, 5, 6, 7, 9};
            case 269:
                return new int[]{1, 4, 5, 6, 7, 9};
            case 270:
                return new int[]{0, 4, 5, 6, 7, 9};
            case 271:
                return new int[]{4, 5, 6, 7, 9};
            case 272:
                return new int[]{0, 1, 2, 3, 5, 6, 7, 9};
            case 273:
                return new int[]{1, 2, 3, 5, 6, 7, 9};
            case 274:
                return new int[]{0, 2, 3, 5, 6, 7, 9};
            case 275:
                return new int[]{2, 3, 5, 6, 7, 9};
            case 276:
                return new int[]{0, 1, 3, 5, 6, 7, 9};
            case 277:
                return new int[]{1, 3, 5, 6, 7, 9};
            case 278:
                return new int[]{0, 3, 5, 6, 7, 9};
            case 279:
                return new int[]{3, 5, 6, 7, 9};
            case 280:
                return new int[]{0, 1, 2, 5, 6, 7, 9};
            case 281:
                return new int[]{1, 2, 5, 6, 7, 9};
            case 282:
                return new int[]{0, 2, 5, 6, 7, 9};
            case 283:
                return new int[]{2, 5, 6, 7, 9};
            case 284:
                return new int[]{0, 1, 5, 6, 7, 9};
            case 285:
                return new int[]{1, 5, 6, 7, 9};
            case 286:
                return new int[]{0, 5, 6, 7, 9};
            case 287:
                return new int[]{5, 6, 7, 9};
            case 288:
                return new int[]{0, 1, 2, 3, 4, 6, 7, 9};
            case 289:
                return new int[]{1, 2, 3, 4, 6, 7, 9};
            case 290:
                return new int[]{0, 2, 3, 4, 6, 7, 9};
            case 291:
                return new int[]{2, 3, 4, 6, 7, 9};
            case 292:
                return new int[]{0, 1, 3, 4, 6, 7, 9};
            case 293:
                return new int[]{1, 3, 4, 6, 7, 9};
            case 294:
                return new int[]{0, 3, 4, 6, 7, 9};
            case 295:
                return new int[]{3, 4, 6, 7, 9};
            case 296:
                return new int[]{0, 1, 2, 4, 6, 7, 9};
            case 297:
                return new int[]{1, 2, 4, 6, 7, 9};
            case 298:
                return new int[]{0, 2, 4, 6, 7, 9};
            case 299:
                return new int[]{2, 4, 6, 7, 9};
            case 300:
                return new int[]{0, 1, 4, 6, 7, 9};
            case 301:
                return new int[]{1, 4, 6, 7, 9};
            case 302:
                return new int[]{0, 4, 6, 7, 9};
            case 303:
                return new int[]{4, 6, 7, 9};
            case 304:
                return new int[]{0, 1, 2, 3, 6, 7, 9};
            case 305:
                return new int[]{1, 2, 3, 6, 7, 9};
            case 306:
                return new int[]{0, 2, 3, 6, 7, 9};
            case 307:
                return new int[]{2, 3, 6, 7, 9};
            case 308:
                return new int[]{0, 1, 3, 6, 7, 9};
            case 309:
                return new int[]{1, 3, 6, 7, 9};
            case 310:
                return new int[]{0, 3, 6, 7, 9};
            case 311:
                return new int[]{3, 6, 7, 9};
            case 312:
                return new int[]{0, 1, 2, 6, 7, 9};
            case 313:
                return new int[]{1, 2, 6, 7, 9};
            case 314:
                return new int[]{0, 2, 6, 7, 9};
            case 315:
                return new int[]{2, 6, 7, 9};
            case 316:
                return new int[]{0, 1, 6, 7, 9};
            case 317:
                return new int[]{1, 6, 7, 9};
            case 318:
                return new int[]{0, 6, 7, 9};
            case 319:
                return new int[]{6, 7, 9};
            case 320:
                return new int[]{0, 1, 2, 3, 4, 5, 7, 9};
            case 321:
                return new int[]{1, 2, 3, 4, 5, 7, 9};
            case 322:
                return new int[]{0, 2, 3, 4, 5, 7, 9};
            case 323:
                return new int[]{2, 3, 4, 5, 7, 9};
            case 324:
                return new int[]{0, 1, 3, 4, 5, 7, 9};
            case 325:
                return new int[]{1, 3, 4, 5, 7, 9};
            case 326:
                return new int[]{0, 3, 4, 5, 7, 9};
            case 327:
                return new int[]{3, 4, 5, 7, 9};
            case 328:
                return new int[]{0, 1, 2, 4, 5, 7, 9};
            case 329:
                return new int[]{1, 2, 4, 5, 7, 9};
            case 330:
                return new int[]{0, 2, 4, 5, 7, 9};
            case 331:
                return new int[]{2, 4, 5, 7, 9};
            case 332:
                return new int[]{0, 1, 4, 5, 7, 9};
            case 333:
                return new int[]{1, 4, 5, 7, 9};
            case 334:
                return new int[]{0, 4, 5, 7, 9};
            case 335:
                return new int[]{4, 5, 7, 9};
            case 336:
                return new int[]{0, 1, 2, 3, 5, 7, 9};
            case 337:
                return new int[]{1, 2, 3, 5, 7, 9};
            case 338:
                return new int[]{0, 2, 3, 5, 7, 9};
            case 339:
                return new int[]{2, 3, 5, 7, 9};
            case 340:
                return new int[]{0, 1, 3, 5, 7, 9};
            case 341:
                return new int[]{1, 3, 5, 7, 9};
            case 342:
                return new int[]{0, 3, 5, 7, 9};
            case 343:
                return new int[]{3, 5, 7, 9};
            case 344:
                return new int[]{0, 1, 2, 5, 7, 9};
            case 345:
                return new int[]{1, 2, 5, 7, 9};
            case 346:
                return new int[]{0, 2, 5, 7, 9};
            case 347:
                return new int[]{2, 5, 7, 9};
            case 348:
                return new int[]{0, 1, 5, 7, 9};
            case 349:
                return new int[]{1, 5, 7, 9};
            case 350:
                return new int[]{0, 5, 7, 9};
            case 351:
                return new int[]{5, 7, 9};
            case 352:
                return new int[]{0, 1, 2, 3, 4, 7, 9};
            case 353:
                return new int[]{1, 2, 3, 4, 7, 9};
            case 354:
                return new int[]{0, 2, 3, 4, 7, 9};
            case 355:
                return new int[]{2, 3, 4, 7, 9};
            case 356:
                return new int[]{0, 1, 3, 4, 7, 9};
            case 357:
                return new int[]{1, 3, 4, 7, 9};
            case 358:
                return new int[]{0, 3, 4, 7, 9};
            case 359:
                return new int[]{3, 4, 7, 9};
            case 360:
                return new int[]{0, 1, 2, 4, 7, 9};
            case 361:
                return new int[]{1, 2, 4, 7, 9};
            case 362:
                return new int[]{0, 2, 4, 7, 9};
            case 363:
                return new int[]{2, 4, 7, 9};
            case 364:
                return new int[]{0, 1, 4, 7, 9};
            case 365:
                return new int[]{1, 4, 7, 9};
            case 366:
                return new int[]{0, 4, 7, 9};
            case 367:
                return new int[]{4, 7, 9};
            case 368:
                return new int[]{0, 1, 2, 3, 7, 9};
            case 369:
                return new int[]{1, 2, 3, 7, 9};
            case 370:
                return new int[]{0, 2, 3, 7, 9};
            case 371:
                return new int[]{2, 3, 7, 9};
            case 372:
                return new int[]{0, 1, 3, 7, 9};
            case 373:
                return new int[]{1, 3, 7, 9};
            case 374:
                return new int[]{0, 3, 7, 9};
            case 375:
                return new int[]{3, 7, 9};
            case 376:
                return new int[]{0, 1, 2, 7, 9};
            case 377:
                return new int[]{1, 2, 7, 9};
            case 378:
                return new int[]{0, 2, 7, 9};
            case 379:
                return new int[]{2, 7, 9};
            case 380:
                return new int[]{0, 1, 7, 9};
            case 381:
                return new int[]{1, 7, 9};
            case 382:
                return new int[]{0, 7, 9};
            case 383:
                return new int[]{7, 9};
            case 384:
                return new int[]{0, 1, 2, 3, 4, 5, 6, 9};
            case 385:
                return new int[]{1, 2, 3, 4, 5, 6, 9};
            case 386:
                return new int[]{0, 2, 3, 4, 5, 6, 9};
            case 387:
                return new int[]{2, 3, 4, 5, 6, 9};
            case 388:
                return new int[]{0, 1, 3, 4, 5, 6, 9};
            case 389:
                return new int[]{1, 3, 4, 5, 6, 9};
            case 390:
                return new int[]{0, 3, 4, 5, 6, 9};
            case 391:
                return new int[]{3, 4, 5, 6, 9};
            case 392:
                return new int[]{0, 1, 2, 4, 5, 6, 9};
            case 393:
                return new int[]{1, 2, 4, 5, 6, 9};
            case 394:
                return new int[]{0, 2, 4, 5, 6, 9};
            case 395:
                return new int[]{2, 4, 5, 6, 9};
            case 396:
                return new int[]{0, 1, 4, 5, 6, 9};
            case 397:
                return new int[]{1, 4, 5, 6, 9};
            case 398:
                return new int[]{0, 4, 5, 6, 9};
            case 399:
                return new int[]{4, 5, 6, 9};
            case 400:
                return new int[]{0, 1, 2, 3, 5, 6, 9};
            case 401:
                return new int[]{1, 2, 3, 5, 6, 9};
            case 402:
                return new int[]{0, 2, 3, 5, 6, 9};
            case 403:
                return new int[]{2, 3, 5, 6, 9};
            case 404:
                return new int[]{0, 1, 3, 5, 6, 9};
            case 405:
                return new int[]{1, 3, 5, 6, 9};
            case 406:
                return new int[]{0, 3, 5, 6, 9};
            case 407:
                return new int[]{3, 5, 6, 9};
            case 408:
                return new int[]{0, 1, 2, 5, 6, 9};
            case 409:
                return new int[]{1, 2, 5, 6, 9};
            case 410:
                return new int[]{0, 2, 5, 6, 9};
            case 411:
                return new int[]{2, 5, 6, 9};
            case 412:
                return new int[]{0, 1, 5, 6, 9};
            case 413:
                return new int[]{1, 5, 6, 9};
            case 414:
                return new int[]{0, 5, 6, 9};
            case 415:
                return new int[]{5, 6, 9};
            case 416:
                return new int[]{0, 1, 2, 3, 4, 6, 9};
            case 417:
                return new int[]{1, 2, 3, 4, 6, 9};
            case 418:
                return new int[]{0, 2, 3, 4, 6, 9};
            case 419:
                return new int[]{2, 3, 4, 6, 9};
            case 420:
                return new int[]{0, 1, 3, 4, 6, 9};
            case 421:
                return new int[]{1, 3, 4, 6, 9};
            case 422:
                return new int[]{0, 3, 4, 6, 9};
            case 423:
                return new int[]{3, 4, 6, 9};
            case 424:
                return new int[]{0, 1, 2, 4, 6, 9};
            case 425:
                return new int[]{1, 2, 4, 6, 9};
            case 426:
                return new int[]{0, 2, 4, 6, 9};
            case 427:
                return new int[]{2, 4, 6, 9};
            case 428:
                return new int[]{0, 1, 4, 6, 9};
            case 429:
                return new int[]{1, 4, 6, 9};
            case 430:
                return new int[]{0, 4, 6, 9};
            case 431:
                return new int[]{4, 6, 9};
            case 432:
                return new int[]{0, 1, 2, 3, 6, 9};
            case 433:
                return new int[]{1, 2, 3, 6, 9};
            case 434:
                return new int[]{0, 2, 3, 6, 9};
            case 435:
                return new int[]{2, 3, 6, 9};
            case 436:
                return new int[]{0, 1, 3, 6, 9};
            case 437:
                return new int[]{1, 3, 6, 9};
            case 438:
                return new int[]{0, 3, 6, 9};
            case 439:
                return new int[]{3, 6, 9};
            case 440:
                return new int[]{0, 1, 2, 6, 9};
            case 441:
                return new int[]{1, 2, 6, 9};
            case 442:
                return new int[]{0, 2, 6, 9};
            case 443:
                return new int[]{2, 6, 9};
            case 444:
                return new int[]{0, 1, 6, 9};
            case 445:
                return new int[]{1, 6, 9};
            case 446:
                return new int[]{0, 6, 9};
            case 447:
                return new int[]{6, 9};
            case 448:
                return new int[]{0, 1, 2, 3, 4, 5, 9};
            case 449:
                return new int[]{1, 2, 3, 4, 5, 9};
            case 450:
                return new int[]{0, 2, 3, 4, 5, 9};
            case 451:
                return new int[]{2, 3, 4, 5, 9};
            case 452:
                return new int[]{0, 1, 3, 4, 5, 9};
            case 453:
                return new int[]{1, 3, 4, 5, 9};
            case 454:
                return new int[]{0, 3, 4, 5, 9};
            case 455:
                return new int[]{3, 4, 5, 9};
            case 456:
                return new int[]{0, 1, 2, 4, 5, 9};
            case 457:
                return new int[]{1, 2, 4, 5, 9};
            case 458:
                return new int[]{0, 2, 4, 5, 9};
            case 459:
                return new int[]{2, 4, 5, 9};
            case 460:
                return new int[]{0, 1, 4, 5, 9};
            case 461:
                return new int[]{1, 4, 5, 9};
            case 462:
                return new int[]{0, 4, 5, 9};
            case 463:
                return new int[]{4, 5, 9};
            case 464:
                return new int[]{0, 1, 2, 3, 5, 9};
            case 465:
                return new int[]{1, 2, 3, 5, 9};
            case 466:
                return new int[]{0, 2, 3, 5, 9};
            case 467:
                return new int[]{2, 3, 5, 9};
            case 468:
                return new int[]{0, 1, 3, 5, 9};
            case 469:
                return new int[]{1, 3, 5, 9};
            case 470:
                return new int[]{0, 3, 5, 9};
            case 471:
                return new int[]{3, 5, 9};
            case 472:
                return new int[]{0, 1, 2, 5, 9};
            case 473:
                return new int[]{1, 2, 5, 9};
            case 474:
                return new int[]{0, 2, 5, 9};
            case 475:
                return new int[]{2, 5, 9};
            case 476:
                return new int[]{0, 1, 5, 9};
            case 477:
                return new int[]{1, 5, 9};
            case 478:
                return new int[]{0, 5, 9};
            case 479:
                return new int[]{5, 9};
            case 480:
                return new int[]{0, 1, 2, 3, 4, 9};
            case 481:
                return new int[]{1, 2, 3, 4, 9};
            case 482:
                return new int[]{0, 2, 3, 4, 9};
            case 483:
                return new int[]{2, 3, 4, 9};
            case 484:
                return new int[]{0, 1, 3, 4, 9};
            case 485:
                return new int[]{1, 3, 4, 9};
            case 486:
                return new int[]{0, 3, 4, 9};
            case 487:
                return new int[]{3, 4, 9};
            case 488:
                return new int[]{0, 1, 2, 4, 9};
            case 489:
                return new int[]{1, 2, 4, 9};
            case 490:
                return new int[]{0, 2, 4, 9};
            case 491:
                return new int[]{2, 4, 9};
            case 492:
                return new int[]{0, 1, 4, 9};
            case 493:
                return new int[]{1, 4, 9};
            case 494:
                return new int[]{0, 4, 9};
            case 495:
                return new int[]{4, 9};
            case 496:
                return new int[]{0, 1, 2, 3, 9};
            case 497:
                return new int[]{1, 2, 3, 9};
            case 498:
                return new int[]{0, 2, 3, 9};
            case 499:
                return new int[]{2, 3, 9};
            case 500:
                return new int[]{0, 1, 3, 9};
            case 501:
                return new int[]{1, 3, 9};
            case 502:
                return new int[]{0, 3, 9};
            case 503:
                return new int[]{3, 9};
            case 504:
                return new int[]{0, 1, 2, 9};
            case 505:
                return new int[]{1, 2, 9};
            case 506:
                return new int[]{0, 2, 9};
            case 507:
                return new int[]{2, 9};
            case 508:
                return new int[]{0, 1, 9};
            case 509:
                return new int[]{1, 9};
            case 510:
                return new int[]{0, 9};
            case 511:
                return new int[]{9};
            case 512:
                return new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
            case 513:
                return new int[]{1, 2, 3, 4, 5, 6, 7, 8};
            case 514:
                return new int[]{0, 2, 3, 4, 5, 6, 7, 8};
            case 515:
                return new int[]{2, 3, 4, 5, 6, 7, 8};
            case 516:
                return new int[]{0, 1, 3, 4, 5, 6, 7, 8};
            case 517:
                return new int[]{1, 3, 4, 5, 6, 7, 8};
            case 518:
                return new int[]{0, 3, 4, 5, 6, 7, 8};
            case 519:
                return new int[]{3, 4, 5, 6, 7, 8};
            case 520:
                return new int[]{0, 1, 2, 4, 5, 6, 7, 8};
            case 521:
                return new int[]{1, 2, 4, 5, 6, 7, 8};
            case 522:
                return new int[]{0, 2, 4, 5, 6, 7, 8};
            case 523:
                return new int[]{2, 4, 5, 6, 7, 8};
            case 524:
                return new int[]{0, 1, 4, 5, 6, 7, 8};
            case 525:
                return new int[]{1, 4, 5, 6, 7, 8};
            case 526:
                return new int[]{0, 4, 5, 6, 7, 8};
            case 527:
                return new int[]{4, 5, 6, 7, 8};
            case 528:
                return new int[]{0, 1, 2, 3, 5, 6, 7, 8};
            case 529:
                return new int[]{1, 2, 3, 5, 6, 7, 8};
            case 530:
                return new int[]{0, 2, 3, 5, 6, 7, 8};
            case 531:
                return new int[]{2, 3, 5, 6, 7, 8};
            case 532:
                return new int[]{0, 1, 3, 5, 6, 7, 8};
            case 533:
                return new int[]{1, 3, 5, 6, 7, 8};
            case 534:
                return new int[]{0, 3, 5, 6, 7, 8};
            case 535:
                return new int[]{3, 5, 6, 7, 8};
            case 536:
                return new int[]{0, 1, 2, 5, 6, 7, 8};
            case 537:
                return new int[]{1, 2, 5, 6, 7, 8};
            case 538:
                return new int[]{0, 2, 5, 6, 7, 8};
            case 539:
                return new int[]{2, 5, 6, 7, 8};
            case 540:
                return new int[]{0, 1, 5, 6, 7, 8};
            case 541:
                return new int[]{1, 5, 6, 7, 8};
            case 542:
                return new int[]{0, 5, 6, 7, 8};
            case 543:
                return new int[]{5, 6, 7, 8};
            case 544:
                return new int[]{0, 1, 2, 3, 4, 6, 7, 8};
            case 545:
                return new int[]{1, 2, 3, 4, 6, 7, 8};
            case 546:
                return new int[]{0, 2, 3, 4, 6, 7, 8};
            case 547:
                return new int[]{2, 3, 4, 6, 7, 8};
            case 548:
                return new int[]{0, 1, 3, 4, 6, 7, 8};
            case 549:
                return new int[]{1, 3, 4, 6, 7, 8};
            case 550:
                return new int[]{0, 3, 4, 6, 7, 8};
            case 551:
                return new int[]{3, 4, 6, 7, 8};
            case 552:
                return new int[]{0, 1, 2, 4, 6, 7, 8};
            case 553:
                return new int[]{1, 2, 4, 6, 7, 8};
            case 554:
                return new int[]{0, 2, 4, 6, 7, 8};
            case 555:
                return new int[]{2, 4, 6, 7, 8};
            case 556:
                return new int[]{0, 1, 4, 6, 7, 8};
            case 557:
                return new int[]{1, 4, 6, 7, 8};
            case 558:
                return new int[]{0, 4, 6, 7, 8};
            case 559:
                return new int[]{4, 6, 7, 8};
            case 560:
                return new int[]{0, 1, 2, 3, 6, 7, 8};
            case 561:
                return new int[]{1, 2, 3, 6, 7, 8};
            case 562:
                return new int[]{0, 2, 3, 6, 7, 8};
            case 563:
                return new int[]{2, 3, 6, 7, 8};
            case 564:
                return new int[]{0, 1, 3, 6, 7, 8};
            case 565:
                return new int[]{1, 3, 6, 7, 8};
            case 566:
                return new int[]{0, 3, 6, 7, 8};
            case 567:
                return new int[]{3, 6, 7, 8};
            case 568:
                return new int[]{0, 1, 2, 6, 7, 8};
            case 569:
                return new int[]{1, 2, 6, 7, 8};
            case 570:
                return new int[]{0, 2, 6, 7, 8};
            case 571:
                return new int[]{2, 6, 7, 8};
            case 572:
                return new int[]{0, 1, 6, 7, 8};
            case 573:
                return new int[]{1, 6, 7, 8};
            case 574:
                return new int[]{0, 6, 7, 8};
            case 575:
                return new int[]{6, 7, 8};
            case 576:
                return new int[]{0, 1, 2, 3, 4, 5, 7, 8};
            case 577:
                return new int[]{1, 2, 3, 4, 5, 7, 8};
            case 578:
                return new int[]{0, 2, 3, 4, 5, 7, 8};
            case 579:
                return new int[]{2, 3, 4, 5, 7, 8};
            case 580:
                return new int[]{0, 1, 3, 4, 5, 7, 8};
            case 581:
                return new int[]{1, 3, 4, 5, 7, 8};
            case 582:
                return new int[]{0, 3, 4, 5, 7, 8};
            case 583:
                return new int[]{3, 4, 5, 7, 8};
            case 584:
                return new int[]{0, 1, 2, 4, 5, 7, 8};
            case 585:
                return new int[]{1, 2, 4, 5, 7, 8};
            case 586:
                return new int[]{0, 2, 4, 5, 7, 8};
            case 587:
                return new int[]{2, 4, 5, 7, 8};
            case 588:
                return new int[]{0, 1, 4, 5, 7, 8};
            case 589:
                return new int[]{1, 4, 5, 7, 8};
            case 590:
                return new int[]{0, 4, 5, 7, 8};
            case 591:
                return new int[]{4, 5, 7, 8};
            case 592:
                return new int[]{0, 1, 2, 3, 5, 7, 8};
            case 593:
                return new int[]{1, 2, 3, 5, 7, 8};
            case 594:
                return new int[]{0, 2, 3, 5, 7, 8};
            case 595:
                return new int[]{2, 3, 5, 7, 8};
            case 596:
                return new int[]{0, 1, 3, 5, 7, 8};
            case 597:
                return new int[]{1, 3, 5, 7, 8};
            case 598:
                return new int[]{0, 3, 5, 7, 8};
            case 599:
                return new int[]{3, 5, 7, 8};
            case 600:
                return new int[]{0, 1, 2, 5, 7, 8};
            case 601:
                return new int[]{1, 2, 5, 7, 8};
            case 602:
                return new int[]{0, 2, 5, 7, 8};
            case 603:
                return new int[]{2, 5, 7, 8};
            case 604:
                return new int[]{0, 1, 5, 7, 8};
            case 605:
                return new int[]{1, 5, 7, 8};
            case 606:
                return new int[]{0, 5, 7, 8};
            case 607:
                return new int[]{5, 7, 8};
            case 608:
                return new int[]{0, 1, 2, 3, 4, 7, 8};
            case 609:
                return new int[]{1, 2, 3, 4, 7, 8};
            case 610:
                return new int[]{0, 2, 3, 4, 7, 8};
            case 611:
                return new int[]{2, 3, 4, 7, 8};
            case 612:
                return new int[]{0, 1, 3, 4, 7, 8};
            case 613:
                return new int[]{1, 3, 4, 7, 8};
            case 614:
                return new int[]{0, 3, 4, 7, 8};
            case 615:
                return new int[]{3, 4, 7, 8};
            case 616:
                return new int[]{0, 1, 2, 4, 7, 8};
            case 617:
                return new int[]{1, 2, 4, 7, 8};
            case 618:
                return new int[]{0, 2, 4, 7, 8};
            case 619:
                return new int[]{2, 4, 7, 8};
            case 620:
                return new int[]{0, 1, 4, 7, 8};
            case 621:
                return new int[]{1, 4, 7, 8};
            case 622:
                return new int[]{0, 4, 7, 8};
            case 623:
                return new int[]{4, 7, 8};
            case 624:
                return new int[]{0, 1, 2, 3, 7, 8};
            case 625:
                return new int[]{1, 2, 3, 7, 8};
            case 626:
                return new int[]{0, 2, 3, 7, 8};
            case 627:
                return new int[]{2, 3, 7, 8};
            case 628:
                return new int[]{0, 1, 3, 7, 8};
            case 629:
                return new int[]{1, 3, 7, 8};
            case 630:
                return new int[]{0, 3, 7, 8};
            case 631:
                return new int[]{3, 7, 8};
            case 632:
                return new int[]{0, 1, 2, 7, 8};
            case 633:
                return new int[]{1, 2, 7, 8};
            case 634:
                return new int[]{0, 2, 7, 8};
            case 635:
                return new int[]{2, 7, 8};
            case 636:
                return new int[]{0, 1, 7, 8};
            case 637:
                return new int[]{1, 7, 8};
            case 638:
                return new int[]{0, 7, 8};
            case 639:
                return new int[]{7, 8};
            case 640:
                return new int[]{0, 1, 2, 3, 4, 5, 6, 8};
            case 641:
                return new int[]{1, 2, 3, 4, 5, 6, 8};
            case 642:
                return new int[]{0, 2, 3, 4, 5, 6, 8};
            case 643:
                return new int[]{2, 3, 4, 5, 6, 8};
            case 644:
                return new int[]{0, 1, 3, 4, 5, 6, 8};
            case 645:
                return new int[]{1, 3, 4, 5, 6, 8};
            case 646:
                return new int[]{0, 3, 4, 5, 6, 8};
            case 647:
                return new int[]{3, 4, 5, 6, 8};
            case 648:
                return new int[]{0, 1, 2, 4, 5, 6, 8};
            case 649:
                return new int[]{1, 2, 4, 5, 6, 8};
            case 650:
                return new int[]{0, 2, 4, 5, 6, 8};
            case 651:
                return new int[]{2, 4, 5, 6, 8};
            case 652:
                return new int[]{0, 1, 4, 5, 6, 8};
            case 653:
                return new int[]{1, 4, 5, 6, 8};
            case 654:
                return new int[]{0, 4, 5, 6, 8};
            case 655:
                return new int[]{4, 5, 6, 8};
            case 656:
                return new int[]{0, 1, 2, 3, 5, 6, 8};
            case 657:
                return new int[]{1, 2, 3, 5, 6, 8};
            case 658:
                return new int[]{0, 2, 3, 5, 6, 8};
            case 659:
                return new int[]{2, 3, 5, 6, 8};
            case 660:
                return new int[]{0, 1, 3, 5, 6, 8};
            case 661:
                return new int[]{1, 3, 5, 6, 8};
            case 662:
                return new int[]{0, 3, 5, 6, 8};
            case 663:
                return new int[]{3, 5, 6, 8};
            case 664:
                return new int[]{0, 1, 2, 5, 6, 8};
            case 665:
                return new int[]{1, 2, 5, 6, 8};
            case 666:
                return new int[]{0, 2, 5, 6, 8};
            case 667:
                return new int[]{2, 5, 6, 8};
            case 668:
                return new int[]{0, 1, 5, 6, 8};
            case 669:
                return new int[]{1, 5, 6, 8};
            case 670:
                return new int[]{0, 5, 6, 8};
            case 671:
                return new int[]{5, 6, 8};
            case 672:
                return new int[]{0, 1, 2, 3, 4, 6, 8};
            case 673:
                return new int[]{1, 2, 3, 4, 6, 8};
            case 674:
                return new int[]{0, 2, 3, 4, 6, 8};
            case 675:
                return new int[]{2, 3, 4, 6, 8};
            case 676:
                return new int[]{0, 1, 3, 4, 6, 8};
            case 677:
                return new int[]{1, 3, 4, 6, 8};
            case 678:
                return new int[]{0, 3, 4, 6, 8};
            case 679:
                return new int[]{3, 4, 6, 8};
            case 680:
                return new int[]{0, 1, 2, 4, 6, 8};
            case 681:
                return new int[]{1, 2, 4, 6, 8};
            case 682:
                return new int[]{0, 2, 4, 6, 8};
            case 683:
                return new int[]{2, 4, 6, 8};
            case 684:
                return new int[]{0, 1, 4, 6, 8};
            case 685:
                return new int[]{1, 4, 6, 8};
            case 686:
                return new int[]{0, 4, 6, 8};
            case 687:
                return new int[]{4, 6, 8};
            case 688:
                return new int[]{0, 1, 2, 3, 6, 8};
            case 689:
                return new int[]{1, 2, 3, 6, 8};
            case 690:
                return new int[]{0, 2, 3, 6, 8};
            case 691:
                return new int[]{2, 3, 6, 8};
            case 692:
                return new int[]{0, 1, 3, 6, 8};
            case 693:
                return new int[]{1, 3, 6, 8};
            case 694:
                return new int[]{0, 3, 6, 8};
            case 695:
                return new int[]{3, 6, 8};
            case 696:
                return new int[]{0, 1, 2, 6, 8};
            case 697:
                return new int[]{1, 2, 6, 8};
            case 698:
                return new int[]{0, 2, 6, 8};
            case 699:
                return new int[]{2, 6, 8};
            case 700:
                return new int[]{0, 1, 6, 8};
            case 701:
                return new int[]{1, 6, 8};
            case 702:
                return new int[]{0, 6, 8};
            case 703:
                return new int[]{6, 8};
            case 704:
                return new int[]{0, 1, 2, 3, 4, 5, 8};
            case 705:
                return new int[]{1, 2, 3, 4, 5, 8};
            case 706:
                return new int[]{0, 2, 3, 4, 5, 8};
            case 707:
                return new int[]{2, 3, 4, 5, 8};
            case 708:
                return new int[]{0, 1, 3, 4, 5, 8};
            case 709:
                return new int[]{1, 3, 4, 5, 8};
            case 710:
                return new int[]{0, 3, 4, 5, 8};
            case 711:
                return new int[]{3, 4, 5, 8};
            case 712:
                return new int[]{0, 1, 2, 4, 5, 8};
            case 713:
                return new int[]{1, 2, 4, 5, 8};
            case 714:
                return new int[]{0, 2, 4, 5, 8};
            case 715:
                return new int[]{2, 4, 5, 8};
            case 716:
                return new int[]{0, 1, 4, 5, 8};
            case 717:
                return new int[]{1, 4, 5, 8};
            case 718:
                return new int[]{0, 4, 5, 8};
            case 719:
                return new int[]{4, 5, 8};
            case 720:
                return new int[]{0, 1, 2, 3, 5, 8};
            case 721:
                return new int[]{1, 2, 3, 5, 8};
            case 722:
                return new int[]{0, 2, 3, 5, 8};
            case 723:
                return new int[]{2, 3, 5, 8};
            case 724:
                return new int[]{0, 1, 3, 5, 8};
            case 725:
                return new int[]{1, 3, 5, 8};
            case 726:
                return new int[]{0, 3, 5, 8};
            case 727:
                return new int[]{3, 5, 8};
            case 728:
                return new int[]{0, 1, 2, 5, 8};
            case 729:
                return new int[]{1, 2, 5, 8};
            case 730:
                return new int[]{0, 2, 5, 8};
            case 731:
                return new int[]{2, 5, 8};
            case 732:
                return new int[]{0, 1, 5, 8};
            case 733:
                return new int[]{1, 5, 8};
            case 734:
                return new int[]{0, 5, 8};
            case 735:
                return new int[]{5, 8};
            case 736:
                return new int[]{0, 1, 2, 3, 4, 8};
            case 737:
                return new int[]{1, 2, 3, 4, 8};
            case 738:
                return new int[]{0, 2, 3, 4, 8};
            case 739:
                return new int[]{2, 3, 4, 8};
            case 740:
                return new int[]{0, 1, 3, 4, 8};
            case 741:
                return new int[]{1, 3, 4, 8};
            case 742:
                return new int[]{0, 3, 4, 8};
            case 743:
                return new int[]{3, 4, 8};
            case 744:
                return new int[]{0, 1, 2, 4, 8};
            case 745:
                return new int[]{1, 2, 4, 8};
            case 746:
                return new int[]{0, 2, 4, 8};
            case 747:
                return new int[]{2, 4, 8};
            case 748:
                return new int[]{0, 1, 4, 8};
            case 749:
                return new int[]{1, 4, 8};
            case 750:
                return new int[]{0, 4, 8};
            case 751:
                return new int[]{4, 8};
            case 752:
                return new int[]{0, 1, 2, 3, 8};
            case 753:
                return new int[]{1, 2, 3, 8};
            case 754:
                return new int[]{0, 2, 3, 8};
            case 755:
                return new int[]{2, 3, 8};
            case 756:
                return new int[]{0, 1, 3, 8};
            case 757:
                return new int[]{1, 3, 8};
            case 758:
                return new int[]{0, 3, 8};
            case 759:
                return new int[]{3, 8};
            case 760:
                return new int[]{0, 1, 2, 8};
            case 761:
                return new int[]{1, 2, 8};
            case 762:
                return new int[]{0, 2, 8};
            case 763:
                return new int[]{2, 8};
            case 764:
                return new int[]{0, 1, 8};
            case 765:
                return new int[]{1, 8};
            case 766:
                return new int[]{0, 8};
            case 767:
                return new int[]{8};
            case 768:
                return new int[]{0, 1, 2, 3, 4, 5, 6, 7};
            case 769:
                return new int[]{1, 2, 3, 4, 5, 6, 7};
            case 770:
                return new int[]{0, 2, 3, 4, 5, 6, 7};
            case 771:
                return new int[]{2, 3, 4, 5, 6, 7};
            case 772:
                return new int[]{0, 1, 3, 4, 5, 6, 7};
            case 773:
                return new int[]{1, 3, 4, 5, 6, 7};
            case 774:
                return new int[]{0, 3, 4, 5, 6, 7};
            case 775:
                return new int[]{3, 4, 5, 6, 7};
            case 776:
                return new int[]{0, 1, 2, 4, 5, 6, 7};
            case 777:
                return new int[]{1, 2, 4, 5, 6, 7};
            case 778:
                return new int[]{0, 2, 4, 5, 6, 7};
            case 779:
                return new int[]{2, 4, 5, 6, 7};
            case 780:
                return new int[]{0, 1, 4, 5, 6, 7};
            case 781:
                return new int[]{1, 4, 5, 6, 7};
            case 782:
                return new int[]{0, 4, 5, 6, 7};
            case 783:
                return new int[]{4, 5, 6, 7};
            case 784:
                return new int[]{0, 1, 2, 3, 5, 6, 7};
            case 785:
                return new int[]{1, 2, 3, 5, 6, 7};
            case 786:
                return new int[]{0, 2, 3, 5, 6, 7};
            case 787:
                return new int[]{2, 3, 5, 6, 7};
            case 788:
                return new int[]{0, 1, 3, 5, 6, 7};
            case 789:
                return new int[]{1, 3, 5, 6, 7};
            case 790:
                return new int[]{0, 3, 5, 6, 7};
            case 791:
                return new int[]{3, 5, 6, 7};
            case 792:
                return new int[]{0, 1, 2, 5, 6, 7};
            case 793:
                return new int[]{1, 2, 5, 6, 7};
            case 794:
                return new int[]{0, 2, 5, 6, 7};
            case 795:
                return new int[]{2, 5, 6, 7};
            case 796:
                return new int[]{0, 1, 5, 6, 7};
            case 797:
                return new int[]{1, 5, 6, 7};
            case 798:
                return new int[]{0, 5, 6, 7};
            case 799:
                return new int[]{5, 6, 7};
            case 800:
                return new int[]{0, 1, 2, 3, 4, 6, 7};
            case 801:
                return new int[]{1, 2, 3, 4, 6, 7};
            case 802:
                return new int[]{0, 2, 3, 4, 6, 7};
            case 803:
                return new int[]{2, 3, 4, 6, 7};
            case 804:
                return new int[]{0, 1, 3, 4, 6, 7};
            case 805:
                return new int[]{1, 3, 4, 6, 7};
            case 806:
                return new int[]{0, 3, 4, 6, 7};
            case 807:
                return new int[]{3, 4, 6, 7};
            case 808:
                return new int[]{0, 1, 2, 4, 6, 7};
            case 809:
                return new int[]{1, 2, 4, 6, 7};
            case 810:
                return new int[]{0, 2, 4, 6, 7};
            case 811:
                return new int[]{2, 4, 6, 7};
            case 812:
                return new int[]{0, 1, 4, 6, 7};
            case 813:
                return new int[]{1, 4, 6, 7};
            case 814:
                return new int[]{0, 4, 6, 7};
            case 815:
                return new int[]{4, 6, 7};
            case 816:
                return new int[]{0, 1, 2, 3, 6, 7};
            case 817:
                return new int[]{1, 2, 3, 6, 7};
            case 818:
                return new int[]{0, 2, 3, 6, 7};
            case 819:
                return new int[]{2, 3, 6, 7};
            case 820:
                return new int[]{0, 1, 3, 6, 7};
            case 821:
                return new int[]{1, 3, 6, 7};
            case 822:
                return new int[]{0, 3, 6, 7};
            case 823:
                return new int[]{3, 6, 7};
            case 824:
                return new int[]{0, 1, 2, 6, 7};
            case 825:
                return new int[]{1, 2, 6, 7};
            case 826:
                return new int[]{0, 2, 6, 7};
            case 827:
                return new int[]{2, 6, 7};
            case 828:
                return new int[]{0, 1, 6, 7};
            case 829:
                return new int[]{1, 6, 7};
            case 830:
                return new int[]{0, 6, 7};
            case 831:
                return new int[]{6, 7};
            case 832:
                return new int[]{0, 1, 2, 3, 4, 5, 7};
            case 833:
                return new int[]{1, 2, 3, 4, 5, 7};
            case 834:
                return new int[]{0, 2, 3, 4, 5, 7};
            case 835:
                return new int[]{2, 3, 4, 5, 7};
            case 836:
                return new int[]{0, 1, 3, 4, 5, 7};
            case 837:
                return new int[]{1, 3, 4, 5, 7};
            case 838:
                return new int[]{0, 3, 4, 5, 7};
            case 839:
                return new int[]{3, 4, 5, 7};
            case 840:
                return new int[]{0, 1, 2, 4, 5, 7};
            case 841:
                return new int[]{1, 2, 4, 5, 7};
            case 842:
                return new int[]{0, 2, 4, 5, 7};
            case 843:
                return new int[]{2, 4, 5, 7};
            case 844:
                return new int[]{0, 1, 4, 5, 7};
            case 845:
                return new int[]{1, 4, 5, 7};
            case 846:
                return new int[]{0, 4, 5, 7};
            case 847:
                return new int[]{4, 5, 7};
            case 848:
                return new int[]{0, 1, 2, 3, 5, 7};
            case 849:
                return new int[]{1, 2, 3, 5, 7};
            case 850:
                return new int[]{0, 2, 3, 5, 7};
            case 851:
                return new int[]{2, 3, 5, 7};
            case 852:
                return new int[]{0, 1, 3, 5, 7};
            case 853:
                return new int[]{1, 3, 5, 7};
            case 854:
                return new int[]{0, 3, 5, 7};
            case 855:
                return new int[]{3, 5, 7};
            case 856:
                return new int[]{0, 1, 2, 5, 7};
            case 857:
                return new int[]{1, 2, 5, 7};
            case 858:
                return new int[]{0, 2, 5, 7};
            case 859:
                return new int[]{2, 5, 7};
            case 860:
                return new int[]{0, 1, 5, 7};
            case 861:
                return new int[]{1, 5, 7};
            case 862:
                return new int[]{0, 5, 7};
            case 863:
                return new int[]{5, 7};
            case 864:
                return new int[]{0, 1, 2, 3, 4, 7};
            case 865:
                return new int[]{1, 2, 3, 4, 7};
            case 866:
                return new int[]{0, 2, 3, 4, 7};
            case 867:
                return new int[]{2, 3, 4, 7};
            case 868:
                return new int[]{0, 1, 3, 4, 7};
            case 869:
                return new int[]{1, 3, 4, 7};
            case 870:
                return new int[]{0, 3, 4, 7};
            case 871:
                return new int[]{3, 4, 7};
            case 872:
                return new int[]{0, 1, 2, 4, 7};
            case 873:
                return new int[]{1, 2, 4, 7};
            case 874:
                return new int[]{0, 2, 4, 7};
            case 875:
                return new int[]{2, 4, 7};
            case 876:
                return new int[]{0, 1, 4, 7};
            case 877:
                return new int[]{1, 4, 7};
            case 878:
                return new int[]{0, 4, 7};
            case 879:
                return new int[]{4, 7};
            case 880:
                return new int[]{0, 1, 2, 3, 7};
            case 881:
                return new int[]{1, 2, 3, 7};
            case 882:
                return new int[]{0, 2, 3, 7};
            case 883:
                return new int[]{2, 3, 7};
            case 884:
                return new int[]{0, 1, 3, 7};
            case 885:
                return new int[]{1, 3, 7};
            case 886:
                return new int[]{0, 3, 7};
            case 887:
                return new int[]{3, 7};
            case 888:
                return new int[]{0, 1, 2, 7};
            case 889:
                return new int[]{1, 2, 7};
            case 890:
                return new int[]{0, 2, 7};
            case 891:
                return new int[]{2, 7};
            case 892:
                return new int[]{0, 1, 7};
            case 893:
                return new int[]{1, 7};
            case 894:
                return new int[]{0, 7};
            case 895:
                return new int[]{7};
            case 896:
                return new int[]{0, 1, 2, 3, 4, 5, 6};
            case 897:
                return new int[]{1, 2, 3, 4, 5, 6};
            case 898:
                return new int[]{0, 2, 3, 4, 5, 6};
            case 899:
                return new int[]{2, 3, 4, 5, 6};
            case 900:
                return new int[]{0, 1, 3, 4, 5, 6};
            case 901:
                return new int[]{1, 3, 4, 5, 6};
            case 902:
                return new int[]{0, 3, 4, 5, 6};
            case 903:
                return new int[]{3, 4, 5, 6};
            case 904:
                return new int[]{0, 1, 2, 4, 5, 6};
            case 905:
                return new int[]{1, 2, 4, 5, 6};
            case 906:
                return new int[]{0, 2, 4, 5, 6};
            case 907:
                return new int[]{2, 4, 5, 6};
            case 908:
                return new int[]{0, 1, 4, 5, 6};
            case 909:
                return new int[]{1, 4, 5, 6};
            case 910:
                return new int[]{0, 4, 5, 6};
            case 911:
                return new int[]{4, 5, 6};
            case 912:
                return new int[]{0, 1, 2, 3, 5, 6};
            case 913:
                return new int[]{1, 2, 3, 5, 6};
            case 914:
                return new int[]{0, 2, 3, 5, 6};
            case 915:
                return new int[]{2, 3, 5, 6};
            case 916:
                return new int[]{0, 1, 3, 5, 6};
            case 917:
                return new int[]{1, 3, 5, 6};
            case 918:
                return new int[]{0, 3, 5, 6};
            case 919:
                return new int[]{3, 5, 6};
            case 920:
                return new int[]{0, 1, 2, 5, 6};
            case 921:
                return new int[]{1, 2, 5, 6};
            case 922:
                return new int[]{0, 2, 5, 6};
            case 923:
                return new int[]{2, 5, 6};
            case 924:
                return new int[]{0, 1, 5, 6};
            case 925:
                return new int[]{1, 5, 6};
            case 926:
                return new int[]{0, 5, 6};
            case 927:
                return new int[]{5, 6};
            case 928:
                return new int[]{0, 1, 2, 3, 4, 6};
            case 929:
                return new int[]{1, 2, 3, 4, 6};
            case 930:
                return new int[]{0, 2, 3, 4, 6};
            case 931:
                return new int[]{2, 3, 4, 6};
            case 932:
                return new int[]{0, 1, 3, 4, 6};
            case 933:
                return new int[]{1, 3, 4, 6};
            case 934:
                return new int[]{0, 3, 4, 6};
            case 935:
                return new int[]{3, 4, 6};
            case 936:
                return new int[]{0, 1, 2, 4, 6};
            case 937:
                return new int[]{1, 2, 4, 6};
            case 938:
                return new int[]{0, 2, 4, 6};
            case 939:
                return new int[]{2, 4, 6};
            case 940:
                return new int[]{0, 1, 4, 6};
            case 941:
                return new int[]{1, 4, 6};
            case 942:
                return new int[]{0, 4, 6};
            case 943:
                return new int[]{4, 6};
            case 944:
                return new int[]{0, 1, 2, 3, 6};
            case 945:
                return new int[]{1, 2, 3, 6};
            case 946:
                return new int[]{0, 2, 3, 6};
            case 947:
                return new int[]{2, 3, 6};
            case 948:
                return new int[]{0, 1, 3, 6};
            case 949:
                return new int[]{1, 3, 6};
            case 950:
                return new int[]{0, 3, 6};
            case 951:
                return new int[]{3, 6};
            case 952:
                return new int[]{0, 1, 2, 6};
            case 953:
                return new int[]{1, 2, 6};
            case 954:
                return new int[]{0, 2, 6};
            case 955:
                return new int[]{2, 6};
            case 956:
                return new int[]{0, 1, 6};
            case 957:
                return new int[]{1, 6};
            case 958:
                return new int[]{0, 6};
            case 959:
                return new int[]{6};
            case 960:
                return new int[]{0, 1, 2, 3, 4, 5};
            case 961:
                return new int[]{1, 2, 3, 4, 5};
            case 962:
                return new int[]{0, 2, 3, 4, 5};
            case 963:
                return new int[]{2, 3, 4, 5};
            case 964:
                return new int[]{0, 1, 3, 4, 5};
            case 965:
                return new int[]{1, 3, 4, 5};
            case 966:
                return new int[]{0, 3, 4, 5};
            case 967:
                return new int[]{3, 4, 5};
            case 968:
                return new int[]{0, 1, 2, 4, 5};
            case 969:
                return new int[]{1, 2, 4, 5};
            case 970:
                return new int[]{0, 2, 4, 5};
            case 971:
                return new int[]{2, 4, 5};
            case 972:
                return new int[]{0, 1, 4, 5};
            case 973:
                return new int[]{1, 4, 5};
            case 974:
                return new int[]{0, 4, 5};
            case 975:
                return new int[]{4, 5};
            case 976:
                return new int[]{0, 1, 2, 3, 5};
            case 977:
                return new int[]{1, 2, 3, 5};
            case 978:
                return new int[]{0, 2, 3, 5};
            case 979:
                return new int[]{2, 3, 5};
            case 980:
                return new int[]{0, 1, 3, 5};
            case 981:
                return new int[]{1, 3, 5};
            case 982:
                return new int[]{0, 3, 5};
            case 983:
                return new int[]{3, 5};
            case 984:
                return new int[]{0, 1, 2, 5};
            case 985:
                return new int[]{1, 2, 5};
            case 986:
                return new int[]{0, 2, 5};
            case 987:
                return new int[]{2, 5};
            case 988:
                return new int[]{0, 1, 5};
            case 989:
                return new int[]{1, 5};
            case 990:
                return new int[]{0, 5};
            case 991:
                return new int[]{5};
            case 992:
                return new int[]{0, 1, 2, 3, 4};
            case 993:
                return new int[]{1, 2, 3, 4};
            case 994:
                return new int[]{0, 2, 3, 4};
            case 995:
                return new int[]{2, 3, 4};
            case 996:
                return new int[]{0, 1, 3, 4};
            case 997:
                return new int[]{1, 3, 4};
            case 998:
                return new int[]{0, 3, 4};
            case 999:
                return new int[]{3, 4};
            case 1000:
                return new int[]{0, 1, 2, 4};
            case 1001:
                return new int[]{1, 2, 4};
            case 1002:
                return new int[]{0, 2, 4};
            case 1003:
                return new int[]{2, 4};
            case 1004:
                return new int[]{0, 1, 4};
            case 1005:
                return new int[]{1, 4};
            case 1006:
                return new int[]{0, 4};
            case 1007:
                return new int[]{4};
            case 1008:
                return new int[]{0, 1, 2, 3};
            case 1009:
                return new int[]{1, 2, 3};
            case 1010:
                return new int[]{0, 2, 3};
            case 1011:
                return new int[]{2, 3};
            case 1012:
                return new int[]{0, 1, 3};
            case 1013:
                return new int[]{1, 3};
            case 1014:
                return new int[]{0, 3};
            case 1015:
                return new int[]{3};
            case 1016:
                return new int[]{0, 1, 2};
            case 1017:
                return new int[]{1, 2};
            case 1018:
                return new int[]{0, 2};
            case 1019:
                return new int[]{2};
            case 1020:
                return new int[]{0, 1};
            case 1021:
                return new int[]{1};
            case 1022:
                return new int[]{0};
            case 1023:
                return new int[]{};
        }
        return new int[]{};
    }

    private class EnemyInfo {
        private RobotInfo nearest;
        private int distToNearest;
        private int droneCount;
        private RobotInfo[] nearby;

        public RobotInfo getNearest() {
            return nearest;
        }

        public int getDistToNearest() {
            return distToNearest;
        }

        public int getDroneCount() {
            return droneCount;
        }

        public EnemyInfo invoke() throws GameActionException {
            RobotInfo[] enemyRobots = rc.senseNearbyRobots(rc.getCurrentSensorRadiusSquared());
            nearby = enemyRobots;
            nearest = null;
            distToNearest = MAX_SQUARED_DISTANCE;
            droneCount = 0;
            boolean bSchool = false;
            for (RobotInfo x : enemyRobots) {
                if (!x.getTeam().equals(allyTeam) && x.getType() == RobotType.DESIGN_SCHOOL) {
                    bSchool = true;
                    break;
                }
            }
            for (RobotInfo enemyRobot : enemyRobots) {
                if (enemyRobot.team != allyTeam && enemyRobot.type == RobotType.HQ && !hasSentEnemyLoc) {
                    if (enemyLocation != ENEMY_HQ_LOCATION) {
                        ENEMY_HQ_LOCATION = enemyRobot.getLocation();
                        enemyLocation = ENEMY_HQ_LOCATION;
                        LocationMessage l = new LocationMessage(MAP_HEIGHT, MAP_WIDTH, teamNum, rc.getRoundNum());
                        l.writeInformation(enemyLocation.x, enemyLocation.y, 1);
                        if (sendMessage(l.getMessage(), 1)) {
                            hasSentEnemyLoc = true;
                            //System.out.println("[i] SENDING ENEMY HQ LOCATION");
                            //System.out.println(enemyLocation);
                        }
                    }
                }
                if (enemyRobot.team == enemyTeam && enemyRobot.type == RobotType.DELIVERY_DRONE)
                    droneCount++;
                if (enemyRobot.team == allyTeam || enemyRobot.type == RobotType.DELIVERY_DRONE
                        || enemyRobot.type.isBuilding())
                    continue;
                int distToEnemy = myLocation.distanceSquaredTo(enemyRobot.location);
                // consider nearest, prioritizing enemy landscapers over all
                if (distToEnemy < distToNearest
                        || (nearest != null && nearest.type != RobotType.LANDSCAPER && enemyRobot.type == RobotType.LANDSCAPER)
                        || (nearest != null && nearest.type != RobotType.MINER && enemyRobot.type == RobotType.MINER)
                        || (nearest != null && nearest.type.equals(RobotType.COW) && !enemyRobot.type.equals(RobotType.COW))) {
                    if (nearest == null && (rc.getRoundNum() > 200 && !attackDrone && myLocation.distanceSquaredTo(hqLocation) < 64
                                            || rc.getRoundNum() > 600 && !attackDrone
                                            || enemyRobot.type != RobotType.COW)
                            || nearest != null && nearest.type == RobotType.COW
                            || nearest != null && (enemyRobot.type != RobotType.COW && nearest.type != RobotType.LANDSCAPER)
                            || nearest != null && enemyRobot.type == RobotType.LANDSCAPER) {
                        //System.out.print("Updating nearest target to ");
                        //System.out.println(enemyRobot);
                        if (!(bSchool && enemyRobot.getType() == RobotType.COW)) {
                            nearest = enemyRobot;
                            distToNearest = distToEnemy;
                        }
                    }
                }
            }
            return this;
        }

        public RobotInfo[] getNearby() {
            return nearby;
        }
    }
}
