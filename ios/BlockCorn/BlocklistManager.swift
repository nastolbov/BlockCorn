import Foundation

/// Fetches and caches the StevenBlack porn blocklist.
/// Results shared with the DNS Proxy extension via App Group container.
final class BlocklistManager {

    static let shared = BlocklistManager()

    private let url = URL(string:
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts")!
    private let ttl: TimeInterval = 24 * 60 * 60
    private let maxDomains = 29_000

    // Shared App Group container so the Network Extension can read the blocklist
    private var containerURL: URL {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: "group.com.blockcorn")!
            .appendingPathComponent("blocklist.hosts")
    }

    private(set) var blockedDomains: Set<String> = []

    private init() {}

    // MARK: - Public

    func loadOrFetch(completion: @escaping (Int) -> Void) {
        DispatchQueue.global(qos: .utility).async {
            let domains: Set<String>
            if let cached = self.loadCache() {
                domains = cached
            } else {
                domains = (try? self.fetch()) ?? []
            }
            DispatchQueue.main.async {
                self.blockedDomains = domains
                completion(domains.count)
            }
        }
    }

    func isBlocked(_ domain: String) -> Bool {
        var d = domain.lowercased()
        if d.hasPrefix("www.") { d = String(d.dropFirst(4)) }
        return blockedDomains.contains(d)
    }

    // MARK: - Private

    private func loadCache() -> Set<String>? {
        guard FileManager.default.fileExists(atPath: containerURL.path) else { return nil }
        let attrs = try? FileManager.default.attributesOfItem(atPath: containerURL.path)
        if let modified = attrs?[.modificationDate] as? Date,
           Date().timeIntervalSince(modified) < ttl,
           let raw = try? String(contentsOf: containerURL) {
            return parseDomains(raw)
        }
        return nil
    }

    private func fetch() throws -> Set<String> {
        let raw = try String(contentsOf: url, encoding: .utf8)
        try FileManager.default.createDirectory(
            at: containerURL.deletingLastPathComponent(),
            withIntermediateDirectories: true)
        try raw.write(to: containerURL, atomically: true, encoding: .utf8)
        return parseDomains(raw)
    }

    private func parseDomains(_ raw: String) -> Set<String> {
        var result = Set<String>()
        for line in raw.split(separator: "\n") {
            let t = line.trimmingCharacters(in: .whitespaces)
            guard !t.isEmpty, !t.hasPrefix("#") else { continue }
            let parts = t.split(separator: " ")
            guard parts.count >= 2,
                  parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1" else { continue }
            var domain = String(parts[1]).lowercased()
            if domain.hasPrefix("www.") { domain = String(domain.dropFirst(4)) }
            if domain.isEmpty || domain == "localhost" { continue }
            result.insert(domain)
            if result.count >= maxDomains { break }
        }
        return result
    }
}
