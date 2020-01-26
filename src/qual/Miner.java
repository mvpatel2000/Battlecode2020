package qual;

import battlecode.common.*;

import java.util.*;

public class Miner extends Unit {

    final int[][] SPIRAL_ORDER = {{0, 0}, {-1, 0}, {0, -1}, {0, 1}, {1, 0}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}, {-2, 0}, {0, -2}, {0, 2}, {2, 0}, {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}, {-2, -2}, {-2, 2}, {2, -2}, {2, 2}, {-3, 0}, {0, -3}, {0, 3}, {3, 0}, {-3, -1}, {-3, 1}, {-1, -3}, {-1, 3}, {1, -3}, {1, 3}, {3, -1}, {3, 1}, {-3, -2}, {-3, 2}, {-2, -3}, {-2, 3}, {2, -3}, {2, 3}, {3, -2}, {3, 2}, {-4, 0}, {0, -4}, {0, 4}, {4, 0}, {-4, -1}, {-4, 1}, {-1, -4}, {-1, 4}, {1, -4}, {1, 4}, {4, -1}, {4, 1}, {-3, -3}, {-3, 3}, {3, -3}, {3, 3}, {-4, -2}, {-4, 2}, {-2, -4}, {-2, 4}, {2, -4}, {2, 4}, {4, -2}, {4, 2}, {-5, 0}, {-4, -3}, {-4, 3}, {-3, -4}, {-3, 4}, {0, -5}, {0, 5}, {3, -4}, {3, 4}, {4, -3}, {4, 3}, {5, 0}, {-5, -1}, {-5, 1}, {-1, -5}, {-1, 5}, {1, -5}, {1, 5}, {5, -1}, {5, 1}, {-5, -2}, {-5, 2}, {-2, -5}, {-2, 5}, {2, -5}, {2, 5}, {5, -2}, {5, 2}, {-4, -4}, {-4, 4}, {4, -4}, {4, 4}, {-5, -3}, {-5, 3}, {-3, -5}, {-3, 5}, {3, -5}, {3, 5}, {5, -3}, {5, 3}};
    final static int TIMEOUT_TIME = 20;

    long[] soupChecked; // align to top right
    SoupList soupListLocations = new SoupList();
    int[] soupMiningTiles; //given by HQ. Check comment in updateActiveLocations.
    boolean readMessage = false;
    public static int SPECULATION = 3;

    MapLocation destination;
    MapLocation hqLocation;
    MapLocation baseLocation;
    MapLocation lastSoupLocation;
    int turnsToBase;
    int[] tilesVisited;

    boolean dSchoolExists;
    boolean fulfillmentCenterExists;
    boolean firstRefineryExists;

    boolean aggro; // true if the one aggro miner
    List<MapLocation> target; // possible enemy HQ locations (target.get(0) is the one after HQ found)
    boolean aggroDone; // done with aggro phase
    boolean hasRun = false;
    MapLocation dLoc; // location of aggro d.school
    boolean hasSentHalt = false;
    boolean hasSentRushCommit = false;
    boolean hasBuiltHaltedNetGun = false;
    boolean hasSentEnemyLoc = false;
    int timeout = 0;

    boolean terraformer = false;
    boolean terraformerSpiralRight = false;

    //For halting production and resuming it.
    boolean rushHold = false;
    boolean holdProduction = false;
    int turnAtProductionHalt = -1;
    int turnAtRushHalt = -1;
    int previousSoup = 200;
    MapLocation enemyHQLocation = null;

    public Miner(RobotController rc) throws GameActionException {
        super(rc);
        checkForLocationMessage();
        initialCheckForEnemyHQLocationMessage();
        if(ENEMY_HQ_LOCATION != null) {
            enemyHQLocation = ENEMY_HQ_LOCATION;
        }
        aggro = rc.getRoundNum() == 2;
        aggro = false; // uncomment to disable aggro
        aggroDone = false;

        terraformer = rc.getRoundNum() < 10;
        // terraformer = false;

        if (aggro) {
            target = new ArrayList<>();
            MapLocation hq = Arrays.stream(rc.senseNearbyRobots()).filter(x ->
                    x.getType().equals(RobotType.HQ) && x.getTeam().equals(rc.getTeam())).toArray(RobotInfo[]::new)[0].location;
            if (rc.getMapWidth() > rc.getMapHeight()) {
                target.add(new MapLocation(rc.getMapWidth() - hq.x - 1, hq.y));
                target.add(new MapLocation(rc.getMapWidth() - hq.x - 1, rc.getMapHeight() - hq.y - 1));
                target.add(new MapLocation(hq.x, rc.getMapHeight() - hq.y - 1));
            } else {
                target.add(new MapLocation(hq.x, rc.getMapHeight() - hq.y - 1));
                target.add(new MapLocation(rc.getMapWidth() - hq.x - 1, rc.getMapHeight() - hq.y - 1));
                target.add(new MapLocation(rc.getMapWidth() - hq.x - 1, hq.y));
            }
            if (target.get(0).equals(target.get(1)) || target.get(1).equals(target.get(2))) {
                target.remove(0);
                target.remove(2);
            }
            setDestination(target.get(0));
        }

        for (Direction dir : directions) {                   // Marginally cheaper than sensing in radius 2
            MapLocation t = myLocation.add(dir);
            if (rc.canSenseLocation(t)) {
                RobotInfo r = rc.senseRobotAtLocation(t);
                if (r != null && r.getType() == RobotType.HQ) {
                    baseLocation = t;
                    hqLocation = t;
                    break;
                }
            }
        }

        if (HEADQUARTERS_LOCATION != null) {
            hqLocation = HEADQUARTERS_LOCATION;
            baseLocation = hqLocation;
        }

        dSchoolExists = false;
        fulfillmentCenterExists = false;
        firstRefineryExists = false;

        soupChecked = new long[64];
        soupMiningTiles = new int[numCols * numRows];
        tilesVisited = new int[numRows * numCols];
        turnsToBase = -1;
        destination = updateNearestSoupLocation();
        updateActiveLocations();
        Clock.yield();
    }

    @Override
    public void run() throws GameActionException {
        super.run();

        findMessageFromAllies(rc.getRoundNum()-1);

        if(rc.getRoundNum()<300 && !enemyAggression) {
            if(enemyAggressionCheck()) {
                turnAtEnemyAggression = rc.getRoundNum();
            }
        }

        if (flee()) {
            return;
        }

        if (holdProduction || rushHold || enemyAggression) {
            checkIfContinueHold();
        }

        if (aggro) {
            handleAggro();
            return;
        }

        tilesVisited[getTileNumber(myLocation)] = 1;

        checkBuildBuildings();

        if (!dSchoolExists || !fulfillmentCenterExists) {
            RobotInfo[] nearbyRobots = rc.senseNearbyRobots(rc.getCurrentSensorRadiusSquared(), allyTeam);
            for (RobotInfo robot : nearbyRobots) {
                switch (robot.getType()) {
                    case DESIGN_SCHOOL:
                        dSchoolExists = true;
                        break;
                    case FULFILLMENT_CENTER:
                        fulfillmentCenterExists = true;
                        break;
                }
            }
        }

        if (terraformer && rc.getRoundNum() > 300) {
            terraform();
        } else {
            if (rc.isReady()) {
                harvest();
            }
            previousSoup = rc.getTeamSoup();
        }
    }

    public void terraform() throws GameActionException {
        if (myLocation.distanceSquaredTo(hqLocation) > 16) {
            path(hqLocation);
        } else {
            if (onBoundary(myLocation)) {
                terraformerSpiralRight = !terraformerSpiralRight;
            }
            Direction d = myLocation.directionTo(hqLocation).opposite().rotateLeft();
            if (terraformerSpiralRight) {
                d = myLocation.directionTo(hqLocation).opposite().rotateRight();
            }
            path(hqLocation.add(d).add(d).add(d).add(d));
        }
    }


    // returns false if special valid grid square, otherwise true
    boolean checkGridExceptions(MapLocation location) throws GameActionException {
        int x = Math.abs(location.y - hqLocation.y);
        int y = Math.abs(location.x - hqLocation.x);
        return !(x == 3 && y == 1 || x == 1 && y == 3);
    }

    //determines if location is on grid and not in landscaper slot
    boolean onBuildingGridSquare(MapLocation location) throws GameActionException {
        if (location.distanceSquaredTo(hqLocation) < 9 || location.distanceSquaredTo(hqLocation) > 20)
            return false;
        if (((location.y - hqLocation.y) % 3 == 0 || (location.x - hqLocation.x) % 3 == 0)
            && checkGridExceptions(location)) {
            return false;
        }
        for (Direction d : directions) { // check location is not reservedForDSchoolBuild
            MapLocation t = location.add(d);
            if (rc.canSenseLocation(t)) {
                RobotInfo r = rc.senseRobotAtLocation(t);
                if (r != null && r.type.equals(RobotType.DESIGN_SCHOOL) && r.team.equals(allyTeam)) { // t is the d.school location
                    if (location.equals(t.add(t.directionTo(hqLocation).opposite().rotateRight()))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    //Returns true if should continue halting production
    //Returns false if should not continue halting production
    private boolean checkIfContinueHold() throws GameActionException {
        //resume production after 10 turns, at most
        if(holdProduction) {
            if (rc.getRoundNum() - turnAtProductionHalt > 30) {
                System.out.println("[i] UNHOLDING PRODUCTION!");
                holdProduction = false;
                return false;
            }
            //-200 soup in one turn good approximation for building net gun
            //so we resume earlier than 10 turns if this happens
            if (previousSoup - rc.getTeamSoup() > 200) {
                System.out.println("[i] UNHOLDING PRODUCTION!");
                holdProduction = false;
                return false;
            }
        }
        if(rushHold) {
            if(rc.getRoundNum() - turnAtRushHalt > 100) {
                System.out.println("[i] NO LONGER RUSH HALTING!");
                rushHold = false;
                return false;
            }
        }
        if(enemyAggression) {
            if(rc.getRoundNum() - turnAtEnemyAggression > 300) {
                enemyAggression = false;
                return false;
            }
        }
        //if neither condition happens (10 turns or -200), continue holding production
        return true;
    }

    private void handleAggro() throws GameActionException {
        RobotInfo[] nearby = rc.senseNearbyRobots();
        if (dLoc != null) {
            RobotInfo[] dinfo = rc.senseNearbyRobots(dLoc, 0, null);
            if (dinfo.length == 0
                    || !dinfo[0].getTeam().equals(allyTeam)
                    || !dinfo[0].getType().equals(RobotType.DESIGN_SCHOOL)) {
                return; // give up
            }
        }
        if (aggroDone // if done finding HQ
                && !target.isEmpty() // was successful
                && myLocation.distanceSquaredTo(target.get(0)) < 3 // next to enemy HQ
                && Arrays.stream(nearby).filter(x -> // >= 3 of my landscapers
                x.getLocation().distanceSquaredTo(target.get(0)) < 3 && x.getType().equals(RobotType.LANDSCAPER)
                        && x.getTeam().equals(rc.getTeam())).toArray(RobotInfo[]::new).length >= 3
                && myLocation.distanceSquaredTo(dLoc) < 3 // next to d.school
                && Arrays.stream(directions).allMatch(d -> // enemy HQ is surrounded
                target.get(0).add(d).equals(myLocation)
                        || rc.senseNearbyRobots(target.get(0).add(d), 0, null).length > 0)) {
            setDestination(myLocation.add(adj(toward(myLocation, target.get(0)), 4))); // then step back
            navigate();
            return;
        }
        if (aggroDone && dLoc != null) { // move to opposite side of d.school around HQ
            for (Direction d : directions) {
                int dist = myLocation.add(d).distanceSquaredTo(dLoc);
                if (myLocation.add(d).distanceSquaredTo(target.get(0)) < 3
                        && (dist > myLocation.distanceSquaredTo(dLoc) || dist == 4)
                        && myLocation.distanceSquaredTo(dLoc) != 4
                        && canMove(d)) {
                    go(d);
                    return;
                }
            }
        }
        if (aggroDone && !target.isEmpty() && Arrays.stream(nearby).anyMatch(x ->
                !x.getTeam().equals(rc.getTeam()) &&
                        (x.getType().equals(RobotType.DELIVERY_DRONE)
                                || x.getType().equals(RobotType.FULFILLMENT_CENTER))) // if I am next to enemy HQ, aggro is done
                // and I sense enemy drone/starport
                // and I don't see any of my netguns
                && Arrays.stream(nearby).noneMatch(x -> x.getTeam().equals(rc.getTeam()) && x.getType().equals(RobotType.NET_GUN))) {
            if (rc.getTeamSoup() < 250 && !hasSentHalt) { // send save resources message if can't build netgun
                HoldProductionMessage h = new HoldProductionMessage(MAP_HEIGHT, MAP_WIDTH, teamNum);
                //make sure this sends successfully.
                if (sendMessage(h.getMessage(), 2)) {
                    hasSentHalt = true;
                }
            }
            // try and build netgun as close to enemy HQ as possible but not next to d.school
            Direction d = Arrays.stream(directions).filter(x ->
                    rc.canBuildRobot(RobotType.NET_GUN, x) && myLocation.add(x).distanceSquaredTo(dLoc) > 2).min(Comparator.comparingInt(x ->
                    myLocation.add(x).distanceSquaredTo(target.get(0)))).orElse(null);
            if (d != null) {
                if (hasSentHalt) {
                    hasBuiltHaltedNetGun = true;
                }
                rc.buildRobot(RobotType.NET_GUN, d);
                return;
            }
        }
        if (target.isEmpty() || aggroDone) // stop if aggro is done
            return;
        // If next to enemy HQ, end aggro and build d.school
        RobotInfo[] seen = rc.senseNearbyRobots(target.get(0), 0, null);
        if(seen.length > 0 && seen[0].getType().equals(RobotType.HQ) && !hasSentEnemyLoc) {
            LocationMessage l = new LocationMessage(MAP_HEIGHT, MAP_WIDTH, teamNum);
            MapLocation ml = seen[0].getLocation();
            l.writeInformation(ml.x, ml.y, 1);
            if(sendMessage(l.getMessage(), 1)) {
                hasSentEnemyLoc = true;
                System.out.println("[i] SENDING ENEMY HQ LOCATION " + ml);
            }
        }
        if ((hasSentHalt == hasBuiltHaltedNetGun) && seen.length > 0 && seen[0].getType().equals(RobotType.HQ)
                && myLocation.distanceSquaredTo(target.get(0)) < 3) {
            for (Direction d : directions)
                if (!hasSentRushCommit && myLocation.add(d).distanceSquaredTo(target.get(0)) < 2 && rc.canBuildRobot(RobotType.DESIGN_SCHOOL, d)) {
                    rc.buildRobot(RobotType.DESIGN_SCHOOL, d);
                    if (Arrays.stream(rc.senseNearbyRobots()).filter(x ->
                        !x.getTeam().equals(allyTeam)).noneMatch(x ->
                        x.getType().equals(RobotType.DESIGN_SCHOOL)
                        || x.getType().equals(RobotType.FULFILLMENT_CENTER))) {
                            RushCommitMessage r = new RushCommitMessage(MAP_HEIGHT, MAP_WIDTH, teamNum);
                            r.writeTypeOfCommit(1);
                            if(sendMessage(r.getMessage(), 1)) {
                                System.out.println("[i] Sending Rush Commit!!");
                                hasSentRushCommit = true;
                            }
                        }
                    aggroDone = true;
                    dLoc = myLocation.add(d);
                    return;
                }
            return;
        }
        // Build starport if stuck during aggro
        if (timeout++ > TIMEOUT_TIME && locAt(TIMEOUT_TIME).distanceSquaredTo(target.get(0)) <= myLocation.distanceSquaredTo(target.get(0)) && rc.getRoundNum() > 20) {
            timeout = 0;
            if (target.size() > 1) {
                MapLocation curr =target.remove(0);
                target.add(curr);
                setDestination(target.get(0));
            }
        }
        if (myLocation.equals(target.get(0))
                || (rc.canSenseLocation(target.get(0)) // or can sense there and no HQ there^M
                && !(seen.length > 0
                && seen[0].getType().equals(RobotType.HQ)))) {
            MapLocation curr =target.remove(0);
            target.add(curr);
            setDestination(target.get(0));
        }

        // path to next candidate enemy HQ locationbat
        if(rc.isReady()) {
            if (seen.length > 0 && seen[0].getType().equals(RobotType.HQ))
                navigate(1);
            else
                navigate(SPECULATION);
        }
    }

    public void checkBuildBuildings() throws GameActionException {
        if (terraformer && rc.getRoundNum() < 350) {
            return;
        }
        if (!rc.isReady() || rc.getTeamSoup() < 500)
            return;
        RobotInfo[] allyRobots = rc.senseNearbyRobots(rc.getCurrentSensorRadiusSquared(), allyTeam);
        boolean existsNetGun = false;
        boolean existsFulfillmentCenter = false;
        for (RobotInfo robot : allyRobots) {
            switch (robot.getType()) {
                case NET_GUN:
                    existsNetGun = true;
                    break;
                case FULFILLMENT_CENTER:
                    existsFulfillmentCenter = true;
                    break;
            }
        }
        for (Direction dir : directions) {
            //TODO: With grid, get rid of elevation turn checks and turn on onBuildingGridSquare
            if (onBuildingGridSquare(myLocation.add(dir))
                    && rc.canSenseLocation(myLocation.add(dir)) && rc.senseElevation(myLocation.add(dir)) > 2) {
                if (!existsNetGun && rc.getRoundNum() > 400) {
                    tryBuild(RobotType.NET_GUN, dir);
                // } else if (dSchoolExists) {
                //     tryBuild(RobotType.DESIGN_SCHOOL, dir);
                } else if (!existsFulfillmentCenter && rc.getRoundNum() > 1300) {
                    tryBuild(RobotType.FULFILLMENT_CENTER, dir);
                } else if (rc.getRoundNum() < 1700) {
                    tryBuild(RobotType.VAPORATOR, dir);
                }
                if (!existsNetGun && rc.getRoundNum() > 500) {
                    tryBuild(RobotType.NET_GUN, dir);
                }
            }
        }
    }

    public void harvest() throws GameActionException {

        //System.out.println("Start harvest round num: " + rc.getRoundNum() + " time: " + Clock.getBytecodeNum() + " dest: " + destination + " dist: " + distanceToDestination);
        System.out.println("Soup: " + rc.getSoupCarrying() + " base location: " + baseLocation);
        System.out.println("Soup: " + rc.getSoupCarrying() + " Turns to Base: " + turnsToBase + " Last Soup Location " + lastSoupLocation + " " + destination);
        //rc.setIndicatorDot(myLocation, (int) (rc.getSoupCarrying() * 2.5), 0, 0);
        //rc.setIndicatorLine(myLocation, destination, 0, 150, 255);

        if (dSchoolExists) {
            refineryCheck();
        }

//        System.out.println(candidateBuildLoc + " " + outsideOuterWall + " " + !fulfillmentCenterExists);
        if (rc.getTeamSoup()>151 && !fulfillmentCenterExists && !holdProduction && !rushHold) {
            Direction optimalDir = determineOptimalFulfillmentCenter();
            if (optimalDir != null) {
                fulfillmentCenterExists = tryBuildIfNotPresent(RobotType.FULFILLMENT_CENTER, optimalDir);
                if (fulfillmentCenterExists) {
                    BuiltMessage b = new BuiltMessage(MAP_HEIGHT, MAP_WIDTH, teamNum);
                    b.writeTypeBuilt(1); //1 is Fulfillment Center
                    sendMessage(b.getMessage(), 1); //Find better bidding scheme
                }
            }
        }

        // refinery flooded
        if (rc.canSenseLocation(baseLocation) && rc.senseFlooding(baseLocation)) {
            baseLocation = hqLocation;
        }

        // build d.school if see enemy or if last departing miner didn't build for whatever reason
        if (fulfillmentCenterExists && rc.getTeamSoup() >= 151 && !dSchoolExists && !holdProduction && !rushHold && (existsNearbyEnemy() || rc.getRoundNum() > 300)
            && myLocation.distanceSquaredTo(hqLocation) < 25) {
            handleBuildDSchool();
        }

        int distanceToDestination = myLocation.distanceSquaredTo(destination);

        if (distanceToDestination <= 2) {                                     // at destination
            if (turnsToBase >= 0) {                                           // at base
                Direction toBase = myLocation.directionTo(baseLocation);
                if (rc.canDepositSoup(toBase)) {                              // deposit. Note: Second check is redundant?
                    rc.depositSoup(toBase, rc.getSoupCarrying());
                }
                if (rc.getSoupCarrying() == 0) {                               // reroute if not carrying soup
                    // build d.school near base when leaving base for far away soup or past round 250
                    destination = updateNearestSoupLocation();
                    turnsToBase = -1;
                }
            } else if (rc.getRoundNum() > 50 && myLocation.distanceSquaredTo(hqLocation) < 3
                    && !destination.equals(hqLocation)) {                   // don't mine next to HQ
                Direction idealDir = null;
                for (Direction dir : directions) {
                    MapLocation newLoc = myLocation.add(dir);
                    if (rc.canMove(dir) && newLoc.distanceSquaredTo(hqLocation) > 2 && newLoc.distanceSquaredTo(destination) < 3) {
                        idealDir = dir;
                    }
                }
                if (idealDir == null && myLocation.distanceSquaredTo(hqLocation) == 1) { //adjacent to HQ, ok to move to diagonal to reposition
                    for (Direction dir : directions) {
                        MapLocation newLoc = myLocation.add(dir);
                        if (rc.canMove(dir) && newLoc.distanceSquaredTo(hqLocation) > 1 && newLoc.distanceSquaredTo(destination) < 3) {
                            idealDir = dir;
                        }
                    }
                }
                if (idealDir != null && rc.isReady() && rc.canMove(idealDir)) {
                    rc.move(idealDir);
                } else {
                    fuzzyMoveToLoc(myLocation.add(myLocation.directionTo(hqLocation).opposite()));
                }
                destination = updateNearestSoupLocation();
            } else {                                                           // mining
                if (rc.getSoupCarrying() == RobotType.MINER.soupLimit
                    || (rc.getRoundNum() < 30 && rc.getSoupCarrying() >= 70)) { // done mining
                    System.out.println("Last soup loc: "+lastSoupLocation);
                    refineryCheck();
                    destination = baseLocation;
                    turnsToBase++;
                }
                if (rc.canSenseLocation(destination) && rc.senseSoup(destination) == 0) { // TODO: does not report empty tile if fills up on soup in same turn
                    System.out.println("Soup finished");
                    sendSoupMessageIfShould(destination, true);
                    if (turnsToBase < 0) {
                        destination = updateNearestSoupLocation();
                        System.out.println("reset destination:" + destination);
                        if ((lastSoupLocation == null || myLocation.distanceSquaredTo(destination) > 34) && rc.getSoupCarrying() > 30) { // next location far, go drop off
                            refineryCheck();
                            destination = baseLocation;
                            turnsToBase++;
                        }
                    }
                }
                if (turnsToBase < 0 && rc.isReady() && myLocation.distanceSquaredTo(destination) <=2) {    // mine
                    sendSoupMessageIfShould(destination, false);
                    rc.mineSoup(myLocation.directionTo(destination));
                }
                if (myLocation.distanceSquaredTo(destination) > 2) {
                    setPathTarget(destination);
                    navigate();
                }
            }
        } else {                                                                // in transit
            // Just dropped off soup (adjacent to HQ) now leaving for far away / late soup
            if (fulfillmentCenterExists && myLocation.isAdjacentTo(hqLocation) &&
                    (lastSoupLocation == null || myLocation.distanceSquaredTo(lastSoupLocation) > 45 || rc.getRoundNum() > 100)
                    && rc.getTeamSoup() >= 151 && !dSchoolExists && !holdProduction && !rushHold) {
                System.out.println("Handling dschool!");
                handleBuildDSchool();
            }
            if (turnsToBase >= 0)
                turnsToBase++;

            if (destination != baseLocation && !readMessage) {                // keep checking soup location
                destination = updateNearestSoupLocation();
            }
            System.out.println("Far pathing: " + destination);
            setPathTarget(destination);
            System.out.println("Start nav " + rc.getRoundNum() + " " + Clock.getBytecodeNum());
            navigate();
            System.out.println("end nav " + rc.getRoundNum() + " " + Clock.getBytecodeNum());
        }
//        System.out.println("end harvest "+rc.getRoundNum() + " " +Clock.getBytecodeNum());
    }

    public void handleBuildDSchool() throws GameActionException {
        MapLocation closestDSchoolBuildLoc = hqLocation;
        int dist = Integer.MAX_VALUE;
        for (Direction d : directions) {
            MapLocation t = hqLocation.add(d).add(d);
            if (myLocation.distanceSquaredTo(t) < dist && (!rc.canSenseLocation(t) || !rc.isLocationOccupied(t))) {
                dist = myLocation.distanceSquaredTo(t);
                closestDSchoolBuildLoc = t;
            }
        }
        if (myLocation.isAdjacentTo(closestDSchoolBuildLoc)) {
            dSchoolExists = tryBuildIfNotPresent(RobotType.DESIGN_SCHOOL, myLocation.directionTo(closestDSchoolBuildLoc));
            if(dSchoolExists) {
                BuiltMessage b = new BuiltMessage(MAP_HEIGHT, MAP_WIDTH, teamNum);
                b.writeTypeBuilt(2); //2 is d.school
                sendMessage(b.getMessage(), 1); //151 is necessary to build d.school and send message. Don't build if can't send message.
            }
        }
        else {
            path(closestDSchoolBuildLoc);
        }
    }

    public Direction determineOptimalFulfillmentCenter() throws GameActionException {
        Direction target = null;
        MapLocation loc = null;
        for (Direction dir : directions) {
            MapLocation newLoc = myLocation.add(dir);
            System.out.println(newLoc + " " + onBuildingGridSquare(newLoc) + " " + hqLocation.distanceSquaredTo(newLoc));
            if (rc.canSenseLocation(newLoc) && Math.abs(rc.senseElevation(myLocation) - rc.senseElevation(newLoc)) <= 3
                    && (loc == null || rc.senseElevation(newLoc) >= rc.senseElevation(loc)) && onBuildingGridSquare(newLoc)
                    && hqLocation.distanceSquaredTo(newLoc) > 9) {
                target = dir;
                loc = newLoc;
            }
        }
        if (target != null && loc.distanceSquaredTo(hqLocation) > 9)
            return target;
        return null;
    }

    //TODO: This is not going to be on grid
    public Direction determineOptimalDSchoolDirection() throws GameActionException {
        // for (Direction d : directions) {
        //     MapLocation t = hqLocation.add(d).add(d);
        //     if (rc.canSenseLocation(t) && )
        // }

        if (myLocation.distanceSquaredTo(hqLocation) < 9) { // close to HQ, build highest elevation in outer ring
            Direction target = myLocation.directionTo(hqLocation).opposite();
            MapLocation loc = myLocation.add(target);
            for (Direction dir : directions) {
                MapLocation newLoc = myLocation.add(dir);
                if (rc.canSenseLocation(newLoc) && Math.abs(rc.senseElevation(myLocation) - rc.senseElevation(newLoc)) <= 3
                        && rc.senseElevation(newLoc) >= rc.senseElevation(loc) //&& onBuildingGridSquare(newLoc)
                        && hqLocation.distanceSquaredTo(newLoc) < 9 && hqLocation.distanceSquaredTo(newLoc) > 2
                        && hqLocation.distanceSquaredTo(newLoc) != 5) {
                    target = dir;
                    loc = newLoc;
                }
            }
            return target;
        } else { // far away, just take highest elevation point
            Direction target = myLocation.directionTo(hqLocation).rotateRight();
            MapLocation loc = myLocation.add(target);
            for (Direction dir : directions) {
                MapLocation newLoc = myLocation.add(dir);
                if (rc.canSenseLocation(newLoc) && Math.abs(rc.senseElevation(myLocation) - rc.senseElevation(newLoc)) <= 3
                        // && onBuildingGridSquare(newLoc)
                        && newLoc.distanceSquaredTo(hqLocation) <= loc.distanceSquaredTo(hqLocation)) {
                    target = dir;
                    loc = newLoc;
                }
            }
            return target;
        }
    }

    // Updates base location and builds refinery if base is too far
    //TODO: Make this actually work and do better
    public void refineryCheck() throws GameActionException {
        if (holdProduction || rushHold)
            return;
        int distToBase = myLocation.distanceSquaredTo(baseLocation);
        RobotInfo[] robots = rc.senseNearbyRobots(rc.getCurrentSensorRadiusSquared(), allyTeam);
        for (RobotInfo robot : robots) {
            if (robot.getType() == RobotType.REFINERY || (robot.getType() == RobotType.HQ && rc.getRoundNum() < 100)) {
                int distToNew = myLocation.distanceSquaredTo(robot.getLocation());
                if (distToNew < distToBase || (distToNew == distToBase && robot.getType() == RobotType.REFINERY)
                        || baseLocation.equals(hqLocation)) {
                    if (baseLocation.equals(destination)) {
                        destination = robot.getLocation();
                    }
                    baseLocation = robot.getLocation();
                    distToBase = distToNew;
                }
            }
        }
        // if (distToBase > 64) {
        //     rc.setIndicatorLine(myLocation, baseLocation, 255, 0, 0);
        // } else {
        //     rc.setIndicatorLine(myLocation, baseLocation, 255, 255, 255);
        // }
        // (far away from base or (current base is HQ and past round 100)) or (next to soup or couldn't path home)
        if ((distToBase > 64 || (baseLocation.equals(hqLocation) && rc.getRoundNum() > 100))
                && (lastSoupLocation != null && myLocation.distanceSquaredTo(lastSoupLocation) < 3 || turnsToBase > 10)
                && rc.getSoupCarrying() > 70) {
            System.out.println("Refinery Check: " + distToBase + " " + lastSoupLocation + " " + myLocation + " " + turnsToBase);
            //TODO: build a refinery smarter and in good direction.
            //build new refinery!
            for (Direction dir : directions) {
                MapLocation candidateBuildLoc = myLocation.add(dir);
                boolean outsideOuterWall = (candidateBuildLoc.x - hqLocation.x) > 3 || (candidateBuildLoc.x - hqLocation.x) < -3 || (candidateBuildLoc.y - hqLocation.y) > 3 || (candidateBuildLoc.y - hqLocation.y) < -3;
                if (outsideOuterWall && rc.isReady() && rc.canBuildRobot(RobotType.REFINERY, dir)
                        && dSchoolExists && onBuildingGridSquare(myLocation.add(dir))) {
                    rc.buildRobot(RobotType.REFINERY, dir);
                    //send message if this is the first refinery built
                    if (!firstRefineryExists) {
                        BuiltMessage b = new BuiltMessage(MAP_HEIGHT, MAP_WIDTH, teamNum);
                        b.writeTypeBuilt(3); //3 is refinery
                        if (sendMessage(b.getMessage(), 1)) {
                            firstRefineryExists = true;
                        }
                    }
                    if (baseLocation.equals(destination)) {
                        destination = myLocation.add(dir);
                    }
                    baseLocation = myLocation.add(dir);
                }
            }
        }
    }

    // Returns location of nearest soup
    //TODO: Replace with linked list. Make this F A S T
    public MapLocation updateNearestSoupLocation() throws GameActionException {
//        System.out.println("start map scan "+rc.getRoundNum() + " " +Clock.getBytecodeNum());
        for (int x = Math.max(myLocation.x - 5, 0); x <= Math.min(myLocation.x + 5, MAP_WIDTH - 1); x++) {
            //TODO: this ignores left most pt bc bit mask size 10. Switch too big to fit with 11. How to fix?
            for (int y : getLocationsToCheck(((soupChecked[x] >> Math.max(myLocation.y - 5, 0)) << Math.max(5-myLocation.y,0)) & 1023)) {
                MapLocation newLoc = new MapLocation(x, myLocation.y + y - 5);
                if (rc.canSenseLocation(newLoc)) {
                    if (rc.senseSoup(newLoc) > 0) {
                        soupListLocations.add(newLoc, 1);
                    }
                    soupChecked[x] = soupChecked[x] | (1L << Math.min(Math.max(myLocation.y + y - 5, 0), MAP_HEIGHT - 1));
                }
            }
        }
//        System.out.println("end map scan "+rc.getRoundNum() + " " +Clock.getBytecodeNum());

//        System.out.println("start find nearest "+rc.getRoundNum() + " " +Clock.getBytecodeNum());
        MapLocation nearest = soupListLocations.findNearest();
//        soupListLocations.printAll();
//        System.out.println("nearest: " + nearest);
//        System.out.println("end find nearest "+rc.getRoundNum() + " " +Clock.getBytecodeNum());

        if (nearest != null) {
            lastSoupLocation = nearest;
            return nearest;
        }
        return getNearestUnexploredTile();
    }

//    public boolean isSurroundedByWater(MapLocation loc) throws GameActionException {
//        for (Direction dir : directions) {
//            if (rc.canSenseLocation(loc.add(dir)) && !rc.senseFlooding(loc.add(dir)))
//                return false;
//        }
//        return true;
//    }

    //TODO: Optimize this. Scan outward with switch statements? Replace int[] for tiles with bits?
    //TODO: Explore in better way, mark off tiles when you see them, not visit!
    public MapLocation getNearestUnexploredTile() throws GameActionException {
        int currentTile = getTileNumber(myLocation);
        int scanRadius = rc.getCurrentSensorRadiusSquared();
        for (int[] shift : SPIRAL_ORDER) {
            int newTile = currentTile + shift[0] + numCols * shift[1];
            if (newTile >= 0 && newTile < numRows * numCols && tilesVisited[newTile] == 0) {
                MapLocation newTileLocation = getCenterFromTileNumber(newTile);
                if (myLocation.distanceSquaredTo(newTileLocation) >= scanRadius || !rc.senseFlooding(newTileLocation)) {
                    //rc.setIndicatorDot(newTileLocation, 255, 0,0);
                    return newTileLocation;
                }
            }
        }
        return new MapLocation(0, 0); // explored entire map and no soup left
    }

    /**
     * Communicating with the HQ
     */

    //Find message from allies given a round number rn
    //Checks block of round number rn, loops through messages
    //Currently: Checks for Patch message from HQ
    //           Checks for haltProductionMessage from a Miner
    //           Checks for BuiltMessage from Miners to see if Fulfillment Center or d.school exists
    //Ideally, should stop when it's found them all, but chances are so low
    //it's probably not a significant bytecode saving.
    public void findMessageFromAllies(int rn) throws GameActionException {
        Transaction[] msgs = rc.getBlock(rn);
       System.out.println("[i] reading messages from " + Integer.toString(rn) + " round.");
        for (Transaction transaction : msgs) {
            int[] msg = transaction.getMessage();
            if (allyMessage(msg[0])) {
                if (getSchema(msg[0]) == 2) {
                    MinePatchMessage p = new MinePatchMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum);
                    //System.out.println("Found a mine patch message with " + Integer.toString(p.numPatchesWritten) + " patches.");
                    int oldPatch = -1;
                    for (int j = 0; j < p.MAX_PATCHES; j++) {
                        int thisPatch = p.readPatch(j);
                        int thisWeight = p.readWeight(j);
                        //System.out.println(thisPatch);
                        if (thisPatch == oldPatch) {
                            break;
                        }
                        if (soupMiningTiles[thisPatch] == 0) {
                            //For weighting, set another array so that
                            soupMiningTiles[thisPatch] = 1;
                            MapLocation cLoc = getCenterFromTileNumber(thisPatch);
                            //System.out.print("HQ told me about this new soup tile: ");
                            //System.out.println(p.patches[j]);
                            //rc.setIndicatorDot(cLoc, 235, 128, 114);
                            if ((soupChecked[cLoc.x] >> cLoc.y & 1) == 0) {
                                soupListLocations.add(cLoc, thisWeight);
                            }
                        }
                        oldPatch = thisPatch;
                    }
                } else if (getSchema(msg[0]) == 3) {
                    //don't actually do anything if you are the miner that sent the halt
                    //you shouldn't halt production, we need you to build the net gun.
                    if (!hasSentHalt) {
                        System.out.println("[i] HOLDING PRODUCTION!");
                        holdProduction = true;
                        turnAtProductionHalt = rc.getRoundNum();
                        //rc.setIndicatorDot(enemyHQLocApprox, 255, 123, 55);
                    }
                } else if ((!fulfillmentCenterExists || !dSchoolExists || !firstRefineryExists) && getSchema(msg[0]) == 5) {
                    //drone has been built.
                    BuiltMessage b = new BuiltMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum);
                    if (b.typeBuilt == 1) {
                        fulfillmentCenterExists = true;
                    } else if (b.typeBuilt == 2) {
                        dSchoolExists = true;
                    } else if (b.typeBuilt == 3) {
                        firstRefineryExists = true;
                    }
                } else if(enemyHQLocation==null && getSchema(msg[0])==4) {
                    checkForEnemyHQLocationMessageSubroutine(msg);
                    if(ENEMY_HQ_LOCATION != null) {
                        enemyHQLocation = ENEMY_HQ_LOCATION;
                        System.out.println("[i] I know ENEMY HQ " + enemyHQLocation);
                    }
                } else if(getSchema(msg[0])==7) {
                    RushCommitMessage r = new RushCommitMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum);
                    if(r.typeOfCommit == 1) {
                        if(!hasSentRushCommit) {
                            System.out.println("[i] Commiting to Rush!");
                            rushHold = true;
                            turnAtRushHalt = rc.getRoundNum();
                        }
                    } else if(r.typeOfCommit == 2) {
                        if(!enemyAggression) {
                            System.out.println("[i] I know Enemy is Rushing!");
                            enemyAggression = true;
                            turnAtEnemyAggression = rc.getRoundNum();
                        }
                    } else if (r.typeOfCommit == 3) {
                        if(enemyAggression) {
                            System.out.println("[i] Enemy has stopped rushing");
                            enemyAggression = false;
                            turnAtEnemyAggression = -1;
                        }
                    }
                }
            }
        }
    }

    //Returns true if it finds all messages it's looking for
    //Only checks every 5 turns. Less robust but less bytecode than
    //previous implementation (commented out below).
    public boolean updateActiveLocations() throws GameActionException {
        //System.out.println("start reading");
        int rn = rc.getRoundNum();
        int prev = rn - messageFrequency;
        for (int i = prev; i < rn; i++) {
            if (i > 0) {
                findMessageFromAllies(i);
            }
        }
        return false;
    }

    //Returns true if it finds all messages it's looking for

    public void sendSoupMessageIfShould(MapLocation destination, boolean noSoup) throws GameActionException {
        int tnum = getTileNumber(destination);
        if (soupMiningTiles[tnum] == 0 || noSoup) {
            int soupTotal = 0;
            MapLocation[] tileLocs = getAllCellsFromTileNumber(tnum);
            for (MapLocation m : tileLocs) {
                // it's ok not to scan full range. Otherwise, you might never delete something. Also HQ scans.
                if (rc.canSenseLocation(m) && !rc.senseFlooding(m)) {
                    soupTotal += rc.senseSoup(m);
                }
            }
            // 0 when hq doesn't know about it
            System.out.println("I see " + Integer.toString(soupTotal) + " soup, so I'm sending a message");
            if (!noSoup || soupTotal == 0) {
                generateSoupMessage(destination, soupToPower(soupTotal));
            }
            soupMiningTiles[tnum] = 1;
        }
    }

    public void generateSoupMessage(MapLocation destination, int soupAmount) throws GameActionException {
        int tnum = getTileNumber(destination);
//        if (soupAmount>0) {
//            System.out.println("Telling HQ about Soup at tile: " + Integer.toString(tnum));
//            rc.setIndicatorDot(getCenterFromTileNumber(tnum), 255, 255, 0);
//        } else {
//            System.out.println("Telling HQ about NO SOUP at tile: " + Integer.toString(tnum));
//            rc.setIndicatorDot(getCenterFromTileNumber(tnum), 168, 0, 255);
//        }
        SoupMessage s = new SoupMessage(MAP_HEIGHT, MAP_WIDTH, teamNum);
        s.writeTile(tnum);
        s.writeSoupAmount(soupAmount);
        sendMessage(s.getMessage(), 1);
    }

    private class SoupList {

        private SoupLoc head;
        private SoupLoc nearest;

        public SoupList() {
            head = new SoupLoc(null, null,0);
            nearest = null;
        }

        public void add(MapLocation ml, int priority) {
            head.next = new SoupLoc(ml, head.next, priority);
        }

        public boolean isSurroundedByWater(MapLocation loc) throws GameActionException {
            for (Direction dir : directions) {
                if (rc.canSenseLocation(loc.add(dir)) && !rc.senseFlooding(loc.add(dir)))
                    return false;
            }
            return true;
        }

        public void printAll() {
            SoupLoc ptr = head;
            while (ptr.next != null) {
                System.out.println("List: " + ptr.mapLocation);
                ptr = ptr.next;
            }
        }

        public MapLocation findNearest() throws GameActionException {
            int scanRadius = rc.getCurrentSensorRadiusSquared();
            if (nearest != null && myLocation.distanceSquaredTo(nearest.mapLocation) <= 2
                && rc.canSenseLocation(nearest.mapLocation) &&
                    (rc.senseSoup(nearest.mapLocation) != 0 && !isSurroundedByWater(nearest.mapLocation))) { // cache nearest
                return nearest.mapLocation;
            }
            int nearestDist = Integer.MAX_VALUE;
            nearest = null;
            SoupLoc oldPtr = head;
            SoupLoc ptr = head.next;
            while (ptr != null) {
                int distToPtr = ptr.mapLocation.distanceSquaredTo(myLocation);
                MapLocation ptrLocation = ptr.mapLocation;
                if (distToPtr < nearestDist) {
                    if (distToPtr < scanRadius && (rc.senseSoup(ptrLocation) == 0 || isSurroundedByWater(ptrLocation))) {
                        oldPtr.next = oldPtr.next.next;
                        if (ptr.priority == HQ_SEARCH) {
                            sendSoupMessageIfShould(ptrLocation, true);
                        }
                    } else {
                        nearest = ptr;
                        nearestDist = distToPtr;
                        if (distToPtr <= 2) {
                            break;
                        }
                    }
                }
                oldPtr = ptr;
                ptr = ptr.next;
            }
            return nearest != null ? nearest.mapLocation : null;
        }

        private class SoupLoc {
            public SoupLoc next;
            public MapLocation mapLocation;
            public int priority;

            public SoupLoc(MapLocation ml, SoupLoc n, int p) {
                mapLocation = ml;
                next = n;
                priority = p;
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
}
