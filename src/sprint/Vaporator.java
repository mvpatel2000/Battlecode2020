package sprint;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public class Vaporator extends Building {

    public Vaporator(RobotController rc) {
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        turnCount++;
    }
}
