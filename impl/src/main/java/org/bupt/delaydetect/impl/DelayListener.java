/*
 * Copyright © 2017 bupt.dtj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.delaydetect.impl;

import org.opendaylight.controller.liblldp.BitBufferHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.basepacket.rev140528.packet.chain.grp.PacketChain;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.basepacket.rev140528.packet.chain.grp.packet.chain.packet.RawPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.delaydetect.config.rev181107.DelaydetectConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ethernet.rev140528.ethernet.packet.received.packet.chain.packet.EthernetPacket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.Ipv4PacketListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.Ipv4PacketReceived;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.KnownIpProtocols;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.ipv4.packet.received.packet.chain.packet.Ipv4Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DelayListener implements Ipv4PacketListener {

    private static final Logger LOG = LoggerFactory.getLogger(DelayListener.class);
    private DelaydetectConfig delaydetectConfig;
    private Map<String, Long> delayMap;
    private Map<String, Long[]> loopDelayMap = new ConcurrentHashMap<>();
    private Map<String, Long> echoDelayMap;

    public DelayListener(DelaydetectConfig config, Map<String, Long> delayMap, Map<String, Long> echoDelayMap) {
        this.delaydetectConfig = config;
        this.delayMap = delayMap;
        this.echoDelayMap = echoDelayMap;
    }

    @Override
    public void onIpv4PacketReceived(Ipv4PacketReceived ipv4PacketReceived) {

        if (ipv4PacketReceived == null || ipv4PacketReceived.getPacketChain() == null) {
            return;
        }

        long Time2 = System.nanoTime();
        RawPacket rawPacket = null;
        EthernetPacket ethernetPacket = null;
        Ipv4Packet ipv4Packet = null;
        for (PacketChain packetChain : ipv4PacketReceived.getPacketChain()) {
            if (packetChain.getPacket() instanceof RawPacket) {
                rawPacket = (RawPacket) packetChain.getPacket();
            } else if (packetChain.getPacket() instanceof EthernetPacket) {
                ethernetPacket = (EthernetPacket) packetChain.getPacket();
            } else if (packetChain.getPacket() instanceof Ipv4Packet) {
                ipv4Packet = (Ipv4Packet) packetChain.getPacket();
            }
        }
        if (rawPacket == null || ethernetPacket == null || ipv4Packet == null) {
            return;
        }
        if (ipv4Packet.getProtocol() != KnownIpProtocols.Experimentation1) {
            return;
        }

        byte[] option = ipv4Packet.getIpv4Options();
        byte[] timeOption = new byte[]{0, 0, 0, 0, 0, 0, 0, 0};
        byte[] idOption = new byte[]{0, 0, 0, 0, 0, 0, 0, 0};
        for (int i = 0; i < timeOption.length; i++) {
            timeOption[i] = option[i];
            idOption[i] = option[i + 8];
        }
        long Time1 = BitBufferHelper.getLong(timeOption);
        String ncId = rawPacket.getIngress().getValue().firstIdentifierOf(NodeConnector.class).firstKeyOf(NodeConnector.class, NodeConnectorKey.class).getId().getValue();
        long delay = Time2 - Time1;
        long rawNodeId = BitBufferHelper.getLong(idOption);
        loopDelayMap.put(ncId, new Long[]{delay, rawNodeId});
        LOG.info(ncId + ": " + delay);

        if (!loopDelayMap.isEmpty() && !echoDelayMap.isEmpty()) {
            for (String ncid : loopDelayMap.keySet()) {
                Long tmp = loopDelayMap.get(ncid)[0];
                long echo1 = echoDelayMap.getOrDefault(ncIdToNodeId(ncId), 0L);
                long echo2 = echoDelayMap.getOrDefault("openflow:" + loopDelayMap.get(ncId)[1], 0L);
                tmp = tmp - echo1 / 2 - echo2 / 2;
                if (tmp >= 0) {
                    delayMap.put(ncid, tmp);
                } else {
                    delayMap.put(ncid, 0L);
                }
            }
        }
    }

    private String ncIdToNodeId(String ncId) {
        String[] info = ncId.split(":");
        return info[0] + ":" + info[1];
    }
}
