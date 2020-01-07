package sprint;

import battlecode.common.*;

interface NetGunInterface
{
    default void shoot(RobotController rc, Team enemyTeam) {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED, enemyTeam);
        //TODO: Filter to just drones and shoot nearest if possible. Put if statement outside sensing?
    }
}