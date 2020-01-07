package examplefuncsplayer;

import battlecode.common.*;

public class HQ extends Robot {

    public HQ(RobotController rc) {
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        for (Direction dir : directions)
            tryBuild(RobotType.MINER, dir);
    }
}
