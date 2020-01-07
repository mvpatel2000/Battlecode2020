package examplefuncsplayer;

import battlecode.common.*;

public class NetGun extends Building implements NetGunInterface {

    public NetGun(RobotController rc) {
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        turnCount++;
    }
}
