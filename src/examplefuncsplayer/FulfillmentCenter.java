package examplefuncsplayer;

import battlecode.common.*;

public class FulfillmentCenter extends Robot {

    public FulfillmentCenter(RobotController rc) {
        super(rc);
    }

    @Override
    public void run() throws GameActionException  {
        for (Direction dir : directions)
            tryBuild(RobotType.DELIVERY_DRONE, dir);
    }
}
