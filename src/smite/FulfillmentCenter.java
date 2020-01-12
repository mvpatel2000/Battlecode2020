package smite;

import battlecode.common.*;

public class FulfillmentCenter extends Building {

    int droneCount = 0;

    public FulfillmentCenter(RobotController rc) {
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        super.run();
        for (Direction dir : directions) {
            if (droneCount < 1 && tryBuild(RobotType.DELIVERY_DRONE, dir))
                droneCount++;
        }
    }
}
