/**
*    Copyright 2014, Columbia University.
*    Homework 1, COMS E6998-10 Fall 2014
*    Software Defined Networking
*    Originally created by Shangjin Zhang, Columbia University
* 
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

/**
 * Floodlight
 * A BSD licensed, Java based OpenFlow controller
 *
 * Floodlight is a Java based OpenFlow controller originally written by David Erickson at Stanford
 * University. It is available under the BSD license.
 *
 * For documentation, forums, issue tracking and more visit:
 *
 * http://www.openflowhub.org/display/Floodlight/Floodlight+Home
 **/

package edu.columbia.cs6998.sdn.hw1;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;

import org.openflow.protocol.OFError;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.openflow.util.LRULinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Hw1Switch 
    implements IFloodlightModule, IOFMessageListener {
    protected static Logger log = LoggerFactory.getLogger(Hw1Switch.class);
    
    // Module dependencies
    protected IFloodlightProviderService floodlightProvider;
    
/* CS6998: data structures for the learning switch feature */
    // Stores the learned state for each switch
    protected Map<IOFSwitch, Map<Long, Short>> macToSwitchPortMap;

/* CS6998: data structures for the firewall feature */
    // Stores the MAC address of hosts to block: <Macaddr, blockedTime>
    protected Map<Long, Long> blacklist;
    protected Map<Long, Set<Long>> dstset;
    protected Map<IOFSwitch, Set<Long>> efset;

    // flow-mod - for use in the cookie
    public static final int HW1_SWITCH_APP_ID = 10;
    // LOOK! This should probably go in some class that encapsulates
    // the app cookie management
    public static final int APP_ID_BITS = 12;
    public static final int APP_ID_SHIFT = (64 - APP_ID_BITS);
    public static final long HW1_SWITCH_COOKIE = (long) (HW1_SWITCH_APP_ID & ((1 << APP_ID_BITS) - 1)) << APP_ID_SHIFT;
    
    // more flow-mod defaults 
    protected static final short IDLE_TIMEOUT_DEFAULT = 10;
    protected static final short HARD_TIMEOUT_DEFAULT = 9;
    protected static final short PRIORITY_DEFAULT = 100;
    protected static final short FIREWALL_BLOCK_TIMEOUT = 10;
    
    // for managing our map sizes
    protected static final int MAX_MACS_PER_SWITCH  = 1000;    

    // maxinum allowed elephant flow number for one switch
    protected static final int MAX_ELEPHANT_FLOW_NUMBER = 1;

    // maximum allowed destination number for one host
    protected static final int MAX_DESTINATION_NUMBER = 3;

    // maxinum allowed transmission rate
    protected static final int ELEPHANT_FLOW_BAND_WIDTH = 500;

    // time duration the firewall will block each node for
    protected static final int FIREWALL_BLOCK_TIME_DUR = (10 * 1000);
    /**
     * @param floodlightProvider the floodlightProvider to set
     */
    public void setFloodlightProvider(IFloodlightProviderService floodlightProvider) {
        this.floodlightProvider = floodlightProvider;
    }
    
    @Override
    public String getName() {
        return "hw1switch";
    }

    /**
     * Adds a host to the MAC->SwitchPort mapping
     * @param sw The switch to add the mapping to
     * @param mac The MAC address of the host to add
     * @param portVal The switchport that the host is on
     */
/* CS6998: fill out the following ????s */
    protected void addToPortMap(IOFSwitch sw, long mac, short portVal) {
        Map<Long, Short> swMap = macToSwitchPortMap.get(sw);
        
        if (swMap == null) {
            // May be accessed by REST API so we need to make it thread safe
            swMap = Collections.synchronizedMap(new LRULinkedHashMap<Long, Short>(MAX_MACS_PER_SWITCH));
            macToSwitchPortMap.put(sw, swMap);
        }
        swMap.put(mac, portVal);
    }
    
    /**
     * Removes a host from the MAC->SwitchPort mapping
     * @param sw The switch to remove the mapping from
     * @param mac The MAC address of the host to remove
     */
/* CS6998: fill out the following ????s */
    protected void removeFromPortMap(IOFSwitch sw, long mac) {
        Map<Long, Short> swMap = macToSwitchPortMap.get(sw);
        
        if (swMap != null)
            swMap.remove(mac);
    }

    /**
     * Get the port that a MAC is associated with
     * @param sw The switch to get the mapping from
     * @param mac The MAC address to get
     * @return The port the host is on
     */
/* CS6998: fill out the following method */
    public Short getFromPortMap(IOFSwitch sw, long mac) {
        Map<Long, Short> swMap = macToSwitchPortMap.get(sw);
        
        if (swMap == null)
            return null;
        else
            return swMap.get(mac);
    }
    
    /**
     * Add the destination MAC to the source destination list
     * @param srcMac The source MAC
     * @param dstMac The destination MAC
     */
    public void addToDstSet(long srcMac, long dstMac) {
    	Set<Long> dset = dstset.get(srcMac);
    	
    	if (dset == null) {
    	    dset = Collections.synchronizedSet(new HashSet<Long>());
    	    dstset.put(srcMac, dset);
    	}
    	dset.add(dstMac);
    }
    
    /**
     * Clear the destination list of the corresponding list
     * @param srcMac The source MAC
     */
    public void clearDstSet(long srcMac) {
    	Set<Long> dset = dstset.get(srcMac);
    	
    	if (dset != null)
    	    dset.clear();
    }
    
    /**
     * Check if the source exceed its destination maximum
     * @param srcMac The source MAC
     * @return The source still have destinations fewer than the maximum
     */
    public boolean checkDstSetCapacity(long srcMac) {
    	Set<Long> dset = dstset.get(srcMac);
    	
    	if (dset == null)
    	    // No set means no destinations were added
    	    return true;
    	
    	return (dset.size() > Hw1Switch.MAX_DESTINATION_NUMBER) ? false :  true;
    }
    
    /**
     * Add source MAC of an elephant flow to the elephant flow list of a switch
     * @param sw The switch
     * @param srcMac The source MAC
     */
    public void addToEleFlowSet(IOFSwitch sw, long srcMac) {
	Set<Long> swSet = efset.get(sw);
        
        if (swSet == null) {
            // May be accessed by REST API so we need to make it thread safe
            swSet = Collections.synchronizedSet(new HashSet<Long>());
            efset.put(sw, swSet);
        }
        swSet.add(srcMac);
    }
    
    /**
     * Remove source MAC from the elephant flow list of a switch
     * @param sw The switch
     * @param srcMac The source MAC
     */
    protected void removeFromEleFlowSet(IOFSwitch sw, long srcMac) {
	Set<Long> swSet = efset.get(sw);
	
	if (swSet != null)
	    swSet.remove(srcMac);
    }
    
    /**
     * Check if the switch has more than 1 elephant flow
     * @param sw The switch
     * @return Whether the switch passes the elephant flow capacity check
     */
    public boolean checkEleFlowSetCapacity(IOFSwitch sw) {
	Set<Long> swSet = efset.get(sw);
	
	if (swSet == null)
	    return true;
	
	return (swSet.size() > Hw1Switch.MAX_ELEPHANT_FLOW_NUMBER) ? false : true;
    }
    
    /**
     * Get the elephant flow set of a switch
     * @param sw The switch
     * @return The source set with elephant flow set
     */
    public Set<Long> cloneFromEleFlowSet(IOFSwitch sw) {
	Set<Long> swSet = Collections.synchronizedSet(new HashSet<Long>());
	
	swSet.addAll(efset.get(sw));
	return swSet;
    }

    /**
     * Writes a OFFlowMod to a switch.
     * @param sw The switch to write the flowmod to.
     * @param command The FlowMod actions (add, delete, etc).
     * @param bufferId The buffer ID if the switch has buffered the packet.
     * @param match The OFMatch structure to write.
     * @param outPort The switch port to output it to.
     */
    private void writeFlowMod(IOFSwitch sw, short command, int bufferId,
            OFMatch match, short outPort) {
        // from openflow 1.0 spec - need to set these on a struct ofp_flow_mod:
        // struct ofp_flow_mod {
        //    struct ofp_header header;
        //    struct ofp_match match; /* Fields to match */
        //    uint64_t cookie; /* Opaque controller-issued identifier. */
        //
        //    /* Flow actions. */
        //    uint16_t command; /* One of OFPFC_*. */
        //    uint16_t idle_timeout; /* Idle time before discarding (seconds). */
        //    uint16_t hard_timeout; /* Max time before discarding (seconds). */
        //    uint16_t priority; /* Priority level of flow entry. */
        //    uint32_t buffer_id; /* Buffered packet to apply to (or -1).
        //                           Not meaningful for OFPFC_DELETE*. */
        //    uint16_t out_port; /* For OFPFC_DELETE* commands, require
        //                          matching entries to include this as an
        //                          output port. A value of OFPP_NONE
        //                          indicates no restriction. */
        //    uint16_t flags; /* One of OFPFF_*. */
        //    struct ofp_action_header actions[0]; /* The action length is inferred
        //                                            from the length field in the
        //                                            header. */
        //    };
        OFFlowMod flowMod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
        flowMod.setMatch(match);
        flowMod.setCookie(Hw1Switch.HW1_SWITCH_COOKIE);
        flowMod.setCommand(command);
        flowMod.setIdleTimeout(Hw1Switch.IDLE_TIMEOUT_DEFAULT);
        flowMod.setHardTimeout(Hw1Switch.HARD_TIMEOUT_DEFAULT);
        flowMod.setPriority(Hw1Switch.PRIORITY_DEFAULT);
        flowMod.setBufferId(bufferId);
        flowMod.setOutPort((command == OFFlowMod.OFPFC_DELETE) ? outPort : OFPort.OFPP_NONE.getValue());
        flowMod.setFlags((command == OFFlowMod.OFPFC_DELETE) ? 0 : (short) (1 << 0)); // OFPFF_SEND_FLOW_REM

        // set the ofp_action_header/out actions:
        // from the openflow 1.0 spec: need to set these on a struct ofp_action_output:
        // uint16_t type; /* OFPAT_OUTPUT. */
        // uint16_t len; /* Length is 8. */
        // uint16_t port; /* Output port. */
        // uint16_t max_len; /* Max length to send to controller. */
        // type/len are set because it is OFActionOutput,
        // and port, max_len are arguments to this constructor
        flowMod.setActions(Arrays.asList((OFAction) new OFActionOutput(outPort, (short) 0xffff)));
        flowMod.setLength((short) (OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH));

        if (log.isTraceEnabled()) {
            log.trace("{} {} flow mod {}", 
                      new Object[] { sw, (command == OFFlowMod.OFPFC_DELETE) ? "deleting" : "adding", flowMod });
        }

        // and write it out
        try {
            sw.write(flowMod, null);
        } catch (IOException e) {
            log.error("Failed to write {} to switch {}", new Object[] { flowMod, sw }, e);
        }
    }

    /**
     * Writes an OFPacketOut message to a switch.
     * @param sw The switch to write the PacketOut to.
     * @param packetInMessage The corresponding PacketIn.
     * @param egressPort The switch port to output the PacketOut.
     */
    private void writePacketOutForPacketIn(IOFSwitch sw, 
                                          OFPacketIn packetInMessage, 
                                          short egressPort) {

        // from openflow 1.0 spec - need to set these on a struct ofp_packet_out:
        // uint32_t buffer_id; /* ID assigned by datapath (-1 if none). */
        // uint16_t in_port; /* Packet's input port (OFPP_NONE if none). */
        // uint16_t actions_len; /* Size of action array in bytes. */
        // struct ofp_action_header actions[0]; /* Actions. */
        /* uint8_t data[0]; */ /* Packet data. The length is inferred
                                  from the length field in the header.
                                  (Only meaningful if buffer_id == -1.) */
        
        OFPacketOut packetOutMessage = (OFPacketOut) floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        short packetOutLength = (short) OFPacketOut.MINIMUM_LENGTH; // starting length

        // Set buffer_id, in_port, actions_len
        packetOutMessage.setBufferId(packetInMessage.getBufferId());
        packetOutMessage.setInPort(packetInMessage.getInPort());
        packetOutMessage.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
        packetOutLength += OFActionOutput.MINIMUM_LENGTH;
        
        // set actions
        List<OFAction> actions = new ArrayList<OFAction>(1);      
        actions.add(new OFActionOutput(egressPort, (short) 0));
        packetOutMessage.setActions(actions);

        // set data - only if buffer_id == -1
        if (packetInMessage.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
            byte[] packetData = packetInMessage.getPacketData();
            packetOutMessage.setPacketData(packetData); 
            packetOutLength += (short) packetData.length;
        }
        
        // finally, set the total length
        packetOutMessage.setLength(packetOutLength);              
            
        // and write it out
        try {
            sw.write(packetOutMessage, null);
        } catch (IOException e) {
            log.error("Failed to write {} to switch {}: {}", new Object[] { packetOutMessage, sw, e });
        }
    }
    
    /**
     * Write packet dropping to the switches
     * @param sourceMac The source MAC to block
     *//*
    private void writeBlockSourceMacToSwitch(long sourceMac) {
	Set<IOFSwitch> swSet = macToSwitchPortMap.keySet();
	OFFlowMod flowMod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
	OFMatch match = new OFMatch();
	
	match.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_SRC);
	match.setDataLayerSource(Ethernet.toByteArray(sourceMac));
	
        flowMod.setMatch(match);
        flowMod.setCookie(Hw1Switch.HW1_SWITCH_COOKIE);
        flowMod.setCommand(OFFlowMod.OFPFC_ADD);
        flowMod.setIdleTimeout((short) 0);
        flowMod.setHardTimeout((short) (Hw1Switch.FIREWALL_BLOCK_TIME_DUR/1000));
        flowMod.setPriority((short) (Hw1Switch.PRIORITY_DEFAULT+1));
        flowMod.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        flowMod.setOutPort(OFPort.OFPP_NONE.getValue());
        flowMod.setFlags((short) (1 << 0)); // OFPFF_SEND_FLOW_REM

        List<OFAction> actions = new ArrayList<OFAction>();
        flowMod.setActions(actions);
        flowMod.setLength((short) OFFlowMod.MINIMUM_LENGTH);
        
        // and write it out
        for (IOFSwitch sw : swSet) {
            if (log.isTraceEnabled()) {
        	log.trace("{} {} flow mod {}", 
        		new Object[] { sw, "blocking", flowMod });
            }
            try {
        	sw.write(flowMod, null);
            } catch (IOException e) {
        	log.error("Failed to write {} to switch {}", new Object[] { flowMod, sw }, e);
            }
        }
    }*/
    
    /**
     * Processes a OFPacketIn message. If the switch has learned the MAC to port mapping
     * for the pair it will write a FlowMod for. If the mapping has not been learned the 
     * we will flood the packet.
     * @param sw
     * @param pi
     * @param cntx
     * @return
     */
    private Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {

        // Read in packet data headers by using OFMatch
        OFMatch match = new OFMatch();
        match.loadFromPacket(pi.getPacketData(), pi.getInPort());
        Long sourceMac = Ethernet.toLong(match.getDataLayerSource());
        Long destMac = Ethernet.toLong(match.getDataLayerDestination());

/* CS6998: Do works here to learn the port for this MAC
        ....
*/
        this.addToPortMap(sw, sourceMac, match.getInputPort());

/* CS6998: Do works here to implement super firewall
        Hint: You may check connection limitation here.
        ....
*/

/* CS6998: Filter-out hosts in blacklist
 *         Also, when the host is in blacklist check if the blockout time is
 *         expired and handle properly */
        if (blacklist.containsKey(sourceMac)) {
            long timeDiff = System.currentTimeMillis() - blacklist.get(sourceMac);
            
            if (timeDiff < Hw1Switch.FIREWALL_BLOCK_TIME_DUR) {
        	return Command.CONTINUE;
            } else {
        	/* Remove from the blacklist. Release the source */
        	blacklist.remove(sourceMac);
            }
        }
        
        if (destMac < 1000)
            // Only take MAC < 1000 into account of valid destination MAC
            this.addToDstSet(sourceMac, destMac);
        
        if (!this.checkDstSetCapacity(sourceMac)) {
            blacklist.put(sourceMac, System.currentTimeMillis());
            // Try to remove all existing flows from the source MAC
            match.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_SRC);
            Set<IOFSwitch> iterSw = macToSwitchPortMap.keySet();
            for (IOFSwitch tmpSw : iterSw) {
        	this.writeFlowMod(tmpSw, OFFlowMod.OFPFC_DELETE, -1, match, OFPort.OFPP_NONE.getValue());
            }
            //this.writeBlockSourceMacToSwitch(sourceMac);
            this.clearDstSet(sourceMac);
            return Command.CONTINUE;
        }

        if (!this.checkEleFlowSetCapacity(sw)) {
            long sysTime = System.currentTimeMillis();
            Set<Long> swSet = this.cloneFromEleFlowSet(sw);

            // Try to remove all existing flows from all the source MACs
            for (Long srcMac : swSet) {
        	blacklist.put(srcMac, sysTime);
        	match.setWildcards(OFMatch.OFPFW_ALL & ~OFMatch.OFPFW_DL_SRC);
        	match.setDataLayerSource(Ethernet.toByteArray(srcMac));
        	Set<IOFSwitch> iterSw = macToSwitchPortMap.keySet();
        	for (IOFSwitch tmpSw : iterSw) {
                    this.writeFlowMod(tmpSw, OFFlowMod.OFPFC_DELETE, -1, match, OFPort.OFPP_NONE.getValue());
                    this.removeFromEleFlowSet(tmpSw, srcMac);
                }
        	//this.writeBlockSourceMacToSwitch(srcMac);
            }

            return Command.CONTINUE;
        }

/* CS6998: Ask the switch to flood the packet to all of its ports
 *         Thus, this module currently works as a dummy hub
 */
        //this.writePacketOutForPacketIn(sw, pi, OFPort.OFPP_FLOOD.getValue());

/* CS6998: Ask the switch to flood the packet to all of its ports */
        // Now output flow-mod and/or packet
        // CS6998: Fill out the following ???? to obtain outPort
        Short outPort = this.getFromPortMap(sw, destMac);
        if (outPort == null) {
            // If we haven't learned the port for the dest MAC, flood it
            // CS6998: Fill out the following ????
            this.writePacketOutForPacketIn(sw, pi, OFPort.OFPP_FLOOD.getValue());
        } else if (outPort == match.getInputPort()) {
            log.trace("ignoring packet that arrived on same port as learned destination:"
                    + " switch {} dest MAC {} port {}",
                    new Object[]{ sw, HexString.toHexString(destMac), outPort });
        } else {
            // Add flow table entry matching source MAC, dest MAC and input port
            // that sends to the port we previously learned for the dest MAC.
            match.setWildcards(((Integer) sw.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).intValue()
                    & ~OFMatch.OFPFW_IN_PORT
                    & ~OFMatch.OFPFW_DL_SRC & ~OFMatch.OFPFW_DL_DST
                    & ~OFMatch.OFPFW_NW_SRC_MASK & ~OFMatch.OFPFW_NW_DST_MASK);
            // CS6998: Fill out the following ????
            this.writeFlowMod(sw, OFFlowMod.OFPFC_ADD, pi.getBufferId(), match, outPort);
        }
        return Command.CONTINUE;
    }

    /**
     * Processes a flow removed message. 
     * @param sw The switch that sent the flow removed message.
     * @param flowRemovedMessage The flow removed message.
     * @return Whether to continue processing this message or stop.
     */
    private Command processFlowRemovedMessage(IOFSwitch sw, OFFlowRemoved flowRemovedMessage) {
        if (flowRemovedMessage.getCookie() != Hw1Switch.HW1_SWITCH_COOKIE) {
            return Command.CONTINUE;
        }

        Long sourceMac = Ethernet.toLong(flowRemovedMessage.getMatch().getDataLayerSource());
        //Long destMac = Ethernet.toLong(flowRemovedMessage.getMatch().getDataLayerDestination());

        if (log.isTraceEnabled()) {
            log.trace("{} flow entry removed {}", sw, flowRemovedMessage);
        }

        // CS6998: Do works here to implement super firewall
        //  Hint: You may detect Elephant Flow here.
        //  ....
        //
        if (flowRemovedMessage.getReason() == OFFlowRemoved.OFFlowRemovedReason.OFPRR_HARD_TIMEOUT) {
            if (flowRemovedMessage.getByteCount() / HARD_TIMEOUT_DEFAULT > ELEPHANT_FLOW_BAND_WIDTH)
        	this.addToEleFlowSet(sw, sourceMac);
            else
        	this.removeFromEleFlowSet(sw, sourceMac);
        } else {
            this.removeFromEleFlowSet(sw, sourceMac);
        }
        
        return Command.CONTINUE;
    }

    // IOFMessageListener
    
    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
                return this.processPacketInMessage(sw, (OFPacketIn) msg, cntx);
            case FLOW_REMOVED:
                return this.processFlowRemovedMessage(sw, (OFFlowRemoved) msg);
            case ERROR:
                log.info("received an error {} from switch {}", (OFError) msg, sw);
                return Command.CONTINUE;
            default:
                break;
        }
        log.error("received an unexpected message {} from switch {}", msg, sw);
        return Command.CONTINUE;
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    // IFloodlightModule
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
            IFloodlightService> m = 
                new HashMap<Class<? extends IFloodlightService>,
                    IFloodlightService>();
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        floodlightProvider =
                context.getServiceImpl(IFloodlightProviderService.class);
/* CS6998: Initialize data structures */
        macToSwitchPortMap = 
                new ConcurrentHashMap<IOFSwitch, Map<Long, Short>>();
        blacklist =
                new HashMap<Long, Long>();
        dstset =
        	new HashMap<Long, Set<Long>>();
        efset =
        	new ConcurrentHashMap<IOFSwitch, Set<Long>>();
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        floodlightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);
        floodlightProvider.addOFMessageListener(OFType.ERROR, this);
    }
}
