package qualAtacc;

import battlecode.common.*;

public class Refinery extends Building {

    public Refinery(RobotController rc) throws GameActionException {
        super(rc);
        checkForLocationMessage();
    }

    @Override
    public void run() throws GameActionException {
        super.run();

        if (rc.getRoundNum() > 1200 && myLocation.distanceSquaredTo(HEADQUARTERS_LOCATION) < 100) {
            rc.disintegrate();
        }
    }
}
