package sprint;

import battlecode.common.*;

public class Landscaper extends Unit {

    public Landscaper(RobotController rc) {
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        super.run();
        for (Direction dir : directions) {
            if (rc.canDigDirt(dir)) {
                rc.digDirt(dir);
                break;
            }
        }
    }
}
