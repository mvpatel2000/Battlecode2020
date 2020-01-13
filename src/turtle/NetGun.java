package turtle;

import battlecode.common.*;

public class NetGun extends Building {

    public NetGun(RobotController rc) throws GameActionException {
        super(rc);
    }

    @Override
    public void run() throws GameActionException {
        super.run();
        shoot();
    }

    /* Shoots nearest enemy drone if possible, returning true if it does shoot */
    public boolean shoot() throws GameActionException {
        if (!rc.isReady()) // Cannot take an action
            return false;
        RobotInfo target = null;
        int distSquared = -1;
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED, enemyTeam);
        for (RobotInfo nearbyRobot : nearbyRobots) {
            if (rc.canShootUnit(nearbyRobot.ID)) {
                if (target == null) { // separate check to avoid extra computation of distance
                    target = nearbyRobot;
                    distSquared = myLocation.distanceSquaredTo(target.location);
                }
                int nearbyDist = myLocation.distanceSquaredTo(target.location);
                if (nearbyDist < distSquared) {
                    target = nearbyRobot;
                    distSquared = nearbyDist;
                }
            }
        }
        if (target != null) {
            rc.shootUnit(target.ID);
            return true;
        }
        return false;
    }
}
