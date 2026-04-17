package com.blockcorn.android;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

/**
 * Parses IPv4/UDP/DNS packets from the TUN interface.
 *
 * For blocked domains:  returns an NXDOMAIN response in-place.
 * For allowed domains:  forwards to upstream DNS (8.8.8.8) and returns the response.
 *
 * Packet layout assumed:
 *   [IPv4 header][UDP header][DNS payload]
 */
public final class DnsPacketProcessor {

    private static final String UPSTREAM_DNS = "8.8.8.8";
    private static final int    DNS_PORT      = 53;
    private static final int    DNS_TIMEOUT   = 3_000; // ms

    private DnsPacketProcessor() {}

    /**
     * Process a raw IP packet.
     * @return response packet bytes, or null if not a DNS query or processing failed.
     */
    public static byte[] process(byte[] packet, int length, BlocklistManager blocklist) {
        if (length < 28) return null; // too short for IP+UDP+DNS header

        // IPv4 header
        int ipVersion = (packet[0] >> 4) & 0x0F;
        if (ipVersion != 4) return null;

        int ipHeaderLen = (packet[0] & 0x0F) * 4;
        int protocol    = packet[9] & 0xFF;
        if (protocol != 17) return null; // not UDP

        // UDP header
        int dstPort = ((packet[ipHeaderLen + 2] & 0xFF) << 8)
                     | (packet[ipHeaderLen + 3] & 0xFF);
        if (dstPort != DNS_PORT) return null;

        int dnsOffset  = ipHeaderLen + 8;
        int dnsLength  = length - dnsOffset;
        if (dnsLength < 12) return null;

        byte[] dnsPayload = Arrays.copyOfRange(packet, dnsOffset, dnsOffset + dnsLength);

        // Parse the first question domain
        String domain = parseDomain(dnsPayload, 12);
        if (domain == null) return null;

        if (blocklist.isBlocked(domain)) {
            byte[] nxdomain = buildNxDomain(dnsPayload);
            return wrapInIpUdp(packet, ipHeaderLen, nxdomain);
        } else {
            byte[] response = forwardDns(dnsPayload);
            if (response == null) return null;
            return wrapInIpUdp(packet, ipHeaderLen, response);
        }
    }

    /** Decode a DNS domain name starting at `offset` inside the DNS payload. */
    static String parseDomain(byte[] dns, int offset) {
        StringBuilder sb = new StringBuilder();
        int pos = offset;
        int safety = 0;

        while (pos < dns.length && safety++ < 128) {
            int len = dns[pos] & 0xFF;
            if (len == 0) break;

            // Pointer (compression)
            if ((len & 0xC0) == 0xC0) {
                int ptrOffset = ((len & 0x3F) << 8) | (dns[pos + 1] & 0xFF);
                pos = ptrOffset;
                continue;
            }

            if (sb.length() > 0) sb.append('.');
            sb.append(new String(dns, pos + 1, len));
            pos += 1 + len;
        }

        String domain = sb.toString().toLowerCase();
        return domain.isEmpty() ? null : domain;
    }

    /** Build a minimal NXDOMAIN DNS response for the given query payload. */
    private static byte[] buildNxDomain(byte[] query) {
        byte[] response = Arrays.copyOf(query, query.length);
        // Byte 2: QR=1 (response), Opcode=0, AA=0, TC=0, RD=1
        response[2] = (byte) ((query[2] & 0x01) | 0x81);
        // Byte 3: RA=1, RCODE=3 (NXDOMAIN)
        response[3] = (byte) 0x83;
        // ANCOUNT = 0 (bytes 6–7)
        response[6] = 0;
        response[7] = 0;
        return response;
    }

    /** Forward raw DNS payload to upstream; return raw DNS response. */
    private static byte[] forwardDns(byte[] query) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(DNS_TIMEOUT);
            InetAddress upstream = InetAddress.getByName(UPSTREAM_DNS);
            socket.send(new DatagramPacket(query, query.length, upstream, DNS_PORT));

            byte[] buf = new byte[4096];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            socket.receive(resp);
            return Arrays.copyOf(resp.getData(), resp.getLength());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Wraps a DNS payload back into an IPv4+UDP packet, swapping src/dst addresses and ports.
     */
    private static byte[] wrapInIpUdp(byte[] originalIp, int ipHeaderLen, byte[] dns) {
        int totalLen = ipHeaderLen + 8 + dns.length;
        byte[] out = new byte[totalLen];

        // Copy original IP header
        System.arraycopy(originalIp, 0, out, 0, ipHeaderLen);

        // Swap src/dst IP
        System.arraycopy(originalIp, 12, out, 16, 4); // original src → new dst
        System.arraycopy(originalIp, 16, out, 12, 4); // original dst → new src

        // Update total length
        out[2] = (byte) ((totalLen >> 8) & 0xFF);
        out[3] = (byte) (totalLen & 0xFF);

        // Recalculate IP checksum
        out[10] = 0;
        out[11] = 0;
        int cs = ipChecksum(out, ipHeaderLen);
        out[10] = (byte) ((cs >> 8) & 0xFF);
        out[11] = (byte) (cs & 0xFF);

        // UDP header: swap src/dst ports
        out[ipHeaderLen]     = originalIp[ipHeaderLen + 2]; // original dst port → new src port
        out[ipHeaderLen + 1] = originalIp[ipHeaderLen + 3];
        out[ipHeaderLen + 2] = originalIp[ipHeaderLen];     // original src port → new dst port
        out[ipHeaderLen + 3] = originalIp[ipHeaderLen + 1];

        // UDP length
        int udpLen = 8 + dns.length;
        out[ipHeaderLen + 4] = (byte) ((udpLen >> 8) & 0xFF);
        out[ipHeaderLen + 5] = (byte) (udpLen & 0xFF);

        // UDP checksum — set to 0 (optional in IPv4)
        out[ipHeaderLen + 6] = 0;
        out[ipHeaderLen + 7] = 0;

        // DNS payload
        System.arraycopy(dns, 0, out, ipHeaderLen + 8, dns.length);

        return out;
    }

    private static int ipChecksum(byte[] buf, int headerLen) {
        int sum = 0;
        for (int i = 0; i < headerLen; i += 2) {
            sum += ((buf[i] & 0xFF) << 8) | (buf[i + 1] & 0xFF);
        }
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return ~sum & 0xFFFF;
    }
}
