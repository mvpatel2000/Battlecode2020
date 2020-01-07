package examplefuncsplayer;

import battlecode.common.*;

public class DesignSchool extends Building {

    public DesignSchool(RobotController rc) {
        super(rc);
    }

    @Override
    public void run() throws GameActionException  {
        turnCount++;
    }
}
