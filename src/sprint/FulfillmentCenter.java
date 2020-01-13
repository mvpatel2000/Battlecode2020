package sprint;

import battlecode.common.*;

public class FulfillmentCenter extends Building {

    int droneCount = 0;

    public FulfillmentCenter(RobotController rc) throws GameActionException {
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        super.run();
        for (Direction dir : directions) {
            if (droneCount < 10 && tryBuild(RobotType.DELIVERY_DRONE, dir))
                droneCount++;
        }
    }
}
