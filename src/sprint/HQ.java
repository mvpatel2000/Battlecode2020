package sprint;
import battlecode.common.*;

public class HQ extends Building {

    static NetGun netgun;
    static Refinery refinery;

    public HQ(RobotController rc) {
        super(rc);
        netgun = new NetGun(rc);
        refinery = new Refinery(rc);
    }

    /*
     * 1. Always shoot enemy drones down if possible
     */
    @Override
    public void run() throws GameActionException {
        setupTurn();

        //netgun.shoot();
        System.out.println("Hello");
        for (Direction dir : directions) {
            tryBuild(RobotType.MINER, dir);
        }
    }
}
