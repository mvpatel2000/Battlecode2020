package sprint;

import battlecode.common.*;

public class FulfillmentCenter extends Building {

    int droneCount = 0;
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
        for (Direction dir : directions) {
            if (droneCount < 10 && tryBuild(RobotType.DELIVERY_DRONE, dir))
                droneCount++;
        }
        if(rc.getRoundNum()%5==3) {
            readMessages();
        }

        //should always be the last thing
        previousSoup = rc.getTeamSoup();
    }

    //Returns true if should continue halting production
    //Returns false if should not continue halting production
    private boolean checkIfContinueHold() throws GameActionException {
        //resume production after 10 turns, at most
        if(rc.getRoundNum()-turnAtProductionHalt>10) {
            holdProduction = false;
            return false;
        }
        //-200 soup in one turn good approximation for building net gun
        //so we resume earlier than 10 turns if this happens
        if(previousSoup - rc.getTeamSoup() > 200) {
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
                    System.out.print("HOLDING PRODUCTION!");
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
