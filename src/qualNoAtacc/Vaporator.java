package qualNoAtacc;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public class Vaporator extends Building {

    public Vaporator(RobotController rc) throws GameActionException {
        super(rc);
        checkForLocationMessage();
    }

    @Override
    public void run() throws GameActionException {
        super.run();
    }
}
