package sprint;

import battlecode.common.*;

import java.util.*;

public abstract class Unit extends Robot {
    private enum Hand {LEFT, RIGHT}

    private Deque<MapLocation> history;
    private Map<MapLocation, Integer> historySet;
    private boolean hasHistory;
    private int stuck;

    public static int HISTORY_SIZE = 30;

    public Unit(RobotController rc) {
        super(rc);
        stuck = 0;
        clearHistory();
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

    boolean path(MapLocation target) throws GameActionException {
        MapLocation me = history.peekFirst();
        if (me.equals(target)) {
            return false;
        }
        Direction best = Arrays.stream(directions).filter(x -> {
            try {
                MapLocation next = me.add(x);
                return rc.canMove(x) && !(rc.canSenseLocation(next) && rc.senseFlooding(next))
                        && historySet.getOrDefault(next, 0) == 0
                        && !(me.add(toward(me, history.peekLast())).equals(next));
            } catch (GameActionException e) {
                return false;
            }
        }).min(Comparator.comparing(x ->
                me.add(x).distanceSquaredTo(target))).orElse(null);
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

    boolean pathWithWater(MapLocation target) throws GameActionException {
        MapLocation me = history.peekFirst();
        if (me.equals(target)) {
            return false;
        }
        Direction best = Arrays.stream(directions).filter(x -> {
                MapLocation next = me.add(x);
                return rc.canMove(x) && historySet.getOrDefault(next, 0) == 0
                        && !(me.add(toward(me, history.peekLast())).equals(next));
        }).min(Comparator.comparing(x ->
                me.add(x).distanceSquaredTo(target))).orElse(null);
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
            if(rc.canMove(dir)) {
                MapLocation newLoc = myLocation.add(dir);
                int thisdist = newLoc.distanceSquaredTo(target);
                if(thisdist < mindist) {
                    mindist = thisdist;
                    bestdir = dir;
                }
            }
        }

        if(bestdir == null) {
            return false;
        } else {
            tryMove(bestdir);
        }
        return true;
    }
}
