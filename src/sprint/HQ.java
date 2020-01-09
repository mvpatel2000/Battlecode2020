package sprint;

import battlecode.common.*;

public class HQ extends Building {

    private NetGun netgun;
    private Refinery refinery;
    int minerCount;

    public HQ(RobotController rc) {
        super(rc);
        netgun = new NetGun(rc);
        refinery = new Refinery(rc);
        minerCount = 0;
    }

    /*
     * 1. Always shoot enemy drones down if possible
     */
    @Override
    public void run() throws GameActionException {
        super.run();
        netgun.shoot();
        for (Direction dir : directions) {
            if (minerCount < 1 && tryBuild(RobotType.MINER, dir))
                minerCount++;
        }
    }
}
