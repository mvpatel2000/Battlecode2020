package examplefuncsplayer;

import battlecode.common.*;

public class HQ extends Building {

    public HQ(RobotController rc) {
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        turnCount++;

        for (Direction dir : directions)
            tryBuild(RobotType.MINER, dir);
    }
}
