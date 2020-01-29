package qual;

import java.util.*;

import battlecode.common.*;

// TODO: heal d.school if it is being attacked.  Haven't started this yet; it is straightforward if in lategame.  But it is nontrivial if in early game.

public class Landscaper extends Unit {

    public static final int MIN_LATTICE_BUILD_HEIGHT = -25;
    public static final int LATTICE_SIZE = 48;

    boolean defensive = false;
    // Map<MapLocation, RobotInfo> nearbyBotsMap;
    RobotInfo[] nearbyBots;
    MapLocation baseLocation;

    // class variables used specifically by terraformers:
    int bornTurn;
    boolean terraformer = false;
    MapLocation[] depositSiteExceptions = {null, null, null, null, null};
    boolean spiralClockwise = true;
    Direction lastPlotICompletedDirToHQ = null;
    int terraformHeight = 2;
    MapLocation reservedForDSchoolBuild = null;
    MapLocation terraformTarget = null;
    int pathingToTargetCounter = 0;

    // class variables used specifically by defensive landscapers:
    MapLocation hqLocation = null;
    int wallPhase;
    MapLocation holdPositionLoc = null;
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
            {Direction.EAST, Direction.NORTHEAST, Direction.NORTHWEST, Direction.NORTH},
            {Direction.CENTER},
            {Direction.WEST, Direction.NORTHWEST, Direction.NORTHEAST, Direction.NORTH},
            {Direction.NORTHEAST, Direction.NORTHWEST, Direction.SOUTHEAST, Direction.NORTH, Direction.EAST},
            {Direction.SOUTH, Direction.SOUTHEAST, Direction.NORTHEAST, Direction.EAST},
            {Direction.CENTER},
            {Direction.NORTH, Direction.NORTHEAST, Direction.SOUTHEAST, Direction.EAST},
            {Direction.SOUTHEAST, Direction.NORTHEAST, Direction.SOUTHWEST, Direction.EAST, Direction.SOUTH},
            {Direction.WEST, Direction.SOUTHWEST, Direction.SOUTHEAST, Direction.SOUTH},
            {Direction.CENTER},
            {Direction.EAST, Direction.SOUTHEAST, Direction.SOUTHWEST, Direction.SOUTH},
            {Direction.SOUTHWEST, Direction.SOUTHEAST, Direction.NORTHWEST, Direction.WEST, Direction.SOUTH},
            {Direction.NORTH, Direction.NORTHWEST, Direction.SOUTHWEST, Direction.WEST},
            {Direction.CENTER},
            {Direction.SOUTH, Direction.SOUTHWEST, Direction.NORTHWEST, Direction.WEST}
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

    boolean superCanMove = false;

    // class variables used by aggressive landscapers:
    boolean aggressive = false;
    boolean wallProxy = false;
    MapLocation enemyHQLocation = null;
    MapLocation enemyDSchoolLocation = null;

    @Override
    protected int getFleeRadius() {
        return 8;
    }

    public Landscaper(RobotController rc) throws GameActionException {
        super(rc);
        System.out.println(myLocation);
        bornTurn = rc.getRoundNum();

        construct();
    }

    private void construct() throws GameActionException {
        // nearbyBotsMap = new HashMap<>();
        nearbyBots = rc.senseNearbyRobots();

        // scan for d.school location
        for (Direction dir : directions) {                   // Marginally cheaper than sensing in radius 2
            MapLocation t = myLocation.add(dir);
            if (existsNearbyBotAt(t) && getNearbyBotAt(t).type.equals(RobotType.DESIGN_SCHOOL) && getNearbyBotAt(t).team.equals(allyTeam)) {
                baseLocation = t;
                break;
            }
        }
        if (baseLocation != null) {
            System.out.println("Found my d.school: " + baseLocation.toString());
        } else {
            for (RobotInfo r : nearbyBots) {
                if (r.type.equals(RobotType.DESIGN_SCHOOL) && r.team.equals(allyTeam)) {
                    baseLocation = r.location;
                }
            }
            if (baseLocation == null) {
                rc.setIndicatorDot(myLocation, 255, 255, 0);
            }
            // for (int i = 0; i < MAP_HEIGHT; i++) {
            //     for (int j = 0; j < MAP_WIDTH; j++) {
            //         rc.setIndicatorDot(new MapLocation(j, i), 255, 255, 0);
            //     }
            // }
        }

        // scan for HQ location
        hqLocation = null;
        holdPositionLoc = null;
        wallPhase = 0;
        checkForLocationMessage();
        hqLocation = HEADQUARTERS_LOCATION;
        defensive = myLocation.distanceSquaredTo(hqLocation) <= 64; // arbitrary cutoff, but should be more than big enough.
        if (defensive) {
            depositSiteExceptions[0] = hqLocation.add(Direction.SOUTH).add(Direction.SOUTH);
            depositSiteExceptions[1] = hqLocation.add(Direction.NORTH).add(Direction.NORTH);
            depositSiteExceptions[2] = hqLocation.add(Direction.EAST).add(Direction.EAST);
            depositSiteExceptions[3] = hqLocation.add(Direction.WEST).add(Direction.WEST);
            if (baseLocation != null) {
                if (baseLocation.distanceSquaredTo(hqLocation) == 4) {
                    depositSiteExceptions[4] = baseLocation.add(baseLocation.directionTo(hqLocation).rotateLeft().rotateLeft());
                }
                innerWallFillOrder = computeInnerWallFillOrder(hqLocation, baseLocation);
            }
            System.out.println("I am a defensive landscaper. Found our HQ at " + hqLocation.toString());
            updateHoldPositionLoc();
            System.out.println("Updated holdPositionLoc to " + holdPositionLoc.toString());
            //System.out.println("My hold position location: " + holdPositionLoc.toString());
        } else {
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

        initialCheckForEnemyHQLocationMessage();
    }

    public void constructTerraformer() throws GameActionException {
        System.out.println("[i] YAY, I'm A TERRAFORMER!");
        terraformer = true;
        spiralClockwise = rc.getRoundNum() % 2 == 0;
        if (baseLocation != null) {
            reservedForDSchoolBuild = baseLocation.add(baseLocation.directionTo(hqLocation).opposite().rotateRight());
        }
    }

    @Override
    public void run() throws GameActionException {
        super.run();
        if (!rc.isReady()) {
            rc.setIndicatorDot(myLocation, 0, 0, 0);
        }

        nearbyBots = rc.senseNearbyRobots();

        if (rc.getRoundNum() - bornTurn == 5) {
            readBirthMessage();
        }

        if (rc.getRoundNum() > 700 && myLocation.distanceSquaredTo(hqLocation) > LATTICE_SIZE) { // I got dropped off somewhere outside the lattice
            aggressive = true;
            terraformer = false;
            defensive = false;
        }

        if (terraformer) {
            terraform();
        } else if (defensive) {

            if(rc.getRoundNum()<200 && !enemyAggression) {
                if(enemyAggressionCheck()) {
                    turnAtEnemyAggression = rc.getRoundNum();
                }
            } else if(enemyAggression) {
                if(rc.getRoundNum() - turnAtEnemyAggression > 200) {
                    enemyAggression = false;
                }
            }

            defense();
        } else if (aggressive) {
            aggro();
        } else {
            construct();
        }
    }

    public void updateBaseLocationIfNull() throws GameActionException {
        if (baseLocation != null && reservedForDSchoolBuild != null) {
            return;
        }
        System.out.println("Updating base location");
        for (RobotInfo r : nearbyBots) {
            if (r.type.equals(RobotType.DESIGN_SCHOOL) && r.team.equals(allyTeam)) {
                baseLocation = r.location;
            }
        }
        if (baseLocation != null) {
            if (baseLocation.distanceSquaredTo(hqLocation) == 4) {
                depositSiteExceptions[4] = baseLocation.add(baseLocation.directionTo(hqLocation).rotateLeft().rotateLeft());
            }
            innerWallFillOrder = computeInnerWallFillOrder(hqLocation, baseLocation);
            reservedForDSchoolBuild = baseLocation.add(baseLocation.directionTo(hqLocation).opposite().rotateRight());
        } else {
            if (!terraformer && defensive && rc.getRoundNum() < DeliveryDrone.FILL_OUTER_ROUND && myLocation.distanceSquaredTo(hqLocation) >= 9) {
                // if you're outside the outer wall before drones start filling in the outer wall and you can't find your d.school, then you switch to being a terraformer
                terraformer = true;
                run();
            }
        }
    }

    public void terraform() throws GameActionException {
        updateBaseLocationIfNull();

        rc.setIndicatorDot(rc.getLocation(), 0, 255, 0);
        superCanMove = true;
        if (flee()) {
            superCanMove = false;
            return;
        }
        superCanMove = false;

        if (rc.getRoundNum() > 1100 && baseLocation != null) {
            moveInDirection(myLocation.directionTo(baseLocation).opposite());
        }
        if (rc.getRoundNum() > 1100 && baseLocation == null) {
            moveInDirection(myLocation.directionTo(hqLocation).opposite());
        }

        int[] dxy = xydist(myLocation, hqLocation);
        if (myLocation.distanceSquaredTo(hqLocation) > LATTICE_SIZE || dxy[0] % 3 + dxy[1] % 3 == 0 || onBoundary(myLocation)) { // if i'm far from HQ or in a dig site
            superCanMove = true;
            path(hqLocation);
            superCanMove = false;
            return;
        }
        if (myLocation.isAdjacentTo(hqLocation) && rc.getRoundNum() > DeliveryDrone.FILL_WALL_ROUND ||
                (myLocation.distanceSquaredTo(hqLocation) < 9 && myLocation.distanceSquaredTo(hqLocation) > 3 && rc.getRoundNum() > DeliveryDrone.FILL_OUTER_ROUND)) {
            // if I'm in the inner wall or in the outer wall after a certain point, become a defender
            terraformer = false;
            defensive = true;
            defense();
            return;
        }
        if (getTerraformDigDirection() == Direction.CENTER) { // if I find myself in a dig site, get out of there
            Direction d = hqLocation.directionTo(myLocation);
            moveInDirection(d);
            return;
        } else {
            if (isWalled() && myLocation.equals(reservedForDSchoolBuild)) {
                // wait for my ride onto the lattice
                return;
            }
            rc.setIndicatorDot(myLocation, 0, 255, 0);

            updateTerraformTarget();
            rc.setIndicatorLine(myLocation, terraformTarget, 0, 255, 255);

            if (myLocation.isAdjacentTo(terraformTarget)) {
                if (rc.getDirtCarrying() == 0) {
                    Direction digDir = getTerraformDigDirection();
                    System.out.println("Trying to dig in direction " + digDir.toString());
                    if (tryDig(digDir)) {
                        return;
                    }
                } else {
                    System.out.println("Trying to deposit at " + terraformTarget.toString());
                    if (tryDeposit(myLocation.directionTo(terraformTarget))) {
                        return;
                    }
                }
            } else {
                path(terraformTarget);
                pathingToTargetCounter++;
            }
        }
    }

    public void updateTerraformTarget() throws GameActionException {
        if (rc.getRoundNum() > 1100) {
            if (onBoundary(myLocation)) {
                terraformTarget = myLocation;
                terraformHeight = Integer.MAX_VALUE;
                return;
            }
            int tmp = terraformHeight;
            terraformHeight = rc.senseElevation(myLocation) - 1;
            MapLocation target = findLatticeDepositSite();
            if (myLocation.isAdjacentTo(target)) {
                terraformTarget = target;
            } else {
                terraformHeight = rc.senseElevation(myLocation);
                terraformTarget = myLocation;
            }
            return;
        }

        MapLocation target = findLatticeDepositSite();
        while (target == null) {
            terraformHeight += 2;
            target = findLatticeDepositSite();
        }
        if (target != terraformTarget) {
            terraformTarget = target;
            pathingToTargetCounter = 0;
        }
        if (pathingToTargetCounter == 5) {
            terraformHeight += 2;
            pathingToTargetCounter = 0;
        }
    }

    public Direction rotateBySpiralDirection(Direction d) {
        if (spiralClockwise) {
            return d.rotateLeft();
        } else {
            return d.rotateRight();
        }
    }

    public boolean onBoundary(MapLocation t) {
        return t.x == 0 || t.y == 0 || t.x == MAP_WIDTH - 1 || t.y == MAP_HEIGHT - 1;
    }

    public void moveInDirection(Direction d) throws GameActionException {
        path(myLocation.add(d));
    }

    public boolean isDepositSiteException(MapLocation t) {
        for (MapLocation l : depositSiteExceptions) {
            if (t.equals(l)) {
                return true;
            }
        }
        if (t.equals(reservedForDSchoolBuild)) {
            return true;
        }
        return false;
    }

    public Direction getTerraformDigDirection() throws GameActionException {
        /*
         *  x - - x
         *  6 7 8 -
         *  3 4 5 -
         *  x 1 2 x
         */
        int k = 3 * ((((myLocation.y - hqLocation.y) % 3) + 3) % 3) + ((((myLocation.x - hqLocation.x) % 3) + 3) % 3);
        switch (k) {
            case 0:
                return Direction.CENTER;
            case 1:
                return Direction.WEST;
            case 2:
                return Direction.EAST;
            case 3:
                return Direction.SOUTH;
            case 4:
                return Direction.SOUTHWEST;
            case 5:
                return Direction.SOUTHEAST;
            case 6:
                return Direction.NORTH;
            case 7:
                return Direction.NORTHWEST;
            case 8:
                return Direction.NORTHEAST;
            default:
                return null;
        }
    }

    public void aggro() throws GameActionException {
        if (enemyHQLocation != null) {
            // update d.school location
            baseLocation = null;
            for (Direction dir : directions) {                   // Marginally cheaper than sensing in radius 2
                MapLocation t = myLocation.add(dir);
                if (existsNearbyBotAt(t) && getNearbyBotAt(t).type.equals(RobotType.DESIGN_SCHOOL) && getNearbyBotAt(t).team.equals(allyTeam)) {
                    baseLocation = t;
                    break;
                }
            }

            enemyDSchoolLocation = null;
            for (RobotInfo r : nearbyBots) { // TODO: merge with previous loop
                if (r.type.equals(RobotType.DESIGN_SCHOOL) && r.team.equals(enemyTeam)) {
                    enemyDSchoolLocation = r.getLocation();
                }
            }

            Direction enemyHQDir = myLocation.directionTo(enemyHQLocation);

            // if i can move closer to enemy d.school while being adjacent to enemy HQ, do it
            if (enemyDSchoolLocation != null) {
                Direction moveDir = Direction.CENTER;
                int minDist = myLocation.distanceSquaredTo(enemyDSchoolLocation);
                MapLocation moveLoc = myLocation;
                for (Direction d : directions) {
                    MapLocation t = enemyHQLocation.add(d);
                    int newDist = enemyDSchoolLocation.distanceSquaredTo(t);
                    if (myLocation.isAdjacentTo(t) && newDist < minDist) {
                        moveDir = d;
                        minDist = newDist;
                        moveLoc = t;
                    }
                }
                if (moveLoc != myLocation) {
                    tryMove(myLocation.directionTo(moveLoc));
                }
            }

            if (rc.getDirtCarrying() == 0) { // dig
                if (baseLocation != null && rc.canDigDirt(myLocation.directionTo(baseLocation))) { // heal d.school
                    System.out.println("Digging from d.school at " + baseLocation.toString());
                    tryDig(myLocation.directionTo(baseLocation));
                } else {
                    for (RobotInfo botInfo : nearbyBots) {
                        if (botInfo.team.equals(allyTeam) && botInfo.location.isAdjacentTo(myLocation) && botInfo.type.isBuilding()) { // heal net guns
                            System.out.println("Digging from ally building at " + botInfo.location.toString());
                            tryDig(myLocation.directionTo(botInfo.location));
                        }
                    }
                    System.out.println("Digging under myself");
                    tryDig(Direction.CENTER);
                }
            } else {
                System.out.println("Depositing under enemy HQ at " + myLocation.directionTo(enemyHQLocation));
                tryDeposit(enemyHQDir);
            }
        } else {
            for (Direction d : directions) {
                if (rc.isLocationOccupied(myLocation.add(d)) && getNearbyBotAt(myLocation.add(d)).team.equals(enemyTeam) && getNearbyBotAt(myLocation.add(d)).type.equals(RobotType.HQ)) {
                    enemyHQLocation = myLocation.add(d);
                }
            }
            if (enemyHQLocation != null) {
                aggro();
            }
            // flee
            // if enemy HQ visible, a-move it
            // if adjacent to enemy building, kill it
            // path away from my HQ location until as far as possible and then path towards HQ location
        }
    }

    public boolean notTrappingAlly(Direction d) throws GameActionException {
        MapLocation t = myLocation.add(d);
        if (existsNearbyBotAt(t) && !getNearbyBotAt(t).team.equals(enemyTeam)) {
            pauseDigAndWaitForAllyToPass++;
            return pauseDigAndWaitForAllyToPass >= 2;
        } else {
            pauseDigAndWaitForAllyToPass = 0;
            return true;
        }
    }


    public void defense() throws GameActionException {
        updateBaseLocationIfNull();

        Direction hqDir = myLocation.directionTo(hqLocation);
        int hqDist = myLocation.distanceSquaredTo(hqLocation);

        // TODO: If we start exceeding bytecode limits, investigate ways to not do these two functions every turn.
        updateHoldPositionLoc();
        System.out.println("Updated holdPositionLoc to " + holdPositionLoc.toString());
        checkWallStage();

        if (wallPhase == 3 && myLocation.distanceSquaredTo(hqLocation) >= 9 && rc.getRoundNum() > DeliveryDrone.FILL_OUTER_ROUND) {
            terraformer = true;
            run();
        }

        for (Direction d : directions) {// zeroth priority: kill an an enemy building
            if (existsNearbyBotAt(myLocation.add(d))) {
                RobotInfo botInfo = getNearbyBotAt(myLocation.add(d));
                if (botInfo.team.equals(enemyTeam) && botInfo.type.isBuilding()) {
                    if (rc.getDirtCarrying() > 0) {
                        System.out.println("Dumping dirt on enemy building at " + botInfo.location);
                        if (tryDeposit(d)) {
                            return;
                        }
                    } else {
                        System.out.println("Attempting to gather dirt in an emergency to kill the enemy building");
                        if (myLocation.isAdjacentTo(hqLocation) && rc.canDigDirt(hqDir)) { // first priority: heal HQ
                            System.out.println("Healing HQ");
                            if (tryDig(hqDir)) {
                                return;
                            }
                        } else if (myLocation.isAdjacentTo(baseLocation) && rc.canDigDirt(myLocation.directionTo(baseLocation))) { // second priority: heal d.school
                            System.out.println("Healing d.school");
                            if (tryDig(myLocation.directionTo(baseLocation))) {
                                return;
                            }
                        } else {
                            for (MapLocation digLoc : depositSiteExceptions) {
                                if (digLoc != null && myLocation.isAdjacentTo(digLoc) && !myLocation.equals(digLoc) &&
                                        (!existsNearbyBotAt(digLoc) ||
                                                (getNearbyBotAt(digLoc).team.equals(enemyTeam) && !getNearbyBotAt(digLoc).type.isBuilding()) ||
                                                (getNearbyBotAt(digLoc).type.equals(RobotType.DELIVERY_DRONE)))) {
                                    System.out.println("Attempting to dig from pre-designated dig site " + digLoc.toString());
                                    if (tryDig(myLocation.directionTo(digLoc))) {
                                        return;
                                    }
                                }
                            }
                            System.out.println("Attempting to dig from direction " + d.opposite().toString());
                            if (tryDig(d.opposite())) {
                                return;
                            }
                            for (Direction di : directions) {
                                MapLocation digLoc = myLocation.add(di);
                                if (!existsNearbyBotAt(digLoc) || !getNearbyBotAt(digLoc).type.isBuilding()) {
                                    System.out.println("Last resort: attempting to dig from direction " + di.toString());
                                    if (tryDig(di)) {
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!myLocation.equals(holdPositionLoc)) { // first priority: path to holdPositionLoc, dig in if needed
            rc.setIndicatorLine(myLocation, holdPositionLoc, 255, 192, 203);
            if (myLocation.isAdjacentTo(holdPositionLoc) && rc.senseElevation(holdPositionLoc) - rc.senseElevation(myLocation) > 3) {
                if (tryDig(myLocation.directionTo(holdPositionLoc))) {
                    return;
                }
                if (tryDeposit(Direction.CENTER)) {
                    return;
                }
            }
            if (myLocation.isAdjacentTo(holdPositionLoc) && rc.senseElevation(holdPositionLoc) - rc.senseElevation(myLocation) < -3) {
                if (tryDig(Direction.CENTER)) {
                    return;
                }
                if (tryDeposit(myLocation.directionTo(holdPositionLoc))) {
                    return;
                }
            }
            System.out.println("Pathing towards my holdPositionLoc: " + holdPositionLoc.toString());
            path(holdPositionLoc);
        } else { // i have already reached my position in the turtle, and can now do the dirty work
            if (wallPhase < 2) { // i am an inner landscaper
                if (rc.canDigDirt(hqDir)) { // first priority: heal HQ
                    System.out.println("Healing HQ");
                    if (tryDig(hqDir)) {
                        return;
                    }
                } else if (rc.getDirtCarrying() < RobotType.LANDSCAPER.dirtLimit) { // dig dirt
                    boolean foundDigSite = false;
                    int hqElevation = rc.senseElevation(hqLocation);
                    for (Direction d : directions) { // dig down after killing an enemy rush building (empty inner wall tile with elev > HQ)
                        if (!rc.onTheMap(hqLocation.add(d))) {
                            continue;
                        }
                        if (rc.getRoundNum() < forceInnerWallTakeoffAt && hqLocation.add(d).isAdjacentTo(myLocation) && !hqLocation.add(d).equals(myLocation) && !existsNearbyBotAt(hqLocation.add(d)) && shouldLowerPile(hqLocation, d)) {
                            foundDigSite = true;
                            System.out.println("Digging from pile in direction " + myLocation.directionTo(hqLocation.add(d)));
                            if (tryDig(myLocation.directionTo(hqLocation.add(d)))) {
                                return;
                            }
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
                                if (!rc.canDigDirt(digDir) && hqDist == 2) {
                                    digDir = hqDir.rotateLeft().rotateLeft();
                                    if (!rc.canDigDirt(digDir)) {
                                        digDir = digDir.rotateLeft();
                                    }
                                    System.out.println("Dig in backup spot because I'm up against the wall");
                                    if (tryDig(digDir)) {
                                        return;
                                    }
                                }
                            }
                        }
                    }
                } else if (wallPhase == 0) { // inner wall not yet complete; deposit under yourself
                    boolean foundDumpSite = false;
                    int hqElevation = rc.senseElevation(hqLocation);
                    for (Direction d : directions) { // dig down after killing an enemy rush building (empty inner wall tile with elev > HQ)
                        if (!rc.onTheMap(hqLocation.add(d))) {
                            continue;
                        }
                        if (rc.getRoundNum() < forceInnerWallTakeoffAt && hqLocation.add(d).isAdjacentTo(myLocation) && !hqLocation.add(d).equals(myLocation) && !existsNearbyBotAt(hqLocation.add(d)) && shouldRaisePit(hqLocation, d)) {
                            foundDumpSite = true;
                            System.out.println("Dumping to pit in direction " + myLocation.directionTo(hqLocation.add(d)));
                            if (tryDeposit(myLocation.directionTo(hqLocation.add(d)))) {
                                return;
                            }
                        }
                    }
                    if (!foundDumpSite) {
                        Direction dump = innerWallLowestNearbyDirection();
                        if (rc.senseElevation(myLocation.add(dump)) > GameConstants.getWaterLevel(rc.getRoundNum() + 10) && !isAdjacentToWater(myLocation.add(dump))) {
                            dump = Direction.CENTER;
                        }
                        System.out.println("Dumping dirt in direction " + dump);
                        if (tryDeposit(dump)) {
                            return;
                        }
                    }
                } else { // inner wall tight; distribute to the lowest point of the inner wall around it
                    Direction dump = innerWallLowestNearbyDirection();
                    System.out.println("Dumping dirt in direction " + dump.toString());
                    if (tryDeposit(dump)) {
                        return;
                    }
                }
            } else if (wallPhase == 2) { // i'm outside the inner wall but the inner wall has enemies on it and hasn't taken off yet
                // path to enemy building if visible (go to holdpositionloc will handle this)
                // if no enemy building is visible then what? path to HQ?
            } else if (wallPhase == 3) { // i am an outer landscaper
                if (rc.getDirtCarrying() == 0) { // dig dirt.  Note that outer landscapers keep their dirt at 0 or 1 while inner landscapers keep their dirt maximized.
                    // TODO (DONE): handle the case where we can't dig where we want to because of buildings, e.g. enemy net guns.
                    for (Direction d : outerRingDig[outerRingIndex]) {
                        System.out.println("Attempting to dig from direction " + d);
                        if (tryDig(d)) {
                            return;
                        }
                    }
                } else if (rc.senseElevation(myLocation) > -10 && (rc.getRoundNum() < INNER_WALL_FORCE_TAKEOFF_DEFAULT || GameConstants.getWaterLevel(rc.getRoundNum() + 3) >= rc.senseElevation(myLocation))) { // deposit under myself if i am not in a dig site and either the inner wall hasn't been force-closed yet or i'm about to die
                    System.out.println("Dumping dirt under myself");
                    if (tryDeposit(Direction.CENTER)) {
                        return;
                    }
                } else {
                    Direction dumpDir = outerRingDeposit[outerRingIndex][0];
                    int minElev = 50000;
                    for (Direction d : outerRingDeposit[outerRingIndex]) {
                        if (rc.canSenseLocation(myLocation.add(d)) && rc.senseElevation(myLocation.add(d)) < minElev) {
                            dumpDir = d;
                            minElev = rc.senseElevation(myLocation.add(d));
                        }
                    }
                    System.out.println("Dumping dirt in direction " + dumpDir.toString());
                    if (tryDeposit(dumpDir)) {
                        return;
                    }
                }
            }
        }
    }

    Direction innerWallLowestNearbyDirection() throws GameActionException {
        Direction hqDir = myLocation.directionTo(hqLocation);
        Direction dump = Direction.CENTER;
        int height = rc.senseElevation(myLocation.add(dump));
        MapLocation candidateDumpLoc = myLocation.add(hqDir.rotateLeft());
        if (rc.canSenseLocation(candidateDumpLoc) && rc.senseElevation(candidateDumpLoc) < height && isNotUselessDumpSpot(candidateDumpLoc)) { // check rotate left
            dump = hqDir.rotateLeft();
            height = rc.senseElevation(candidateDumpLoc);
        }
        candidateDumpLoc = myLocation.add(hqDir.rotateRight());
        if (rc.canSenseLocation(candidateDumpLoc) && rc.senseElevation(candidateDumpLoc) < height && isNotUselessDumpSpot(candidateDumpLoc)) { // check rotate right
            dump = hqDir.rotateRight();
            height = rc.senseElevation(candidateDumpLoc);
        }
        if (myLocation.distanceSquaredTo(hqLocation) == 1) {
            candidateDumpLoc = myLocation.add(hqDir.rotateLeft().rotateLeft());
            if (rc.canSenseLocation(candidateDumpLoc) && rc.senseElevation(candidateDumpLoc) < height && isNotUselessDumpSpot(candidateDumpLoc)) { // check rotate left
                dump = hqDir.rotateLeft().rotateLeft();
                height = rc.senseElevation(candidateDumpLoc);
            }
            candidateDumpLoc = myLocation.add(hqDir.rotateRight().rotateRight());
            if (rc.canSenseLocation(candidateDumpLoc) && rc.senseElevation(candidateDumpLoc) < height && isNotUselessDumpSpot(candidateDumpLoc)) { // check rotate right
                dump = hqDir.rotateRight().rotateRight();
                height = rc.senseElevation(candidateDumpLoc);
            }
        }
        return dump;
    }

    boolean isNotUselessDumpSpot(MapLocation l) { // useless dump spot is somewhere the water can't reach anyway if the HQ is close to the boundary
        for (Direction d : cardinal) {
            if (l.equals(hqLocation.add(d)) && onBoundary(l) && !rc.onTheMap(hqLocation.add(d).add(d))) {
                return false;
            }
        }
        return true;
    }

    boolean shouldLowerPile(MapLocation hqLocation, Direction d) throws GameActionException {
        if (baseLocation == null || !rc.canSenseLocation(baseLocation)) {
            return rc.senseElevation(hqLocation.add(d)) > rc.senseElevation(hqLocation);
        }
        return rc.senseElevation(hqLocation.add(d)) > (0.5 * (rc.senseElevation(hqLocation) + rc.senseElevation(baseLocation))) + 0.5;
    }

    boolean shouldRaisePit(MapLocation hqLocation, Direction d) throws GameActionException {
        if (baseLocation == null || !rc.canSenseLocation(baseLocation)) {
            return rc.senseElevation(hqLocation.add(d)) < rc.senseElevation(hqLocation);
        }
        return rc.senseElevation(hqLocation.add(d)) < (0.5 * (rc.senseElevation(hqLocation) + rc.senseElevation(baseLocation))) - 0.5;
    }

    boolean tryDeposit(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDepositDirt(dir)) {
            rc.depositDirt(dir);
            if (dir.equals(Direction.CENTER)) {
                rc.setIndicatorDot(myLocation, 150, 160, 110);
            } else {
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
            } else {
                rc.setIndicatorLine(myLocation, myLocation.add(dir), 250, 250, 250);
            }
            return true;
        } else {
            return false;
        }
    }

    // ****** THIS FUNCTION HAS BEEN DEPRECATED AS OF 1/26 AFTERNOON ******
    // void updateNearbyBots() throws GameActionException {
    //     int b = 0;

    //     nearbyBots = rc.senseNearbyRobots();
    //     nearbyBotsMap.clear();
    //     int a = Clock.getBytecodeNum();
    //     forceInnerWallTakeoffAt = INNER_WALL_FORCE_TAKEOFF_DEFAULT;
    //     for (RobotInfo botInfo : nearbyBots) {
    //         b -= Clock.getBytecodeNum();
    //         nearbyBotsMap.put(botInfo.location, botInfo);
    //         // System.out.println(botInfo);
    //         if (botInfo.team.equals(enemyTeam) && (botInfo.type.equals(RobotType.MINER) || botInfo.type.equals(RobotType.LANDSCAPER))) {
    //             forceInnerWallTakeoffAt = INNER_WALL_FORCE_TAKEOFF_CONTESTED;
    //         }
    //         b += Clock.getBytecodeNum();
    //     }
    //     System.out.println("Bytecodes spent on put: " + Integer.toString(b));
    //     System.out.println("Bytecodes spent iterating: " + Integer.toString(Clock.getBytecodeNum() - a - b));
    // }

    void checkWallStage() throws GameActionException {
        // if (wallPhase < 3) {
        int numInnerWallOurs = 0; // number of OUR LANSCAPERS in the inner wall (not counting current robot if applicable)
        int numInnerWall = 0; // number of ALL ROBOTS in the inner wall (not counting current robot if applicable)
        int numInnerWallSpots = 0;
        for (Direction dir : directions) {
            if (rc.onTheMap(hqLocation.add(dir))) {
                numInnerWallSpots++;
                if (existsNearbyBotAt(hqLocation.add(dir))) {
                    numInnerWall++;
                    RobotInfo botInfo = getNearbyBotAt(hqLocation.add(dir));
                    if (botInfo.type.equals(RobotType.LANDSCAPER) && botInfo.team.equals(allyTeam)) {
                        numInnerWallOurs++;
                    }
                }
            }
        }
        wallPhase = 0;
        if (currentlyInInnerWall && numInnerWallOurs == numInnerWallSpots - 1 && holdPositionLoc != null && myLocation.equals(holdPositionLoc)) {
            System.out.println("I see that the inner wall is tight!");
            wallPhase = 1;
        } else if (numInnerWall == numInnerWallSpots - 1 && holdPositionLoc != null && currentlyInInnerWall && myLocation.equals(holdPositionLoc) && rc.getRoundNum() > 300) { // TODO: important constant round num 300
            System.out.println("The inner wall is full, including some enemies.  Trying to close it off right now.");
            wallPhase = 1;
        } else if (currentlyInInnerWall && rc.getRoundNum() > forceInnerWallTakeoffAt) {
            System.out.println("It's round " + Integer.toString(forceInnerWallTakeoffAt) + " and about time to force the inner wall up even if it's not closed.");
            wallPhase = 1;
        } else if (numInnerWallOurs == numInnerWallSpots || (rc.getRoundNum() > forceInnerWallTakeoffAt && !currentlyInInnerWall)) {
            System.out.println("The inner wall is already full.  So I am an outer landscaper.");
            wallPhase = 3;
        } else if (numInnerWall == numInnerWallSpots && !currentlyInInnerWall) {
            System.out.println("The inner wall is full but it contains enemy units and we haven't taken off yet");
            wallPhase = 2;
        }
        // }
        System.out.println("Wall phase: " + Integer.toString(wallPhase));
    }

    Direction[] computeInnerWallFillOrder(MapLocation hqLoc, MapLocation dSchoolLoc) {
        Direction[] lDir = new Direction[8];
        Direction hqToD = hqLoc.directionTo(dSchoolLoc);
        lDir[7] = hqToD; //always build away from d.school first
        //case 1: Underneath
        if (directionToInt(hqToD) % 2 == 0) {
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

    public boolean readBirthMessage() throws GameActionException {
        int rn = rc.getRoundNum();
        int prev1 = rn - 6;
        for (int i = prev1; i < rn; i++) {
            if (i > 0) {
                if (findTerraformMessage(i)) {
                    return true;
                }
            }
        }
        return false;
    }

    //Find message from allies given a round number rn
    //Checks block of round number rn, loops through messages
    //Currently: Checks for haltProductionMessage from a Miner
    public boolean findTerraformMessage(int rn) throws GameActionException {
        Transaction[] msgs = rc.getBlock(rn);
        for (Transaction transaction : msgs) {
            int[] msg = transaction.getMessage();
            if (allyMessage(msg[0], rn)) {
                if (getSchema(msg[0]) == 6) {
                    TerraformMessage t = new TerraformMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum, rn);
                    if (t.type == 1 && t.id == rc.getID() % 1000) {
                        constructTerraformer();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected boolean canMove(Direction d) {
        return canMove(myLocation, d);
    }

    @Override
    protected boolean canMove(MapLocation me, Direction d) {
        if (superCanMove) {
            return super.canMove(me, d);
        }
        if (terraformer) {
            MapLocation to = me.add(d);
            if (me.distanceSquaredTo(hqLocation) > 8 && to.distanceSquaredTo(hqLocation) < 9)
                return false;
            if (onBoundary(to))
                return false;
            int[] dxy = xydist(to, hqLocation);
            if (dxy[0] % 3 + dxy[1] % 3 == 0)
                return false;
            if (to.distanceSquaredTo(hqLocation) > LATTICE_SIZE)
                return false;
        }
        return super.canMove(me, d);
    }

    void updateHoldPositionLoc() throws GameActionException {
        currentlyInInnerWall = myLocation.isAdjacentTo(hqLocation);
        if (wallPhase < 2) {
            holdPositionLoc = null;
            boolean hqInDanger = false;
            if (innerWallFillOrder == null) {
                innerWallFillOrder = directions;
            }
            for (Direction dir : innerWallFillOrder) {
                MapLocation t = hqLocation.add(dir);
                if (!rc.onTheMap(t)) {
                    continue;
                }
                if (holdPositionLoc == null && !existsNearbyBotAt(t)) { // find the first empty spot in the fill order
                    if (rc.canSenseLocation(t) && rc.senseElevation(t) >= rc.senseElevation(myLocation) - 8 && rc.senseElevation(t) <= rc.senseElevation(myLocation) + 8) {
                        holdPositionLoc = t;
                    }
                }
            }
            if (existsNearbyBotAt(hqLocation) && getNearbyBotAt(hqLocation).getDirtCarrying() > 20 || existsNearbyEnemyOfType(RobotType.LANDSCAPER, 15)) {
                hqInDanger = true;
            }
            if (currentlyInInnerWall && (hqInDanger || rc.senseElevation(myLocation) > rc.senseElevation(hqLocation) + 3)) { // override: if HQ is dying or i'm standing on a pillar and i'm already in the wall, just hold there
                holdPositionLoc = myLocation;
            }
        }
        if (wallPhase == 2) {
            holdPositionLoc = hqLocation;
            for (Direction d : directions) {
                MapLocation t = hqLocation.add(d);
                if (existsNearbyBotAt(t) && getNearbyBotAt(t).type.isBuilding() && getNearbyBotAt(t).team.equals(enemyTeam)) {
                    holdPositionLoc = t;
                }
            }
        }
        if (wallPhase >= 3 || holdPositionLoc == null) {
            // System.out.println("asdfasdfasdf");
            boolean amInOuterRing = false;
            for (int i = 0; i < 16; i++) {
                if (hqLocation.add(outerRing[i][0]).add(outerRing[i][1]).equals(myLocation)) {
                    outerRingIndex = i;
                    System.out.println("I'm already in the outer ring.");
                    amInOuterRing = true;
                    // if (rc.getRoundNum() > 280) { // TODO: ROUND NUM CONSTANT
                    System.out.println("Setting holdPositionLoc to my current location: " + myLocation.toString());
                    holdPositionLoc = myLocation;
                    // }
                    break;
                }
            }
            if (!amInOuterRing) {
                holdPositionLoc = hqLocation.add(outerRing[outerRingIndex][0]).add(outerRing[outerRingIndex][1]);
                int i = 0;
                while (i < 16 && !rc.onTheMap(holdPositionLoc) || existsNearbyBotAt(holdPositionLoc) || (rc.canSenseLocation(holdPositionLoc) && (rc.senseElevation(holdPositionLoc) < -5 || rc.senseElevation(holdPositionLoc) > 10))) {
                    // if the holdposition is off the map or occupied or is a pit/hill that we likely can't path to, then try the next holdposition in the ring
                    outerRingIndex = (outerRingIndex + 1) % 16;
                    holdPositionLoc = hqLocation.add(outerRing[outerRingIndex][0]).add(outerRing[outerRingIndex][1]);
                    i++;
                    // System.out.println("Trying new holdPositionLoc: " + holdPositionLoc.toString());
                }
                if (i == 16) {
                    holdPositionLoc = hqLocation;
                }
            }
        }
    }

    protected MapLocation findLatticeDepositSite() throws GameActionException {
        for (int[] d : visionSpiral) {
            MapLocation loc = add(myLocation, d);
            int dist = loc.distanceSquaredTo(hqLocation);
            if (dist < 5 || dist > LATTICE_SIZE)
                continue;
            if (rc.getRoundNum() > 1500 && myLocation.distanceSquaredTo(loc) > 5)
                return null;
            int[] dxy = xydist(loc, hqLocation);
            if (dxy[0] % 3 + dxy[1] % 3 == 0)
                continue;
            if (isDepositSiteException(loc) || !rc.canSenseLocation(loc))
                continue;
            if (existsNearbyBotAt(loc) && getNearbyBotAt(loc).team.equals(allyTeam) && getNearbyBotAt(loc).type.isBuilding() && (getNearbyBotAt(loc).getDirtCarrying() >= 5 || rc.getRoundNum() < 400))
                continue;
            int height = rc.senseElevation(loc);
            if (height >= terraformHeight || height <= MIN_LATTICE_BUILD_HEIGHT)
                continue;
            return loc;
        }
        return null;
    }

    public boolean isWalled() throws GameActionException {
        for (Direction d : directions) {
            MapLocation loc = myLocation.add(d);
            if (!rc.canSenseLocation(loc))
                continue;
            RobotInfo x = getNearbyBotAt(loc);
            if (!(Math.abs(rc.senseElevation(myLocation) - rc.senseElevation(loc)) > 3)
                    && !(x != null && x.getType().isBuilding()))
                return false;
        }
        return true;
    }

    private int[][] visionSpiral = {{0, 0}, {-1, 0}, {0, -1}, {0, 1}, {1, 0}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}, {-2, 0}, {0, -2},
            {0, 2}, {2, 0}, {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}, {-2, -2}, {-2, 2}, {2, -2}, {2, 2},
            {-3, 0}, {0, -3}, {0, 3}, {3, 0}, {-3, -1}, {-3, 1}, {-1, -3}, {-1, 3}, {1, -3}, {1, 3}, {3, -1}, {3, 1}, {-3, -2}, {-3, 2},
            {-2, -3}, {-2, 3}, {2, -3}, {2, 3}, {3, -2}, {3, 2}, {-4, 0}, {0, -4}, {0, 4}, {4, 0}, {-4, -1}, {-4, 1}, {-1, -4}, {-1, 4},
            {1, -4}, {1, 4}, {4, -1}, {4, 1}, {-3, -3}, {-3, 3}, {3, -3}, {3, 3}, {-4, -2}, {-4, 2}, {-2, -4}, {-2, 4}, {2, -4}, {2, 4},
            {4, -2}, {4, 2}};


    public boolean existsNearbyBotAt(MapLocation l) throws GameActionException {
        return !myLocation.equals(l) && rc.canSenseLocation(l) && rc.isLocationOccupied(l);
        // return nearbyBotsMap.containsKey(l);
    }

    public RobotInfo getNearbyBotAt(MapLocation l) throws GameActionException {
        return rc.senseRobotAtLocation(l);
        // return nearbyBotsMap.get(l);
    }

}
