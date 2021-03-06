/*
 * Copyright 2015 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.driver.handshaker;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.onlab.util.Spectrum;
import org.onosproject.net.ChannelSpacing;
import org.onosproject.net.DefaultOchSignalComparator;
import org.onosproject.net.Device;
import org.onosproject.net.GridType;
import org.onosproject.net.OchSignal;
import org.onosproject.net.PortNumber;
import org.onosproject.net.behaviour.LambdaQuery;
import org.onosproject.openflow.controller.OpenFlowOpticalSwitch;
import org.onosproject.openflow.controller.PortDescPropertyType;
import org.onosproject.openflow.controller.driver.AbstractOpenFlowSwitch;
import org.onosproject.openflow.controller.driver.SwitchDriverSubHandshakeAlreadyStarted;
import org.onosproject.openflow.controller.driver.SwitchDriverSubHandshakeCompleted;
import org.onosproject.openflow.controller.driver.SwitchDriverSubHandshakeNotStarted;
import org.projectfloodlight.openflow.protocol.OFCalientFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.OFCalientPortDescStatsEntry;
import org.projectfloodlight.openflow.protocol.OFCalientPortDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFCalientPortDescStatsRequest;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFObject;
import org.projectfloodlight.openflow.protocol.OFStatsReplyFlags;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Driver for Calient S160 Optical Circuit Switch. Untested on Calient S320 but probably works ok.
 *
 * Driver implements custom handshaker, and rewrites flow stats as expected by the device. Port stats are currently
 * not supported.
 *
 * The device consists of OMS ports only, and each port exposes lambda resources covering the whole
 * usable optical spectrum (U to O band, see {@link Spectrum} for spectrum definitions).
 */
public class CalientFiberSwitchHandshaker
        extends AbstractOpenFlowSwitch
        implements OpenFlowOpticalSwitch, LambdaQuery {

    private final AtomicBoolean driverHandshakeComplete = new AtomicBoolean(false);
    private List<OFCalientPortDescStatsEntry> fiberPorts = new ArrayList<>();


    @Override
    public Boolean supportNxRole() {
        return false;
    }

    @Override
    public void startDriverHandshake() {
        log.warn("Starting driver handshake for sw {}", getStringId());
        if (startDriverHandshakeCalled) {
            throw new SwitchDriverSubHandshakeAlreadyStarted();
        }
        startDriverHandshakeCalled = true;
        try {
            sendHandshakeOFExperimenterPortDescRequest();
        } catch (IOException e) {
            log.error("Exception while sending experimenter port desc:", e.getMessage());
            e.printStackTrace();
        }

    }

    private void sendHandshakeOFExperimenterPortDescRequest() throws IOException {
        // send multi part message for port description for optical switches
        OFCalientPortDescStatsRequest portsRequest = factory()
                .buildCalientPortDescStatsRequest()
                .build();
        log.warn("Sending experimenter port description message {}",
                portsRequest.toString());
        this.sendHandshakeMessage(portsRequest);
    }

    @Override
    public boolean isDriverHandshakeComplete() {
        return driverHandshakeComplete.get();
    }

    @Override
    public void processDriverHandshakeMessage(OFMessage m) {
        if (!startDriverHandshakeCalled) {
            throw new SwitchDriverSubHandshakeNotStarted();
        }
        if (driverHandshakeComplete.get()) {
            throw new SwitchDriverSubHandshakeCompleted(m);
        }

        switch (m.getType()) {
            case BARRIER_REPLY:
                break;
            case ERROR:
                log.error("Switch Error {} {}", getStringId(), m);
                break;
            case FEATURES_REPLY:
                break;
            case FLOW_REMOVED:
                break;
            case GET_ASYNC_REPLY:
                break;
            case PACKET_IN:
                break;
            case PORT_STATUS:
                break;
            case QUEUE_GET_CONFIG_REPLY:
                break;
            case ROLE_REPLY:
                break;
            case STATS_REPLY:
                log.warn("Received port desc reply");
                OFCalientPortDescStatsReply descStatsReply = (OFCalientPortDescStatsReply) m;
                fiberPorts.addAll(descStatsReply.getPortDesc());
                // Multi-part message
                if (!descStatsReply.getFlags().contains(OFStatsReplyFlags.REPLY_MORE)) {
                    driverHandshakeComplete.set(true);
                }
                break;
            default:
                log.warn("Received message {} during switch-driver " +
                                "subhandshake " + "from switch {} ... " +
                                "Ignoring message", m,
                        getStringId());

        }
    }

    @Override
    public Device.Type deviceType() {
        return Device.Type.FIBER_SWITCH;
    }

    @Override
    public List<? extends OFObject> getPortsOf(PortDescPropertyType type) {
        return ImmutableList.copyOf(fiberPorts);
    }

    @Override
    public Set<PortDescPropertyType> getPortTypes() {
        return ImmutableSet.of(PortDescPropertyType.OPTICAL_TRANSPORT);
    }

    @Override
    public final void sendMsg(OFMessage m) {
        OFMessage newMsg = m;

        if (m.getType() == OFType.STATS_REQUEST) {
            OFStatsRequest sr = (OFStatsRequest) m;
            log.debug("Rebuilding stats request type {}", sr.getStatsType());
            switch (sr.getStatsType()) {
                case FLOW:
                    OFCalientFlowStatsRequest request = this.factory().buildCalientFlowStatsRequest()
                            .setCookie(((OFFlowStatsRequest) sr).getCookie())
                            .setCookieMask(((OFFlowStatsRequest) sr).getCookieMask())
                            .setMatch(this.factory().matchWildcardAll())
                            .setOutGroup(((OFFlowStatsRequest) sr).getOutGroup().getGroupNumber())
                            .setOutPort(OFPort.ANY)
                            .setTableId(TableId.ALL)
                            .setXid(sr.getXid())
                            .setFlags(sr.getFlags())
                            .build();
                    newMsg = request;
                    break;
                case PORT:
                    // TODO
                    break;
                default:
                    break;
            }
        }

        super.sendMsg(newMsg);
    }

    @Override
    public SortedSet<OchSignal> queryLambdas(PortNumber port) {
        // S160 data sheet
        // Wavelength range: 1260 - 1630 nm
        long startSpacingMultiplier = Spectrum.U_BAND_MIN.subtract(Spectrum.CENTER_FREQUENCY).asHz() /
                ChannelSpacing.CHL_12P5GHZ.frequency().asHz();
        long stopSpacingMultiplier = Spectrum.O_BAND_MAX.subtract(Spectrum.CENTER_FREQUENCY).asHz() /
                ChannelSpacing.CHL_12P5GHZ.frequency().asHz();
        List<OchSignal> lambdas = IntStream.rangeClosed((int) startSpacingMultiplier, (int) stopSpacingMultiplier)
                .mapToObj(x -> new OchSignal(GridType.FLEX, ChannelSpacing.CHL_12P5GHZ, x, 1))
                .collect(Collectors.toList());

        SortedSet<OchSignal> result = new TreeSet<>(new DefaultOchSignalComparator());
        result.addAll(lambdas);

        return result;
    }
}
