import NetworkExtension
import Foundation

/**
 * NEDNSProxyProvider — intercepts all DNS queries on the device.
 *
 * Blocked domains → NXDOMAIN response returned immediately.
 * Allowed domains → forwarded to the system upstream resolver.
 *
 * Requires entitlements:
 *   com.apple.developer.networking.networkextension: [dns-proxy]
 *   com.apple.security.application-groups: [group.com.blockcorn]
 */
class DNSProxyProvider: NEDNSProxyProvider {

    private var blocklist: Set<String> = []
    private let upstreamDNS = "8.8.8.8"
    private let dnsPort: UInt16 = 53

    // MARK: - Lifecycle

    override func startProxy(options: [String: Any]? = nil,
                             completionHandler: @escaping (Error?) -> Void) {
        loadBlocklist()
        completionHandler(nil)
    }

    override func stopProxy(with reason: NEProviderStopReason,
                            completionHandler: @escaping () -> Void) {
        completionHandler()
    }

    // MARK: - Flow handling

    override func handleNewFlow(_ flow: NEAppProxyFlow) -> Bool {
        guard let udpFlow = flow as? NEAppProxyUDPFlow else { return false }

        udpFlow.open(withLocalEndpoint: nil) { [weak self] error in
            guard error == nil, let self = self else { return }
            self.processDNSFlow(udpFlow)
        }
        return true
    }

    // MARK: - DNS processing

    private func processDNSFlow(_ flow: NEAppProxyUDPFlow) {
        flow.readDatagrams { [weak self] datagrams, remoteEndpoints, error in
            guard let self = self,
                  let datagrams = datagrams,
                  let endpoints = remoteEndpoints,
                  error == nil else { return }

            for (data, endpoint) in zip(datagrams, endpoints) {
                if let domain = DnsPacketParser.parseDomain(from: data),
                   self.isBlocked(domain) {
                    // Return NXDOMAIN without hitting the network
                    let nxdomain = DnsPacketParser.buildNxDomain(from: data)
                    flow.writeDatagrams([nxdomain], sentBy: [endpoint]) { _ in }
                } else {
                    // Forward to upstream DNS
                    self.forward(data, from: endpoint, flow: flow)
                }
            }

            // Keep reading
            self.processDNSFlow(flow)
        }
    }

    private func forward(_ data: Data, from endpoint: NWEndpoint, flow: NEAppProxyUDPFlow) {
        guard let upstreamEndpoint = NWHostEndpoint(
            hostname: upstreamDNS,
            port: String(dnsPort)) as? NWEndpoint else { return }

        let socket = NEAppProxyUDPFlow.self
        // Use a UDP socket to forward to real DNS
        DispatchQueue.global(qos: .userInitiated).async {
            var sock = sockaddr_in()
            sock.sin_family = sa_family_t(AF_INET)
            sock.sin_port   = self.dnsPort.bigEndian
            inet_pton(AF_INET, self.upstreamDNS, &sock.sin_addr)

            let fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
            defer { close(fd) }
            guard fd >= 0 else { return }

            var timeout = timeval(tv_sec: 3, tv_usec: 0)
            setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, socklen_t(MemoryLayout<timeval>.size))

            _ = withUnsafeBytes(of: &sock) { ptr in
                sendto(fd, (data as NSData).bytes, data.count,
                       0, ptr.baseAddress?.assumingMemoryBound(to: sockaddr.self),
                       socklen_t(MemoryLayout<sockaddr_in>.size))
            }

            var buf = [UInt8](repeating: 0, count: 4096)
            let n = recv(fd, &buf, buf.count, 0)
            if n > 0 {
                let response = Data(buf[..<n])
                flow.writeDatagrams([response], sentBy: [endpoint]) { _ in }
            }
        }
    }

    // MARK: - Blocklist

    private func isBlocked(_ domain: String) -> Bool {
        var d = domain.lowercased()
        if d.hasPrefix("www.") { d = String(d.dropFirst(4)) }
        return blocklist.contains(d)
    }

    private func loadBlocklist() {
        DispatchQueue.global(qos: .utility).async { [weak self] in
            guard let self = self else { return }
            let containerURL = FileManager.default
                .containerURL(forSecurityApplicationGroupIdentifier: "group.com.blockcorn")?
                .appendingPathComponent("blocklist.hosts")

            guard let url = containerURL,
                  FileManager.default.fileExists(atPath: url.path),
                  let raw = try? String(contentsOf: url) else {
                NSLog("[DNSProxyProvider] No cached blocklist found")
                return
            }

            var result = Set<String>()
            for line in raw.split(separator: "\n") {
                let t = String(line).trimmingCharacters(in: .whitespaces)
                guard !t.isEmpty, !t.hasPrefix("#") else { continue }
                let parts = t.split(separator: " ")
                guard parts.count >= 2,
                      parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1" else { continue }
                var d = String(parts[1]).lowercased()
                if d.hasPrefix("www.") { d = String(d.dropFirst(4)) }
                if !d.isEmpty { result.insert(d) }
            }
            self.blocklist = result
            NSLog("[DNSProxyProvider] Loaded %d domains", result.count)
        }
    }
}
