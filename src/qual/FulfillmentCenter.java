package qual;

import battlecode.common.*;

public class FulfillmentCenter extends Building {

    int attackDroneCount = 0;
    int defenseDroneCount = 0;
    final double ATTACK_TO_DEFENSE_RATIO = .5;

    MapLocation hqLocation = null;

    //For halting production and resuming it.
    boolean holdProduction = false;
    int turnAtProductionHalt = -1;
    int previousSoup = 200;
    boolean enemyNetGun = false;
    int spawnTurn;


    public FulfillmentCenter(RobotController rc) throws GameActionException {
        super(rc);
        checkForLocationMessage();
        hqLocation = HEADQUARTERS_LOCATION;

        //readmessages before you were born to see if we are under attack
        int rn = rc.getRoundNum();
        int topr = Math.min(rn, 200);
        for(int i=1; i<topr; i++) {
            findMessagesFromAllies(i);
        }
        spawnTurn = rc.getRoundNum();
    }

    @Override
    public void run() throws GameActionException {
        super.run();

        if(holdProduction || enemyAggression) {
            checkIfContinueHold();
        }

        if(rc.getRoundNum() < 300 && !enemyAggression) {
            if(enemyAggressionCheck()) {
                turnAtEnemyAggression = rc.getRoundNum();
            }
        }

        enemyNetGun = false;
        for (RobotInfo enemy : rc.senseNearbyRobots(rc.getCurrentSensorRadiusSquared(), enemyTeam)) {
            if (enemy.type == RobotType.NET_GUN) {
                enemyNetGun = true;
                break;
            }
        }

        if(!holdProduction && !enemyNetGun && rc.getRoundNum() - spawnTurn > 30) {
            if (attackDroneCount + defenseDroneCount < 1) {
                buildDrone();
            } else if (!enemyAggression && rc.getRoundNum() > 200 && attackDroneCount + defenseDroneCount < 4) {
                buildDrone();
            } if (rc.getTeamSoup() >= 521 && rc.getRoundNum() > 655 || rc.getTeamSoup() > 1100) {
                buildDrone();
            }
        }

        findMessagesFromAllies(rc.getRoundNum()-1);

        //should always be the last thing
        previousSoup = rc.getTeamSoup();
    }

    private void buildDrone() throws GameActionException {
        boolean built = true;
        if (defenseDroneCount == 0 || attackDroneCount > defenseDroneCount * ATTACK_TO_DEFENSE_RATIO) {
            System.out.println("Building defense drone");
            Direction toHQ = myLocation.directionTo(hqLocation);
            if (tryBuild(RobotType.DELIVERY_DRONE, toHQ)) {
                defenseDroneCount++;
            } else if (tryBuild(RobotType.DELIVERY_DRONE, toHQ.rotateLeft())) {
                defenseDroneCount++;
            } else if (tryBuild(RobotType.DELIVERY_DRONE, toHQ.rotateRight())) {
                defenseDroneCount++;
            } else {
                built = false;
            }
        }
        else {
            System.out.println("Building attack drone");
            Direction awayHQ = myLocation.directionTo(hqLocation).opposite();
            if (tryBuild(RobotType.DELIVERY_DRONE, awayHQ)) {
                attackDroneCount++;
            } else if (tryBuild(RobotType.DELIVERY_DRONE, awayHQ.rotateLeft())) {
                attackDroneCount++;
            } else if (tryBuild(RobotType.DELIVERY_DRONE, awayHQ.rotateRight())) {
                attackDroneCount++;
            } else if (tryBuild(RobotType.DELIVERY_DRONE, awayHQ.rotateLeft().rotateLeft())) {
                attackDroneCount++;
            } else if (tryBuild(RobotType.DELIVERY_DRONE, awayHQ.rotateRight().rotateRight())) {
                attackDroneCount++;
            } else {
                built = false;
            }
        }
        if (!built) {
            System.out.println("Build failed, just build whatever");
            for (Direction dir : directions) {
                if (tryBuild(RobotType.DELIVERY_DRONE, dir)) { // we built a type that we already had and tried not to
                    if (attackDroneCount > defenseDroneCount * ATTACK_TO_DEFENSE_RATIO)
                        attackDroneCount++;
                    else
                        defenseDroneCount++;
                }
            }
        }
    }

    //Returns true if should continue halting production
    //Returns false if should not continue halting production
    private boolean checkIfContinueHold() throws GameActionException {
        //resume production after 10 turns, at most
        if(holdProduction) {
            if(rc.getRoundNum()-turnAtProductionHalt>30) {
                System.out.println("[i] UNHOLDING PRODUCTION!");
                holdProduction = false;
                return false;
            }
            //-200 soup in one turn good approximation for building net gun
            //so we resume earlier than 10 turns if this happens
            if(previousSoup - rc.getTeamSoup() > 200) {
                System.out.println("[i] UNHOLDING PRODUCTION!");
                holdProduction = false;
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

    /**
     * Communication methods
     * readMessages() calls findMessagesFromAllies() for set number of rounds
     */
    public boolean readMessages() throws GameActionException {
        int rn = rc.getRoundNum();
        int prev1 = rn-5;
        for(int i=prev1; i<rn; i++) {
            if(i>0) {
                if(findMessagesFromAllies(i)) {
                    return true;
                }
            }
        }
        return false;
    }
    //Find message from allies given a round number rn
    //Checks block of round number rn, loops through messages
    //Currently: Checks for haltProductionMessage from a Miner
    public boolean findMessagesFromAllies(int rn) throws GameActionException {
        Transaction[] msgs = rc.getBlock(rn);
        for (Transaction transaction : msgs) {
            int[] msg = transaction.getMessage();
            if (allyMessage(msg[0])) {
                if(getSchema(msg[0])==3) {
                    HoldProductionMessage h = new HoldProductionMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum);
                    System.out.println("[i] HOLDING PRODUCTION!");
                    holdProduction = true;
                    turnAtProductionHalt = rc.getRoundNum();
                    return true;
                } else if (getSchema(msg[0])==7) {
                    RushCommitMessage r = new RushCommitMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum);
                    if(!enemyAggression) {
                        if(r.typeOfCommit==2) {
                            System.out.println("[i] Enemy is Rushing!");
                            enemyAggression = true;
                            turnAtEnemyAggression = rc.getRoundNum();
                        }
                    } else {
                        if(r.typeOfCommit==3) {
                            System.out.println("[i] Enemy has stopped rushing");
                            enemyAggression = false;
                            turnAtEnemyAggression = -1;
                        }
                    }
                }
            }
        }
        return false;
    }
}
