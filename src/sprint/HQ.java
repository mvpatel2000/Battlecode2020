package sprint;

import battlecode.common.*;

public class HQ extends Building {

    private NetGun netgun;
    private Refinery refinery;
    int minerCount;
    int[] patchList;
    int[] patchProbs;

    public HQ(RobotController rc) throws GameActionException {
        super(rc);
        netgun = new NetGun(rc);
        refinery = new Refinery(rc);
        minerCount = 0;
        patchList = new int[17];
        patchProbs = new int[17];
    }

    /*
     * 1. Always shoot enemy drones down if possible
     */
    @Override
    public void run() throws GameActionException {
        super.run();
        netgun.shoot();
        for (Direction dir : directions) {
            if (minerCount < 3 && tryBuild(RobotType.MINER, dir))
                minerCount++;
        }
        readMessages();
    }

    void readMessages() {

    }
}
