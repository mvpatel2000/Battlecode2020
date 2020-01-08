package sprint;

import battlecode.common.*;

public class HQ extends Building {

    static NetGun netgun;
    static Refinery refinery;

    public HQ(RobotController rc) {
        super(rc);
        netgun = new NetGun(rc);
        refinery = new Refinery(rc);
    }

    @Override
    public void run() throws GameActionException {
        setupTurn();

        for (Direction dir : directions)
            tryBuild(RobotType.MINER, dir);

        // netgun.shoot(); // This takes up the action!
    }
}
