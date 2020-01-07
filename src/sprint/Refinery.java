package sprint;

import battlecode.common.*;

public class Refinery extends Building implements RefineryInterface {

    public Refinery(RobotController rc) {
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        turnCount++;
    }
}
