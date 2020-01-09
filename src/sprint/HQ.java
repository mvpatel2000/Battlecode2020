package sprint;

import battlecode.common.*;

public class HQ extends Building {

    private NetGun netgun;
    private Refinery refinery;

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
            tryBuild(RobotType.MINER, dir);
        }
    }
}
