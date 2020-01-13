package zeus;

import battlecode.common.*;

public class FulfillmentCenter extends Building {

    int attackDroneCount = 0;
    int defendDroneCount = 0;
    final double ATTACK_TO_DEFENSE_RATIO = .5;

    MapLocation hqLocation = null;

    //For halting production and resuming it.
    boolean holdProduction = false;
    int turnAtProductionHalt = -1;
    int previousSoup = 200;
    MapLocation enemyHQLocApprox = null;


    public FulfillmentCenter(RobotController rc) throws GameActionException {
        super(rc);
        hqLocation = checkForLocationMessage();
    }

    @Override
    public void run() throws GameActionException {
        if(holdProduction) {
            checkIfContinueHold();
        }

        super.run();
        if(!holdProduction) {
            if ((rc.getTeamSoup() >= Math.min(150 + 15 * (attackDroneCount + defendDroneCount), 200)) && ((attackDroneCount + defendDroneCount) < 10 || rc.getRoundNum() > 655 || rc.getTeamSoup() > 1100))
                buildDrone();
        }

        if(rc.getRoundNum()%5==3) {
            readMessages();
        }

        //should always be the last thing
        previousSoup = rc.getTeamSoup();
    }

    private void buildDrone() throws GameActionException {
        boolean built = false;
        if (attackDroneCount > defendDroneCount * ATTACK_TO_DEFENSE_RATIO) {
            Direction toHQ = myLocation.directionTo(hqLocation);
            if (tryBuild(RobotType.DELIVERY_DRONE, toHQ)) {
                defendDroneCount++;
            } else if (tryBuild(RobotType.DELIVERY_DRONE, toHQ.rotateLeft())) {
                defendDroneCount++;
            } else if (tryBuild(RobotType.DELIVERY_DRONE, toHQ.rotateRight())) {
                defendDroneCount++;
            } else {
                built = true;
            }
        }
        else {
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
                built = true;
            }
        }
        if (!built) {
            for (Direction dir : directions) {
                if (tryBuild(RobotType.DELIVERY_DRONE, dir)) {
                    if (attackDroneCount > defendDroneCount) // we built a type that we already had
                        attackDroneCount++;
                    else
                        defendDroneCount++;
                }
            }
        }
    }

    //Returns true if should continue halting production
    //Returns false if should not continue halting production
    private boolean checkIfContinueHold() throws GameActionException {
        //resume production after 10 turns, at most
        if(rc.getRoundNum()-turnAtProductionHalt>10) {
            System.out.println("UNHOLDING PRODUCTION!");
            holdProduction = false;
            return false;
        }
        //-200 soup in one turn good approximation for building net gun
        //so we resume earlier than 10 turns if this happens
        if(previousSoup - rc.getTeamSoup() > 200) {
            System.out.println("UNHOLDING PRODUCTION!");
            holdProduction = false;
            return false;
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
            Message m = new Message(msg, MAP_HEIGHT, MAP_WIDTH, teamNum);
            if (m.origin) {
                if(m.schema == 3) {
                    HoldProductionMessage h = new HoldProductionMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum);
                    System.out.println("HOLDING PRODUCTION!");
                    holdProduction = true;
                    turnAtProductionHalt = rc.getRoundNum();
                    enemyHQLocApprox = getCenterFromTileNumber(h.enemyHQTile);
                    return true;
                }
            }
        }
        return false;
    }
}
