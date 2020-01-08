package sprint;

import battlecode.common.*;

public class FulfillmentCenter extends Building {

    public FulfillmentCenter(RobotController rc) {
        super(rc);
    }

    @Override
    public void run() throws GameActionException  {
        setupTurn();

        for (Direction dir : directions)
            tryBuild(RobotType.DELIVERY_DRONE, dir);
    }
}
