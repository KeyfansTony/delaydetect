/*
 * Copyright © 2017 bupt.dtj and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.delaydetect.impl;

import org.bupt.delaydetect.impl.util.IPv4;
import org.opendaylight.controller.liblldp.*;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.bupt.delaydetect.impl.util.PacketDispatcher;
import org.bupt.delaydetect.impl.util.InventoryReader;
import org.opendaylight.yang.gen.v1.urn.opendaylight.echo.service.rev150305.SalEchoService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.echo.service.rev150305.SendEchoInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.echo.service.rev150305.SendEchoInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.echo.service.rev150305.SendEchoOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.delaydetect.config.rev181107.DelaydetectConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.KnownIpProtocols;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.Future;

public class DelaySender implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(DelaySender.class);
    private final DataBroker dataBroker;
    private final DelaydetectConfig delaydetectConfig;
    private final PacketProcessingService packetProcessingService;
    private final SalEchoService salEchoService;
    private Map<String, Long> echoDelayMap;

    public DelaySender(DataBroker dataBroker, DelaydetectConfig delaydetectConfig, PacketProcessingService packetProcessingService, SalEchoService salEchoService, Map<String, Long> echoDelayMap) {
        this.dataBroker = dataBroker;
        this.delaydetectConfig = delaydetectConfig;
        this.packetProcessingService = packetProcessingService;
        this.salEchoService = salEchoService;
        this.echoDelayMap = echoDelayMap;
    }

    @Override
    public void run() {

        PacketDispatcher packetDispatcher = new PacketDispatcher();
        packetDispatcher.setPacketProcessingService(packetProcessingService);
        InventoryReader inventoryReader = new InventoryReader(dataBroker);
        inventoryReader.setRefreshData(true);
        inventoryReader.readInventory();

        while (delaydetectConfig.isIsActive()) {
            IPv4 iPv4 = new IPv4();
            iPv4.setTtl((byte) 1).setProtocol((byte) KnownIpProtocols.Experimentation1.getIntValue());
            try {
                //generate a ipv4 packet
                iPv4.setSourceAddress(InetAddress.getByName("0.0.0.1"))
                        .setDestinationAddress(InetAddress.getByName("0.0.0.2"));

                //generate a ethernet packet
                Ethernet ethernet = new Ethernet();
                EthernetAddress srcMac = new EthernetAddress(new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xee});
                EthernetAddress destMac = new EthernetAddress(new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xef});
                ethernet.setSourceMACAddress(srcMac.getValue())
                        .setDestinationMACAddress(destMac.getValue())
                        .setEtherType(EtherTypes.IPv4.shortValue());

                //flood packet
                packetDispatcher.setInventoryReader(inventoryReader);
                Map<String, NodeConnectorRef> nodeConnectorMap = inventoryReader.getControllerSwitchConnectors();

                for (String nodeId : nodeConnectorMap.keySet()) {
                    iPv4.setOptions(mergeOptions(BitBufferHelper.toByteArray(System.nanoTime()), BitBufferHelper.toByteArray(Long.parseLong(nodeId.split(":")[1]))));
                    ethernet.setPayload(iPv4);
                    packetDispatcher.floodPacket(nodeId, ethernet.serialize(), nodeConnectorMap.get(nodeId), null);

                    InstanceIdentifier<Node> nodeInstanceId = InstanceIdentifier.builder(Nodes.class)
                            .child(Node.class, new NodeKey(new NodeId(nodeId))).build();
                    SendEchoInput sendEchoInput = new SendEchoInputBuilder()
                            .setData(BitBufferHelper.toByteArray(System.nanoTime()))
                            .setNode(new NodeRef(nodeInstanceId)).build();
                    long Time1 = System.nanoTime();
                    Future<RpcResult<SendEchoOutput>> result = salEchoService.sendEcho(sendEchoInput);
                    while (!result.isDone()) ;
                    long Time2 = System.nanoTime();
                    long echoDelay = (Time2 - Time1);
                    echoDelayMap.put(nodeId, echoDelay);
                    // LOG.info("echo " + nodeId + ": " + echoDelay);

                }
            } catch (ConstructionException | UnknownHostException | PacketException e) {
                e.printStackTrace();
            }

            try {
                Thread.sleep(delaydetectConfig.getQuerryDelay() * 100);
            } catch (InterruptedException e) {
                e.printStackTrace();

            }
        }

    }

    private byte[] mergeOptions(byte[] aOption, byte[] bOption) {
        byte[] option = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        for (int i = 0; i < aOption.length; i++) {
            option[i] = aOption[i];
            option[i + 8] = bOption[i];
        }
        return option;
    }
}
