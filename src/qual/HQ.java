package qual;

import battlecode.common.*;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java. util.Iterator;

public class HQ extends Building {

    private NetGun netgun;
    private Refinery refinery;
    int minerCount;
    int minerCooldown = 0;
    //each elements is an [tilenum, soupHere]
    List<int[]> accessibleSoupsPerTile = new ArrayList<int[]>();
    List<int[]> inaccessibleSoupsPerTile = new ArrayList<int[]>();

    int[][] visible = {{0,0}, {-1,0}, {0,-1}, {0,1}, {1,0}, {-1,-1}, {-1,1}, {1,-1}, {1,1}, {-2,0}, {0,-2}, {0,2}, {2,0}, {-2,-1}, {-2,1}, {-1,-2}, {-1,2}, {1,-2}, {1,2}, {2,-1}, {2,1}, {-2,-2}, {-2,2}, {2,-2}, {2,2}, {-3,0}, {0,-3}, {0,3}, {3,0}, {-3,-1}, {-3,1}, {-1,-3}, {-1,3}, {1,-3}, {1,3}, {3,-1}, {3,1}, {-3,-2}, {-3,2}, {-2,-3}, {-2,3}, {2,-3}, {2,3}, {3,-2}, {3,2}, {-4,0}, {0,-4}, {0,4}, {4,0}, {-4,-1}, {-4,1}, {-1,-4}, {-1,4}, {1,-4}, {1,4}, {4,-1}, {4,1}, {-3,-3}, {-3,3}, {3,-3}, {3,3}, {-4,-2}, {-4,2}, {-2,-4}, {-2,4}, {2,-4}, {2,4}, {4,-2}, {4,2}, {-5,0}, {-4,-3}, {-4,3}, {-3,-4}, {-3,4}, {0,-5}, {0,5}, {3,-4}, {3,4}, {4,-3}, {4,3}, {5,0}, {-5,-1}, {-5,1}, {-1,-5}, {-1,5}, {1,-5}, {1,5}, {5,-1}, {5,1}, {-5,-2}, {-5,2}, {-2,-5}, {-2,5}, {2,-5}, {2,5}, {5,-2}, {5,2}, {-4,-4}, {-4,4}, {4,-4}, {4,4}, {-5,-3}, {-5,3}, {-3,-5}, {-3,5}, {3,-5}, {3,5}, {5,-3}, {5,3}, {-6,0}, {0,-6}, {0,6}, {6,0}, {-6,-1}, {-6,1}, {-1,-6}, {-1,6}, {1,-6}, {1,6}, {6,-1}, {6,1}, {-6,-2}, {-6,2}, {-2,-6}, {-2,6}, {2,-6}, {2,6}, {6,-2}, {6,2}, {-5,-4}, {-5,4}, {-4,-5}, {-4,5}, {4,-5}, {4,5}, {5,-4}, {5,4}, {-6,-3}, {-6,3}, {-3,-6}, {-3,6}, {3,-6}, {3,6}, {6,-3}, {6,3}};

    //For halting production and resuming it.
    boolean holdProduction = false;
    int turnAtProductionHalt = -1;
    int previousSoup = 200;
    MapLocation enemyHQLocation = null;

    public HQ(RobotController rc) throws GameActionException {
        super(rc);
        HEADQUARTERS_LOCATION = myLocation;
        writeLocationMessage();
        netgun = new NetGun(rc);
        refinery = new Refinery(rc);
        minerCount = 0;

        for (Direction dir : directions) {
            if (minerCount < 4 && tryBuild(RobotType.MINER, dir)) {
                minerCount++;
            }
        }

        initialScan();
        accessibleSoupsPerTile.add(new int[]{getTileNumber(new MapLocation(MAP_WIDTH / 2, MAP_HEIGHT / 2)), -1});
        accessibleSoupsPerTile.add(new int[]{getTileNumber(new MapLocation(MAP_WIDTH - myLocation.x - 1, MAP_HEIGHT - myLocation.y - 1)), -1});
        accessibleSoupsPerTile.add(new int[]{getTileNumber(new MapLocation(MAP_WIDTH - myLocation.x - 1, myLocation.y)), -1});
        accessibleSoupsPerTile.add(new int[]{getTileNumber(new MapLocation(myLocation.x, MAP_HEIGHT - myLocation.y - 1)), -1});

        /*
        for(int i=0; i<numRows*numCols; i++) {
            MapLocation cen = getCenterFromTileNumber(i);
            rc.setIndicatorDot(cen, 255, i*3, i*3);
        }*/

    }

    /*
     * 1. Always shoot enemy drones down if possible
     */
    @Override
    public void run() throws GameActionException {
        if(holdProduction) {
            checkIfContinueHold();
        }
        super.run();
        netgun.shoot();
        minerCooldown--;
        if(!holdProduction) {
            int soupSum = 0;
            for (int[] soupPerTile : accessibleSoupsPerTile) {
                if (soupPerTile[1] > 0) {
                    System.out.println("[i] " + Integer.toString(soupPerTile[1]) + " soup at tile " + Integer.toString(soupPerTile[0]));
                    rc.setIndicatorDot(getCenterFromTileNumber(soupPerTile[0]), 224, 124, 42);
                    soupSum += soupPerTile[1];
                }
            }
            for (Direction dir : directions) {
                /*
                if ((minerCount < 4 || (soupSum > 0 && rc.getRoundNum() >= 200 && minerCount < 10 && minerCooldown < 0)) && tryBuild(RobotType.MINER, dir)) {
                    minerCount++;
                    minerCooldown = 5;
                }*/
                if(minerCount < 4 && tryBuild(RobotType.MINER, dir)) {
                    minerCount++;
                    minerCooldown = 5;
                } else if ((soupSum/(300*minerCount)>Math.cbrt(rc.getRoundNum()+1000)/5 && rc.getRoundNum() < INNER_WALL_FORCE_TAKEOFF_DEFAULT) && tryBuild(RobotType.MINER, dir)) {
                    //System.out.println("I producing miners");
                    //System.out.println("SoupSum/MinerCount " + Integer.toString(soupSum/minerCount));
                    //System.out.println("SQRT(roundNum/5) " + Integer.toString(rc.getRoundNum()/5));
                    System.out.println("[i] Producing extra miner");
                    minerCount++;
                } else {
                    System.out.println("[i] Heuristic says " + Double.toString((5*soupSum)/(Math.cbrt(rc.getRoundNum()+1000)*300)) + " miners optimal");
                    System.out.println("[i] I have produced " + Integer.toString(minerCount));
                    //System.out.println("I can't build miners");
                    //System.out.println("SoupSum " + Integer.toString(soupSum));
                    //System.out.println("MinerCount " + Integer.toString(minerCount));
                    //System.out.println("SQRT(roundNum)/5 " + Math.sqrt(rc.getRoundNum())/5);
                }
            }
        }

        if(rc.getRoundNum()!=1) {
            readMessages();
        }
        generateMessage();

        //should always be the last thing
        previousSoup = rc.getTeamSoup();
    }

    // Get soups around HQ initially, put in accessibleSoupsPerTile arraylist for
    // future sending to miners.
    void initialScan() throws GameActionException {
        HashMap<Integer, Integer> tileToCount = new HashMap<Integer, Integer>();
        MapLocation[] nearbySoups = rc.senseNearbySoup();
        for (MapLocation m: nearbySoups) {
            int sloc = rc.senseSoup(m);
            int tnum = getTileNumber(m);
            tileToCount.put(tnum, sloc+tileToCount.getOrDefault(tnum, 0));
        }
        for(int key: tileToCount.keySet()) {
            int soupval = tileToCount.get(key);
            if(soupval>0) {
                accessibleAddToSoupList(key, soupval);
            }
        }
    }

    // inserts into soup list in a sorted order, checks if tile is accessible
    void accessibleAddToSoupList(int tileNum, int soupThere) throws GameActionException {
        boolean added = false;
        if (isAccessible(getCenterFromTileNumber(tileNum))) {
            for (int j = 0; j < accessibleSoupsPerTile.size(); j++) {
                //TODO: instead of inserting s.soupThere do the weighting calculation here and compare based on weighting?
                if (tileNum == accessibleSoupsPerTile.get(j)[0]) {
                    added = true;
                    break;
                } else if (accessibleSoupsPerTile.get(j)[1] < soupThere) {
                    accessibleSoupsPerTile.add(j, new int[]{tileNum, soupThere});
                    added = true;
                    break;
                }
            }
            if (!added) {
                accessibleSoupsPerTile.add(new int[]{tileNum, soupThere});
            }
        }
        else {
            for (int j = 0; j < inaccessibleSoupsPerTile.size(); j++) {
                //TODO: instead of inserting s.soupThere do the weighting calculation here and compare based on weighting?
                if (tileNum == inaccessibleSoupsPerTile.get(j)[0]) {
                    added = true;
                    break;
                } else if (inaccessibleSoupsPerTile.get(j)[1] < soupThere) {
                    inaccessibleSoupsPerTile.add(j, new int[]{tileNum, soupThere});
                    added = true;
                    break;
                }
            }
            if (!added) {
                inaccessibleSoupsPerTile.add(new int[]{tileNum, soupThere});
            }
        }
    }


    // inserts into soup list in a sorted order, checks if tile is accessible
    void addToSoupList(int tileNum, int soupThere) throws GameActionException {
        boolean added = false;
        for (int j = 0; j < accessibleSoupsPerTile.size(); j++) {
            //TODO: instead of inserting s.soupThere do the weighting calculation here and compare based on weighting?
            if (tileNum == accessibleSoupsPerTile.get(j)[0]) {
                added = true;
                break;
            } else if (accessibleSoupsPerTile.get(j)[1] < soupThere) {
                accessibleSoupsPerTile.add(j, new int[]{tileNum, soupThere});
                added = true;
                break;
            }
        }
        if (!added) {
            accessibleSoupsPerTile.add(new int[]{tileNum, soupThere});
        }
    }

    void removeExtraneous(MapLocation ehql) throws GameActionException {
        for (Iterator<int[]> itr = accessibleSoupsPerTile.iterator(); itr.hasNext();) {
            int[] soupPerTile = itr.next();
            if(soupPerTile[1] == -1 && ehql != null) {
                if(soupPerTile[0] != getTileNumber(enemyHQLocation)) {
                    itr.remove();
                }
            }
        }
    }

    void writeLocationMessage() throws GameActionException {
        LocationMessage l = new LocationMessage(MAP_WIDTH, MAP_HEIGHT, teamNum);
        l.writeInformation(myLocation.x, myLocation.y, 0); // 0 indicates our HQ
        sendMessage(l.getMessage(), 1);
    }

    void generateMessage() throws GameActionException {
        if(rc.getRoundNum()%messageFrequency==messageModulus) {
            MinePatchMessage m = new MinePatchMessage(MAP_HEIGHT, MAP_WIDTH, teamNum);
            int numToSend = Math.min(m.MAX_PATCHES, accessibleSoupsPerTile.size());
            int lastPatchNum = 0;
            int lastWeight = 0;
            int i=0;
            for(int[] x : accessibleSoupsPerTile) {
                if(i>=numToSend) {
                    break;
                }
                MapLocation cen = getCenterFromTileNumber(x[0]);
                rc.setIndicatorDot(cen, 255, 0, 255);
                m.writePatch(x[0], soupToPower(x[1]));

                if(i==accessibleSoupsPerTile.size()-1) {
                    lastPatchNum = x[0];
                    lastWeight = soupToPower(x[1]); // set to final weight
                }

                i++;
            }
            for(int j=accessibleSoupsPerTile.size(); j<m.MAX_PATCHES; j++) {
                m.writePatch(lastPatchNum, lastWeight);
            }
            //TODO: figure out better bidding scheme
            sendMessage(m.getMessage(), 1);
        }

        if(rc.getRoundNum()%100==0 && enemyHQLocation!=null) {
            LocationMessage l = new LocationMessage(MAP_HEIGHT, MAP_WIDTH, teamNum);
            l.writeInformation(enemyHQLocation.x, enemyHQLocation.y, 1);
            if(sendMessage(l.getMessage(), 1)) {
                System.out.println("[i] SENDING ENEMY HQ LOCATION");
            }
        }
    }

    //Returns true if should continue halting production
    //Returns false if should not continue halting production
    private boolean checkIfContinueHold() throws GameActionException {
        //resume production after 10 turns, at most
        if(rc.getRoundNum()-turnAtProductionHalt>30) {
            System.out.println("[i] UNHOLDING PRODUCTION!");
            holdProduction = false;
            return false;
        }
        //-200 soup in one turn good approximation for building net gun
        //so we resume earlier than 10 turns if this happens
        if(previousSoup - rc.getTeamSoup() > 200) {
            System.out.println("[i] UNHOLDING PRODUCTION!");
            holdProduction = false;
            return false;
        }
        //if neither condition happens (10 turns or -200), continue holding production
        return true;
    }

    void readMessages() throws GameActionException {
        System.out.println("[i] reading messages...");
        Transaction[] msgs = rc.getBlock(rc.getRoundNum()-1);
        for (int i=0; i<msgs.length; i++) {
            int f = msgs[i].getMessage()[0];
            //sent from our team
            if(allyMessage(f)) {
                //soup message
                if(getSchema(f)==1) {
                    SoupMessage s = new SoupMessage(msgs[i].getMessage(), MAP_HEIGHT, MAP_WIDTH, teamNum);
                    if (s.soupThere==0) {
                        //delete from arraylist of soups
                        for(int j=0; j<accessibleSoupsPerTile.size(); j++) {
                            if(accessibleSoupsPerTile.get(j)[0]==s.tile) {
                                accessibleSoupsPerTile.remove(j);
                                break;
                            }
                        }
                    } else {
                        //miner telling me there is soup at tile
                        System.out.println("[i] MINER TELLING ME THERE IS " + Integer.toString(powerToSoup(s.soupThere)) + " POWERSOUP AT TILE " + Integer.toString(s.tile));
                        addToSoupList(s.tile, powerToSoup(s.soupThere));
                    }
                }
                if(getSchema(f)==3) {
                    HoldProductionMessage h = new HoldProductionMessage(msgs[i].getMessage(), MAP_HEIGHT, MAP_WIDTH, teamNum);
                    System.out.println("[i] HOLDING PRODUCTION!");
                    holdProduction = true;
                    turnAtProductionHalt = rc.getRoundNum();
                }
                if(getSchema(f)==4 && enemyHQLocation==null) {
                    checkForEnemyHQLocationMessageSubroutine(msgs[i].getMessage());
                    if(ENEMY_HQ_LOCATION != null) {
                        System.out.println("[i] I know ENEMY HQ");
                        enemyHQLocation = ENEMY_HQ_LOCATION;
                        removeExtraneous(enemyHQLocation);
                    }
                }
            }
        }
    }
}
