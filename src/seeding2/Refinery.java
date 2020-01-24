package seeding2;

import battlecode.common.*;

public class Refinery extends Building {

    public Refinery(RobotController rc) throws GameActionException {
        super(rc);
        checkForLocationMessage();
    }

    @Override
    public void run() throws GameActionException {
        super.run();
    }
}
