package sprint;

import battlecode.common.*;

public class HQ extends Building {

    static NetGun netgun;
    static Refinery refinery;
    static int count = 0;

    public HQ(RobotController rc) {
        super(rc);
        netgun = new NetGun(rc);
        refinery = new Refinery(rc);
    }

    /*
     * 1. Always shoot enemy drones down if possible
     */
    @Override
    public void run() throws GameActionException {
        setupTurn();

        netgun.shoot();

        for (Direction dir : directions) {
            if (count < 1)
            tryBuild(RobotType.MINER, dir);
            count++;
        }
    }
}
