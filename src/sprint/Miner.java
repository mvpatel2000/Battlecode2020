package sprint;

import battlecode.common.*;

public class Miner extends Unit {

    int[][] moveLocs = {{0, 0}, {-1, 0}, {0, -1}, {0, 1}, {1, 0}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}, {-2, 0}, {0, -2}, {0, 2}, {2, 0}, {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}, {-2, -2}, {-2, 2}, {2, -2}, {2, 2}, {-3, 0}, {0, -3}, {0, 3}, {3, 0}, {-3, -1}, {-3, 1}, {-1, -3}, {-1, 3}, {1, -3}, {1, 3}, {3, -1}, {3, 1}, {-3, -2}, {-3, 2}, {-2, -3}, {-2, 3}, {2, -3}, {2, 3}, {3, -2}, {3, 2}, {-4, 0}, {0, -4}, {0, 4}, {4, 0}, {-4, -1}, {-4, 1}, {-1, -4}, {-1, 4}, {1, -4}, {1, 4}, {4, -1}, {4, 1}, {-3, -3}, {-3, 3}, {3, -3}, {3, 3}, {-4, -2}, {-4, 2}, {-2, -4}, {-2, 4}, {2, -4}, {2, 4}, {4, -2}, {4, 2}, {-5, 0}, {-4, -3}, {-4, 3}, {-3, -4}, {-3, 4}, {0, -5}, {0, 5}, {3, -4}, {3, 4}, {4, -3}, {4, 3}, {5, 0}, {-5, -1}, {-5, 1}, {-1, -5}, {-1, 5}, {1, -5}, {1, 5}, {5, -1}, {5, 1}, {-5, -2}, {-5, 2}, {-2, -5}, {-2, 5}, {2, -5}, {2, 5}, {5, -2}, {5, 2}, {-4, -4}, {-4, 4}, {4, -4}, {4, 4}, {-5, -3}, {-5, 3}, {-3, -5}, {-3, 5}, {3, -5}, {3, 5}, {5, -3}, {5, 3}};

    MapLocation destination;
    MapLocation baseLocation;

    public Miner(RobotController rc) throws GameActionException {
        super(rc);
        System.out.println(myLocation);
        for (Direction dir : directions) {                   // Marginally cheaper than sensing in radius 2
            MapLocation t = myLocation.add(dir);
            RobotInfo r = rc.senseRobotAtLocation(t);
            if (r != null && r.getType() == RobotType.HQ) {
                baseLocation = t;
                break;
            }
        }
        destination = nearestSoupLocation();
        if (destination == null) // TODO: Fix this. Handle this case.
            destination = new MapLocation(0, 0);
    }

    @Override
    public void run() throws GameActionException {
        super.run();
        setupTurn();
        harvest();
        //TODO: Modify Harvest to build refineries if mining location > some dist from base
        //TODO: Handle case where no stuff found. Switch to explore mode. See other TODOs after calling nearestSoup
        //TODO: Debug movement and verify it all works.
        //TODO: Add in pathfinding to keep track of observed stuff. Build map over time.
    }

    public void harvest() throws GameActionException {
        int distanceToDestination = myLocation.distanceSquaredTo(destination);
        if (distanceToDestination <= 2) {
            if (destination == baseLocation) { // at HQ
                Direction hqDir = myLocation.directionTo(destination);
                if (rc.canDepositSoup(hqDir)) // Note: Second check is redundant?
                    rc.depositSoup(hqDir, rc.getSoupCarrying());
                if (rc.getSoupCarrying() == 0) { // still carrying soup
                    destination = nearestSoupLocation();
                    if (destination == null) // TODO: Fix this. Handle this case.
                        destination = new MapLocation(0, 0);
                }
            } else { // mining
                Direction soupDir = myLocation.directionTo(destination);
                if (rc.isReady() && rc.canMineSoup(soupDir)) { // Note: Second check is redundant?
                    rc.mineSoup(soupDir);
                }
                if (rc.getSoupCarrying() == RobotType.MINER.soupLimit) {
                    destination = baseLocation;
                } else {
                    destination = nearestSoupLocation(); // returns same location if it still has soup
                }
            }
        } else { //in transit
            path(destination);
            if (destination != baseLocation) {
                destination = nearestSoupLocation();
                if (destination == null) // TODO: Fix this. Handle this case.
                    destination = new MapLocation(0, 0);
            }
        }
    }

    // Returns location of nearest soup
    public MapLocation nearestSoupLocation() throws GameActionException {
        for (int i = 0; i < moveLocs.length; i++) {
            MapLocation newLoc = myLocation.translate(moveLocs[i][0], moveLocs[i][1]);
            if (rc.canSenseLocation(newLoc)) {
                int souphere = rc.senseSoup(newLoc);
                if (souphere > 0) {
                    return newLoc;
                }
            }
        }
        return null;
    }

    /**
     * Attempts to mine soup in a given direction.
     *
     * @param dir The intended direction of mining
     * @return true if a move was performed
     * @throws GameActionException
     */
    boolean tryMine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canMineSoup(dir)) {
            rc.mineSoup(dir);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Attempts to refine soup in a given direction.
     *
     * @param dir The intended direction of refining
     * @return true if a move was performed
     * @throws GameActionException
     */
    boolean tryRefine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDepositSoup(dir)) {
            if (rc.senseRobotAtLocation(myLocation.add(dir)).getTeam() == allyTeam) {
                rc.depositSoup(dir, rc.getSoupCarrying());
                return true;
            }
        } else {
            return false;
        }
        return false;
    }
}
