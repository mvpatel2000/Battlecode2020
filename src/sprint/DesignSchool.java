package sprint;

import battlecode.common.*;

public class DesignSchool extends Building {

    public DesignSchool(RobotController rc) {
        super(rc);
    }

    @Override
    public void run() throws GameActionException  {
        setupTurn();
    }
}
