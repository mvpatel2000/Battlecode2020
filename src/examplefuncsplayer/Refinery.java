package examplefuncsplayer;

import battlecode.common.*;

public class Refinery extends Building {

    public Refinery(RobotController rc) {
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        turnCount++;
    }
}
