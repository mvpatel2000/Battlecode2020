package smite;

import battlecode.common.*;

import java.util.*;

public abstract class Unit extends Robot {

    private enum Hand {LEFT, RIGHT}

    protected Deque<MapLocation> history;
    protected Map<MapLocation, Integer> historySet;
    protected boolean hasHistory;
    protected int stuck;
    protected Direction facing;
    protected Direction following;
    protected int rand;
    protected int time;

    public static int WALL_FOLLOW_LENGTH = 10 ;
    public static int HISTORY_SIZE = 10;

    public Unit(RobotController rc) throws GameActionException {
        super(rc);
        stuck = 0;
        clearHistory();
        facing = null;
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
        following = null;
        facing = null;
        time = 0;
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
        // //System.out.println("I am trying to move " + dir + "; " + rc.isReady() + " " + rc.getCooldownTurns() + " " + rc.canMove(dir));
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


    protected Direction right(Direction in) {
        return intToDirection((directionToInt(in) + 2) % 8);
    }

    protected Direction left(Direction in) {
        return intToDirection((directionToInt(in) + 6) % 8);
    }

    protected Direction adj(Direction in, int k) {
        return intToDirection((directionToInt(in) + k) % 8);
    }

    protected boolean canMove(Direction in) {
        MapLocation me = history.peekFirst().add(in);
        try {
            return rc.canSenseLocation(me) && rc.canMove(in) && !rc.senseFlooding(me);
        } catch (GameActionException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean aggroPath(MapLocation target) throws GameActionException {
        //System.out.println("Pathing to: " + target);
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
        if (following != null && !canMove(facing) && canMove(following) && time < 20) {
            time++;
            go(following);
            return false;
        }
        if (following != null && !canMove(facing) && (!canMove(following) || time >= 20)) {
            time = 0;
            following = null;
            facing = null;
            best = following;
        }
        if (following == null && !canMove(best)) {
            if (Math.random() < 0.5) {
                for (int i = 0; i < 8; i++) {
                    Direction d = adj(best, i);
                    if (canMove(d)) {
                        facing = best;
                        following = d;
                        tryMove(d);
                        return true;
                    }
                }
                for (int i = 0; i < 8; i++) {
                    Direction d = adj(best, 8 - i);
                    if (canMove(d)) {
                        facing = best;
                        following = d;
                        tryMove(d);
                        return true;
                    }
                }
            }
        }
        time = 0;
        if (facing != null && canMove(facing)) {
            go(facing);
            following = null;
            facing = null;
            return true;
        }
        facing = null;
        following = null;
        return pathHelper(target, best);
    }

    public boolean path(MapLocation target) throws GameActionException {
        //System.out.println("Pathing to: " + target);
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
        if (Math.random() < 0.2 && following == null) {
            return pathHelper(target, pbest);
        }
        if (following != null && !canMove(facing) && canMove(following) && time < 20) {
            time++;
            go(following);
            return false;
        }
        if (following != null && !canMove(facing) && (!canMove(following) || time >= 20)) {
            time = 0;
            following = null;
            facing = null;
            best = following;
        }
        if (following == null && !canMove(best)) {
            if (Math.random() < 0.5) {
                for (int i = 0; i < 16; i++) {
                    Direction d = adj(best, i % 2 == 0 ? i / 2 : 8 - i / 2);
                    if (canMove(d)) {
                        facing = best;
                        following = d;
                        tryMove(d);
                        return true;
                    }
                }
            } else {
                for (int i = 0; i < 16; i++) {
                    Direction d = adj(best, i % 2 == 0 ? 8 - i / 2 : i / 2);
                    if (canMove(d)) {
                        facing = best;
                        following = d;
                        tryMove(d);
                        return true;
                    }
                }
            }
        }
        time = 0;
        if (facing != null && canMove(facing)) {
            go(facing);
            following = null;
            facing = null;
            return true;
        }
        facing = null;
        following = null;
        return pathHelper(target, best);
    }

    protected void go(Direction d) throws GameActionException {
        tryMove(d);
        myLocation = rc.getLocation();
    }

    protected boolean pathHelper(MapLocation target, Direction best) throws GameActionException {
        if (best != null) {
            stuck = 0;
            go(best);
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
