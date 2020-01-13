package smite;

import battlecode.common.*;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

public class HQ extends Building {

    private NetGun netgun;
    private Refinery refinery;
    int minerCount;
    //each elements is an [tilenum, soupHere]
    List<int[]> soupsPerTile = new ArrayList<int[]>();

    int[][] visible = {{0,0}, {-1,0}, {0,-1}, {0,1}, {1,0}, {-1,-1}, {-1,1}, {1,-1}, {1,1}, {-2,0}, {0,-2}, {0,2}, {2,0}, {-2,-1}, {-2,1}, {-1,-2}, {-1,2}, {1,-2}, {1,2}, {2,-1}, {2,1}, {-2,-2}, {-2,2}, {2,-2}, {2,2}, {-3,0}, {0,-3}, {0,3}, {3,0}, {-3,-1}, {-3,1}, {-1,-3}, {-1,3}, {1,-3}, {1,3}, {3,-1}, {3,1}, {-3,-2}, {-3,2}, {-2,-3}, {-2,3}, {2,-3}, {2,3}, {3,-2}, {3,2}, {-4,0}, {0,-4}, {0,4}, {4,0}, {-4,-1}, {-4,1}, {-1,-4}, {-1,4}, {1,-4}, {1,4}, {4,-1}, {4,1}, {-3,-3}, {-3,3}, {3,-3}, {3,3}, {-4,-2}, {-4,2}, {-2,-4}, {-2,4}, {2,-4}, {2,4}, {4,-2}, {4,2}, {-5,0}, {-4,-3}, {-4,3}, {-3,-4}, {-3,4}, {0,-5}, {0,5}, {3,-4}, {3,4}, {4,-3}, {4,3}, {5,0}, {-5,-1}, {-5,1}, {-1,-5}, {-1,5}, {1,-5}, {1,5}, {5,-1}, {5,1}, {-5,-2}, {-5,2}, {-2,-5}, {-2,5}, {2,-5}, {2,5}, {5,-2}, {5,2}, {-4,-4}, {-4,4}, {4,-4}, {4,4}, {-5,-3}, {-5,3}, {-3,-5}, {-3,5}, {3,-5}, {3,5}, {5,-3}, {5,3}, {-6,0}, {0,-6}, {0,6}, {6,0}, {-6,-1}, {-6,1}, {-1,-6}, {-1,6}, {1,-6}, {1,6}, {6,-1}, {6,1}, {-6,-2}, {-6,2}, {-2,-6}, {-2,6}, {2,-6}, {2,6}, {6,-2}, {6,2}, {-5,-4}, {-5,4}, {-4,-5}, {-4,5}, {4,-5}, {4,5}, {5,-4}, {5,4}, {-6,-3}, {-6,3}, {-3,-6}, {-3,6}, {3,-6}, {3,6}, {6,-3}, {6,3}};

    public HQ(RobotController rc) throws GameActionException {
        super(rc);
        netgun = new NetGun(rc);
        refinery = new Refinery(rc);
        minerCount = 0;
        initialScan();
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
        super.run();
        netgun.shoot();
        for (Direction dir : directions) {
            if (minerCount < 10 && tryBuild(RobotType.MINER, dir))
                minerCount++;
        }
        if(rc.getRoundNum()!=1) {
            readMessages();
        }
        generateMessage();
    }

    // Get soups around HQ initially, put in soupsPerTile arraylist for
    // future sending to miners.
    void initialScan() throws GameActionException {
        HashMap<Integer, Integer> tileToCount = new HashMap<Integer, Integer>();
        for (int[] d : visible) {
            MapLocation m = myLocation.translate(d[0], d[1]);
            if(rc.canSenseLocation(m)) {
                int sloc = rc.senseSoup(m);
                int tnum = getTileNumber(m);
                tileToCount.put(tnum, sloc+tileToCount.getOrDefault(tnum, 0));
            }
        }
        for(int key: tileToCount.keySet()) {
            int soupval = tileToCount.get(key);
            if(soupval>0) {
                addToSoupList(key, soupval/50);
            }
        }
    }

    void generateMessage() throws GameActionException {
        if(rc.getRoundNum()%messageFrequency==messageModulus) {
            MinePatchMessage m = new MinePatchMessage(MAP_HEIGHT, MAP_WIDTH, teamNum);
            int numToSend = Math.min(m.MAX_PATCHES, soupsPerTile.size());
            int lastPatchNum = 0;
            int lastWeight = 0;
            int i=0;
            for(int[] x : soupsPerTile) {
                if(i>=numToSend) {
                    break;
                }
                MapLocation cen = getCenterFromTileNumber(x[0]);
                rc.setIndicatorDot(cen, 255, 0, 255);
                m.writePatch(x[0], 1); //TODO: use x[1] in the future

                if(i==soupsPerTile.size()-1) {
                    lastPatchNum = x[0];
                    lastWeight = 1; // set to final weight
                }

                i++;
            }
            for(int j=soupsPerTile.size(); j<m.MAX_PATCHES; j++) {
                m.writePatch(lastPatchNum, lastWeight);
            }
            //TODO figure out better bidding scheme
            sendMessage(m.getMessage(), 1);
        }
    }

    void addToSoupList(int tileNum, int soupThere) throws GameActionException {
        boolean added = false;
        for(int j=0; j<soupsPerTile.size(); j++) {
            ///////////////////////////////
            //TODO: instead of inserting s.soupThere
            //do the weighting calculation here and compare
            //based on weighting.
            if(tileNum==soupsPerTile.get(j)[0]) {
                added = true;
                break;
            } else if (soupsPerTile.get(j)[1] < soupThere) {
                soupsPerTile.add(j, new int[]{tileNum, soupThere});
                added = true;
                break;
            }
        }
        if (!added) {
            soupsPerTile.add(new int[]{tileNum, soupThere});
        }
    }

    void readMessages() throws GameActionException {
        Transaction[] msgs = rc.getBlock(rc.getRoundNum()-1);
        for (int i=0; i<msgs.length; i++) {
            int[] msg = msgs[i].getMessage();
            Message m = new Message(msg, MAP_HEIGHT, MAP_WIDTH, teamNum);
            //sent from our team
            if(m.origin) {
                //soup message
                if(m.schema == 1) {
                    SoupMessage s = new SoupMessage(msg, MAP_HEIGHT, MAP_WIDTH, teamNum);
                    if (s.soupThere==0) {
                        //delete from arraylist of soups
                        for(int j=0; j<soupsPerTile.size(); j++) {
                            if(soupsPerTile.get(j)[0]==s.tile) {
                                soupsPerTile.remove(j);
                                break;
                            }
                        }
                    } else {
                        //miner telling me there is soup at tile
                        addToSoupList(s.tile, s.soupThere);
                    }
                }
            }
        }
    }
}
