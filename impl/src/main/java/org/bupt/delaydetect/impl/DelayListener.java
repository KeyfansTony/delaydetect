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

public class DelayListener implements Ipv4PacketListener {

    private static final Logger LOG = LoggerFactory.getLogger(DelayListener.class);
    private DelaydetectConfig delaydetectConfig;
    private Map<String, Long> delayMap;

    public DelayListener(DelaydetectConfig config, Map<String, Long> delayMap) {
        this.delaydetectConfig = config;
        this.delayMap = delayMap;
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
        if (ipv4Packet.getProtocol() == KnownIpProtocols.Experimentation1) {
            long Time1 = BitBufferHelper.getLong(ipv4Packet.getIpv4Options());
            String ncId = rawPacket.getIngress().getValue().firstIdentifierOf(NodeConnector.class).firstKeyOf(NodeConnector.class, NodeConnectorKey.class).getId().getValue();
            long delay = Time2 - Time1;
            delayMap.put(ncId, delay);
            LOG.info(ncId + ": " + delay);
        }
        if (ipv4Packet.getProtocol() == KnownIpProtocols.Experimentation2) {
            long Time1 = BitBufferHelper.getLong(ipv4Packet.getIpv4Options());
            String ncId = rawPacket.getIngress().getValue().firstIdentifierOf(NodeConnector.class).firstKeyOf(NodeConnector.class, NodeConnectorKey.class).getId().getValue();
            long delay = Time2 - Time1;
            LOG.info("Echo: " + ncId + ": " + delay);
        }

    }

}
