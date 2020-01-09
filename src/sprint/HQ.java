package sprint;
import battlecode.common.*;

public class HQ extends Building {

    static NetGun netgun;
    static Refinery refinery;
    int minerCount;

    public HQ(RobotController rc) {
        super(rc);
        netgun = new NetGun(rc);
        refinery = new Refinery(rc);
        minerCount = 0;
    }

    /*
     * 1. Always shoot enemy drones down if possible
     */
    @Override
    public void run() throws GameActionException {
        setupTurn();

        netgun.shoot();

        for (Direction dir : directions) {
            if (minerCount < 5 && tryBuild(RobotType.MINER, dir))
                minerCount++;
        }
    }
}
