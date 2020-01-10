package sprint;

import battlecode.common.*;

public class FulfillmentCenter extends Building {

    public FulfillmentCenter(RobotController rc) throws GameActionException {
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        super.run();
        for (Direction dir : directions)
            tryBuild(RobotType.DELIVERY_DRONE, dir);
    }
}
