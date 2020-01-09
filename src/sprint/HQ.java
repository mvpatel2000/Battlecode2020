package sprint;
import battlecode.common.*;

public class HQ extends Building {

    NetGun netgun;
    Refinery refinery;
    int numMiners;

    public HQ(RobotController rc) {
        super(rc);
        netgun = new NetGun(rc);
        refinery = new Refinery(rc);
        numMiners = 0;
    }

    /*
     * 1. Always shoot enemy drones down if possible
     */
    @Override
    public void run() throws GameActionException {
        setupTurn();

        netgun.shoot();

        for (Direction dir : directions) {
            if(numMiners < 1 && tryBuild(RobotType.MINER, dir)) numMiners++;
        }
    }
}
