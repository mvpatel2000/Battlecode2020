package qual;

import battlecode.common.*;

import java.util.*;

public abstract class Unit extends Robot {

    protected Deque<MapLocation> history;
    protected Map<MapLocation, Integer> historySet;
    protected boolean hasHistory;
    protected int stuck;
    protected PathState state;

    public static int SPECULATION = 2;
    public static int HISTORY_SIZE = 30;

    public Unit(RobotController rc) throws GameActionException {
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
        state = new PathState(myLocation, null, null, null, Integer.MAX_VALUE);
    }

    @Override
    public void run() throws GameActionException {
        myLocation = rc.getLocation();
        MapLocation me = myLocation;
        state.me = me;
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
        MapLocation me = myLocation.add(in);
        try {
            return rc.canSenseLocation(me) && rc.canMove(in) && !rc.senseFlooding(me);
        } catch (GameActionException e) {
            e.printStackTrace();
            return false;
        }
    }

    protected boolean canMove(MapLocation from, Direction in) {
        if (from.equals(myLocation)) {
            return canMove(in);
        }
        MapLocation to = from.add(in);
        try {
            return rc.canSenseLocation(to)
                    && rc.senseNearbyRobots(to, 0, null).length == 0
                    && Math.abs(rc.senseElevation(to) - rc.senseElevation(from)) < 4;
        } catch (GameActionException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void aggroPath(MapLocation target) throws GameActionException {
        path(target);
    }

    public void path(MapLocation target) {
        if (state.target == null || !state.target.equals(target))
            setDestination(target);
        navigate();
    }

    public void setDestination(MapLocation loc) {
        state = new PathState(myLocation, loc, null, null, Integer.MAX_VALUE);
    }

    public void navigate() {
        if (rc.getCooldownTurns() >= 1) {
            return;
        }
        System.out.println("Pathing to: " + state.target);
        try {
            PathState next = bugPath(state, null);
            //PathState tmp = new PathState(myLocation, next.me, state.follow, state.face, Integer.MAX_VALUE);
            //next = bugPath(tmp, null);
            //next.best = Math.min(next.me.distanceSquaredTo(state.target), state.best);
            //next.target = state.target;
            state = next;
            go(toward(myLocation, state.me));
        } catch (GameActionException e) {
            e.printStackTrace();
        }
    }

    private LinkedList<PathState> speculativePath(PathState state, int depth) throws GameActionException {
        LinkedList<LinkedList<PathState>> states = new LinkedList<>(
                Collections.singleton(new LinkedList<>(Collections.singletonList(state))));
        LinkedList<LinkedList<PathState>> buffer = new LinkedList<>();

        boolean[] branch = {false};
        for (int i = 0; i < depth; i++) {
            for (LinkedList<PathState> st : states) {
                PathState next = bugPath(st.getLast(), branch);
                if (branch[0]) {
                    PathState tmp = st.getLast().clone();
                    tmp.follow = next.follow == Hand.Left ? Hand.Right : Hand.Left;
                    PathState alternate = bugPath(tmp, null);
                    branch[0] = false;
                    LinkedList<PathState> alst = new LinkedList<>(st);
                    alst.addLast(alternate);
                    buffer.add(alst);
                }
                st.addLast(next);
            }
            states.addAll(buffer);
            buffer.clear();
        }
        return states.stream().min(Comparator.comparingInt(x -> x.getLast().best)).orElse(null);
    }

    private enum Hand implements Cloneable {
        Left, Right;

        public int dir() {
            if (this == Hand.Left) {
                return -1;
            } else if (this == Hand.Right) {
                return 1;
            }
            throw new RuntimeException();
        }
    }

    private static class PathState implements Cloneable {
        MapLocation me;
        MapLocation target;
        Hand follow;
        Direction face;
        Integer best;

        public PathState clone() {
            try {
                return (PathState) super.clone();
            } catch (CloneNotSupportedException e) {
                return null;
            }
        }

        public String toString() {
            return "PathState{" +
                    "me=" + me +
                    ", target=" + target +
                    ", follow=" + follow +
                    ", face=" + face +
                    ", best=" + best +
                    '}';
        }

        public PathState(MapLocation me, MapLocation target, Hand follow, Direction face, Integer best) {
            this.me = me;
            this.target = target;
            this.follow = follow;
            this.face = face;
            this.best = best;
        }
    }

    private PathState bugPath(PathState state, boolean[] needsBranch) throws GameActionException {
        Direction best = null;
        int bestDist = Integer.MAX_VALUE;
        Direction possible = null;
        int possibleDist = Integer.MAX_VALUE;
        for (Direction d : directions) {
            int dist = state.me.add(d).distanceSquaredTo(state.target);

            if (dist < possibleDist && canMove(state.me, d)) {
                possibleDist = dist;
                possible = d;
            }
            if (dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        if (possible == null) {
            return state;
        }
        if (possibleDist < state.best) {
            return new PathState(state.me.add(possible), state.target, null, null, possibleDist);
        }
        if (state.face != null && !canMove(state.me, state.face)) {
            return pathHelper(state);
        }

        if (canMove(state.me, best)) {
            PathState next = state.clone();
            next.me = next.me.add(best);
            return next;
        } else {
            if (needsBranch != null)
                needsBranch[0] = true;
            PathState next = state.clone();
            if (next.follow == null) {
                next.follow = state.me.add(best.rotateRight()).distanceSquaredTo(state.target) <
                        state.me.add(best.rotateLeft()).distanceSquaredTo(state.target) ?
                        Hand.Right : Hand.Left;
            }
            next.face = best;
            return pathHelper(next);
        }
    }

    public PathState pathHelper(PathState state) {
        Direction best = state.face;
        Direction prev = null;
        for (int i = 0; i < 8 && !canMove(state.me, best); i++) {
            prev = best;
            best = adj(best, 8 + state.follow.dir());
        }
        MapLocation next = state.me.add(best);
        Direction newFace = toward(next, state.me.add(prev));
        if (!canMove(state.me, best)) {
            return state;
        }
        return new PathState(next, state.target, state.follow, newFace,
                Math.min(state.best, next.distanceSquaredTo(state.target)));
    }

    protected void go(Direction d) throws GameActionException {
        tryMove(d);
        myLocation = rc.getLocation();
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
