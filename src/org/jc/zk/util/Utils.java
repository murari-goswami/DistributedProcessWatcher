/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jc.zk.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

/**
 *
 * @author cespedjo
 */
public class Utils {
    
    //Describes a REQUEST
    public static final String AMW_PAYLOAD_TYPE_REQUEST = "pisrq";
    
    public static final String AMW_PAYLOAD_TYPE_RESTORE = "pisrt";
    
    public static final String AMW_PAYLOAD_TYPE_INIT = "pisin";
    
    public static final String AMW_PAYLOAD_TYPE_RESPONSE = "pisre";
    
    /**
     * Method in charge of retrieving time from NTP servers.
     * @param servers String array containing ip addresses of NTP servers.
     * @return a long representing current time in milliseconds.
     * @throws Exception If anything goes wrong, an exception is thrown.
     */
    public static long getNetworkTime(String[] servers) throws Exception {
        for (int i = 0; i < servers.length; ++i) {
            NTPUDPClient timeClient = new NTPUDPClient();
            try {
                InetAddress inetAddress = InetAddress.getByName(servers[i]);
                TimeInfo timeInfo = timeClient.getTime(inetAddress);
                timeInfo.computeDetails();
                long retVal = timeInfo.getReturnTime() + timeInfo.getOffset();
                
                return retVal;
            } catch (UnknownHostException ex) {
                Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        throw new Exception("Impossible to retrieve network time tried these servers: " + Arrays.toString(servers));
    }
    
    /**
     * Method in charge of appending payload to a queue. Queue is implemented as
     * a StringBuilder and payload is encoded as string. Delimiter of queue is 
     * \002.
     * 
     * @param sb StringBuilder payload will be appended to.
     * @param cmwUpdate byte array representing payload.
     */
    public static void addUpdateToCMWUpdatesQueue(StringBuilder sb, byte[] cmwUpdate) {
        if (sb.length() > 0) {
            sb.append("\002");
        }
        sb.append(Utils.childMasterWatcherDataToString(cmwUpdate));
    }
    
    /**
     * This method is used by CMWs to generate the information that will be placed
     * under the updates znodes they're assigned. When MWs request a health status
     * report, they expect CMWs to modify their own assigned updates znodes with
     * the data generated by this method.
     * 
     * @param currentTime long representing time in milliseconds.
     * @param programToRun String representing path to the executable the CMW is supposed to deploy.
     * @param args String array containing the arguments for the executable to be run.
     * @param ntpServers String array containing the ip addresses of NTP servers.
     * @return byte array representing the data to be placed under update znode.
     * @throws Exception Throws an exception if anything goes wrong.
     */
    public static byte[] generateDataForChildMasterWatcher(
            long currentTime,
            String programToRun,
            String[] args,
            String[] ntpServers) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(programToRun);
        
        for (String arg : args) {
            sb.append("\001").append(arg);
        }
        sb.append("\001").append(
                String.valueOf(currentTime == 0L ? 
                        getNetworkTime(ntpServers) : 
                        currentTime));
        
        return sb.toString().getBytes(Charset.forName("UTF-8"));
    }
    
    /**
     * This method is used by the Active MW to generate the data that it is supposed
     * to place under the masters' keep alive znode, that is, the znode where health
     * status reports are placed. 
     * 
     * @param currentTime long representing time in milliseconds.
     * @param cmwQueue String encoding of the queue containing updates from CMWs.
     * @param masterIdentifier String containing the identifier of active master.
     * @param ntpServers String array containing ip addresses from NTP servers.
     * @return byte array with the data to be placed under znode.
     * @throws Exception throws an exception if anything goes wrong.
     */
    public static byte[] generateDataForZNode (
            long currentTime,
            String cmwQueue,
            String masterIdentifier, String[] ntpServers) throws Exception{
        StringBuilder sb = new StringBuilder();
        sb.append(masterIdentifier)
                .append("\000")
                .append(cmwQueue);
        
        sb.append("\000").append(
                String.valueOf(currentTime == 0L ? 
                        getNetworkTime(ntpServers) : 
                        currentTime));
        
        return sb.toString().getBytes(Charset.forName("UTF-8"));
    }
    
    /**
     * Method to query masters' keep alive znode's data and retrieve time from it.
     * @param data byte array from which time will be extracted.
     * @return long representing time in milliseconds.
     */
    public static long getTimeFromZnode(byte[] data) {
        String s = new String(data);
        String[] parts = s.split("\000");
        return Long.parseLong(parts[parts.length - 1]);
    }
    
    /**
     * Method to query time listeners' znode and retrieve time from it.
     * @param data byte array from which time will be extracted.
     * @return long representing time in milliseconds.
     */
    public static long getTimeFromTimeZnode(byte[] data) {
        String s = new String(data, Charset.forName("UTF-8"));
        return Long.parseLong(s.split(";")[1]);
    }
    
    /**
     * Method used by Active TM to generate data to be placed under time masters'
     * keep alive znode. This method does not throw an exception if time could not
     * be retrieved from NTP servers, and it just relies on host's internal clock
     * to provide time for the rest of the components.
     * 
     * @param masterId String representing the identifier of current active TM.
     * @param time long representing time in milliseconds.
     * @param ntpServers String array containing the ip addresses of NTP servers.
     * @return byte array containing the data to be placed under time masters' keep
     * alive znode.
     */
    public static byte[] generateDataForTimeZnode(String masterId, long time, String[] ntpServers) {
        if (time < 0) {
            try {
                time = Utils.getNetworkTime(ntpServers);
            } catch (Exception ex) {
                Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
                time = System.currentTimeMillis();
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(masterId).append(";").append(String.valueOf(time));
        
        return sb.toString().getBytes(Charset.forName("UTF-8"));
    }
    
    /**
     * Method invoked by Active MW to generate data for znode that signals CMWs
     * that it is time to deploy their processes.
     * @param masterId String representing the id of current active MW.
     * @param uniqueId String representing an unique id for the data to be placed
     * under the znode.
     * @param time long representing time in milliseconds.
     * @return byte array containing the data to be placed under the znode. 
     */
    public static byte[] generateDataForObservedZnode(String masterId, String uniqueId, long time) {
        StringBuilder sb = new StringBuilder();
        sb.append(masterId).append(";").append(uniqueId).append(String.valueOf(time));
        
        return sb.toString().getBytes(Charset.forName("UTF-8"));
    }
    
    /**
     * Method to query time listeners' znode and obtain master id from it.
     * @param data byte array containing the data to be queried.
     * @return String representing id of active TM.
     */
    public static String getIdOfMasterFromTimeZnode(byte[] data) {
        String d = new String(data, Charset.forName("UTF-8"));
        return d.split(";")[0];
    }
    
    /**
     * Method to query masters' keep alive znode and retrieve id of current active
     * MW.
     * @param data byte array containing the data to be queried.
     * @return String representing the id of current active MW.
     */
    public static String getIdOfMasterWatcherFromZnode(byte[] data) {
        String d = new String(data, Charset.forName("UTF-8"));
        return d.split("\000")[0];
    }
    
    @Deprecated
    public static String getIdOfProcessWatcherFromZnode(byte[] data) {
        String d = new String(data, Charset.forName("UTF-8"));
        return d.split(";")[0];
    }
    
    /**
     * Method used to query processes' keep alive znode to retrieve time from data
     * placed under znode.
     * @param data byte array containing the data from which time will be extracted.
     * @return long representing time in milliseconds.
     */
    public static long getTimeFromProcessWatcherZnode(byte[] data) {
        String d = new String(data, Charset.forName("UTF-8"));
        return Long.valueOf(d.split(";")[2]);
    }
    
    /**
     * Method to encode data created by CMW as String
     * @param data byte array containing the data to be encoded as String.
     * @return String representation of data.
     */
    public static String childMasterWatcherDataToString(byte[] data) {
        return new String(data, Charset.forName("UTF-8"));
    }
    
    /**
     * Method to encode data for time listeners as String
     * @param data byte array containing data to be encoded as String.
     * @return String representation of data.
     */
    public static String timeMasterDataForTimeListenersToString(byte[] data) {
        return new String(data, Charset.forName("UTF-8"));
    }
    
    /**
     * Method to encode data for TM's keep alive znode as String.
     * @param data byte array containing data to be encoded as String.
     * @return String representing data.
     */
    public static String timeMasterDataForTimeZnodeToString(byte[] data) {
        return new String(data, Charset.forName("UTF-8"));
    }
    
    /**
     * Method to encode data for TM's keep alive znode as byte array.
     * @param data String data to be encoded as byte array.
     * @return byte array representing stringified data.
     */
    public static byte[] timeMasterDataForTimeZnodeToBytes(String data) {
        return data.getBytes(Charset.forName("UTF-8"));
    }
    
    /**
     * Method to encode CMW's data as byte array.
     * @param data String data to be encoded as byte array.
     * @return byte array representing stringified data.
     */
    public static byte[] childMasterWatcherDataToBytes(String data) {
        return data.getBytes(Charset.forName("UTF-8"));
    }
    
    /**
     * Method to encode MW's keep alive znode as String.
     * @param data byte array to be encoded as String
     * @return String representation of data.
     */
    public static String masterWatcherZnodeDataToString(byte[] data) {
        return new String(data, Charset.forName("UTF-8"));
    }
    
    /**
     * Method to encode MW's keep alive as byte array.
     * @param data Stringified data to be encoded as byte array.
     * @return byte array representing stringified data.
     */
    public static byte[] masterWatcherZnodeDataToBytes(String data) {
        return data.getBytes(Charset.forName("UTF-8"));
    }
    
    /**
     * Method to encode data from znode to signal CMWs to deploy processes, as 
     * String.
     * @param data byte array containing data to be encoded as String.
     * @return String representation of data.
     */
    public static String processObservedZnodeDataToString(byte[] data) {
        return new String(data, Charset.forName("UTF-8"));
    }
    
    /**
     * Method to encode data from znode to signal CMWs to deploy processes, as 
     * byte array.
     * @param data String representing data to be encoded as byte array.
     * @return byte array representing stringified data.
     */
    public static byte[] processObservedZnodeDataToBytes(String data) {
        return data.getBytes(Charset.forName("UTF-8"));
    }
    
    /**
     * Method to encode Process Heartbeat znode's data as String.
     * @param data byte array containing the data to be stringified.
     * @return String representation of data.
     */
    public static String processHeartBeatDataToString(byte[] data) {
        return new String(data, Charset.forName("UTF-8"));
    }
    
    /**
     * Method to encode Process Heartbeat znode's data as byte array.
     * @param data Stringified data to encode as byte array.
     * @return byte array representing stringified data.
     */
    public static byte[] processHeartBeatDataToBytes(String data) {
        return data.getBytes(Charset.forName("UTF-8"));
    }
    
    /**
     * Method to encode data generated by Active MW when creating update znode,
     * as byte array.
     * @param time long representing time in milliseconds.
     * @return byte array containing data for update znode.
     */
    public static byte[] updateZnodeCreatedByMastersDataToBytes(long time) {
        return String.valueOf(time).getBytes(Charset.forName("UTF-8"));
    }
    
    /**
     * Method to encode data generated by Active MW when creating update znode,
     * as String.
     * @param data byte array containing data to be stringified.
     * @return String representing data encoded.
     */
    public static String updateZnodeCreatedByMastersDataToString(byte[] data) {
        return new String(data, Charset.forName("UTF-8"));
    }
    
    /**
     * Method to encode AMW Kill Request znode's data as String.
     * @param data byte array representing the data to be encoded as String.
     * @return the UTF-8 encoded String.
     */
    public static String requestAMWKillZnodeDataToString(byte[] data) {
        return new String(data, Charset.forName("UTF-8"));
    }
    
    /**
     * Method to create a ZooKeeper compliant data.
     * @param data this is a String representing the possible states of AMW Kill 
     * Request znode: {@link org.jc.zk.dpw.TimeMaster#AMW_REQUEST_KILL_BUSY},
     * {@link org.jc.zk.dpw.TimeMaster#AMW_REQUEST_KILL_CODE_ALLOW},
     * {@link org.jc.zk.dpw.TimeMaster#AMW_REQUEST_KILL_CODE_DENIED},
     * {@link org.jc.zk.dpw.TimeMaster#AMW_REQUEST_KILL_CODE_KILL},
     * {@link org.jc.zk.dpw.TimeMaster#AMW_REQUEST_KILL_FREE}
     * 
     * @param type String representing what type of update will be pushed. See
     * {@link org.jc.zk.util.Utils#AMW_PAYLOAD_TYPE_INIT},
     * {@link org.jc.zk.util.Utils#AMW_PAYLOAD_TYPE_REQUEST},
     * {@link org.jc.zk.util.Utils#AMW_PAYLOAD_TYPE_RESPONSE},
     * {@link org.jc.zk.util.Utils#AMW_PAYLOAD_TYPE_RESTORE}
     * 
     * @param requester String representing the identifier of the Master
     * requesting permission to kill AMW or the Master authorizing/denying/initializing
     * AMW Kill Request znode.
     * 
     * @return byte array representing the data to be set under AMW Kill Request
     * znode.
     */
    public static byte[] requestAMWKillZnodeDataToBytes(String data, String type, String requester) {
        String payload = type + ";" + data + ";" + requester;
        return payload.getBytes(Charset.forName("UTF-8"));
    }
    
    /**
     * Method to get Data part from data extracted or to be written to AMW Kill
     * request znode. Data part as in {@link org.jc.zk.dpw.TimeMaster#AMW_REQUEST_KILL_BUSY},
     * {@link org.jc.zk.dpw.TimeMaster#AMW_REQUEST_KILL_CODE_ALLOW},
     * {@link org.jc.zk.dpw.TimeMaster#AMW_REQUEST_KILL_CODE_DENIED},
     * {@link org.jc.zk.dpw.TimeMaster#AMW_REQUEST_KILL_CODE_KILL},
     * {@link org.jc.zk.dpw.TimeMaster#AMW_REQUEST_KILL_FREE}
     * 
     * @param data byte array representing znode's data.
     * 
     * @return String representing Data part.
     */
    public static String getDataFromRequestAmwKillZnodeData(byte[] data) {
        return Utils.getDataFromRequestAmwKillZnodeData(Utils.requestAMWKillZnodeDataToString(data));
    }
    
    /**
     * Method to retrieve the Type part from data extracted or to be written to AMW Kill
     * request znode. Type part as in {@link org.jc.zk.util.Utils#AMW_PAYLOAD_TYPE_INIT},
     * {@link org.jc.zk.util.Utils#AMW_PAYLOAD_TYPE_REQUEST},
     * {@link org.jc.zk.util.Utils#AMW_PAYLOAD_TYPE_RESPONSE},
     * {@link org.jc.zk.util.Utils#AMW_PAYLOAD_TYPE_RESTORE}
     * 
     * @param data byte array representing znode's data.
     * @return String representing Type part.
     */
    public static String getTypeFromRequestAmwKillZnodeData(byte[] data) {
        return Utils.getTypeFromRequestAmwKillZnodeData(Utils.requestAMWKillZnodeDataToString(data));
    }
    
    /**
     * Method to retrieve Requester part from data extracted or to be written to AMW Kill
     * request znode.
     * 
     * @param data byte array representing znode's data.
     * @return String representing the identifier of the Master
     * requesting permission to kill AMW or the Master authorizing/denying/initializing
     * AMW Kill Request znode.
     */
    public static String getRequesterFromRequestAmwKillZnodeData(byte[] data) {
        return Utils.getRequesterFromRequestAmwKillZnodeData(Utils.requestAMWKillZnodeDataToString(data));
    }
    
    /**
     * Method to get Data part from data extracted or to be written to AMW Kill
     * request znode. Data part as in {@link org.jc.zk.dpw.TimeMaster#AMW_REQUEST_KILL_BUSY},
     * {@link org.jc.zk.dpw.TimeMaster#AMW_REQUEST_KILL_CODE_ALLOW},
     * {@link org.jc.zk.dpw.TimeMaster#AMW_REQUEST_KILL_CODE_DENIED},
     * {@link org.jc.zk.dpw.TimeMaster#AMW_REQUEST_KILL_CODE_KILL},
     * {@link org.jc.zk.dpw.TimeMaster#AMW_REQUEST_KILL_FREE}
     * 
     * @param data String-encoded znode's data.
     * 
     * @return String representing Data part.
     */
    public static String getDataFromRequestAmwKillZnodeData(String data) {
        return data.split(";")[1];
    }
    
    /**
     * Method to retrieve the Type part from data extracted or to be written to AMW Kill
     * request znode. Type part as in {@link org.jc.zk.util.Utils#AMW_PAYLOAD_TYPE_INIT},
     * {@link org.jc.zk.util.Utils#AMW_PAYLOAD_TYPE_REQUEST},
     * {@link org.jc.zk.util.Utils#AMW_PAYLOAD_TYPE_RESPONSE},
     * {@link org.jc.zk.util.Utils#AMW_PAYLOAD_TYPE_RESTORE}
     * 
     * @param data byte array representing znode's data.
     * 
     * @return String representing Type part.
     */
    public static String getTypeFromRequestAmwKillZnodeData(String data) {
        return data.split(";")[0];
    }
    
    /**
     * Method to retrieve Requester part from data extracted or to be written to AMW Kill
     * request znode.
     * 
     * @param data byte array representing znode's data.
     * @return String representing the identifier of the Master
     * requesting permission to kill AMW or the Master authorizing/denying/initializing
     * AMW Kill Request znode.
     */
    public static String getRequesterFromRequestAmwKillZnodeData(String data) {
        return data.split(";")[2];
    }
}
