import Foundation

/// Minimal DNS packet parser for blocking decisions.
enum DnsPacketParser {

    /// Extract the first queried domain from a raw DNS payload (no IP/UDP headers).
    static func parseDomain(from data: Data) -> String? {
        guard data.count >= 12 else { return nil }
        let bytes = [UInt8](data)

        var pos = 12 // skip DNS header
        var labels = [String]()
        var safety = 0

        while pos < bytes.count && safety < 128 {
            safety += 1
            let len = Int(bytes[pos])

            if len == 0 { break }

            // Pointer compression
            if (len & 0xC0) == 0xC0 {
                guard pos + 1 < bytes.count else { return nil }
                let ptr = ((len & 0x3F) << 8) | Int(bytes[pos + 1])
                pos = ptr
                continue
            }

            pos += 1
            guard pos + len <= bytes.count else { return nil }
            let label = String(bytes: bytes[pos..<(pos + len)], encoding: .ascii) ?? ""
            labels.append(label)
            pos += len
        }

        let domain = labels.joined(separator: ".").lowercased()
        return domain.isEmpty ? nil : domain
    }

    /// Build a minimal NXDOMAIN response for the given DNS query payload.
    static func buildNxDomain(from query: Data) -> Data {
        var response = [UInt8](query)
        guard response.count >= 4 else { return query }

        // Set QR=1 (response), keep RD from query
        response[2] = (query[2] & 0x01) | 0x81
        // Set RA=1, RCODE=3 (NXDOMAIN)
        response[3] = 0x83
        // Zero answer, authority, additional counts
        if response.count >= 8 {
            response[6] = 0; response[7] = 0 // ANCOUNT
        }
        return Data(response)
    }
}
