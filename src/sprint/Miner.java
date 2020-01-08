package sprint;

import battlecode.common.*;

public class Miner extends Unit {

    int[][] moveLocs = {{0,0}, {-1,0}, {0,-1}, {0,1}, {1,0}, {-1,-1}, {-1,1}, {1,-1}, {1,1}, {-2,0}, {0,-2}, {0,2}, {2,0}, {-2,-1}, {-2,1}, {-1,-2}, {-1,2}, {1,-2}, {1,2}, {2,-1}, {2,1}, {-2,-2}, {-2,2}, {2,-2}, {2,2}, {-3,0}, {0,-3}, {0,3}, {3,0}, {-3,-1}, {-3,1}, {-1,-3}, {-1,3}, {1,-3}, {1,3}, {3,-1}, {3,1}, {-3,-2}, {-3,2}, {-2,-3}, {-2,3}, {2,-3}, {2,3}, {3,-2}, {3,2}, {-4,0}, {0,-4}, {0,4}, {4,0}, {-4,-1}, {-4,1}, {-1,-4}, {-1,4}, {1,-4}, {1,4}, {4,-1}, {4,1}, {-3,-3}, {-3,3}, {3,-3}, {3,3}, {-4,-2}, {-4,2}, {-2,-4}, {-2,4}, {2,-4}, {2,4}, {4,-2}, {4,2}, {-5,0}, {-4,-3}, {-4,3}, {-3,-4}, {-3,4}, {0,-5}, {0,5}, {3,-4}, {3,4}, {4,-3}, {4,3}, {5,0}, {-5,-1}, {-5,1}, {-1,-5}, {-1,5}, {1,-5}, {1,5}, {5,-1}, {5,1}, {-5,-2}, {-5,2}, {-2,-5}, {-2,5}, {2,-5}, {2,5}, {5,-2}, {5,2}, {-4,-4}, {-4,4}, {4,-4}, {4,4}, {-5,-3}, {-5,3}, {-3,-5}, {-3,5}, {3,-5}, {3,5}, {5,-3}, {5,3}};
    public Miner(RobotController rc) {
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        setupTurn();

        tryBlockchain();
        for (Direction dir : directionsWithCenter) {
            if (tryMine(dir)) {
                System.out.println("I mined soup! " + rc.getSoupCarrying());
                break;
            }
        }
        // tryBuild(randomSpawnedByMiner(), randomDirection());
        for (Direction dir : directions)
            tryBuild(RobotType.FULFILLMENT_CENTER, dir);
        for (Direction dir : directions) {
            if (tryRefine(dir)) {
                System.out.println("I refined soup! " + rc.getTeamSoup());
                break;
            }
        }

        for (int i=0; i<moveLocs.length; i++) {
            MapLocation newLoc = myLocation.translate(moveLocs[i][0], moveLocs[i][1]);
            if(rc.canSenseLocation(newLoc)) {
                int souphere = rc.senseSoup(newLoc);
                if(souphere>0) {
                    fuzzyMoveToLoc(newLoc);
                    System.out.println("Fuzzy moved!");
                    break;
                }
            }
        }

        if (tryMove(randomDirection())) {
            System.out.println("Random moved!");
        }
    }

    /**
     * Attempts to mine soup in a given direction.
     *
     * @param dir The intended direction of mining
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMine(Direction dir) throws GameActionException {
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
    static boolean tryRefine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDepositSoup(dir)) {
            rc.depositSoup(dir, rc.getSoupCarrying());
            return true;
        } else return false;
    }
}
