package sprint;

import battlecode.common.*;

import java.util.*;

public abstract class Unit extends Robot {

    private enum Hand {LEFT, RIGHT}

    protected Deque<MapLocation> history;
    protected Map<MapLocation, Integer> historySet;
    protected boolean hasHistory;
    protected int stuck;
    protected Direction facing;
    protected Hand dir;
    protected int rand;
    protected int time;

    public static int HISTORY_SIZE = 5;

    public Unit(RobotController rc) throws GameActionException {
        super(rc);
        stuck = 0;
        clearHistory();
        facing = null;
        dir = null;
        rand = 0;
    }

    public void clearHistory() {
        hasHistory = false;
        history = new LinkedList<>();
        historySet = new HashMap<>();
        MapLocation loc = rc.getLocation();
        for (int i = 0; i < HISTORY_SIZE; i++) {
            history.addFirst(loc);
        }
        historySet.put(loc, HISTORY_SIZE);
        dir = null;
    }

    @Override
    public void run() throws GameActionException {
        myLocation = rc.getLocation();
        MapLocation me = myLocation;
        history.addFirst(me);
        historySet.merge(me, 1, Integer::sum);
        MapLocation loc = history.pollLast();
        historySet.merge(loc, -1, Integer::sum);
        hasHistory = true;
    }

    boolean tryMove() throws GameActionException {
        for (Direction dir : directions)
            if (tryMove(dir))
                return true;
        return false;
        // MapLocation loc = rc.getLocation();
        // if (loc.x < 10 && loc.x < loc.y)
        //     return tryMove(Direction.EAST);
        // else if (loc.x < 10)
        //     return tryMove(Direction.SOUTH);
        // else if (loc.x > loc.y)
        //     return tryMove(Direction.WEST);
        // else
        //     return tryMove(Direction.NORTH);
    }

    /**
     * Attempts to move in a given direction.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    boolean tryMove(Direction dir) throws GameActionException {
        // System.out.println("I am trying to move " + dir + "; " + rc.isReady() + " " + rc.getCooldownTurns() + " " + rc.canMove(dir));
        if (rc.isReady() && rc.canMove(dir)) {
            rc.move(dir);
            return true;
        } else return false;
    }

    public MapLocation locAt(int t) {
        Iterator<MapLocation> itr = history.iterator();
        for (int i = 0; i < t; i++) {
            itr.next();
        }
        return itr.next();
    }

    private Direction left(Direction in) {
        return intToDirection((directionToInt(in) + 2) % 8);
    }

    private Direction right(Direction in) {
        return intToDirection((directionToInt(in) + 6) % 8);
    }

    private Direction adj(Direction in, int k) {
        return intToDirection((directionToInt(in) + k) % 8);
    }

    private boolean canMove(Direction in) {
        MapLocation me = history.peekFirst().add(in);
        try {
            return rc.canSenseLocation(me) && rc.canMove(in) && !rc.senseFlooding(me);
        } catch (GameActionException e) {
            e.printStackTrace();
            return false;
        }
    }

    boolean path(MapLocation target) throws GameActionException {
        System.out.println("Pathing to: " + target);
        if (rc.getCooldownTurns() >= 1)
            return true;
        MapLocation me = history.peekFirst();
        if (me.equals(target)) {
            return false;
        }
        double cost = Double.POSITIVE_INFINITY;
        Direction best = null;
        double pcost = Double.POSITIVE_INFINITY;
        Direction pbest = null;
        for (Direction x : directions) {
            MapLocation next = me.add(x);
            int tmpcost = next.distanceSquaredTo(target);
            if (tmpcost < cost) {
                cost = tmpcost;
                best = x;
            }
            if (tmpcost < pcost && canMove(x)) {
                pcost = tmpcost;
                pbest = x;
            }
        }
        int itr = 0;
        if (Math.random() < 0.2 && dir == null) {
            return pathHelper(target, pbest);
        }
        while ((dir != null && !canMove(facing)) || (dir == null && !canMove(best))) {
            if (itr == 0)
                time++;
            if (time == 10)
                break;
            itr++;
            if (dir == null) {
                if (Math.random() < 0.5)
                    rand = 1 - rand;
                if (Math.random() < 0.3)
                    best = adj(best, rand * 2 + 7);
                facing = best;
            }
            if (rand == 1) {
                Direction l = left(facing);
                if (canMove(l) && dir != Hand.RIGHT) {
                    dir = Hand.LEFT;
                    tryMove(l);
                    return true;
                }
                Direction r = right(facing);
                if (canMove(r) && dir != Hand.LEFT) {
                    dir = Hand.RIGHT;
                    tryMove(r);
                    return true;
                }
            }
            if (rand == 0) {
                Direction r = right(facing);
                if (canMove(r) && dir != Hand.LEFT) {
                    dir = Hand.RIGHT;
                    tryMove(r);
                    return true;
                }
                Direction l = left(facing);
                if (canMove(l) && dir != Hand.RIGHT) {
                    dir = Hand.LEFT;
                    tryMove(l);
                    return true;
                }
            }
            if (itr == 8)
                break;
            facing = adj(facing, rand * 2 + 7);
        }
        dir = null;
        time = 0;
        if (facing != null && canMove(facing)) {
            tryMove(facing);
            facing = null;
            return true;
        }
        facing = null;
        return pathHelper(target, best);
    }

    protected boolean pathHelper(MapLocation target, Direction best) throws GameActionException {
        if (best != null) {
            stuck = 0;
            tryMove(best);
            return true;
        } else {
            if (!hasHistory) {
                stuck = 0;
                return false;
            } else {
                if (stuck < 3) {
                    stuck++;
                    return true;
                } else {
                    stuck = 0;
                    clearHistory();
                    return path(target);
                }
            }
        }
    }

    boolean fuzzyMoveToLoc(MapLocation target) throws GameActionException {
        int mindist = 50000;
        Direction bestdir = null;
        for (Direction dir : directions) {
            if (rc.canMove(dir)) {
                MapLocation newLoc = myLocation.add(dir);
                int thisdist = newLoc.distanceSquaredTo(target);
                if (thisdist < mindist) {
                    mindist = thisdist;
                    bestdir = dir;
                }
            }
        }

        if (bestdir == null) {
            return false;
        } else {
            tryMove(bestdir);
        }
        return true;
    }
}
