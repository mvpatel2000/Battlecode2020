package examplefuncsplayer;

import battlecode.common.*;

public class NetGun extends Building {

    public NetGun(RobotController rc) {
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        turnCount++;
    }
}
